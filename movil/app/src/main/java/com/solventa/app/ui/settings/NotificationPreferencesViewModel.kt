package com.solventa.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.app.domain.policies.ConsentRevocationResult
import com.solventa.app.domain.policies.NoticeCategory
import com.solventa.app.domain.policies.NotificationPreferences
import com.solventa.app.domain.policies.PolicyActions
import com.solventa.app.domain.policies.PreferencesResult
import com.solventa.app.domain.policies.PushPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PreferencesError { SESSION, INVALID, UNAVAILABLE, IDENTITY_IN_REVIEW }

/** Revocación de la autorización biométrica desde «Avisos» (mejora FE-MOV-01). */
enum class BiometricRevocation { CONFIRMING, REVOKING, REVOKED, FAILED, SESSION }

data class NotificationPreferencesUiState(
    val preferences: PushPreferences = PushPreferences(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    /** Se muestra la copia del teléfono; el último cambio espera a la red. */
    val offline: Boolean = false,
    val saved: Boolean = false,
    val error: PreferencesError? = null,
    val revocation: BiometricRevocation? = null,
)

/**
 * Preferencias de avisos (FE-MOV-04). Al abrirse trae las del servidor y actualiza la copia local; si hay un
 * cambio pendiente lo envía primero. Cada interruptor guarda al instante; sin red el cambio queda en el teléfono
 * marcado como pendiente y se envía con la próxima sincronización, al reabrir la pantalla o con «Reintentar».
 */
class NotificationPreferencesViewModel(
    private val actions: PolicyActions,
    private val store: NotificationPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationPreferencesUiState())
    val uiState: StateFlow<NotificationPreferencesUiState> = _uiState.asStateFlow()

    private var account: String? = null
    private var job: Job? = null

    fun open(accountEmail: String) {
        job?.cancel()
        account = accountEmail
        val local = store.read(accountEmail)
        _uiState.value = NotificationPreferencesUiState(preferences = local, loading = true)
        job = viewModelScope.launch {
            if (store.pending(accountEmail)) {
                apply(accountEmail, local, actions.savePushPreferences(local))
            } else {
                apply(accountEmail, local, actions.pushPreferences())
            }
        }
    }

    fun close() {
        job?.cancel()
        account = null
        _uiState.value = NotificationPreferencesUiState()
    }

    fun toggle(category: NoticeCategory, enabled: Boolean) {
        val accountEmail = account ?: return
        val next = _uiState.value.preferences.with(category, enabled)
        store.save(accountEmail, next, pending = true)
        _uiState.value = _uiState.value.copy(preferences = next, loading = false, saving = true, saved = false, error = null)
        send(accountEmail, next)
    }

    fun retry() {
        val accountEmail = account ?: return
        val current = _uiState.value.preferences
        _uiState.value = _uiState.value.copy(saving = true, saved = false, error = null)
        send(accountEmail, current)
    }

    fun askRevocation() {
        if (_uiState.value.revocation != BiometricRevocation.REVOKING) {
            _uiState.value = _uiState.value.copy(revocation = BiometricRevocation.CONFIRMING)
        }
    }

    fun cancelRevocation() {
        if (_uiState.value.revocation == BiometricRevocation.CONFIRMING) _uiState.value = _uiState.value.copy(revocation = null)
    }

    /** Llama a `POST /insured/consents/biometric/revoke`; no depende de que carguen las preferencias. */
    fun revokeBiometricConsent() {
        if (account == null || _uiState.value.revocation == BiometricRevocation.REVOKING) return
        _uiState.value = _uiState.value.copy(revocation = BiometricRevocation.REVOKING)
        viewModelScope.launch {
            val next = when (actions.revokeBiometricConsent()) {
                ConsentRevocationResult.Revoked -> BiometricRevocation.REVOKED
                ConsentRevocationResult.Unauthorized -> BiometricRevocation.SESSION
                ConsentRevocationResult.Unavailable -> BiometricRevocation.FAILED
            }
            if (account != null) _uiState.value = _uiState.value.copy(revocation = next)
        }
    }

    private fun send(accountEmail: String, preferences: PushPreferences) {
        job?.cancel()
        job = viewModelScope.launch { apply(accountEmail, preferences, actions.savePushPreferences(preferences)) }
    }

    private fun apply(accountEmail: String, sent: PushPreferences, result: PreferencesResult) {
        if (account != accountEmail) return
        val state = _uiState.value
        _uiState.value = when (result) {
            is PreferencesResult.Loaded -> {
                store.save(accountEmail, result.preferences, pending = false)
                state.copy(preferences = result.preferences, loading = false, saving = false, offline = false, saved = state.saving, error = null)
            }
            PreferencesResult.Unavailable -> state.copy(preferences = sent, loading = false, saving = false, offline = true, saved = false)
            PreferencesResult.Unauthorized -> state.copy(preferences = sent, loading = false, saving = false, saved = false, error = PreferencesError.SESSION)
            PreferencesResult.Invalid -> state.copy(preferences = sent, loading = false, saving = false, saved = false, error = PreferencesError.INVALID)
            PreferencesResult.IdentityInReview ->
                state.copy(preferences = sent, loading = false, saving = false, offline = false, saved = false, error = PreferencesError.IDENTITY_IN_REVIEW)
        }
    }
}
