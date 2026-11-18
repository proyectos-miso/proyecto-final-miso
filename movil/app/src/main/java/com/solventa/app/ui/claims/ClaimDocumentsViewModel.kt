package com.solventa.app.ui.claims

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.app.domain.claims.ClaimDocumentUploader
import com.solventa.app.domain.claims.DocumentRules
import com.solventa.app.domain.claims.DocumentUploadResult
import com.solventa.app.domain.claims.ScanResult
import com.solventa.app.domain.claims.ScannedPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Aviso de la pantalla de documentos. */
enum class DocumentsNotice { SCANNER_UNAVAILABLE, SCAN_FAILED, CAPTURE_FAILED, CAMERA_DENIED, OFFLINE, NOT_FOUND, CLOSED, SESSION, REJECTED }

data class ClaimDocumentsUiState(
    val claimNumber: String? = null,
    val pages: List<ScannedPage> = emptyList(),
    /** Sin escáner de Google: se fotografía cada página con la cámara trasera, sin recorte. */
    val useCamera: Boolean = false,
    val cameraOpen: Boolean = false,
    val capturing: Boolean = false,
    val sending: Boolean = false,
    val sentPages: Int? = null,
    val notice: DocumentsNotice? = null,
    val rejection: String? = null,
) {
    val remaining: Int get() = DocumentRules.MAX_PAGES - pages.size
    val canAdd: Boolean get() = remaining > 0 && !sending
    val canSend: Boolean get() = pages.isNotEmpty() && !sending
}

/**
 * Agregar documentos a un siniestro abierto (mejora FE-MOV-05): escanear, ver y quitar páginas y enviarlas. Las
 * páginas solo viven en memoria; sin red el envío no se guarda en el teléfono y el asegurado lo reintenta (DEC-02).
 */
class ClaimDocumentsViewModel(
    private val uploader: ClaimDocumentUploader,
    private val newUploadId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClaimDocumentsUiState())
    val uiState: StateFlow<ClaimDocumentsUiState> = _uiState.asStateFlow()

    // El mismo envío reintentado conserva su id para que el servidor no duplique; cambiar las páginas lo renueva.
    private var uploadId: String? = null

    fun open(claimNumber: String, scannerAvailable: Boolean) {
        if (_uiState.value.claimNumber == claimNumber) return
        clear()
        _uiState.value = ClaimDocumentsUiState(claimNumber = claimNumber, useCamera = !scannerAvailable)
    }

    fun onScan(result: ScanResult) {
        when (result) {
            is ScanResult.Pages -> add(result.pages)
            ScanResult.Cancelled -> Unit
            ScanResult.Unavailable -> _uiState.value = _uiState.value.copy(useCamera = true, notice = DocumentsNotice.SCANNER_UNAVAILABLE)
            ScanResult.Failed -> _uiState.value = _uiState.value.copy(notice = DocumentsNotice.SCAN_FAILED)
        }
    }

    fun openCamera() {
        if (_uiState.value.canAdd) _uiState.value = _uiState.value.copy(cameraOpen = true, notice = null)
    }

    fun closeCamera() {
        _uiState.value = _uiState.value.copy(cameraOpen = false)
    }

    fun onCameraDenied() {
        _uiState.value = _uiState.value.copy(cameraOpen = false, notice = DocumentsNotice.CAMERA_DENIED)
    }

    fun capture(camera: EvidenceCamera) {
        if (_uiState.value.capturing || !_uiState.value.canAdd) return
        _uiState.value = _uiState.value.copy(capturing = true)
        viewModelScope.launch {
            val jpeg = camera.capture()
            _uiState.value = _uiState.value.copy(capturing = false)
            if (jpeg == null) {
                _uiState.value = _uiState.value.copy(notice = DocumentsNotice.CAPTURE_FAILED)
            } else {
                add(listOf(jpeg))
                if (!_uiState.value.canAdd) closeCamera()
            }
        }
    }

    fun remove(pageId: Long) {
        val state = _uiState.value
        if (state.sending) return
        val page = state.pages.firstOrNull { it.id == pageId } ?: return
        page.wipe()
        uploadId = null
        _uiState.value = state.copy(pages = state.pages - page, notice = null, rejection = null)
    }

    fun dismissNotice() {
        _uiState.value = _uiState.value.copy(notice = null, rejection = null)
    }

    fun send() {
        val state = _uiState.value
        val claimNumber = state.claimNumber ?: return
        if (!state.canSend) return
        val id = uploadId ?: newUploadId().also { uploadId = it }
        _uiState.value = state.copy(sending = true, notice = null, rejection = null)
        viewModelScope.launch {
            val result = uploader.upload(claimNumber, id, state.pages)
            val current = _uiState.value.copy(sending = false)
            _uiState.value = when (result) {
                is DocumentUploadResult.Received -> {
                    current.pages.forEach(ScannedPage::wipe)
                    uploadId = null
                    current.copy(pages = emptyList(), sentPages = result.pages)
                }
                DocumentUploadResult.Offline -> current.copy(notice = DocumentsNotice.OFFLINE)
                DocumentUploadResult.NotFound -> current.copy(notice = DocumentsNotice.NOT_FOUND)
                DocumentUploadResult.Closed -> current.copy(notice = DocumentsNotice.CLOSED)
                DocumentUploadResult.Unauthorized -> current.copy(notice = DocumentsNotice.SESSION)
                is DocumentUploadResult.Rejected -> current.copy(notice = DocumentsNotice.REJECTED, rejection = result.reason)
            }
        }
    }

    /** Al salir de la pantalla, cerrar sesión o terminar: las páginas se ponen en cero y se sueltan. */
    fun close() {
        clear()
        _uiState.value = ClaimDocumentsUiState()
    }

    override fun onCleared() {
        clear()
    }

    private fun add(jpegs: List<ByteArray>) {
        val state = _uiState.value
        val accepted = jpegs.take(state.remaining)
        jpegs.drop(accepted.size).forEach { it.fill(0) }
        if (accepted.isEmpty()) return
        uploadId = null
        _uiState.value = state.copy(pages = state.pages + accepted.map(::ScannedPage), sentPages = null, notice = null, rejection = null)
    }

    private fun clear() {
        _uiState.value.pages.forEach(ScannedPage::wipe)
        uploadId = null
    }
}
