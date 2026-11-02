package com.solventa.app.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.app.domain.policies.PolicyCard
import com.solventa.app.domain.policies.PolicyStore
import com.solventa.app.domain.policies.PolicySync
import com.solventa.app.domain.policies.SessionEndReason
import com.solventa.app.domain.policies.StoredCopy
import com.solventa.app.domain.policies.SyncOutcome
import com.solventa.app.domain.policies.WalletCopy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class WalletCard(val policy: PolicyCard, val expired: Boolean)

data class WalletUiState(
    val loading: Boolean = true,
    val policies: List<WalletCard> = emptyList(),
    val offline: Boolean = false,
    val needsConnection: Boolean = false,
    val sessionEnded: SessionEndReason? = null,
    val lastSync: Instant? = null,
    val stale: Boolean = false,
    val copyDiscarded: Boolean = false,
    val copyUnavailable: Boolean = false,
) {
    val empty: Boolean get() = !loading && !needsConnection && !copyUnavailable && sessionEnded == null && policies.isEmpty()
}

/**
 * Billetera de pólizas (HU-MOV-04): sincroniza al abrirse y, sin red o con el servidor caído, muestra la copia
 * de la cuenta con el aviso de sin conexión. La copia guarda la hora de su sincronización y se marca
 * desactualizada pasadas 24 horas (HU-MOV-06); una copia descartada o no disponible tiene su propio aviso
 * (HU-MOV-05). Sin conexión pide la sincronización en segundo plano y muestra su resultado (HU-MOV-07).
 */
class WalletViewModel(
    private val policySync: PolicySync,
    private val policyStore: PolicyStore,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var account: String? = null
    private var unavailableAccount: String? = null
    private var lastSyncAttempt: Instant? = null

    init {
        // Lo que sincroniza el trabajo en segundo plano llega aquí; solo se aplica a la cuenta abierta.
        viewModelScope.launch { policySync.outcomes.collect(::apply) }
    }

    /** Si la cuenta ya estaba abierta, sigue mostrando lo que tenía mientras sincroniza (AC-17). */
    fun open(accountEmail: String) {
        val previous = _uiState.value
        val reopening = accountEmail == account && !previous.loading
        close()
        account = accountEmail
        if (reopening) _uiState.value = previous
        sync(accountEmail)
    }

    /** Vuelve a leer una copia que no estaba disponible (HU-MOV-05, AC-13). */
    fun retry() {
        val accountEmail = unavailableAccount ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { _uiState.value = fromCopy(accountEmail) }
    }

    /** Cancela la carga antes de cerrar sesión, para que ningún guardado llegue después del borrado (HU-MOV-05). */
    fun close() {
        loadJob?.cancel()
        account = null
        unavailableAccount = null
        _uiState.value = WalletUiState()
    }

    /**
     * Al volver a la app recalcula el aviso y las pólizas vencidas (HU-MOV-06) y, sin conexión, sincroniza
     * enseguida a lo sumo cada 30 s, porque con el teléfono bloqueado la espera del trabajo crece (AC-13).
     */
    fun refreshClock() {
        if (unavailableAccount != null) return retry()
        val state = _uiState.value
        if (state.loading) return
        val now = clock.instant()
        _uiState.value = state.copy(
            policies = state.policies.map { it.copy(expired = it.policy.isExpiredAt(now)) },
            stale = state.lastSync?.let(::isStale) ?: false,
        )
        val accountEmail = account ?: return
        val recent = lastSyncAttempt?.let { Duration.between(it, now) < RESYNC_EVERY } ?: false
        if (state.offline && !recent) sync(accountEmail)
    }

    private fun sync(accountEmail: String) {
        loadJob?.cancel()
        lastSyncAttempt = clock.instant()
        loadJob = viewModelScope.launch {
            when (val outcome = policySync.sync()) {
                is SyncOutcome.Synced, is SyncOutcome.SessionEnded -> apply(outcome)
                SyncOutcome.Failed, SyncOutcome.NoSession -> {
                    _uiState.value = fromCopy(accountEmail)
                    policySync.schedule()
                }
            }
        }
    }

    private fun apply(outcome: SyncOutcome) {
        when (outcome) {
            is SyncOutcome.Synced -> if (outcome.accountEmail == account) {
                unavailableAccount = null
                _uiState.value = synced(outcome.copy)
            }
            is SyncOutcome.SessionEnded -> if (outcome.accountEmail == account) {
                _uiState.value = WalletUiState(loading = false, sessionEnded = outcome.reason)
            }
            SyncOutcome.Failed, SyncOutcome.NoSession -> Unit
        }
    }

    private fun synced(copy: WalletCopy) =
        WalletUiState(loading = false, policies = cards(copy.policies), lastSync = copy.syncedAt, stale = isStale(copy.syncedAt))

    private suspend fun fromCopy(accountEmail: String): WalletUiState =
        when (val stored = policyStore.read(accountEmail)) {
            is StoredCopy.Found -> WalletUiState(
                loading = false,
                offline = true,
                policies = cards(stored.copy.policies),
                lastSync = stored.copy.syncedAt,
                stale = isStale(stored.copy.syncedAt),
            )
            StoredCopy.Missing -> WalletUiState(loading = false, offline = true, needsConnection = true)
            StoredCopy.Discarded -> WalletUiState(loading = false, offline = true, needsConnection = true, copyDiscarded = true)
            StoredCopy.Unavailable -> {
                unavailableAccount = accountEmail
                WalletUiState(loading = false, offline = true, copyUnavailable = true)
            }
        }

    // Una hora guardada en el futuro significa un reloj atrasado: se avisa igual (HU-MOV-06, AC-06).
    private fun isStale(syncedAt: Instant): Boolean {
        val now = clock.instant()
        return syncedAt.isAfter(now) || Duration.between(syncedAt, now) > STALE_AFTER
    }

    private fun cards(policies: List<PolicyCard>): List<WalletCard> {
        val now = clock.instant()
        return policies.map { WalletCard(it, it.isExpiredAt(now)) }
    }

    companion object {
        val STALE_AFTER: Duration = Duration.ofHours(24)
        private val RESYNC_EVERY: Duration = Duration.ofSeconds(30)
    }
}
