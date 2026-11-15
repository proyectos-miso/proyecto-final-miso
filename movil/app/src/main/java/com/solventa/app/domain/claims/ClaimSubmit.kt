package com.solventa.app.domain.claims

import java.time.Instant
import java.time.ZoneOffset

/** Foto descifrada que viaja en el envío, con la posición que declara el reporte. */
class SubmissionPhoto(val position: Int, val jpeg: ByteArray)

/** Respuesta del servidor al reporte (HU-MOV-09). */
sealed interface SubmitResult {
    /** 201 nuevo o 200 repetido: el servidor ya tiene el siniestro (AC-03), con la hora oficial del registro (HU-MOV-10). */
    data class Received(val claimNumber: String, val reportedAt: Instant? = null) : SubmitResult

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
 * Reporte del teléfono que el servidor aún no recibe, como se muestra arriba de «Mis siniestros» (HU-MOV-09, AC-05),
 * con su radicado provisional (HU-MOV-10, AC-02).
 */
data class PendingReport(
    val policyNumber: String?,
    val reportedAt: Instant,
    val rejectionReason: String? = null,
    val provisionalNumber: String = "",
) {
    val rejected: Boolean get() = rejectionReason != null

    companion object {
        fun of(report: ClaimReport): PendingReport? = when (report.state) {
            ReportState.DRAFT -> null
            ReportState.REJECTED -> PendingReport(
                report.policyNumber, report.queuedAt ?: report.updatedAt, report.rejectionReason.orEmpty(), ProvisionalNumber.of(report),
            )
            ReportState.READY, ReportState.QUEUED -> PendingReport(
                report.policyNumber, report.queuedAt ?: report.updatedAt, provisionalNumber = ProvisionalNumber.of(report),
            )
        }
    }
}

/**
 * Radicado provisional `OFF-AAAA-XXXX` del reporte en cola (HU-MOV-10, AC-02): sale del id del reporte y del año en
 * que se creó, que nunca cambian, así que siempre es el mismo y no hay que guardarlo. Solo identifica la constancia en el
 * teléfono; el servidor no lo conoce y lo reemplaza el radicado oficial.
 */
object ProvisionalNumber {
    fun of(reportId: String, at: Instant): String {
        val code = reportId.filter(Char::isLetterOrDigit).take(CODE_LENGTH).uppercase().padEnd(CODE_LENGTH, '0')
        return "OFF-${at.atZone(ZoneOffset.UTC).year}-$code"
    }

    fun of(report: ClaimReport): String = of(report.id, report.createdAt)

    private const val CODE_LENGTH = 4
}

/** Constancia oficial del último reporte recibido de la cuenta (HU-MOV-10, AC-01). */
data class ClaimReceipt(
    val claimNumber: String,
    val provisionalNumber: String,
    val policyNumber: String?,
    val reportedAt: Instant,
)

/**
 * Guarda la constancia oficial de la cuenta al recibirse el reporte, antes de que se borre su copia local. Corre
 * sin sesión, por eso recibe el id de la cuenta. false si no se pudo guardar; el radicado igual llega a «Mis siniestros».
 */
fun interface ReceiptKeeper {
    suspend fun keep(accountId: String, receipt: ClaimReceipt): Boolean
}
