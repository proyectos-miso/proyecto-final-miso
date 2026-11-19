package com.solventa.app.domain.claims

import java.util.concurrent.atomic.AtomicLong

/** Límites del servidor para los documentos de un siniestro abierto (mejora FE-MOV-05). */
object DocumentRules {
    const val MAX_PAGES = 10
    const val MAX_PAGE_BYTES = 5_000_000
}

/** Página escaneada en JPEG. Solo vive en memoria: nunca se guarda en disco ni en el estado guardado. */
class ScannedPage(val jpeg: ByteArray) {
    val id: Long = ids.incrementAndGet()
    val sha256: String = sha256Hex(jpeg)

    fun wipe() = jpeg.fill(0)

    private companion object {
        val ids = AtomicLong()
    }
}

/** Lo que devuelve el escáner: las páginas en JPEG ya en memoria, o por qué no hay. */
sealed interface ScanResult {
    class Pages(val pages: List<ByteArray>) : ScanResult
    data object Cancelled : ScanResult

    /** Sin Google Play services o sin el módulo del escáner: la app usa la cámara trasera. */
    data object Unavailable : ScanResult
    data object Failed : ScanResult
}

/** Respuesta del servidor al envío de las páginas. */
sealed interface DocumentUploadResult {
    /** 201 nuevo o 200 repetido: el servidor ya tiene las páginas de ese envío. */
    data class Received(val pages: Int) : DocumentUploadResult

    /** Sin red, tiempo agotado o error del servidor: las páginas siguen en memoria para reintentar. */
    data object Offline : DocumentUploadResult

    /** El siniestro no existe o no es del asegurado. */
    data object NotFound : DocumentUploadResult

    /** El siniestro ya fue decidido y no recibe documentos. */
    data object Closed : DocumentUploadResult

    data object Unauthorized : DocumentUploadResult

    /** Rechazo que reintentar no arregla, con el motivo del servidor. */
    data class Rejected(val reason: String) : DocumentUploadResult
}

interface ClaimDocumentsApi {
    suspend fun upload(accessToken: String, claimNumber: String, uploadId: String, pages: List<ScannedPage>): DocumentUploadResult
}

/** El envío con la sesión abierta; un 401 renueva la sesión una vez. */
fun interface ClaimDocumentUploader {
    suspend fun upload(claimNumber: String, uploadId: String, pages: List<ScannedPage>): DocumentUploadResult
}
