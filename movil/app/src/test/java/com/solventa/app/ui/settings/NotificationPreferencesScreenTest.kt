package com.solventa.app.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.PHONE
import com.solventa.app.R
import com.solventa.app.domain.policies.NoticeCategory
import com.solventa.app.domain.policies.PushPreferences
import com.solventa.app.ui.theme.SolventaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Pantalla de preferencias de avisos y su lectura con TalkBack (FE-MOV-04).
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class NotificationPreferencesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun row(label: Int, help: Int) = compose.onNodeWithContentDescription("${s(label)}. ${s(help)}")

    private fun show(
        state: NotificationPreferencesUiState,
        onToggle: (NoticeCategory, Boolean) -> Unit = { _, _ -> },
        onRetry: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        compose.setContent {
            SolventaTheme { NotificationPreferencesScreen(state = state, onBack = onBack, onToggle = onToggle, onRetry = onRetry) }
        }
    }

    @Test
    fun `muestra tres interruptores con su nombre, su ayuda y su estado, de al menos 48 dp`() {
        val toggled = mutableListOf<Pair<NoticeCategory, Boolean>>()
        show(NotificationPreferencesUiState(preferences = PushPreferences(renewals = false), loading = false), onToggle = { c, v -> toggled += c to v })

        row(R.string.notices_claims, R.string.notices_claims_help).assertIsDisplayed().assertIsOn().assertHeightIsAtLeast(48.dp)
        row(R.string.notices_renewals, R.string.notices_renewals_help).assertIsOff().assertHeightIsAtLeast(48.dp)
        row(R.string.notices_payments, R.string.notices_payments_help).assertIsOn().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(s(R.string.notices_title)).assertIsDisplayed()

        row(R.string.notices_claims, R.string.notices_claims_help).performClick()
        row(R.string.notices_renewals, R.string.notices_renewals_help).performClick()

        assertEquals(listOf(NoticeCategory.CLAIMS to false, NoticeCategory.RENEWALS to true), toggled)
    }

    @Test
    fun `Guardado se anuncia por TalkBack al guardarse`() {
        show(NotificationPreferencesUiState(loading = false, saved = true))

        compose.onNode(hasText(s(R.string.notices_saved)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertIsDisplayed()
    }

    @Test
    fun `un error muestra el aviso y el boton Reintentar de 48 dp`() {
        var retried = false
        show(NotificationPreferencesUiState(loading = false, error = PreferencesError.UNAVAILABLE), onRetry = { retried = true })

        compose.onNode(hasAnyDescendant(hasText(s(R.string.notices_error))) and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion), useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithText(s(R.string.notices_retry)).assertHeightIsAtLeast(48.dp).performClick()

        assertTrue(retried)
    }

    @Test
    fun `sin red muestra la copia local con el aviso de que se guardara al volver la red`() {
        var retried = false
        show(NotificationPreferencesUiState(preferences = PushPreferences(payments = false), loading = false, offline = true), onRetry = { retried = true })

        compose.onNodeWithText(s(R.string.notices_offline)).assertIsDisplayed()
        row(R.string.notices_payments, R.string.notices_payments_help).assertIsOff()
        compose.onNodeWithText(s(R.string.notices_retry)).performClick()
        assertTrue(retried)
    }

    @Test
    fun `mientras carga anuncia la carga y Atras cierra la pantalla`() {
        var closed = false
        show(NotificationPreferencesUiState(), onBack = { closed = true })

        compose.onNodeWithContentDescription(s(R.string.notices_loading)).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.claim_back)).performClick()

        assertTrue(closed)
    }

    @Test
    fun `FE-MOV-01 revocar la autorizacion biometrica pide confirmacion en un dialogo`() {
        var asked = false
        var confirmed = false
        var cancelled = false
        var state by mutableStateOf(NotificationPreferencesUiState(loading = false))
        compose.setContent {
            SolventaTheme {
                NotificationPreferencesScreen(
                    state = state, onBack = {}, onToggle = { _, _ -> }, onRetry = {},
                    onAskRevocation = { asked = true }, onConfirmRevocation = { confirmed = true },
                    onCancelRevocation = { cancelled = true },
                )
            }
        }

        compose.onNodeWithText(s(R.string.biometric_consent_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(s(R.string.biometric_consent_revoke)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(asked)

        state = state.copy(revocation = BiometricRevocation.CONFIRMING)
        compose.onNodeWithText(s(R.string.biometric_consent_confirm_title)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.biometric_consent_cancel)).performClick()
        compose.onNodeWithText(s(R.string.biometric_consent_confirm)).performClick()
        assertTrue(cancelled && confirmed)

        state = state.copy(revocation = BiometricRevocation.REVOKED)
        compose.onNodeWithText(s(R.string.biometric_consent_revoked)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(s(R.string.biometric_consent_revoke)).assertDoesNotExist()
    }
}
