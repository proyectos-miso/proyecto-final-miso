package com.solventa.app.data.claims

import com.solventa.app.data.policies.PassphraseVault
import com.solventa.app.data.policies.SessionPolicySync
import com.solventa.app.data.session.ReportTokens
import com.solventa.app.data.session.SecureSessionRepository
import com.solventa.app.domain.claims.ClaimOutbox
import com.solventa.app.domain.claims.ClaimReceipt
import com.solventa.app.domain.claims.ClaimReport
import com.solventa.app.domain.claims.ClaimSubmitApi
import com.solventa.app.domain.claims.PhotoImage
import com.solventa.app.domain.claims.ProvisionalNumber
import com.solventa.app.domain.claims.ReceiptKeeper
import com.solventa.app.domain.claims.StoredReport
import com.solventa.app.domain.claims.SubmissionPhoto
import com.solventa.app.domain.claims.SubmitResult

/** Resultado de una pasada por la cola: reintentar más tarde o nada más por hacer. */
enum class SendRun { DONE, RETRY }

/** Token con que se envía: el de acceso de la sesión abierta de esa cuenta, o null si no hay (renueva una vez ante 401). */
fun interface SessionSubmitter {
    suspend fun submit(accountId: String, call: suspend (String) -> SubmitResult): SubmitResult?
}

/** Usa la sesión abierta solo si es de la misma cuenta que el reporte. */
class OpenSessionSubmitter(
    private val session: SecureSessionRepository,
    private val sync: SessionPolicySync,
) : SessionSubmitter {
    override suspend fun submit(accountId: String, call: suspend (String) -> SubmitResult): SubmitResult? {
        val email = session.openSession?.email ?: return null
        if (PassphraseVault.accountId(email) != accountId) return null
        return sync.withAccessToken({ it == SubmitResult.Unauthorized }, call)
    }
}

/**
 * Envía el reporte en cola de cada cuenta del teléfono (HU-MOV-09). Con la sesión de esa cuenta abierta usa su token
 * de acceso; si no, el token de reporte guardado. Recibido: borra la copia local y pide sincronizar «Mis siniestros»
 * (AC-08), guardando antes la constancia oficial con el radicado provisional que reemplaza (HU-MOV-10, AC-01). Red o servidor caído: reintenta (AC-04). Rechazo definitivo: deja de reintentar y guarda el motivo (AC-09).
 * Token rechazado: espera al próximo ingreso, que vuelve a agendar el envío.
 */
class ClaimSender(
    private val outbox: ClaimOutbox,
    private val api: ClaimSubmitApi,
    private val reportTokens: ReportTokens,
    private val session: SessionSubmitter,
    private val onReceived: () -> Unit = {},
    private val receipts: ReceiptKeeper = ReceiptKeeper { _, _ -> false },
) {

    suspend fun sendQueued(): SendRun {
        var retry = false
        outbox.accounts().forEach { accountId -> if (send(accountId) == SendRun.RETRY) retry = true }
        return if (retry) SendRun.RETRY else SendRun.DONE
    }

    private suspend fun send(accountId: String): SendRun {
        val report = when (val stored = outbox.queued(accountId)) {
            is StoredReport.Found -> stored.report
            // Teléfono bloqueado o falla pasajera: el reporte sigue en cola.
            StoredReport.Unavailable -> return SendRun.RETRY
            StoredReport.None, StoredReport.CannotOpen -> return SendRun.DONE
        }
        val photos = mutableListOf<SubmissionPhoto>()
        for (photo in report.photos) {
            when (val image = outbox.photo(accountId, photo.id)) {
                is PhotoImage.Found -> photos += SubmissionPhoto(photo.position, image.jpeg)
                PhotoImage.Damaged -> {
                    outbox.reject(accountId, report.id, DAMAGED_PHOTO)
                    return SendRun.DONE
                }
                PhotoImage.Unavailable -> return SendRun.RETRY
            }
        }
        return when (val result = submit(accountId, report, photos)) {
            is SubmitResult.Received -> {
                val receipt = ClaimReceipt(
                    result.claimNumber, ProvisionalNumber.of(report), report.policyNumber,
                    result.reportedAt ?: report.queuedAt ?: report.updatedAt,
                )
                receipts.keep(accountId, receipt)
                outbox.received(accountId, report.id)
                onReceived()
                SendRun.DONE
            }
            SubmitResult.Retry -> {
                outbox.countAttempt(accountId, report.id)
                SendRun.RETRY
            }
            is SubmitResult.Rejected -> {
                outbox.reject(accountId, report.id, result.reason)
                SendRun.DONE
            }
            SubmitResult.Unauthorized -> SendRun.DONE
        }
    }

    private suspend fun submit(accountId: String, report: ClaimReport, photos: List<SubmissionPhoto>): SubmitResult {
        session.submit(accountId) { token -> api.submit(token, report, photos) }?.let { return it }
        val token = reportTokens.read(accountId) ?: return SubmitResult.Unauthorized
        return api.submit(token, report, photos)
    }

    companion object {
        const val DAMAGED_PHOTO = "Una de las fotos se dañó en el teléfono y no se puede enviar. Descarte este reporte y tome las fotos de nuevo."
    }
}
