package com.solventa.app.ui.claims

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.solventa.app.domain.claims.ClaimDocumentUploader
import com.solventa.app.domain.claims.DocumentRules
import com.solventa.app.domain.claims.DocumentUploadResult
import com.solventa.app.domain.claims.ScanResult
import com.solventa.app.domain.claims.ScannedPage
import com.solventa.app.domain.claims.sha256Hex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Doble del puerto del escáner: entrega el resultado que se le programe (mejora FE-MOV-05). */
class FakeDocumentScanner(var available: Boolean = true, var next: ScanResult = ScanResult.Cancelled) : DocumentScanner {
    val requested = mutableListOf<Int>()
    var cleared = 0

    override fun available() = available

    override fun clearCache() {
        cleared++
    }

    @Composable
    override fun rememberLauncher(onResult: (ScanResult) -> Unit): (Int) -> Unit = { max ->
        requested += max
        onResult(next)
    }
}

class RecordingUploader(var result: DocumentUploadResult = DocumentUploadResult.Received(1)) : ClaimDocumentUploader {
    val calls = mutableListOf<Triple<String, String, List<String>>>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun upload(claimNumber: String, uploadId: String, pages: List<ScannedPage>): DocumentUploadResult {
        calls += Triple(claimNumber, uploadId, pages.map { it.sha256 })
        gate?.await()
        return result
    }
}

// Mejora FE-MOV-05: escanear, ver y quitar páginas, enviarlas y los errores, todo en memoria.
@OptIn(ExperimentalCoroutinesApi::class)
class ClaimDocumentsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val uploader = RecordingUploader()
    private var ids = 0
    private lateinit var viewModel: ClaimDocumentsViewModel

    private fun page(seed: Int) = ByteArray(64) { (seed + it).toByte() }

    private fun scanned(vararg seeds: Int) = ScanResult.Pages(seeds.map(::page))

    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = ClaimDocumentsViewModel(uploader) { "upload-${++ids}" }
        viewModel.open(CLAIM, scannerAvailable = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `las paginas escaneadas se ven en orden y se envian con su huella`() = runTest(dispatcher) {
        viewModel.onScan(scanned(1, 2))
        uploader.result = DocumentUploadResult.Received(2)

        viewModel.send()
        advanceUntilIdle()

        assertEquals(Triple(CLAIM, "upload-1", listOf(sha256Hex(page(1)), sha256Hex(page(2)))), uploader.calls.single())
        assertEquals(2, state.sentPages)
        assertTrue(state.pages.isEmpty())
    }

    @Test
    fun `quitar una pagina la borra de memoria y cambia el id del envio`() = runTest(dispatcher) {
        viewModel.onScan(scanned(1, 2, 3))
        uploader.result = DocumentUploadResult.Offline
        viewModel.send()
        advanceUntilIdle()
        val removed = state.pages[1]

        viewModel.remove(removed.id)
        uploader.result = DocumentUploadResult.Received(2)
        viewModel.send()
        advanceUntilIdle()

        assertTrue(removed.jpeg.all { it == 0.toByte() })
        assertEquals(listOf("upload-1", "upload-2"), uploader.calls.map { it.second })
        assertEquals(2, uploader.calls.last().third.size)
    }

    @Test
    fun `sin red avisa, conserva las paginas en memoria y el reintento usa el mismo id`() = runTest(dispatcher) {
        viewModel.onScan(scanned(1))
        uploader.result = DocumentUploadResult.Offline

        viewModel.send()
        advanceUntilIdle()
        assertEquals(DocumentsNotice.OFFLINE, state.notice)
        assertEquals(1, state.pages.size)

        uploader.result = DocumentUploadResult.Received(1)
        viewModel.send()
        advanceUntilIdle()

        assertEquals(listOf("upload-1", "upload-1"), uploader.calls.map { it.second })
        assertEquals(1, state.sentPages)
    }

    @Test
    fun `el siniestro cerrado, inexistente, la sesion vencida y el rechazo se informan`() = runTest(dispatcher) {
        val cases = listOf(
            DocumentUploadResult.Closed to DocumentsNotice.CLOSED,
            DocumentUploadResult.NotFound to DocumentsNotice.NOT_FOUND,
            DocumentUploadResult.Unauthorized to DocumentsNotice.SESSION,
            DocumentUploadResult.Rejected("La página 1 no es una imagen JPEG ni un PDF.") to DocumentsNotice.REJECTED,
        )
        viewModel.onScan(scanned(1))
        cases.forEach { (result, notice) ->
            uploader.result = result
            viewModel.send()
            advanceUntilIdle()
            assertEquals(notice, state.notice)
            assertEquals(1, state.pages.size)
        }
        assertEquals("La página 1 no es una imagen JPEG ni un PDF.", state.rejection)
    }

    @Test
    fun `no acepta mas de 10 paginas y descarta las que sobran`() = runTest(dispatcher) {
        val extra = page(99)
        viewModel.onScan(ScanResult.Pages(List(DocumentRules.MAX_PAGES) { page(it) } + extra))

        assertEquals(DocumentRules.MAX_PAGES, state.pages.size)
        assertFalse(state.canAdd)
        assertTrue(extra.all { it == 0.toByte() })
    }

    @Test
    fun `mientras envia no se puede quitar ni enviar dos veces`() = runTest(dispatcher) {
        viewModel.onScan(scanned(1))
        uploader.gate = CompletableDeferred()

        viewModel.send()
        advanceUntilIdle()
        viewModel.send()
        viewModel.remove(state.pages.single().id)

        assertTrue(state.sending)
        assertEquals(1, state.pages.size)
        uploader.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, uploader.calls.size)
    }

    @Test
    fun `sin escaner de Google se pasa a la camara trasera`() = runTest(dispatcher) {
        viewModel.onScan(ScanResult.Unavailable)

        assertTrue(state.useCamera)
        assertEquals(DocumentsNotice.SCANNER_UNAVAILABLE, state.notice)

        val other = ClaimDocumentsViewModel(uploader)
        other.open(CLAIM, scannerAvailable = false)
        assertTrue(other.uiState.value.useCamera)
    }

    @Test
    fun `la camara agrega la pagina y una captura fallida avisa`() = runTest(dispatcher) {
        val camera = ScriptedCamera(listOf(page(5), null))
        viewModel.openCamera()

        viewModel.capture(camera)
        advanceUntilIdle()
        viewModel.capture(camera)
        advanceUntilIdle()

        assertEquals(1, state.pages.size)
        assertEquals(DocumentsNotice.CAPTURE_FAILED, state.notice)
        viewModel.onCameraDenied()
        assertFalse(state.cameraOpen)
        assertEquals(DocumentsNotice.CAMERA_DENIED, state.notice)
    }

    @Test
    fun `un escaneo cancelado no cambia nada y uno fallido avisa`() = runTest(dispatcher) {
        viewModel.onScan(ScanResult.Cancelled)
        assertNull(state.notice)

        viewModel.onScan(ScanResult.Failed)
        assertEquals(DocumentsNotice.SCAN_FAILED, state.notice)
        assertTrue(state.pages.isEmpty())
    }

    @Test
    fun `cerrar pone las paginas en cero y olvida el siniestro`() = runTest(dispatcher) {
        viewModel.onScan(scanned(1, 2))
        val pages = state.pages

        viewModel.close()

        assertTrue(pages.all { p -> p.jpeg.all { it == 0.toByte() } })
        assertNull(state.claimNumber)
        assertTrue(state.pages.isEmpty())
    }

    @Test
    fun `abrir otro siniestro descarta las paginas del anterior`() = runTest(dispatcher) {
        viewModel.onScan(scanned(1))
        val previous = state.pages.single()

        viewModel.open("SIN-2026-00002", scannerAvailable = true)

        assertTrue(previous.jpeg.all { it == 0.toByte() })
        assertTrue(state.pages.isEmpty())
        assertEquals("SIN-2026-00002", state.claimNumber)
    }

    private class ScriptedCamera(private val shots: List<ByteArray?>) : EvidenceCamera {
        private var next = 0

        @Composable
        override fun Preview(modifier: Modifier, onUnavailable: () -> Unit) = Unit

        override suspend fun capture(): ByteArray? = shots[next++]
    }

    private companion object {
        const val CLAIM = "SIN-2026-00001"
    }
}
