package com.solventa.app.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.app.domain.policies.PoliciesApi
import com.solventa.app.domain.policies.PoliciesResult
import com.solventa.app.domain.policies.PolicyCard
import com.solventa.app.domain.policies.PolicyStore
import com.solventa.app.domain.policies.StoredCopy
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
    val sessionExpired: Boolean = false,
    val lastSync: Instant? = null,
    val stale: Boolean = false,
    val copyDiscarded: Boolean = false,
    val copyUnavailable: Boolean = false,
) {
    val empty: Boolean get() = !loading && !needsConnection && !copyUnavailable && !sessionExpired && policies.isEmpty()
}

/**
 * Billetera de pólizas (HU-MOV-04): con red trae las pólizas y guarda la copia de la cuenta; sin red o con
 * el servidor caído muestra esa copia con el aviso de sin conexión. Un 401 borra la copia. La copia guarda
 * la hora de su sincronización y se marca desactualizada pasadas 24 horas (HU-MOV-06). Una copia descartada
 * o no disponible tiene su propio aviso (HU-MOV-05).
 */
class WalletViewModel(
    private val policiesApi: PoliciesApi,
    private val policyStore: PolicyStore,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var unavailableAccount: String? = null

    /** [accessToken] null es el ingreso sin red: solo se lee la copia. */
    fun open(accountEmail: String, accessToken: String?) {
        load {
            val result = accessToken?.let { policiesApi.activePolicies(it) } ?: PoliciesResult.Unavailable
            _uiState.value = when (result) {
                is PoliciesResult.Loaded -> {
                    val syncedAt = clock.instant()
                    policyStore.save(accountEmail, result.policies, syncedAt)
                    WalletUiState(loading = false, policies = cards(result.policies), lastSync = syncedAt)
                }
                PoliciesResult.Unauthorized -> {
                    policyStore.delete(accountEmail)
                    WalletUiState(loading = false, sessionExpired = true)
                }
                PoliciesResult.Unavailable -> fromCopy(accountEmail)
            }
        }
    }

    /** Vuelve a leer una copia que no estaba disponible (AC-13). */
    fun retry() {
        val account = unavailableAccount ?: return
        load { _uiState.value = fromCopy(account) }
    }

    /** Cancela la carga antes de cerrar sesión, para que ningún guardado llegue después del borrado (AC-12). */
    fun close() {
        loadJob?.cancel()
        unavailableAccount = null
        _uiState.value = WalletUiState()
    }

    /** Recalcula el aviso y las pólizas vencidas con la hora actual, por ejemplo al volver a la app (AC-08). */
    fun refreshClock() {
        if (unavailableAccount != null) return retry()
        val state = _uiState.value
        if (state.loading) return
        val now = clock.instant()
        _uiState.value = state.copy(
            policies = state.policies.map { it.copy(expired = it.policy.isExpiredAt(now)) },
            stale = state.lastSync?.let(::isStale) ?: false,
        )
    }

    private fun load(block: suspend () -> Unit) {
        close()
        loadJob = viewModelScope.launch { block() }
    }

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

    // Una hora guardada en el futuro significa un reloj atrasado: se avisa igual (AC-06).
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
    }
}
