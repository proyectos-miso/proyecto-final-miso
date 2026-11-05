package com.solventa.app.ui.claims

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.solventa.app.PHONE
import com.solventa.app.R
import com.solventa.app.data.claims.FakeCaptureLocator
import com.solventa.app.data.claims.InMemoryClaimStore
import com.solventa.app.data.policies.samplePolicy
import com.solventa.app.domain.claims.LocationAccess
import com.solventa.app.ui.theme.SolventaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers

/** Pantallas del reporte (HU-MOV-08: TC-02, TC-03, TC-12, TC-17). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class ClaimScreensTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val store = InMemoryClaimStore()
    private val locator = FakeCaptureLocator()
    private val permissions = FakePermissions()
    private val camera = FakeCamera()
    private val clock = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneOffset.UTC)
    private lateinit var vm: ClaimReportViewModel
    private var closed = false

    private fun s(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)

    @Before
    fun setUp() {
        // Todo en el hilo principal: `waitUntil` de Robolectric no lo procesa mientras espera.
        vm = ClaimReportViewModel(store, locator, { null }, clock, thumbnail = { null }, background = Dispatchers.Main.immediate)
    }

    private fun show(policies: List<com.solventa.app.domain.policies.PolicyCard> = listOf(samplePolicy())) {
        vm.open(EMAIL, policies)
        compose.setContent {
            SolventaTheme {
                ClaimFlow(vm, camera, onClosed = { closed = true }, permissions = permissions, clock = clock)
            }
        }
        compose.waitForIdle()
        assertTrue(!vm.uiState.value.loading && vm.uiState.value.report != null)
    }

    @Test
    fun `TC-02 la evidencia sale solo de la cámara de la app, sin galería ni otras apps`() {
        show()
        compose.onNodeWithText(s(R.string.claim_take_photo)).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription(s(R.string.claim_shutter)).performClick()
        compose.waitUntil(5_000) { store.reports[EMAIL]?.photos?.size == 1 }

        assertEquals(1, camera.captures)
        // La única mención de la galería es el aviso de que las fotos no aparecen ahí.
        assertEquals(1, compose.onAllNodesWithText("galería", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        compose.onNodeWithText(s(R.string.claim_gallery_note)).assertExists()

        compose.onNodeWithText(s(R.string.claim_take_another, 2)).performClick()
        compose.waitForIdle()
        assertEquals("«Tomar otra foto» abre la cámara de la app", ClaimScreen.CAMERA, vm.uiState.value.screen)
        compose.onNodeWithContentDescription(s(R.string.claim_shutter)).assertExists()
        assertNull("no abrió ninguna otra app", shadowOf(compose.activity).nextStartedActivity)
    }

    @Test
    fun `TC-03 si revocan un permiso a mitad del reporte se vuelve a pedir y el reporte sigue`() {
        show()
        compose.onNodeWithText(s(R.string.claim_take_photo)).performClick()
        compose.onNodeWithContentDescription(s(R.string.claim_shutter)).performClick()
        compose.waitForIdle()
        assertEquals(1, vm.uiState.value.photoCount)

        permissions.access = LocationAccess.DENIED
        compose.onNodeWithText(s(R.string.claim_take_another, 2)).performClick()
        compose.waitForIdle()

        val requested = shadowOf(compose.activity).lastRequestedPermission?.requestedPermissions.orEmpty().toSet()
        assertTrue(requested.containsAll(setOf("android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION")))
        assertEquals(ClaimScreen.EVIDENCE, vm.uiState.value.screen)
        assertEquals(1, vm.uiState.value.photoCount)
    }

    @Test
    fun `TC-03 sin cámara en el teléfono lo explica sin ofrecer ajustes`() {
        permissions.hasCamera = false
        show()

        compose.onNodeWithText(s(R.string.claim_take_photo)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(s(R.string.claim_notice_no_camera_title)).assertExists()
        compose.onNodeWithText(s(R.string.claim_open_settings)).assertDoesNotExist()
        compose.onNodeWithText(s(R.string.claim_back_to_report)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertNull(vm.uiState.value.notice)
        compose.onNodeWithText(s(R.string.claim_notice_no_camera_title)).assertDoesNotExist()
        compose.onNodeWithText(s(R.string.claim_evidence_title)).assertExists()
    }

    @Test
    fun `TC-03 el permiso negado explica los pasos y abre los ajustes de la app`() {
        permissions.access = LocationAccess.APPROXIMATE_ONLY
        show()
        compose.runOnIdle { vm.onPermissions(hasCamera = true, cameraGranted = true, access = LocationAccess.APPROXIMATE_ONLY) }

        compose.onNodeWithText(s(R.string.claim_notice_approximate_title)).assertExists()
        compose.onNodeWithText(s(R.string.claim_spoken_step, 3, s(R.string.claim_notice_location_step3))).assertExists()
        compose.onNodeWithText(s(R.string.claim_open_settings)).performSemanticsAction(SemanticsActions.OnClick)

        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${compose.activity.packageName}", intent.dataString)
    }

    @Test
    fun `TC-03 al volver de los ajustes con el permiso la cámara se abre`() {
        permissions.cameraGranted = false
        show()
        compose.runOnIdle { vm.onPermissions(hasCamera = true, cameraGranted = false, access = LocationAccess.PRECISE) }
        compose.onNodeWithText(s(R.string.claim_notice_camera_title)).assertExists()

        permissions.cameraGranted = true
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitForIdle()

        assertEquals(ClaimScreen.CAMERA, vm.uiState.value.screen)
    }

    @Test
    fun `TC-04 con la ubicación apagada ofrece los ajustes de ubicación`() {
        show()
        compose.runOnIdle { vm.showNotice(CaptureNotice.LOCATION_OFF) }

        compose.onNodeWithText(s(R.string.claim_open_location_settings)).performSemanticsAction(SemanticsActions.OnClick)

        assertNull("el aviso se cierra al ir a los ajustes", vm.uiState.value.notice)

        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, shadowOf(compose.activity).nextStartedActivity.action)
    }

    @Test
    fun `TC-12 sin pólizas vigentes el detalle lo dice y deja descartar`() {
        show(policies = emptyList())
        compose.runOnIdle {
            vm.openCamera()
            vm.shoot { ByteArray(10) }
        }
        compose.waitForIdle()
        assertEquals(1, vm.uiState.value.photoCount)
        compose.runOnIdle { vm.continueToDetail() }

        compose.onNodeWithText(s(R.string.claim_no_active_policies)).assertExists()
        compose.onNodeWithText(s(R.string.claim_discard)).assertExists()
        compose.onNodeWithText(s(R.string.claim_send)).assertIsNotEnabled()
    }

    @Test
    fun `TC-09 el detalle dice qué falta mientras Enviar está desactivado`() {
        show()
        compose.runOnIdle {
            vm.openCamera()
            vm.shoot { ByteArray(10) }
        }
        compose.waitForIdle()
        assertEquals(1, vm.uiState.value.photoCount)
        compose.runOnIdle { vm.continueToDetail() }

        compose.onNodeWithText(s(R.string.claim_missing_title)).assertDoesNotExist()
        compose.onNodeWithContentDescription(
            s(R.string.claim_missing_title) + ": " + s(R.string.claim_missing_policy) + " " + s(R.string.claim_missing_description),
        ).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        compose.onNodeWithText(s(R.string.claim_send)).assertIsNotEnabled()
    }

    @Test
    fun `TC-17 el disparador y borrar la foto tienen nombre y miden al menos 48 dp`() {
        show()
        compose.runOnIdle {
            vm.openCamera()
            vm.shoot { ByteArray(10) }
        }
        compose.waitForIdle()
        assertEquals(1, vm.uiState.value.photoCount)

        compose.onNodeWithContentDescription(s(R.string.claim_photo_delete, 1)).assertTouchTargetAtLeast48()
        compose.onNodeWithText(s(R.string.claim_take_another, 2)).assertTouchTargetAtLeast48()
        compose.runOnIdle { vm.openCamera() }
        compose.onNodeWithContentDescription(s(R.string.claim_shutter)).assertTouchTargetAtLeast48()
        compose.onNodeWithContentDescription(s(R.string.claim_close_camera)).assertTouchTargetAtLeast48()
    }

    // Área de toque, no la del dibujo: un `IconButton` dibuja 40 dp y reserva 48 dp para el dedo.
    private fun SemanticsNodeInteraction.assertTouchTargetAtLeast48() {
        val bounds = fetchSemanticsNode().touchBoundsInRoot
        val min = with(compose.density) { 48.dp.toPx() } - 1f
        assertTrue("el área de toque mide ${bounds.width} × ${bounds.height} px", bounds.width >= min && bounds.height >= min)
    }

    // TC-17: en el teléfono, la zona accesible agrandada de `LinearProgressIndicator` dejaba Atrás en 48 × 46 dp.
    @Test
    fun `TC-17 la barra de progreso es decorativa y no tapa el botón Atrás`() {
        show()

        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        compose.onNodeWithText("Paso 1 de 2", substring = true).assertExists()
        compose.onNodeWithContentDescription(s(R.string.claim_back)).assertTouchTargetAtLeast48()
    }

    // UC-04, SR-13: con pólizas vigentes y aun sin fotos, el borrador se descarta desde la evidencia.
    @Test
    fun `TC-24 un borrador con pólizas vigentes se descarta desde la evidencia, aun sin fotos`() {
        show()
        compose.onNodeWithText(s(R.string.claim_take_photo)).performClick()
        compose.onNodeWithContentDescription(s(R.string.claim_shutter)).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(s(R.string.claim_photo_delete, 1)).performClick()
        compose.waitForIdle()
        assertEquals(0, vm.uiState.value.photoCount)

        compose.onNodeWithText(s(R.string.claim_discard)).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.claim_discard_confirm)).performClick()
        compose.waitUntil(5_000) { closed }

        assertEquals(listOf(EMAIL), store.deleted)
    }

    // TC-17 (QR-02): cada pantalla tiene su título para TalkBack; al volver de la cámara confirma la foto.
    @Test
    fun `TC-17 cada pantalla del reporte anuncia su título`() {
        show()
        pane(s(R.string.claim_pane_evidence)).assertExists()

        compose.onNodeWithText(s(R.string.claim_take_photo)).performClick()
        compose.waitForIdle()
        pane(s(R.string.claim_pane_camera, 1)).assertExists()

        compose.onNodeWithContentDescription(s(R.string.claim_shutter)).performClick()
        compose.waitForIdle()
        pane(s(R.string.claim_pane_evidence_saved, 1)).assertExists()

        compose.runOnIdle {
            vm.selectPolicy("POL-2026-00001")
            vm.updateDescription("Golpe en la puerta")
            vm.continueToDetail()
        }
        pane(s(R.string.claim_pane_detail)).assertExists()

        compose.runOnIdle { vm.send() }
        compose.waitForIdle()
        pane(s(R.string.claim_saved_heading)).assertExists()
        compose.onNodeWithContentDescription(s(R.string.claim_spoken_summary, s(R.string.claim_summary_policy), "POL-2026-00001")).assertExists()
        compose.onNodeWithContentDescription(
            s(R.string.claim_spoken_summary, s(R.string.claim_summary_state), s(R.string.claim_state_ready)),
        ).assertExists()
        compose.onNodeWithContentDescription("${s(R.string.claim_summary_place)}: latitud 4,6512 grados norte", substring = true).assertExists()
    }

    // TC-17: el aviso junto a «Continuar» es un nodo vivo que siempre está; un nodo que aparece no se anuncia.
    @Test
    fun `TC-17 sin fotos, Continuar anuncia lo que falta en un nodo vivo que ya estaba`() {
        show()
        val live = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        val before = live.fetchSemanticsNode()
        assertTrue(SemanticsProperties.ContentDescription !in before.config)

        compose.onNodeWithText(s(R.string.claim_continue)).performClick()
        compose.waitForIdle()

        val after = live.fetchSemanticsNode()
        assertEquals("el mismo nodo vivo", before.id, after.id)
        assertEquals(
            listOf(s(R.string.claim_missing_title) + ": " + s(R.string.claim_missing_photo)),
            after.config[SemanticsProperties.ContentDescription],
        )
    }

    @Test
    fun `TC-17 borrar una foto se anuncia con las que quedan`() {
        show()
        compose.onNodeWithText(s(R.string.claim_take_photo)).performClick()
        compose.onNodeWithContentDescription(s(R.string.claim_shutter)).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription(s(R.string.claim_photo_delete, 1)).performClick()
        compose.waitForIdle()

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
            .assert(hasContentDescription(s(R.string.claim_photo_deleted, 1, 0)))
    }

    // TC-17: la foto se lee en un solo nodo y las coordenadas en palabras, sin «°», «±» ni la «O».
    @Test
    fun `TC-17 la fila de la foto se lee en un nodo y en palabras`() {
        show()
        compose.onNodeWithText(s(R.string.claim_take_photo)).performClick()
        compose.onNodeWithContentDescription(s(R.string.claim_shutter)).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription(
            "Foto 1. latitud 4,6512 grados norte, longitud 74,0568 grados oeste, precisión de 4 metros.",
            substring = true,
        ).assertExists()
        compose.onNodeWithContentDescription(s(R.string.claim_photo_encrypted), substring = true).assertExists()
        compose.onNodeWithText(s(R.string.claim_photo_label, 1)).assertDoesNotExist()
        compose.onAllNodesWithText("°", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription(s(R.string.claim_photos_attached, 1)).assertExists()
        compose.onNodeWithText(s(R.string.claim_evidence_title)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    // TC-17: el disparador es un botón y la cámara solo anuncia su estado, no el reloj ni las coordenadas.
    @Test
    fun `TC-17 la cámara anuncia solo su estado y el disparador es un botón`() {
        show()
        compose.onNodeWithText(s(R.string.claim_take_photo)).performClick()
        compose.waitForIdle()
        val hud = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        hud.assert(hasContentDescription(s(R.string.claim_status_waiting)))

        compose.runOnIdle { locator.latestFix.value = FakeCaptureLocator.FIX }
        compose.waitForIdle()

        hud.assert(hasContentDescription(s(R.string.claim_status_ready)))
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertCountEquals(1)
        compose.onNodeWithContentDescription(s(R.string.claim_shutter))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    // TC-17: los obligatorios se leen «obligatoria», no «asterisco»; el contador en palabras y el tope avisado.
    @Test
    fun `TC-17 los campos obligatorios y el contador se leen en palabras`() {
        show()
        compose.runOnIdle {
            vm.openCamera()
            vm.shoot { ByteArray(10) }
        }
        compose.waitForIdle()
        compose.runOnIdle { vm.continueToDetail() }

        compose.onAllNodesWithText("*", substring = true).assertCountEquals(0)
        compose.onNode(hasText(s(R.string.claim_policy_label_spoken), substring = true)).assertExists()
        compose.onNode(hasText(s(R.string.claim_description_label_spoken), substring = true)).assertExists()
        compose.onNode(hasText(s(R.string.claim_description_count_spoken, 0), substring = true)).assertExists()

        compose.runOnIdle { vm.updateDescription("a".repeat(510)) }
        compose.waitForIdle()
        compose.onNode(hasText(s(R.string.claim_description_max), substring = true)).assertExists()
        compose.onNode(hasText(s(R.string.claim_description_count_spoken, 500), substring = true)).assertExists()
    }

    // TC-17 (QR-02): con la fuente al máximo la explicación del permiso se lee completa y sus botones se alcanzan.
    @Test
    @Config(qualifiers = PHONE, fontScale = 2.0f)
    fun `TC-17 con la fuente al máximo la explicación del permiso se lee completa`() {
        permissions.access = LocationAccess.DENIED
        show()
        assertEquals(2.0f, compose.activity.resources.configuration.fontScale)
        compose.runOnIdle { vm.onPermissions(hasCamera = true, cameraGranted = true, access = LocationAccess.DENIED) }
        compose.waitForIdle()

        compose.onNodeWithText(s(R.string.claim_notice_location_title)).assertExists()
        compose.onNodeWithText(s(R.string.claim_notice_location_body)).assertExists()
        listOf(R.string.claim_notice_location_step1, R.string.claim_notice_location_step2, R.string.claim_notice_location_step3)
            .forEachIndexed { i, step -> compose.onNodeWithText(s(R.string.claim_spoken_step, i + 1, s(step))).assertExists() }
        compose.onNodeWithText(s(R.string.claim_back_to_report)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(s(R.string.claim_report_kept)).performScrollTo().assertIsDisplayed()
    }

    private fun pane(title: String) = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title))

    @Test
    fun `atrás desde la evidencia vuelve a la billetera`() {
        show()

        compose.onNodeWithContentDescription(s(R.string.claim_back)).performClick()
        compose.waitUntil(5_000) { closed }
    }

    class FakePermissions : ClaimPermissions {
        var hasCamera = true
        var cameraGranted = true
        var access = LocationAccess.PRECISE
        override fun hasCamera() = hasCamera
        override fun cameraGranted() = cameraGranted
        override fun locationAccess() = access
    }

    class FakeCamera : EvidenceCamera {
        var captures = 0

        @androidx.compose.runtime.Composable
        override fun Preview(modifier: Modifier, onUnavailable: () -> Unit) = Unit

        override suspend fun capture(): ByteArray {
            captures++
            return ByteArray(2_048) { it.toByte() }
        }
    }

    private companion object {
        const val EMAIL = "asegurado@correo.test"
    }
}
