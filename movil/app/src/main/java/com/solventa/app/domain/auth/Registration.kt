package com.solventa.app.domain.auth

/** Registro desde la app con la foto del documento y una selfie (mejora FE-MOV-01). */
enum class DocumentType { CC, CE, PA }

class RegistrationForm(
    val email: String,
    val fullName: String,
    val documentType: DocumentType,
    val documentNumber: String,
    val phone: String,
    val password: String,
    val otpCode: String,
) {
    override fun toString(): String = "RegistrationForm(***)"
}

/** Las fotos solo existen en memoria; nunca se escriben en el teléfono. */
class IdentityPhotos(val documentFront: ByteArray, val selfie: ByteArray) {
    override fun toString(): String = "IdentityPhotos(***)"
}

sealed interface RegistrationResult {
    /** [pending]: la cuenta existe pero la identidad sigue en verificación (202). */
    class Registered(val tokens: SessionTokens, val fullName: String, val pending: Boolean) : RegistrationResult
    data object Rejected : RegistrationResult
    data object InvalidCode : RegistrationResult
    data object WeakPassword : RegistrationResult
    data object InvalidData : RegistrationResult
    data object Duplicate : RegistrationResult
    data class TooManyRequests(val retryAfterSeconds: Int) : RegistrationResult
    data object NetworkError : RegistrationResult
}

object RegistrationRules {
    private val EMAIL = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
    private val PHONE = Regex("""^3\d{9}$""")
    private val DOCUMENT = Regex("""^[0-9A-Z]{5,12}$""")

    fun validEmail(email: String) = email.trim().length in 6..254 && EMAIL.matches(email.trim())

    fun validName(name: String) = name.trim().length in 3..150

    fun validPhone(phone: String) = PHONE.matches(phone)

    fun validDocument(type: DocumentType, number: String) =
        DOCUMENT.matches(number) && (type != DocumentType.CC || number.all(Char::isDigit))
}
