package com.solventa.app.ui.auth

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillTree
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.PHONE
import com.solventa.app.R
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
import com.solventa.app.ui.claims.EvidenceCamera
import com.solventa.app.ui.theme.SolventaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cámaras del registro dentro del flujo completo (mejora FE-MOV-01): cada paso crea su propia vista previa, un
 * teléfono sin cámara frontal recibe un aviso con salida y «Atrás» en el resultado no descarta la cuenta creada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class RegistrationCameraTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    private val draft = RegistrationDraft(
        fullName = "Ana Prueba Registro",
        email = "ana.registro@example.com",
        documentNumber = "1098765432",
        phone = "3000000000",
        password = "Registro2026",
        confirmation = "Registro2026",
        termsAccepted = true,
    )

    private val viewModel = RegistrationViewModel(StubRegistrationApi())

    private var closed = 0
    private var finished: RegisteredSession? = null

    private fun showFlow(documentCamera: EvidenceCamera, selfieCamera: EvidenceCamera) {
        compose.setContent {
            SolventaTheme {
                RegistrationFlow(
                    viewModel = viewModel,
                    documentCamera = documentCamera,
                    selfieCamera = selfieCamera,
                    onClose = { closed++ },
                    onFinished = { finished = it },
                    cameraGranted = { true },
                )
            }
        }
    }

    private fun untilDocument() {
        compose.runOnIdle {
            viewModel.updateDraft(draft)
            viewModel.submitData()
            viewModel.submitCode("417902")
        }
        compose.waitForIdle()
    }

    private fun shoot() {
        compose.onNodeWithContentDescription(s(R.string.register_shutter)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `al pasar del documento a la selfie se crea una vista previa nueva con la camara frontal`() {
        val document = CountingCamera()
        val selfie = CountingCamera()
        showFlow(document, selfie)
        untilDocument()
        assertEquals(1, document.started)

        shoot()

        compose.onNodeWithText(s(R.string.register_selfie_hint)).assertIsDisplayed()
        assertEquals(1, selfie.started)
        assertEquals(1, document.stopped)
    }

    @Test
    fun `un telefono sin camara frontal muestra un aviso con salida en lugar del obturador`() {
        showFlow(CountingCamera(), CountingCamera(available = false))
        untilDocument()

        shoot()

        compose.onNodeWithText(s(R.string.register_camera_missing_front)).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.register_shutter)).assertDoesNotExist()
        compose.onNodeWithText(s(R.string.register_leave)).performClick()
        assertEquals(1, closed)
        assertEquals(0, viewModel.photosInMemory())
    }

    @Test
    fun `atras en el resultado entra con la cuenta creada en lugar de cerrar la sesion`() {
        showFlow(CountingCamera(), CountingCamera())
        untilDocument()
        shoot()
        shoot()
        compose.runOnIdle {
            viewModel.setConsent(true)
            viewModel.submit()
        }
        compose.onNodeWithText(s(R.string.register_approved_title)).assertIsDisplayed()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertNotNull(finished)
        assertEquals("ana.registro@example.com", finished?.email)
        assertEquals(0, closed)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `el campo del codigo ofrece autocompletar el codigo del SMS`() {
        val tree = AutofillTree()
        compose.setContent {
            CompositionLocalProvider(LocalAutofillTree provides tree) {
                RegistrationCodeScreen(RegistrationUiState(step = RegistrationStep.CODE), onSubmit = {}, onResend = {}, onBack = {})
            }
        }

        val node = compose.runOnIdle { tree.children.values.single { AutofillType.SmsOtpCode in it.autofillTypes } }
        compose.runOnIdle { tree.performAutofill(node.id, "41-79 02") }

        compose.onNodeWithText("417902").assertIsDisplayed()
        compose.onNodeWithText(s(R.string.register_continue)).assertIsEnabled()
    }

    /** Cámara falsa que cuenta cuántas vistas previas se crearon y se cerraron, como el enlace de CameraX. */
    private class CountingCamera(private val available: Boolean = true) : EvidenceCamera {
        var started = 0
        var stopped = 0

        @Composable
        override fun Preview(modifier: Modifier, onUnavailable: () -> Unit) {
            DisposableEffect(Unit) {
                started++
                if (!available) onUnavailable()
                onDispose { stopped++ }
            }
            Box(modifier)
        }

        override suspend fun capture(): ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1)
    }

    private class StubRegistrationApi : AuthApi {
        override suspend fun requestRegistrationOtp(documentType: DocumentType, documentNumber: String, phone: String) =
            OtpResult.Sent(null)

        override suspend fun register(form: RegistrationForm, photos: IdentityPhotos): RegistrationResult =
            RegistrationResult.Registered(SessionTokens("a", "r", "p"), "Ana", pending = false)

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
