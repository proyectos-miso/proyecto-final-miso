package com.solventa.app.domain.claims

import java.time.Instant

/** Foto descifrada que viaja en el envío, con la posición que declara el reporte. */
class SubmissionPhoto(val position: Int, val jpeg: ByteArray)

/** Respuesta del servidor al reporte (HU-MOV-09). */
sealed interface SubmitResult {
    /** 201 nuevo o 200 repetido: el servidor ya tiene el siniestro (AC-03). */
    data class Received(val claimNumber: String) : SubmitResult

    /** Sin red, tiempo agotado o error del servidor: se reintenta con espera creciente (AC-04). */
    data object Retry : SubmitResult

    /** Rechazo que reintentar no arregla, con el motivo para el asegurado (AC-09). */
    data class Rejected(val reason: String) : SubmitResult

    /** El token no sirve: el reporte espera a la próxima sesión. */
    data object Unauthorized : SubmitResult
}

interface ClaimSubmitApi {
    suspend fun submit(token: String, report: ClaimReport, photos: List<SubmissionPhoto>): SubmitResult
}

/**
 * Cola de envío vista por cuenta, sin el correo: el envío corre con la app cerrada y sin sesión. [accountId] es
 * el de `PassphraseVault.accountId` (HU-MOV-09).
 */
interface ClaimOutbox {
    /** Cuentas con un reporte guardado en el teléfono, en cola o no. */
    fun accounts(): List<String>

    /** El reporte en cola de la cuenta; None si no tiene uno en cola. */
    suspend fun queued(accountId: String): StoredReport

    suspend fun photo(accountId: String, photoId: String): PhotoImage

    suspend fun countAttempt(accountId: String, reportId: String)

    /** Deja de reintentar y guarda el motivo para mostrarlo (AC-09). */
    suspend fun reject(accountId: String, reportId: String, reason: String): Boolean

    /** El servidor lo recibió: se borra la copia local, si sigue siendo ese reporte (AC-08). */
    suspend fun received(accountId: String, reportId: String)
}

/** Agenda el envío de la cola; WorkManager lo corre cuando hay red, aunque la app esté cerrada (AC-02). */
fun interface ClaimSendScheduler {
    /** [now] reemplaza una espera pendiente para intentar ya, por ejemplo tras pulsar «Enviar» o al ingresar. */
    fun request(now: Boolean)
}

/**
 * Reporte del teléfono que el servidor aún no recibe, como se muestra arriba de «Mis siniestros» (HU-MOV-09, AC-05).
 * HU-MOV-10 le suma el radicado provisional.
 */
data class PendingReport(val policyNumber: String?, val reportedAt: Instant, val rejectionReason: String? = null) {
    val rejected: Boolean get() = rejectionReason != null

    companion object {
        fun of(report: ClaimReport): PendingReport? = when (report.state) {
            ReportState.DRAFT -> null
            ReportState.REJECTED -> PendingReport(report.policyNumber, report.queuedAt ?: report.updatedAt, report.rejectionReason.orEmpty())
            ReportState.READY, ReportState.QUEUED -> PendingReport(report.policyNumber, report.queuedAt ?: report.updatedAt)
        }
    }
}
