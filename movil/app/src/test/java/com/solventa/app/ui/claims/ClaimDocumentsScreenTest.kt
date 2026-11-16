package com.solventa.app.ui.claims

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.PHONE
import com.solventa.app.R
import com.solventa.app.data.policies.sampleClaim
import com.solventa.app.domain.claims.ScanResult
import com.solventa.app.domain.claims.ScannedPage
import com.solventa.app.ui.theme.SolventaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Mejora FE-MOV-05: pantallas de «Agregar documentos» con TalkBack y objetivos de 48 dp.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class ClaimDocumentsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun pages(count: Int) = List(count) { ScannedPage(ByteArray(16) { b -> (it + b).toByte() }) }

    private fun show(
        state: ClaimDocumentsUiState,
        onScan: () -> Unit = {},
        onPhoto: () -> Unit = {},
        onRemove: (Long) -> Unit = {},
        onSend: () -> Unit = {},
    ) {
        compose.setContent {
            SolventaTheme {
                ClaimDocumentsScreen(state, onBack = {}, onScan = onScan, onPhoto = onPhoto, onRemove = onRemove, onSend = onSend, onDismissNotice = {})
            }
        }
    }

    @Test
    fun `el detalle del siniestro abierto ofrece agregar documentos y el decidido no`() {
        var opened = false
        compose.setContent { SolventaTheme { ClaimDetailScreen(sampleClaim(status = "IN_REVIEW"), onBack = {}, onAddDocuments = { opened = true }) } }

        compose.onNodeWithText(s(R.string.documents_add_button)).assertHeightIsAtLeast(48.dp).performClick()

        assertTrue(opened)
    }

    @Test
    fun `el siniestro decidido no ofrece agregar documentos`() {
        compose.setContent { SolventaTheme { ClaimDetailScreen(sampleClaim(status = "PAID", label = "Pagado"), onBack = {}, onAddDocuments = {}) } }

        compose.onNodeWithText(s(R.string.documents_add_button)).assertDoesNotExist()
    }

    @Test
    fun `cada pagina se anuncia con su vista previa y su boton de quitar de 48 dp`() {
        val list = pages(2)
        val removed = mutableListOf<Long>()
        show(ClaimDocumentsUiState(claimNumber = "SIN-2026-00001", pages = list), onRemove = { removed += it })

        compose.onNodeWithText(s(R.string.documents_pages_title, 2).uppercase()).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithContentDescription(s(R.string.documents_page_preview, 1)).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.documents_remove_page, 2))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()

        assertEquals(listOf(list[1].id), removed)
    }

    @Test
    fun `el boton de enviar dice cuantas paginas y escanear abre el escaner`() {
        var scans = 0
        var sends = 0
        show(ClaimDocumentsUiState(claimNumber = "SIN-2026-00001", pages = pages(3)), onScan = { scans++ }, onSend = { sends++ })

        compose.onNodeWithText(s(R.string.documents_scan)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText(s(R.string.documents_send, 3)).performScrollTo().assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1 to 1, scans to sends)
    }

    @Test
    fun `sin paginas no se puede enviar y con 10 no se puede agregar`() {
        show(ClaimDocumentsUiState(claimNumber = "SIN-2026-00001", pages = pages(10)))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.documents_scan)))

        compose.onNodeWithText(s(R.string.documents_scan)).assertIsNotEnabled()
        compose.onNodeWithText(s(R.string.documents_limit)).assertIsDisplayed()
    }

    @Test
    fun `sin paginas el boton de enviar esta deshabilitado`() {
        show(ClaimDocumentsUiState(claimNumber = "SIN-2026-00001"))

        compose.onNodeWithText(s(R.string.documents_empty)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.documents_send, 0)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `sin red el aviso se anuncia y las paginas siguen en pantalla`() {
        show(ClaimDocumentsUiState(claimNumber = "SIN-2026-00001", pages = pages(1), notice = DocumentsNotice.OFFLINE))

        compose.onNodeWithText(s(R.string.documents_notice_offline))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        compose.onNodeWithText(s(R.string.documents_send_one)).performScrollTo().assertIsEnabled()
    }

    @Test
    fun `sin escaner ofrece fotografiar la pagina con la camara`() {
        var photos = 0
        show(ClaimDocumentsUiState(claimNumber = "SIN-2026-00001", useCamera = true), onPhoto = { photos++ })

        compose.onNodeWithText(s(R.string.documents_camera_mode)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.documents_photo)).performScrollTo().performClick()

        assertEquals(1, photos)
    }

    @Test
    fun `el obturador de la camara tiene nombre y mide al menos 48 dp`() {
        var shots = 0
        compose.setContent {
            SolventaTheme { DocumentCameraScreen(pageNumber = 2, capturing = false, preview = {}, onShoot = { shots++ }, onClose = {}) }
        }

        compose.onNodeWithContentDescription(s(R.string.documents_shutter)).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription(s(R.string.documents_close_camera)).assertHeightIsAtLeast(48.dp)

        assertEquals(1, shots)
    }

    @Test
    fun `el flujo usa el puerto del escaner y muestra lo escaneado`() {
        val scanner = FakeDocumentScanner(next = ScanResult.Pages(listOf(ByteArray(16) { 1 }, ByteArray(16) { 2 })))
        val viewModel = ClaimDocumentsViewModel(RecordingUploader())
        viewModel.open("SIN-2026-00001", scanner.available())
        compose.setContent {
            SolventaTheme { ClaimDocumentsFlow(viewModel, scanner, ClaimScreensTest.FakeCamera(), onClose = {}) }
        }

        compose.onNodeWithText(s(R.string.documents_scan)).performScrollTo().performClick()

        assertEquals(listOf(10), scanner.requested)
        compose.onNodeWithText(s(R.string.documents_pages_title, 2).uppercase()).assertIsDisplayed()
    }
}
