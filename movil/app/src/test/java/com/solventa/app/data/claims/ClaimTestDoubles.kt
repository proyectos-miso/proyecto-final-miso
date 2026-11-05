package com.solventa.app.data.claims

import com.solventa.app.domain.claims.CaptureFix
import com.solventa.app.domain.claims.CaptureLocator
import com.solventa.app.domain.claims.CapturedPhoto
import com.solventa.app.domain.claims.ClaimReport
import com.solventa.app.domain.claims.ClaimReportStore
import com.solventa.app.domain.claims.ClaimRules
import com.solventa.app.domain.claims.EvidencePhoto
import com.solventa.app.domain.claims.LocationAccess
import com.solventa.app.domain.claims.LocationResult
import com.solventa.app.domain.claims.PhotoImage
import com.solventa.app.domain.claims.ReportState
import com.solventa.app.domain.claims.StoredReport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** Reporte en memoria con las reglas del almacén real: uno por cuenta y el listo no se edita. */
class InMemoryClaimStore : ClaimReportStore {
    val reports = mutableMapOf<String, ClaimReport>()
    val images = mutableMapOf<String, ByteArray>()
    val deleted = mutableListOf<String>()

    /** Lo que responde `read` y `openDraft` en lugar del reporte, para simular bloqueo o daño. */
    var readAs: StoredReport? = null

    /** Fotos que se leen como dañadas. */
    val damaged = mutableSetOf<String>()

    /** Si existe, el borrado espera a que la prueba lo complete. */
    var deleteGate: CompletableDeferred<Unit>? = null
    private var ids = 0

    override suspend fun read(accountEmail: String): StoredReport =
        readAs ?: reports[accountEmail]?.let { StoredReport.Found(it) } ?: StoredReport.None

    override suspend fun openDraft(accountEmail: String): StoredReport {
        readAs?.let { return it }
        val report = reports.getOrPut(accountEmail) {
            ClaimReport("r${++ids}", null, "", ReportState.DRAFT, emptyList(), Instant.EPOCH, Instant.EPOCH)
        }
        return StoredReport.Found(report)
    }

    override suspend fun saveDetails(accountEmail: String, policyNumber: String?, description: String): Boolean {
        val report = reports[accountEmail]?.takeIf { !it.isReady && readAs == null } ?: return false
        reports[accountEmail] = report.copy(policyNumber = policyNumber, description = ClaimRules.limitDescription(description))
        return true
    }

    override suspend fun addPhoto(accountEmail: String, photo: CapturedPhoto): EvidencePhoto? {
        val report = reports[accountEmail]?.takeIf { ClaimRules.canAddPhoto(it) && readAs == null } ?: return null
        val saved = EvidencePhoto("f${++ids}", report.photos.size + 1, photo.capturedAt, photo.trustedTime, photo.fix, photo.sha256, photo.jpeg.size)
        images[saved.id] = photo.jpeg
        reports[accountEmail] = report.copy(photos = report.photos + saved)
        return saved
    }

    override suspend fun photoImage(accountEmail: String, photoId: String): PhotoImage = when {
        photoId in damaged -> PhotoImage.Damaged
        else -> images[photoId]?.let { PhotoImage.Found(it) } ?: PhotoImage.Damaged
    }

    override suspend fun deletePhoto(accountEmail: String, photoId: String): Boolean {
        val report = reports[accountEmail]?.takeIf { !it.isReady } ?: return false
        if (report.photos.none { it.id == photoId }) return false
        images.remove(photoId)
        reports[accountEmail] = report.copy(
            photos = report.photos.filter { it.id != photoId }.mapIndexed { i, p -> p.copy(position = i + 1) },
        )
        return true
    }

    override suspend fun markReady(accountEmail: String): Boolean {
        val report = reports[accountEmail]?.takeIf { !it.isReady } ?: return false
        reports[accountEmail] = report.copy(state = ReportState.READY)
        return true
    }

    override suspend fun delete(accountEmail: String) {
        deleteGate?.await()
        deleted += accountEmail
        reports.remove(accountEmail)?.photos?.forEach { images.remove(it.id) }
    }
}

/** Ubicación controlada por la prueba: cada disparo toma el siguiente resultado de [results]. */
class FakeCaptureLocator : CaptureLocator {
    override val latestFix = MutableStateFlow<CaptureFix?>(null)
    var access = LocationAccess.PRECISE
    var enabled = true
    var started = false
    var starts = 0
    var stops = 0
    val results = ArrayDeque<LocationResult>()

    /** Si existe, el disparo espera a que la prueba lo complete (para ver «Buscando ubicación…»). */
    var gate: CompletableDeferred<LocationResult>? = null

    override fun access() = access

    override fun isEnabled() = enabled

    override fun start() {
        started = true
        starts++
    }

    override fun stop() {
        started = false
        stops++
        latestFix.value = null
    }

    override suspend fun fixForShutter(): LocationResult {
        if (access != LocationAccess.PRECISE) return LocationResult.NoPermission(access)
        if (!enabled) return LocationResult.Disabled
        gate?.let { return it.await() }
        return results.removeFirstOrNull() ?: LocationResult.Fix(FIX)
    }

    companion object {
        val FIX = CaptureFix(4.6512, -74.0568, 4f, mock = false)
    }
}
