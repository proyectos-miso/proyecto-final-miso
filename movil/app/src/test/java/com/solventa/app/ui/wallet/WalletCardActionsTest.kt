package com.solventa.app.ui.wallet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.PHONE
import com.solventa.app.R
import com.solventa.app.data.policies.samplePolicy
import com.solventa.app.ui.theme.SolventaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

// Acciones de la tarjeta: vencimiento con «Renovar», carátula, avisos y acceso a Mis siniestros (HU-MOV-11: AC-01, AC-09, AC-10).
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class WalletCardActionsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    private val soon = samplePolicy("POL-2026-00001", "2026-11-30T00:00:00Z")

    private val renewed = mutableListOf<String>()
    private val downloaded = mutableListOf<String>()
    private var dismissed = 0
    private var claimsOpened = 0

    private fun show(state: WalletUiState, cardActions: Boolean = true, onOpenClaims: (() -> Unit)? = { claimsOpened++ }) {
        compose.setContent {
            SolventaTheme {
                WalletScreen(
                    uiState = state,
                    fullName = "María Ruiz",
                    biometricEnabled = false,
                    onDisableBiometrics = {},
                    onLogout = {},
                    onOpenClaims = onOpenClaims,
                    cardActions = cardActions,
                    onRenew = { renewed += it },
                    onDownloadCaratula = { downloaded += it },
                    onDismissNotice = { dismissed++ },
                )
            }
        }
    }

    @Test
    fun `AC-09 una poliza por vencer avisa la fecha y renueva`() {
        show(WalletUiState(loading = false, policies = listOf(WalletCard(soon, expired = false, expiresSoon = true))))

        compose.onNodeWithText(s(R.string.wallet_expires_soon, formatShortDate(soon.validUntil))).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.wallet_renew)).performClick()

        assertEquals(listOf("POL-2026-00001"), renewed)
    }

    @Test
    fun `AC-10 descarga la caratula de la poliza mostrada`() {
        show(WalletUiState(loading = false, policies = listOf(WalletCard(samplePolicy(), expired = false))))

        compose.onNodeWithText(s(R.string.wallet_renew)).assertDoesNotExist()
        compose.onNodeWithText(s(R.string.wallet_download_caratula)).performClick()

        assertEquals(listOf("POL-2026-00001"), downloaded)
    }

    @Test
    fun `mientras corre una accion los botones quedan deshabilitados`() {
        show(WalletUiState(loading = false, policies = listOf(WalletCard(soon, false, expiresSoon = true)), actionInProgress = true))

        compose.onNodeWithText(s(R.string.wallet_renew)).assertIsNotEnabled()
        compose.onNodeWithText(s(R.string.wallet_download_caratula)).assertIsNotEnabled()
    }

    @Test
    fun `sin acciones de tarjeta no ofrece renovar ni descargar`() {
        show(WalletUiState(loading = false, policies = listOf(WalletCard(soon, false, expiresSoon = true))), cardActions = false)

        compose.onNodeWithText(s(R.string.wallet_download_caratula)).assertDoesNotExist()
        compose.onNodeWithText(s(R.string.wallet_renew)).assertDoesNotExist()
    }

    @Test
    fun `AC-01 el boton Mis siniestros abre la lista`() {
        show(WalletUiState(loading = false, policies = listOf(WalletCard(samplePolicy(), false))))

        compose.onNodeWithText(s(R.string.claims_open_button)).performScrollTo().assertIsEnabled().performClick()

        assertEquals(1, claimsOpened)
    }

    @Test
    fun `mientras carga no ofrece Mis siniestros`() {
        show(WalletUiState())

        compose.onNodeWithText(s(R.string.claims_open_button)).assertDoesNotExist()
    }

    @Test
    fun `AC-09 la renovacion confirma la nueva vigencia y el aviso se cierra`() {
        val until = Instant.parse("2027-11-30T00:00:00Z")
        show(WalletUiState(loading = false, policies = listOf(WalletCard(samplePolicy(), false)), notice = WalletNotice.RENEWED, renewedUntil = until))

        compose.onNodeWithText(s(R.string.wallet_renewed, formatShortDate(until))).performScrollTo().assertIsDisplayed()
        compose.onAllNodes(
            hasAnyDescendant(hasText(s(R.string.wallet_renewed, formatShortDate(until)))) and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
            useUnmergedTree = true,
        ).onFirst().assertExists()
        compose.onNodeWithText(s(R.string.wallet_dismiss_message)).performScrollTo().performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun `cada aviso de la tarjeta muestra su mensaje`() {
        val expected = mapOf(
            WalletNotice.RENEWED to s(R.string.wallet_renewed, ""),
            WalletNotice.NOT_RENEWABLE to s(R.string.wallet_not_renewable),
            WalletNotice.CARATULA_NOT_FOUND to s(R.string.wallet_caratula_not_found),
            WalletNotice.CARATULA_CANCELLED to s(R.string.wallet_caratula_cancelled),
            WalletNotice.CARATULA_GENERATING to s(R.string.wallet_caratula_generating),
            WalletNotice.OFFLINE to s(R.string.wallet_action_offline),
            WalletNotice.SESSION to s(R.string.wallet_action_session),
        )
        assertTrue(expected.keys.containsAll(WalletNotice.entries))
        var state by mutableStateOf(WalletUiState(loading = false))
        compose.setContent {
            SolventaTheme {
                WalletScreen(uiState = state, fullName = null, biometricEnabled = true, onDisableBiometrics = {}, onLogout = {})
            }
        }
        expected.forEach { (notice, text) ->
            state = WalletUiState(loading = false, notice = notice)
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
    }
}
