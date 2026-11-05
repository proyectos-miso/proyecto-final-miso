package com.solventa.app.ui.claims

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.app.domain.claims.CaptureFix
import com.solventa.app.domain.claims.CaptureLocator
import com.solventa.app.domain.claims.CapturedPhoto
import com.solventa.app.domain.claims.ClaimReport
import com.solventa.app.domain.claims.ClaimReportStore
import com.solventa.app.domain.claims.ClaimRules
import com.solventa.app.domain.claims.LocationAccess
import com.solventa.app.domain.claims.LocationResult
import com.solventa.app.domain.claims.Missing
import com.solventa.app.domain.claims.PhotoImage
import com.solventa.app.domain.claims.ReportState
import com.solventa.app.domain.claims.StoredReport
import com.solventa.app.domain.claims.TrustedClock
import com.solventa.app.domain.claims.sha256Hex
import com.solventa.app.domain.policies.PolicyCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock

enum class ClaimScreen { EVIDENCE, CAMERA, DETAIL, SAVED }

/** Lo que impide abrir la cámara o tomar la foto, con su explicación (AC-03, AC-07, AC-08, AC-17). */
enum class CaptureNotice { LOCATION_DENIED, LOCATION_APPROXIMATE, LOCATION_OFF, LOCATION_TIMEOUT, CAMERA_DENIED, NO_CAMERA, CAPTURE_FAILED }

/** Qué ofrece la billetera: nada, un reporte nuevo, retomar uno guardado o ver uno listo (AC-06, SR-13). */
enum class ReportEntry { HIDDEN, NEW, RESUME, READY }

data class PolicyOption(val policyNumber: String, val plan: String)

/** La foto que se acaba de borrar y cuántas quedan, para anunciarlo con TalkBack (QR-02). */
data class DeletedPhoto(val position: Int, val remaining: Int)

data class ClaimUiState(
    val loading: Boolean = true,
    val screen: ClaimScreen = ClaimScreen.EVIDENCE,
    val report: ClaimReport? = null,
    val description: String = "",
    val policies: List<PolicyOption> = emptyList(),
    val missing: List<Missing> = emptyList(),
    val showMissing: Boolean = false,
    val unavailable: Boolean = false,
    val cannotOpen: Boolean = false,
    val seeking: Boolean = false,
    val seekSeconds: Int = 0,
    val shooting: Boolean = false,
    val notice: CaptureNotice? = null,
    val damaged: Set<String> = emptySet(),
    val thumbnails: Map<String, Bitmap> = emptyMap(),
    val justSaved: Boolean = false,
    val closed: Boolean = false,
    // Para TalkBack: la foto recién guardada al volver de la cámara y la recién borrada (QR-02).
    val savedPhoto: Int? = null,
    val deletedPhoto: DeletedPhoto? = null,
) {
    val photoCount: Int get() = report?.photos?.size ?: 0
    val canAddPhoto: Boolean get() = report?.let(ClaimRules::canAddPhoto) ?: false
}

/**
 * Reporte del siniestro con fotos (HU-MOV-08): evidencia (paso 1), cámara, detalle (paso 2) y guardado. Cada foto
 * se toma solo con ubicación, que se lee solo con la cámara abierta (AC-20). La descripción vive aquí y en el
 * almacén cifrado, nunca en el estado que guarda el sistema (CTL-09).
 */
class ClaimReportViewModel(
    private val store: ClaimReportStore,
    private val locator: CaptureLocator,
    private val trustedClock: TrustedClock,
    private val clock: Clock = Clock.systemUTC(),
    private val thumbnail: (ByteArray) -> Bitmap? = ::decodeThumbnail,
    private val background: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClaimUiState())
    val uiState: StateFlow<ClaimUiState> = _uiState.asStateFlow()

    private val _entry = MutableStateFlow(ReportEntry.HIDDEN)
    val entry: StateFlow<ReportEntry> = _entry.asStateFlow()

    /** La ubicación que se muestra sobre el visor. */
    val liveFix: StateFlow<CaptureFix?> = locator.latestFix

    private var account: String? = null
    private var activePolicies: Set<String> = emptySet()
    private var saveJob: Job? = null
    private var seekJob: Job? = null
    private var loadJob: Job? = null

    /** Lo que ofrece la billetera para la cuenta abierta (AC-06): con un reporte guardado, siempre se puede retomar. */
    fun refreshEntry(accountEmail: String, hasActivePolicy: Boolean) {
        viewModelScope.launch {
            _entry.value = when (val stored = store.read(accountEmail)) {
                is StoredReport.Found -> if (stored.report.isReady) ReportEntry.READY else ReportEntry.RESUME
                StoredReport.Unavailable, StoredReport.CannotOpen -> ReportEntry.RESUME
                StoredReport.None -> if (hasActivePolicy) ReportEntry.NEW else ReportEntry.HIDDEN
            }
        }
    }

    /** Abre el reporte de la cuenta, o uno nuevo; las pólizas son las de la copia de la billetera, también sin red. */
    fun open(accountEmail: String, policies: List<PolicyCard>) {
        val now = clock.instant()
        val active = policies.filterNot { it.isExpiredAt(now) }
        activePolicies = active.map(PolicyCard::policyNumber).toSet()
        val options = active.map { PolicyOption(it.policyNumber, it.plan) }
        // Ya abierto: solo se actualizan las pólizas, por ejemplo cuando llega la sincronización.
        if (account == accountEmail && !_uiState.value.closed) {
            _uiState.update { it.copy(policies = options).withMissing() }
            return
        }
        cancelWork()
        account = accountEmail
        _uiState.value = ClaimUiState(policies = options)
        load()
    }

    fun retry() = load()

    private fun load() {
        val email = account ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, unavailable = false, cannotOpen = false) }
            when (val stored = store.openDraft(email)) {
                is StoredReport.Found -> {
                    val report = stored.report
                    _uiState.update {
                        it.copy(
                            loading = false,
                            report = report,
                            description = report.description,
                            screen = if (report.isReady) ClaimScreen.SAVED else it.screen,
                        ).withMissing()
                    }
                    loadThumbnails(email, report)
                }
                StoredReport.Unavailable, StoredReport.None -> _uiState.update { it.copy(loading = false, unavailable = true) }
                StoredReport.CannotOpen -> _uiState.update { it.copy(loading = false, cannotOpen = true) }
            }
        }
    }

    private suspend fun loadThumbnails(email: String, report: ClaimReport) {
        for (photo in report.photos) {
            if (photo.id in _uiState.value.thumbnails) continue
            when (val image = store.photoImage(email, photo.id)) {
                is PhotoImage.Found -> withContext(background) { thumbnail(image.jpeg) }?.let { bitmap ->
                    _uiState.update { it.copy(thumbnails = it.thumbnails + (photo.id to bitmap)) }
                }
                PhotoImage.Damaged -> _uiState.update { it.copy(damaged = it.damaged + photo.id) }
                PhotoImage.Unavailable -> Unit
            }
        }
    }

    /** Abre la cámara; la interfaz ya revisó los permisos de cámara y de ubicación precisa. */
    fun openCamera() {
        val state = _uiState.value
        if (!state.canAddPhoto) return
        _uiState.update { it.copy(screen = ClaimScreen.CAMERA, notice = null, savedPhoto = null, deletedPhoto = null) }
    }

    /** Resultado de revisar los permisos antes de abrir la cámara. */
    fun onPermissions(hasCamera: Boolean, cameraGranted: Boolean, access: LocationAccess) {
        val notice = when {
            !hasCamera -> CaptureNotice.NO_CAMERA
            !cameraGranted -> CaptureNotice.CAMERA_DENIED
            access == LocationAccess.APPROXIMATE_ONLY -> CaptureNotice.LOCATION_APPROXIMATE
            access == LocationAccess.DENIED -> CaptureNotice.LOCATION_DENIED
            else -> null
        }
        if (notice == null) openCamera() else showNotice(notice)
    }

    fun showNotice(notice: CaptureNotice) {
        _uiState.update { state ->
            // Sin permisos la cámara se cierra; el reporte sigue tal como estaba.
            val screen = if (notice in PERMISSION_NOTICES && state.screen == ClaimScreen.CAMERA) ClaimScreen.EVIDENCE else state.screen
            state.copy(notice = notice, screen = screen)
        }
    }

    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    /** La cámara está a la vista y en primer plano: solo entonces se escucha la ubicación (AC-20). */
    fun cameraVisible(visible: Boolean) {
        if (visible) locator.start() else locator.stop()
    }

    /**
     * Toma la foto solo con ubicación: la última fresca y precisa, o la espera hasta 30 s (AC-01, AC-08). [capture]
     * toma la imagen y la devuelve en JPEG sin EXIF, o null si la cámara falló.
     */
    fun shoot(capture: suspend () -> ByteArray?) {
        val email = account ?: return
        val state = _uiState.value
        if (state.seeking || state.shooting || !state.canAddPhoto) return
        _uiState.update { it.copy(seeking = true, seekSeconds = 0, notice = null) }
        seekJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _uiState.update { it.copy(seekSeconds = it.seekSeconds + 1) }
            }
        }
        viewModelScope.launch {
            val located = locator.fixForShutter()
            seekJob?.cancel()
            _uiState.update { it.copy(seeking = false) }
            when (located) {
                is LocationResult.Fix -> takePhoto(email, located.fix, capture)
                is LocationResult.NoPermission -> showNotice(
                    if (located.access == LocationAccess.APPROXIMATE_ONLY) CaptureNotice.LOCATION_APPROXIMATE else CaptureNotice.LOCATION_DENIED,
                )
                LocationResult.Disabled -> showNotice(CaptureNotice.LOCATION_OFF)
                LocationResult.Timeout -> showNotice(CaptureNotice.LOCATION_TIMEOUT)
            }
        }
    }

    private suspend fun takePhoto(email: String, fix: CaptureFix, capture: suspend () -> ByteArray?) {
        _uiState.update { it.copy(shooting = true) }
        val jpeg = capture()
        val capturedAt = clock.instant()
        val trusted = trustedClock.now()
        val saved = jpeg?.let { store.addPhoto(email, CapturedPhoto(it, sha256Hex(it), capturedAt, trusted, fix)) }
        if (jpeg == null || saved == null) {
            _uiState.update { it.copy(shooting = false, notice = CaptureNotice.CAPTURE_FAILED) }
            return
        }
        val bitmap = withContext(background) { thumbnail(jpeg) }
        _uiState.update { state ->
            val report = state.report?.let { it.copy(photos = it.photos + saved) }
            state.copy(
                shooting = false,
                report = report,
                screen = ClaimScreen.EVIDENCE,
                savedPhoto = saved.position,
                deletedPhoto = null,
                thumbnails = bitmap?.let { state.thumbnails + (saved.id to it) } ?: state.thumbnails,
            ).withMissing()
        }
    }

    fun deletePhoto(photoId: String) {
        val email = account ?: return
        viewModelScope.launch {
            if (!store.deletePhoto(email, photoId)) return@launch
            _uiState.update { state ->
                val removed = state.report?.photos?.firstOrNull { it.id == photoId }
                val photos = state.report?.photos.orEmpty().filter { it.id != photoId }.mapIndexed { i, p -> p.copy(position = i + 1) }
                state.copy(
                    report = state.report?.copy(photos = photos),
                    deletedPhoto = removed?.let { DeletedPhoto(it.position, photos.size) },
                    showMissing = false,
                    thumbnails = state.thumbnails - photoId,
                    damaged = state.damaged - photoId,
                ).withMissing()
            }
        }
    }

    /** Del paso 1 al 2; sin fotos dice lo que falta (AC-04). */
    fun continueToDetail() {
        val state = _uiState.value
        if (state.photoCount == 0) {
            _uiState.update { it.copy(showMissing = true) }
            return
        }
        _uiState.update { it.copy(screen = ClaimScreen.DETAIL, savedPhoto = null, deletedPhoto = null) }
    }

    fun selectPolicy(policyNumber: String) {
        _uiState.update { state -> state.copy(report = state.report?.copy(policyNumber = policyNumber)).withMissing() }
        saveSoon(immediate = true)
    }

    /** La descripción se recorta en 500 caracteres y se guarda 500 ms después de dejar de escribir (AC-14). */
    fun updateDescription(text: String) {
        val limited = ClaimRules.limitDescription(text)
        _uiState.update { it.copy(description = limited).withMissing() }
        saveSoon(immediate = false)
    }

    /**
     * «Enviar» deja el reporte guardado y listo para enviar (AC-15, DEC-02), si tiene fotos, una póliza que sigue
     * vigente en la copia y descripción (AC-04, AC-18).
     */
    fun send() {
        val email = account ?: return
        viewModelScope.launch {
            val missing = _uiState.value.withMissing().missing
            if (missing.isNotEmpty()) {
                _uiState.update { it.copy(showMissing = true, missing = missing) }
                return@launch
            }
            if (!flushDetails(email) || !store.markReady(email)) {
                _uiState.update { it.copy(unavailable = true) }
                return@launch
            }
            _uiState.update { state -> state.copy(report = state.report?.copy(state = ReportState.READY), screen = ClaimScreen.SAVED, justSaved = true) }
            refreshEntry(email, activePolicies.isNotEmpty())
        }
    }

    /** Borra el reporte, sus fotos, su frase y su llave; solo lo decide la persona (AC-13, AC-19, CTL-10). */
    fun discard() {
        val email = account ?: return
        cancelWork()
        viewModelScope.launch {
            store.delete(email)
            _uiState.update { ClaimUiState(loading = false, closed = true) }
            refreshEntry(email, activePolicies.isNotEmpty())
        }
    }

    /** «Atrás»: de la cámara o del detalle a la evidencia; de la evidencia o del guardado, a la billetera. */
    fun back() {
        when (_uiState.value.screen) {
            ClaimScreen.CAMERA, ClaimScreen.DETAIL -> _uiState.update {
                it.copy(screen = ClaimScreen.EVIDENCE, showMissing = false, savedPhoto = null, deletedPhoto = null)
            }
            ClaimScreen.EVIDENCE, ClaimScreen.SAVED -> leave()
        }
    }

    /** Vuelve a la billetera guardando lo escrito. */
    fun leave() {
        val email = account
        locator.stop()
        viewModelScope.launch {
            if (email != null && _uiState.value.report?.isReady == false) flushDetails(email)
            _uiState.update { it.copy(closed = true) }
            email?.let { refreshEntry(it, activePolicies.isNotEmpty()) }
        }
    }

    /** Al cerrar sesión: sin trabajo pendiente ni datos del reporte en memoria. */
    fun close() {
        cancelWork()
        locator.stop()
        account = null
        activePolicies = emptySet()
        _uiState.value = ClaimUiState(loading = false, closed = true)
        _entry.value = ReportEntry.HIDDEN
    }

    private fun saveSoon(immediate: Boolean) {
        val email = account ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            if (!immediate) delay(SAVE_DELAY_MILLIS)
            flushDetails(email)
        }
    }

    private suspend fun flushDetails(email: String): Boolean {
        val state = _uiState.value
        val report = state.report ?: return false
        if (report.isReady) return true
        return store.saveDetails(email, report.policyNumber, state.description)
    }

    private fun ClaimUiState.withMissing(): ClaimUiState {
        val current = report ?: return copy(missing = emptyList())
        return copy(missing = ClaimRules.missing(current.copy(description = description), activePolicies))
    }

    private fun cancelWork() {
        loadJob?.cancel()
        saveJob?.cancel()
        seekJob?.cancel()
    }

    override fun onCleared() {
        locator.stop()
    }

    companion object {
        const val SAVE_DELAY_MILLIS = 500L
        private val PERMISSION_NOTICES = setOf(
            CaptureNotice.LOCATION_DENIED, CaptureNotice.LOCATION_APPROXIMATE, CaptureNotice.CAMERA_DENIED, CaptureNotice.NO_CAMERA,
        )
    }
}

// Miniatura de unos 320 px, solo en memoria: nunca se guarda en disco (CTL-05).
private fun decodeThumbnail(jpeg: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 320 && bounds.outHeight / (sample * 2) >= 320) sample *= 2
    return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
}
