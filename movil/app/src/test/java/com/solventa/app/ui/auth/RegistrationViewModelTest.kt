package com.solventa.app.ui.auth

import com.solventa.app.domain.auth.AuthApi
import com.solventa.app.domain.auth.DeviceCredential
import com.solventa.app.domain.auth.DocumentType
import com.solventa.app.domain.auth.IdentityPhotos
import com.solventa.app.domain.auth.LoginResult
import com.solventa.app.domain.auth.OtpResult
import com.solventa.app.domain.auth.RefreshResult
import com.solventa.app.domain.auth.RegistrationForm
import com.solventa.app.domain.auth.RegistrationResult
import com.solventa.app.domain.auth.ResetResult
import com.solventa.app.domain.auth.SessionTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {

    private val tokens = SessionTokens("access", "refresh", "report")
    private val document = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1)
    private val selfie = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 2)
    private val draft = RegistrationDraft(
        fullName = "Ana Prueba Registro",
        email = "Ana.Registro@Example.com",
        documentType = DocumentType.CC,
        documentNumber = "1098765432",
        phone = "3000000000",
        password = "Registro2026",
        confirmation = "Registro2026",
        termsAccepted = true,
    )

    private lateinit var api: FakeRegistrationApi
    private lateinit var viewModel: RegistrationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api = FakeRegistrationApi()
        viewModel = RegistrationViewModel(api)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    private fun untilConsent() {
        viewModel.updateDraft(draft)
        viewModel.submitData()
        viewModel.submitCode("417902")
        viewModel.capture { document.copyOf() }
        viewModel.capture { selfie.copyOf() }
        viewModel.setConsent(true)
    }

    @Test
    fun `los datos completos piden el codigo al celular y pasan al paso del codigo`() {
        viewModel.updateDraft(draft)

        viewModel.submitData()

        assertEquals(RegistrationStep.CODE, state.step)
        assertEquals("417902", state.debugCode)
        assertEquals(Triple(DocumentType.CC, "1098765432", "3000000000"), api.otpRequest)
    }

    @Test
    fun `datos incompletos o una clave debil no piden codigo`() {
        listOf(
            draft.copy(email = "sin-arroba"),
            draft.copy(documentNumber = "12AB45"),
            draft.copy(phone = "1234567890"),
            draft.copy(password = "solo-letras", confirmation = "solo-letras"),
            draft.copy(confirmation = "Otra2026"),
            draft.copy(termsAccepted = false),
        ).forEach { incomplete ->
            viewModel.updateDraft(incomplete)
            viewModel.submitData()

            assertEquals(RegistrationStep.DATA, state.step)
            assertEquals(RegistrationError.INVALID_DATA, state.error)
        }
        assertNull(api.otpRequest)
    }

    @Test
    fun `el pasaporte acepta letras y la cedula solo numeros`() {
        assertTrue(draft.copy(documentType = DocumentType.PA, documentNumber = "AB12345").complete)
        assertFalse(draft.copy(documentType = DocumentType.CC, documentNumber = "AB12345").complete)
    }

    @Test
    fun `los errores al pedir el codigo se muestran sin avanzar`() {
        mapOf(
            OtpResult.TooManyRequests(60) to RegistrationError.TOO_MANY_REQUESTS,
            OtpResult.SendFailed to RegistrationError.SMS_UNAVAILABLE,
            OtpResult.Failed to RegistrationError.NETWORK,
        ).forEach { (result, error) ->
            api.otpResult = result
            viewModel.updateDraft(draft)
            viewModel.submitData()

            assertEquals(RegistrationStep.DATA, state.step)
            assertEquals(error, state.error)
            assertFalse(state.busy)
        }
    }

    @Test
    fun `el codigo pasa a la foto del documento y luego a la selfie y a la autorizacion`() {
        viewModel.updateDraft(draft)
        viewModel.submitData()

        viewModel.submitCode("12")
        assertEquals(RegistrationStep.CODE, state.step)
        viewModel.submitCode("417902")
        assertEquals(RegistrationStep.DOCUMENT, state.step)
        viewModel.capture { document.copyOf() }
        assertEquals(RegistrationStep.SELFIE, state.step)
        viewModel.capture { selfie.copyOf() }
        assertEquals(RegistrationStep.CONSENT, state.step)
        assertEquals(2, viewModel.photosInMemory())
    }

    @Test
    fun `una foto fallida se avisa y se queda en el mismo paso`() {
        viewModel.updateDraft(draft)
        viewModel.submitData()
        viewModel.submitCode("417902")

        viewModel.capture { null }

        assertEquals(RegistrationStep.DOCUMENT, state.step)
        assertEquals(RegistrationError.CAPTURE_FAILED, state.error)
        assertEquals(0, viewModel.photosInMemory())
    }

    @Test
    fun `sin autorizacion biometrica no se envia`() {
        untilConsent()
        viewModel.setConsent(false)

        viewModel.submit()

        assertNull(api.registered)
        assertEquals(RegistrationStep.CONSENT, state.step)
    }

    @Test
    fun `aprobada envia los datos y las dos fotos y entrega la sesion una sola vez`() {
        api.registerResult = RegistrationResult.Registered(tokens, "Ana Prueba Registro", pending = false)
        untilConsent()

        viewModel.submit()

        val (form, photos) = api.registered!!
        assertEquals("Ana.Registro@Example.com", form.email)
        assertEquals("417902", form.otpCode)
        assertEquals("Registro2026", form.password)
        assertArrayEquals(document, api.sentFront)
        assertArrayEquals(selfie, api.sentSelfie)
        assertTrue(photos.documentFront.all { it == 0.toByte() })
        assertEquals(RegistrationStep.RESULT, state.step)
        assertEquals(RegistrationOutcome.APPROVED, state.outcome)
        assertEquals("", state.draft.password)
        assertEquals(0, viewModel.photosInMemory())
        val session = viewModel.takeSession()!!
        assertEquals("ana.registro@example.com", session.email)
        assertEquals(tokens, session.tokens)
        assertNull(viewModel.takeSession())
    }

    @Test
    fun `pendiente tambien abre la sesion y muestra que se esta verificando`() {
        api.registerResult = RegistrationResult.Registered(tokens, "Ana Prueba Registro", pending = true)
        untilConsent()

        viewModel.submit()

        assertEquals(RegistrationOutcome.PENDING, state.outcome)
        assertEquals(tokens, viewModel.takeSession()?.tokens)
    }

    @Test
    fun `rechazada no entrega sesion y borra las fotos`() {
        api.registerResult = RegistrationResult.Rejected
        untilConsent()

        viewModel.submit()

        assertEquals(RegistrationOutcome.REJECTED, state.outcome)
        assertNull(viewModel.takeSession())
        assertEquals(0, viewModel.photosInMemory())
    }

    @Test
    fun `cada error del servidor vuelve al paso que hay que corregir`() {
        mapOf(
            RegistrationResult.InvalidCode to (RegistrationError.INVALID_CODE to RegistrationStep.CODE),
            RegistrationResult.WeakPassword to (RegistrationError.WEAK_PASSWORD to RegistrationStep.DATA),
            RegistrationResult.InvalidData to (RegistrationError.INVALID_DATA to RegistrationStep.DATA),
            RegistrationResult.Duplicate to (RegistrationError.DUPLICATE to RegistrationStep.DATA),
            RegistrationResult.TooManyRequests(60) to (RegistrationError.TOO_MANY_REQUESTS to RegistrationStep.CONSENT),
        ).forEach { (result, expected) ->
            viewModel.open()
            api.registerResult = result
            untilConsent()

            viewModel.submit()

            assertEquals(expected, state.error to state.step)
            assertNull(viewModel.takeSession())
        }
    }

    @Test
    fun `sin red se puede reintentar con las fotos que siguen en memoria`() {
        api.registerResult = RegistrationResult.NetworkError
        untilConsent()

        viewModel.submit()
        assertEquals(RegistrationError.NETWORK, state.error)
        assertEquals(RegistrationStep.CONSENT, state.step)
        assertEquals(2, viewModel.photosInMemory())

        api.registerResult = RegistrationResult.Registered(tokens, "Ana Prueba Registro", pending = false)
        viewModel.submit()
        assertEquals(RegistrationOutcome.APPROVED, state.outcome)
    }

    @Test
    fun `atras recorre los pasos y en el primero pide cerrar`() {
        untilConsent()

        assertTrue(viewModel.back())
        assertEquals(RegistrationStep.SELFIE, state.step)
        assertTrue(viewModel.back())
        assertTrue(viewModel.back())
        assertTrue(viewModel.back())
        assertEquals(RegistrationStep.DATA, state.step)
        assertFalse(viewModel.back())
    }

    @Test
    fun `cerrar el registro borra las fotos, el codigo y los datos`() {
        untilConsent()

        viewModel.close()

        assertEquals(0, viewModel.photosInMemory())
        assertEquals(RegistrationUiState(), state)
        assertFalse(state.toString().contains("1098765432"))
    }

    private class FakeRegistrationApi : AuthApi {
        var otpResult: OtpResult = OtpResult.Sent("417902")
        var registerResult: RegistrationResult = RegistrationResult.Registered(
            SessionTokens("a", "r"), "Ana", pending = false,
        )
        var otpRequest: Triple<DocumentType, String, String>? = null
        var registered: Pair<RegistrationForm, IdentityPhotos>? = null
        var sentFront: ByteArray? = null
        var sentSelfie: ByteArray? = null

        override suspend fun requestRegistrationOtp(documentType: DocumentType, documentNumber: String, phone: String): OtpResult {
            otpRequest = Triple(documentType, documentNumber, phone)
            return otpResult
        }

        override suspend fun register(form: RegistrationForm, photos: IdentityPhotos): RegistrationResult {
            registered = form to photos
            sentFront = photos.documentFront.copyOf()
            sentSelfie = photos.selfie.copyOf()
            return registerResult
        }

        override suspend fun requestOtp(email: String) = OtpResult.Failed
        override suspend fun login(email: String, password: String, otpCode: String) = LoginResult.NetworkError
        override suspend fun logout(refreshToken: String, keepReporting: Boolean) = false
        override suspend fun refresh(refreshToken: String) = RefreshResult.Unavailable
        override suspend fun registerDevice(accessToken: String): DeviceCredential? = null
        override suspend fun biometricLogin(credential: DeviceCredential) = LoginResult.NetworkError
        override suspend fun revokeDevice(accessToken: String, deviceId: String) = false
        override suspend fun forgotPassword(email: String) = OtpResult.Failed
        override suspend fun resetPassword(email: String, code: String, newPassword: String) = ResetResult.NetworkError
    }
}
