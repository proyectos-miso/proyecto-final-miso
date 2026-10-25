package com.solventa.app.ui.wallet

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.PHONE
import com.solventa.app.R
import com.solventa.app.data.policies.samplePolicy
import com.solventa.app.ui.theme.SolventaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Billetera según el mockup y su lectura con TalkBack (HU-MOV-04: TC-04, TC-05, TC-11, TC-12).
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class WalletScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    private val carne = SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription) and hasClickAction()

    private fun show(state: WalletUiState, biometricEnabled: Boolean = true) {
        compose.setContent {
            SolventaTheme {
                WalletScreen(
                    uiState = state,
                    fullName = "María Ruiz",
                    biometricEnabled = biometricEnabled,
                    onDisableBiometrics = {},
                    onLogout = {},
                )
            }
        }
    }

    @Test
    fun `TC-12 con red saluda, muestra el chip en linea, el carne y la lista`() {
        show(WalletUiState(loading = false, policies = listOf(WalletCard(samplePolicy(), expired = false))))

        compose.onNodeWithText(s(R.string.home_title_named, "María")).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.wallet_chip_online)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.wallet_offline)).assertDoesNotExist()
        compose.onAllNodesWithText("POL-2026-00001", substring = true).assertCountEquals(2)
        compose.onNode(carne).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.biometric_disable)).assertIsDisplayed()
    }

    @Test
    fun `TC-11 el carne y la lista muestran la suma asegurada en COP`() {
        show(WalletUiState(loading = false, policies = listOf(WalletCard(samplePolicy(), expired = false))))

        compose.onAllNodesWithText(formatCop(2.5e8), substring = true).assertCountEquals(2)
    }

    @Test
    fun `TC-12 tocar el carne muestra las coberturas y tocarlo otra vez vuelve al frente`() {
        show(WalletUiState(loading = false, policies = listOf(WalletCard(samplePolicy(), expired = false))))

        compose.onNode(carne).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, s(R.string.wallet_face_front)))
        compose.onNode(carne).performClick()

        compose.onNodeWithText(s(R.string.wallet_coverages_title), substring = true).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.wallet_coverage, "Muerte por cualquier causa", formatCop(2.5e8)), substring = true)
            .assertIsDisplayed()
        compose.onNode(carne).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, s(R.string.wallet_face_back)))

        compose.onNode(carne).performClick()
        compose.onNodeWithText(s(R.string.wallet_coverages_title), substring = true).assertDoesNotExist()
    }

    @Test
    fun `TC-12 elegir otra poliza de la lista la lleva al carne`() {
        val second = samplePolicy("POL-2026-00002", "2027-06-01T00:00:00Z")
        show(
            WalletUiState(
                loading = false,
                policies = listOf(WalletCard(samplePolicy(), false), WalletCard(second, false)),
            )
        )
        compose.onAllNodesWithText("POL-2026-00002", substring = true).assertCountEquals(1)

        compose.onNode(hasText("POL-2026-00002", substring = true) and hasClickAction()).performClick()

        compose.onAllNodesWithText("POL-2026-00002", substring = true).assertCountEquals(2)
    }

    @Test
    fun `TC-05 sin red muestra el aviso, el chip sin conexion y una poliza vencida`() {
        show(
            WalletUiState(
                loading = false,
                offline = true,
                policies = listOf(WalletCard(samplePolicy(), expired = true)),
            )
        )

        compose.onNodeWithText(s(R.string.wallet_offline)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.wallet_chip_offline)).assertIsDisplayed()
        compose.onAllNodesWithText(s(R.string.wallet_expired), substring = true).onFirst().assertIsDisplayed()
        compose.onNodeWithText(s(R.string.biometric_disable)).assertDoesNotExist()
    }

    @Test
    fun `TC-05 sin red y sin copia pide conexion para la primera descarga`() {
        show(WalletUiState(loading = false, offline = true, needsConnection = true))

        compose.onNodeWithText(s(R.string.wallet_needs_connection)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.wallet_offline)).assertDoesNotExist()
    }

    @Test
    fun `TC-04 sin polizas vigentes lo indica`() {
        show(WalletUiState(loading = false))

        compose.onNodeWithText(s(R.string.wallet_empty)).assertIsDisplayed()
    }

    @Test
    fun `TC-01 muestra la ultima sincronizacion con fecha y hora`() {
        val syncedAt = java.time.Instant.parse("2026-10-24T15:30:00Z")
        show(WalletUiState(loading = false, policies = listOf(WalletCard(samplePolicy(), false)), lastSync = syncedAt))

        compose.onNodeWithText(s(R.string.wallet_last_sync, formatDateTime(syncedAt))).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.wallet_stale)).assertDoesNotExist()
    }

    @Test
    fun `TC-02 una copia desactualizada muestra el aviso, anunciado por TalkBack`() {
        show(
            WalletUiState(
                loading = false,
                offline = true,
                policies = listOf(WalletCard(samplePolicy(), false)),
                lastSync = java.time.Instant.parse("2026-10-20T15:30:00Z"),
                stale = true,
            )
        )

        compose.onNode(hasAnyDescendant(hasText(s(R.string.wallet_stale))) and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `mientras carga anuncia la carga`() {
        show(WalletUiState())

        compose.onNodeWithContentDescription(s(R.string.wallet_loading)).assertIsDisplayed()
    }
}
