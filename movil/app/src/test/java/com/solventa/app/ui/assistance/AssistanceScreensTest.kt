package com.solventa.app.ui.assistance

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
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
import com.solventa.app.data.assistance.sampleProvider
import com.solventa.app.domain.assistance.AssistanceResult
import com.solventa.app.domain.assistance.AssistanceService
import com.solventa.app.ui.theme.SolventaTheme
import com.solventa.app.ui.wallet.WalletScreen
import com.solventa.app.ui.wallet.WalletUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Pantallas de prestadores y de «Pedir asistencia», su lectura con TalkBack y objetivos de 48 dp (mejora FE-MOV-07).
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class AssistanceScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    private val provider = sampleProvider()

    @Test
    fun `la billetera ofrece Asistencia con un boton de 48 dp`() {
        var opened = 0
        compose.setContent {
            SolventaTheme {
                WalletScreen(
                    uiState = WalletUiState(loading = false),
                    fullName = "Asegurada de Prueba",
                    biometricEnabled = true,
                    onDisableBiometrics = {},
                    onLogout = {},
                    onOpenAssistance = { opened++ },
                )
            }
        }

        compose.onNodeWithText(s(R.string.assistance_open_button)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `cada prestador tiene Llamar y Ver en el mapa con su nombre para TalkBack y de al menos 48 dp`() {
        val calls = mutableListOf<String>()
        val maps = mutableListOf<String>()
        compose.setContent {
            SolventaTheme {
                AssistanceScreen(
                    AssistanceUiState(nearMe = true, providers = listOf(provider)),
                    AssistanceActions(onCall = { calls += it }, onShowOnMap = { maps += it.id }),
                )
            }
        }

        compose.onNodeWithText(s(R.string.assistance_near_me)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.assistance_distance, "1,2")).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.assistance_call_provider, provider.name)).performScrollTo()
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription(s(R.string.assistance_map_provider, provider.name)).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(provider.phone), calls)
        assertEquals(listOf(provider.id), maps)
    }

    @Test
    fun `sin ubicacion ofrece usarla, elegir ciudad y llamar a la linea, todo de 48 dp`() {
        var located = 0
        val calls = mutableListOf<String>()
        compose.setContent {
            SolventaTheme {
                AssistanceScreen(AssistanceUiState(), AssistanceActions(onUseLocation = { located++ }, onCall = { calls += it }))
            }
        }

        compose.onNodeWithText(s(R.string.assistance_location_help)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.assistance_use_location)).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText(s(R.string.assistance_choose_city)).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(s(R.string.assistance_call_line)).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, located)
        assertEquals(listOf(s(R.string.assistance_line_phone)), calls)
    }

    @Test
    fun `el filtro de servicio marca el elegido y cada opcion mide 48 dp`() {
        val chosen = mutableListOf<AssistanceService?>()
        compose.setContent {
            SolventaTheme {
                AssistanceScreen(AssistanceUiState(service = AssistanceService.GRUA), AssistanceActions(onSelectService = { chosen += it }))
            }
        }

        compose.onNodeWithText(s(R.string.assistance_service_GRUA)).assertIsSelected().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(s(R.string.assistance_service_all)).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf<AssistanceService?>(null), chosen)
    }

    @Test
    fun `sin conexion el aviso con la fecha de la copia se anuncia por TalkBack`() {
        compose.setContent {
            SolventaTheme {
                AssistanceScreen(
                    AssistanceUiState(nearMe = true, providers = listOf(provider), copySavedAt = java.time.Instant.parse("2026-11-14T15:00:00Z"),
                        notice = AssistanceNotice.OFFLINE),
                    AssistanceActions(),
                )
            }
        }

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion) and hasText(s(R.string.assistance_notice_offline), substring = true))
            .assertIsDisplayed()
        compose.onNodeWithText(s(R.string.assistance_retry)).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `el formulario elige poliza y servicio como radios de 48 dp y envia`() {
        var sent = 0
        val policies = mutableListOf<String>()
        compose.setContent {
            SolventaTheme {
                AssistanceRequestScreen(
                    AssistanceUiState(nearMe = true, form = RequestFormState(listOf("POL-2026-00001", "POL-2026-00002"), "POL-2026-00001", AssistanceService.GRUA)),
                    RequestFormActions(onSubmit = { sent++ }, onSelectPolicy = { policies += it }),
                )
            }
        }

        compose.onNodeWithText("POL-2026-00001").assertIsSelected()
        compose.onNodeWithText("POL-2026-00002").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText(s(R.string.assistance_service_CERRAJERIA)).performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(s(R.string.assistance_form_send)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf("POL-2026-00002"), policies)
        assertEquals(1, sent)
    }

    @Test
    fun `sin ubicacion ni ciudad el envio queda deshabilitado y lo explica`() {
        compose.setContent {
            SolventaTheme {
                AssistanceRequestScreen(
                    AssistanceUiState(form = RequestFormState(listOf("POL-2026-00001"), "POL-2026-00001", AssistanceService.GRUA)),
                    RequestFormActions(),
                )
            }
        }

        compose.onNodeWithText(s(R.string.assistance_form_needs_place)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.assistance_form_send)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `sin red el formulario avisa y ofrece llamar a la linea de Solventa`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            SolventaTheme {
                AssistanceRequestScreen(
                    AssistanceUiState(nearMe = true, form = RequestFormState(listOf("POL-2026-00001"), "POL-2026-00001", AssistanceService.GRUA,
                        notice = RequestNotice.OFFLINE)),
                    RequestFormActions(onCall = { calls += it }),
                )
            }
        }

        compose.onNode(hasText(s(R.string.assistance_request_offline)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
            .assertIsDisplayed()
        compose.onNodeWithText(s(R.string.assistance_call_line)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(s(R.string.assistance_line_phone)), calls)
    }

    @Test
    fun `la solicitud recibida muestra el prestador y el tiempo estimado y se anuncia`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            SolventaTheme {
                AssistanceRequestScreen(
                    AssistanceUiState(nearMe = true, form = RequestFormState(listOf("POL-2026-00001"), "POL-2026-00001", AssistanceService.GRUA,
                        assigned = AssistanceResult.Assigned("REQ-1", provider, 18))),
                    RequestFormActions(onCall = { calls += it }),
                )
            }
        }

        compose.onNode(hasText(s(R.string.assistance_assigned_provider, provider.name)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
            .assertIsDisplayed()
        compose.onNodeWithText(s(R.string.assistance_assigned_eta, 18)).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.assistance_call_provider, provider.name)).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(provider.phone), calls)
    }
}
