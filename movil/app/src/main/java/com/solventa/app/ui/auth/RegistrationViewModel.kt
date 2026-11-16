package com.solventa.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.app.domain.auth.AuthApi
import com.solventa.app.domain.auth.DocumentType
import com.solventa.app.domain.auth.IdentityPhotos
import com.solventa.app.domain.auth.OtpResult
import com.solventa.app.domain.auth.RegistrationForm
import com.solventa.app.domain.auth.RegistrationResult
import com.solventa.app.domain.auth.RegistrationRules
import com.solventa.app.domain.auth.SessionTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RegistrationStep { DATA, CODE, DOCUMENT, SELFIE, CONSENT, RESULT }

enum class RegistrationOutcome { APPROVED, PENDING, REJECTED }

enum class RegistrationError {
    INVALID_DATA,
    INVALID_CODE,
    WEAK_PASSWORD,
    DUPLICATE,
    TOO_MANY_REQUESTS,
    SMS_UNAVAILABLE,
    NETWORK,
    CAPTURE_FAILED,
    /** El teléfono no tiene la cámara del paso (la frontal para la selfie) o no se pudo abrir. */
    CAMERA_UNAVAILABLE,
}

/** Lo que escribe la persona; vive solo en memoria, nunca en el estado que guarda el sistema. */
data class RegistrationDraft(
    val fullName: String = "",
    val email: String = "",
    val documentType: DocumentType = DocumentType.CC,
    val documentNumber: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmation: String = "",
    val termsAccepted: Boolean = false,
) {
    val complete: Boolean
        get() = RegistrationRules.validName(fullName) && RegistrationRules.validEmail(email) &&
            RegistrationRules.validDocument(documentType, documentNumber) && RegistrationRules.validPhone(phone) &&
            passwordRulesMet(password, confirmation) && termsAccepted

    override fun toString(): String = "RegistrationDraft(***)"
}

data class RegistrationUiState(
    val step: RegistrationStep = RegistrationStep.DATA,
    val draft: RegistrationDraft = RegistrationDraft(),
    val busy: Boolean = false,
    val debugCode: String? = null,
    val consentAccepted: Boolean = false,
    val outcome: RegistrationOutcome? = null,
    val error: RegistrationError? = null,
) {
    val stepNumber: Int get() = step.ordinal + 1

    override fun toString(): String = "RegistrationUiState(step=$step, busy=$busy, outcome=$outcome, error=$error)"
}

class RegisteredSession(val email: String, val tokens: SessionTokens, val fullName: String)

/**
 * Registro en seis pasos: datos, código SMS, foto del documento, selfie, autorización biométrica y resultado
 * (mejora FE-MOV-01). Las fotos y la clave solo existen en memoria y se borran al terminar o al salir.
 */
class RegistrationViewModel(private val authApi: AuthApi) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    private var otpCode = ""
    private var documentPhoto: ByteArray? = null
    private var selfiePhoto: ByteArray? = null
    private var session: RegisteredSession? = null

    fun open() {
        clearSecrets()
        _uiState.value = RegistrationUiState()
    }

    fun updateDraft(draft: RegistrationDraft) {
        _uiState.update { it.copy(draft = draft, error = null) }
    }

    fun submitData() {
        val state = _uiState.value
        if (state.busy) return
        if (!state.draft.complete) {
            _uiState.update { it.copy(error = RegistrationError.INVALID_DATA) }
            return
        }
        requestCode()
    }

    fun resendCode() {
        if (!_uiState.value.busy) requestCode()
    }

    private fun requestCode() {
        val draft = _uiState.value.draft
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = authApi.requestRegistrationOtp(draft.documentType, draft.documentNumber, draft.phone)
            _uiState.update { state ->
                when (result) {
                    is OtpResult.Sent -> state.copy(busy = false, step = RegistrationStep.CODE, debugCode = result.debugCode)
                    is OtpResult.TooManyRequests -> state.copy(busy = false, error = RegistrationError.TOO_MANY_REQUESTS)
                    OtpResult.SendFailed -> state.copy(busy = false, error = RegistrationError.SMS_UNAVAILABLE)
                    OtpResult.Failed -> state.copy(busy = false, error = RegistrationError.NETWORK)
                }
            }
        }
    }

    fun submitCode(code: String) {
        if (code.length != CODE_LENGTH || !code.all(Char::isDigit)) return
        otpCode = code
        _uiState.update { it.copy(step = RegistrationStep.DOCUMENT, error = null) }
    }

    /** Toma la foto del paso actual con la cámara de la app; el resultado queda solo en memoria. */
    fun capture(shoot: suspend () -> ByteArray?) {
        val step = _uiState.value.step
        if (_uiState.value.busy || _uiState.value.error == RegistrationError.CAMERA_UNAVAILABLE) return
        if (step != RegistrationStep.DOCUMENT && step != RegistrationStep.SELFIE) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val jpeg = shoot()
            if (jpeg == null) {
                _uiState.update { it.copy(busy = false, error = RegistrationError.CAPTURE_FAILED) }
                return@launch
            }
            if (step == RegistrationStep.DOCUMENT) {
                documentPhoto?.fill(0)
                documentPhoto = jpeg
            } else {
                selfiePhoto?.fill(0)
                selfiePhoto = jpeg
            }
            val next = if (step == RegistrationStep.DOCUMENT) RegistrationStep.SELFIE else RegistrationStep.CONSENT
            _uiState.update { it.copy(busy = false, step = next) }
        }
    }

    /** La cámara del paso no existe o no abrió: se avisa con una salida, no se ofrece un obturador que siempre falla. */
    fun cameraUnavailable() {
        val step = _uiState.value.step
        if (step != RegistrationStep.DOCUMENT && step != RegistrationStep.SELFIE) return
        _uiState.update { it.copy(busy = false, error = RegistrationError.CAMERA_UNAVAILABLE) }
    }

    fun setConsent(accepted: Boolean) {
        _uiState.update { it.copy(consentAccepted = accepted, error = null) }
    }

    fun submit() {
        val state = _uiState.value
        val front = documentPhoto
        val selfie = selfiePhoto
        if (state.busy || !state.consentAccepted || front == null || selfie == null) return
        val draft = state.draft
        val form = RegistrationForm(
            email = draft.email.trim(),
            fullName = draft.fullName.trim(),
            documentType = draft.documentType,
            documentNumber = draft.documentNumber,
            phone = draft.phone,
            password = draft.password,
            otpCode = otpCode,
        )
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = authApi.register(form, IdentityPhotos(front, selfie))
            when (result) {
                is RegistrationResult.Registered -> {
                    session = RegisteredSession(normalizeEmail(draft.email), result.tokens, result.fullName)
                    finish(if (result.pending) RegistrationOutcome.PENDING else RegistrationOutcome.APPROVED)
                }
                RegistrationResult.Rejected -> finish(RegistrationOutcome.REJECTED)
                RegistrationResult.InvalidCode -> fail(RegistrationError.INVALID_CODE, RegistrationStep.CODE)
                RegistrationResult.WeakPassword -> fail(RegistrationError.WEAK_PASSWORD, RegistrationStep.DATA)
                RegistrationResult.InvalidData -> fail(RegistrationError.INVALID_DATA, RegistrationStep.DATA)
                RegistrationResult.Duplicate -> fail(RegistrationError.DUPLICATE, RegistrationStep.DATA)
                is RegistrationResult.TooManyRequests -> fail(RegistrationError.TOO_MANY_REQUESTS, RegistrationStep.CONSENT)
                RegistrationResult.NetworkError -> fail(RegistrationError.NETWORK, RegistrationStep.CONSENT)
            }
        }
    }

    // Con un resultado del servidor las fotos y la clave ya no hacen falta.
    private fun finish(outcome: RegistrationOutcome) {
        clearPhotos()
        otpCode = ""
        _uiState.update {
            it.copy(
                busy = false,
                step = RegistrationStep.RESULT,
                outcome = outcome,
                draft = it.draft.copy(password = "", confirmation = ""),
            )
        }
    }

    private fun fail(error: RegistrationError, step: RegistrationStep) {
        _uiState.update { it.copy(busy = false, error = error, step = step) }
    }

    /** La sesión del registro aprobado o pendiente; se entrega una sola vez. */
    fun takeSession(): RegisteredSession? = session.also { session = null }

    /** false cuando no hay paso anterior y hay que cerrar el registro. En el resultado, «Atrás» lo decide la
     *  pantalla: con la cuenta creada continúa como «Entrar a la app», nunca descarta la sesión. */
    fun back(): Boolean {
        val previous = when (_uiState.value.step) {
            RegistrationStep.DATA, RegistrationStep.RESULT -> null
            RegistrationStep.CODE -> RegistrationStep.DATA
            RegistrationStep.DOCUMENT -> RegistrationStep.CODE
            RegistrationStep.SELFIE -> RegistrationStep.DOCUMENT
            RegistrationStep.CONSENT -> RegistrationStep.SELFIE
        } ?: return false
        if (_uiState.value.busy) return true
        _uiState.update { it.copy(step = previous, error = null) }
        return true
    }

    fun close() {
        clearSecrets()
        _uiState.value = RegistrationUiState()
    }

    internal fun photosInMemory(): Int = listOfNotNull(documentPhoto, selfiePhoto).size

    private fun clearPhotos() {
        documentPhoto?.fill(0)
        selfiePhoto?.fill(0)
        documentPhoto = null
        selfiePhoto = null
    }

    private fun clearSecrets() {
        clearPhotos()
        otpCode = ""
        session = null
    }

    override fun onCleared() = clearSecrets()

    private companion object {
        const val CODE_LENGTH = 6
    }
}
