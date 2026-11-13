package com.solventa.app.ui.claims

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.app.domain.claims.ClaimsCopy
import com.solventa.app.domain.claims.InsuredClaim
import com.solventa.app.domain.policies.PolicyStore
import com.solventa.app.domain.policies.PolicySync
import com.solventa.app.domain.policies.SyncOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

data class ClaimsUiState(
    val loading: Boolean = true,
    val claims: List<InsuredClaim> = emptyList(),
    val offline: Boolean = false,
    val lastUpdate: Instant? = null,
    val needsConnection: Boolean = false,
) {
    val empty: Boolean get() = !loading && !needsConnection && claims.isEmpty()

    fun claim(claimNumber: String?): InsuredClaim? = claims.firstOrNull { it.claimNumber == claimNumber }
}

/**
 * Mis siniestros (HU-MOV-11): sincroniza al abrirse y, sin red, muestra la copia cifrada de la cuenta con la
 * fecha de su última actualización (AC-04).
 */
class ClaimsViewModel(
    private val policySync: PolicySync,
    private val policyStore: PolicyStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClaimsUiState())
    val uiState: StateFlow<ClaimsUiState> = _uiState.asStateFlow()

    private var account: String? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            policySync.outcomes.collect { outcome ->
                val claims = (outcome as? SyncOutcome.Synced)?.takeIf { it.accountEmail == account }?.copy?.claims
                if (claims != null) _uiState.value = online(claims)
            }
        }
    }

    fun open(accountEmail: String) {
        val keep = accountEmail == account && !_uiState.value.loading
        loadJob?.cancel()
        account = accountEmail
        if (!keep) _uiState.value = ClaimsUiState()
        loadJob = viewModelScope.launch {
            val outcome = policySync.sync()
            val claims = (outcome as? SyncOutcome.Synced)?.takeIf { it.accountEmail == accountEmail }?.copy?.claims
            _uiState.value = if (claims != null) online(claims) else fromCopy(accountEmail)
            if (outcome is SyncOutcome.Failed || outcome is SyncOutcome.NoSession) policySync.schedule()
        }
    }

    fun close() {
        loadJob?.cancel()
        account = null
        _uiState.value = ClaimsUiState()
    }

    private fun online(copy: ClaimsCopy) = ClaimsUiState(loading = false, claims = copy.claims, lastUpdate = copy.updatedAt)

    private suspend fun fromCopy(accountEmail: String): ClaimsUiState =
        policyStore.readClaims(accountEmail)?.let { ClaimsUiState(loading = false, claims = it.claims, offline = true, lastUpdate = it.updatedAt) }
            ?: ClaimsUiState(loading = false, offline = true, needsConnection = true)
}
