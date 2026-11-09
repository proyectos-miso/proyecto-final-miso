package com.solventa.app.domain.claims

import java.time.Instant

/** Estado del reporte en esta historia: a medias o listo para enviar (HU-MOV-08, AC-15 y AC-19). */
enum class ReportState { DRAFT, READY }

/** De dónde sale la hora que no depende del reloj que ajusta la persona (AC-10). */
enum class TimeSource { GNSS, NETWORK }

data class TrustedTime(val epochMillis: Long, val source: TimeSource)

/** Ubicación tomada al disparar: coordenadas, precisión y si viene de una app de ubicación simulada (AC-01, AC-10). */
data class CaptureFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val mock: Boolean,
)

/** Foto guardada en el teléfono: sus datos sin la imagen, que va cifrada aparte (CRY-03). */
data class EvidencePhoto(
    val id: String,
    val position: Int,
    val capturedAt: Instant,
    val trustedTime: TrustedTime?,
    val fix: CaptureFix,
    val sha256: String,
    val sizeBytes: Int,
)

data class ClaimReport(
    val id: String,
    val policyNumber: String?,
    val description: String,
    val state: ReportState,
    val photos: List<EvidencePhoto>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isReady: Boolean get() = state == ReportState.READY
}

/** Foto recién tomada, antes de cifrarla: los bytes JPEG sin EXIF y lo que se guarda con ella. */
class CapturedPhoto(
    val jpeg: ByteArray,
    val sha256: String,
    val capturedAt: Instant,
    val trustedTime: TrustedTime?,
    val fix: CaptureFix,
)

/** Lectura del reporte de una cuenta. Nada se borra solo (CTL-10). */
sealed interface StoredReport {
    data class Found(val report: ClaimReport) : StoredReport

    /** La cuenta no tiene reporte. */
    data object None : StoredReport

    /** El teléfono está bloqueado o hubo una falla pasajera; el reporte se conserva. */
    data object Unavailable : StoredReport

    /** La llave quedó invalidada o la base está dañada: no se puede abrir y la persona decide descartarlo. */
    data object CannotOpen : StoredReport
}

/** Imagen de una foto guardada. */
sealed interface PhotoImage {
    class Found(val jpeg: ByteArray) : PhotoImage

    /** El archivo falta, no descifra o no coincide con su huella: hay que tomarla de nuevo (CRY-03, CRY-04). */
    data object Damaged : PhotoImage

    data object Unavailable : PhotoImage
}

/**
 * Reporte del siniestro por cuenta, cifrado en el teléfono (ADR 0024). A lo sumo un reporte por cuenta.
 * Ninguna operación lanza por la llave, la base o los archivos, salvo la cancelación.
 */
interface ClaimReportStore {
    suspend fun read(accountEmail: String): StoredReport

    /** El reporte de la cuenta, o uno nuevo si no tenía; Unavailable si la llave no se pudo usar. */
    suspend fun openDraft(accountEmail: String): StoredReport

    /** Guarda la póliza y la descripción de un borrador; false si no se pudo o si el reporte ya está listo. */
    suspend fun saveDetails(accountEmail: String, policyNumber: String?, description: String): Boolean

    /** Agrega la foto al borrador; null si no se pudo, si ya hay 5 o si el reporte ya está listo. */
    suspend fun addPhoto(accountEmail: String, photo: CapturedPhoto): EvidencePhoto?

    suspend fun photoImage(accountEmail: String, photoId: String): PhotoImage

    /** Borra la foto y su archivo, y vuelve a numerar las demás (AC-13). */
    suspend fun deletePhoto(accountEmail: String, photoId: String): Boolean

    /** Pasa el borrador a listo para enviar (AC-15). */
    suspend fun markReady(accountEmail: String): Boolean

    /** Borra el reporte, sus fotos, su frase y su llave; no se cancela (AC-12, AC-13). */
    suspend fun delete(accountEmail: String)
}
