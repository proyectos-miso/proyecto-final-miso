package com.solventa.app.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.app.domain.policies.PoliciesApi
import com.solventa.app.domain.policies.PoliciesResult
import com.solventa.app.domain.policies.PolicyCard
import com.solventa.app.domain.policies.PolicyStore
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
) {
    val empty: Boolean get() = !loading && !needsConnection && !sessionExpired && policies.isEmpty()
}

/**
 * Billetera de pólizas (HU-MOV-04): con red trae las pólizas y guarda la copia de la cuenta; sin red o con
 * el servidor caído muestra esa copia con el aviso de sin conexión. Un 401 borra la copia. La copia guarda
 * la hora de su sincronización y se marca desactualizada pasadas 24 horas (HU-MOV-06).
 */
class WalletViewModel(
    private val policiesApi: PoliciesApi,
    private val policyStore: PolicyStore,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** [accessToken] null es el ingreso sin red: solo se lee la copia. */
    fun open(accountEmail: String, accessToken: String?) {
        loadJob?.cancel()
        _uiState.value = WalletUiState()
        loadJob = viewModelScope.launch {
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

    /** Recalcula el aviso y las pólizas vencidas con la hora actual, por ejemplo al volver a la app (AC-08). */
    fun refreshClock() {
        val state = _uiState.value
        if (state.loading) return
        val now = clock.instant()
        _uiState.value = state.copy(
            policies = state.policies.map { it.copy(expired = it.policy.isExpiredAt(now)) },
            stale = state.lastSync?.let(::isStale) ?: false,
        )
    }

    private suspend fun fromCopy(accountEmail: String): WalletUiState {
        val copy = policyStore.read(accountEmail)
            ?: return WalletUiState(loading = false, offline = true, needsConnection = true)
        return WalletUiState(
            loading = false,
            offline = true,
            policies = cards(copy.policies),
            lastSync = copy.syncedAt,
            stale = isStale(copy.syncedAt),
        )
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
