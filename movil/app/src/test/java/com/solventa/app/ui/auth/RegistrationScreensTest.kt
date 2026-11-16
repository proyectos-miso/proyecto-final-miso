package com.solventa.app.ui.auth

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.PHONE
import com.solventa.app.R
import com.solventa.app.domain.auth.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class RegistrationScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    private val complete = RegistrationDraft(
        fullName = "Ana Prueba Registro",
        email = "ana.registro@example.com",
        documentNumber = "1098765432",
        phone = "3000000000",
        password = "Registro2026",
        confirmation = "Registro2026",
        termsAccepted = true,
    )

    // Área de toque, no la del dibujo: un `IconButton` dibuja 40 dp y reserva 48 dp para el dedo.
    private fun SemanticsNodeInteraction.assertTouchTargetAtLeast48(): SemanticsNodeInteraction {
        val bounds = fetchSemanticsNode().touchBoundsInRoot
        val min = with(compose.density) { 48.dp.toPx() } - 1f
        assertTrue("el área de toque mide ${bounds.width} x ${bounds.height} px", bounds.width >= min && bounds.height >= min)
        return this
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun `los datos tienen etiquetas, el paso y el boton solo se activa con todo completo`() {
        compose.setContent {
            RegistrationDataScreen(RegistrationUiState(), onChange = {}, onSubmit = {}, onBack = {})
        }

        listOf(
            R.string.register_full_name, R.string.auth_email, R.string.register_document_number,
            R.string.register_phone, R.string.recovery_rule_length,
        ).forEach { compose.onAllNodesWithText(s(it), useUnmergedTree = true)[0].assertExists() }
        compose.onNodeWithText(s(R.string.register_step, 1, 5)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.register_continue)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `con los datos completos se puede continuar`() {
        var submitted = 0
        compose.setContent {
            RegistrationDataScreen(RegistrationUiState(draft = complete), onChange = {}, onSubmit = { submitted++ }, onBack = {})
        }

        compose.onNodeWithText(s(R.string.register_continue)).performScrollTo().assertIsEnabled().performClick()

        assertEquals(1, submitted)
    }

    @Test
    fun `el tipo de documento es un grupo de opciones de 48 dp que TalkBack lee como radio`() {
        var changed: RegistrationDraft? = null
        compose.setContent {
            RegistrationDataScreen(RegistrationUiState(), onChange = { changed = it }, onSubmit = {}, onBack = {})
        }

        compose.onNodeWithText(s(R.string.register_document_cc)).assertIsSelected()
        val passport = compose.onNodeWithText(s(R.string.register_document_pa))
        passport.assert(hasRole(Role.RadioButton)).assertHeightIsAtLeast(48.dp)
        passport.performClick()

        assertEquals(DocumentType.PA, changed?.documentType)
    }

    @Test
    fun `los terminos son una casilla de 48 dp con su estado`() {
        compose.setContent {
            RegistrationDataScreen(RegistrationUiState(draft = complete), onChange = {}, onSubmit = {}, onBack = {})
        }

        compose.onNodeWithText(s(R.string.register_terms)).performScrollTo()
            .assert(hasRole(Role.Checkbox)).assertIsOn().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `atras tiene etiqueta y mide 48 dp`() {
        var back = 0
        compose.setContent {
            RegistrationCodeScreen(RegistrationUiState(step = RegistrationStep.CODE), onSubmit = {}, onResend = {}, onBack = { back++ })
        }

        compose.onNodeWithContentDescription(s(R.string.register_back)).assertTouchTargetAtLeast48().performClick()

        assertEquals(1, back)
    }

    @Test
    fun `el codigo muestra el de prueba y el error del servidor`() {
        compose.setContent {
            RegistrationCodeScreen(
                RegistrationUiState(step = RegistrationStep.CODE, debugCode = "417902", error = RegistrationError.INVALID_CODE),
                onSubmit = {}, onResend = {}, onBack = {},
            )
        }

        compose.onNodeWithText(s(R.string.auth_otp_sent_debug, "417902")).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.register_error_code)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.register_code_resend)).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `la camara tiene el obturador con etiqueta para TalkBack`() {
        var shots = 0
        compose.setContent {
            IdentityCameraScreen(
                state = RegistrationUiState(step = RegistrationStep.SELFIE),
                title = R.string.register_selfie_title,
                hint = R.string.register_selfie_hint,
                preview = { Box(it) },
                onShoot = { shots++ },
                onBack = {},
            )
        }

        compose.onNodeWithText(s(R.string.register_selfie_hint)).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.register_shutter))
            .assert(hasRole(Role.Button)).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, shots)
    }

    @Test
    fun `la autorizacion biometrica explica el uso y exige la casilla`() {
        var consent: Boolean? = null
        compose.setContent {
            BiometricConsentScreen(
                RegistrationUiState(step = RegistrationStep.CONSENT), onConsent = { consent = it }, onSubmit = {}, onBack = {},
            )
        }

        compose.onNodeWithText(s(R.string.register_consent_body)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.register_submit)).assertIsNotEnabled()
        compose.onNodeWithText(s(R.string.register_consent_check))
            .assert(hasRole(Role.Checkbox)).assertIsOff().assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(true, consent)
    }

    @Test
    fun `cada resultado muestra su titulo y su accion`() {
        var outcome by mutableStateOf(RegistrationOutcome.APPROVED)
        var entered = 0
        var closed = 0
        compose.setContent { RegistrationResultScreen(outcome, onEnter = { entered++ }, onClose = { closed++ }) }

        compose.onNodeWithText(s(R.string.register_approved_title)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.register_enter)).performClick()
        outcome = RegistrationOutcome.PENDING
        compose.onNodeWithText(s(R.string.register_pending_title)).assertIsDisplayed()
        outcome = RegistrationOutcome.REJECTED
        compose.onNodeWithText(s(R.string.register_rejected_title)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.register_close)).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, entered)
        assertEquals(1, closed)
    }

    @Test
    fun `las pantallas de ingreso ofrecen crear cuenta`() {
        var created = 0
        compose.setContent {
            BiometricLoginScreen(AuthUiState(), onAuthenticate = {}, onUseAlternative = {}, onCreateAccount = { created++ })
        }

        compose.onNodeWithText(s(R.string.register_open)).assertHeightIsAtLeast(48.dp).performClick()

        assertTrue(created == 1)
    }
}
