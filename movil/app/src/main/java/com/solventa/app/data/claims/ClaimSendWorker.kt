package com.solventa.app.data.claims

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.solventa.app.domain.claims.ClaimSendScheduler
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** La aplicación entrega el remitente al trabajo, que WorkManager crea sin argumentos (ADR 0014). */
interface ClaimSendHost {
    val claimSender: ClaimSender
}

/**
 * Envía la cola cuando hay red, aunque la app esté cerrada (HU-MOV-09, AC-02). No recibe datos: recorre las
 * cuentas con reporte en el teléfono, así nada de la cuenta queda en la base de WorkManager.
 */
class ClaimSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        when ((applicationContext as ClaimSendHost).claimSender.sendQueued()) {
            SendRun.DONE -> Result.success()
            SendRun.RETRY -> Result.retry()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }
}

/** Un solo trabajo para todo el teléfono, que exige red y espera cada vez más desde 10 s (AC-04, QR-01). */
class WorkManagerClaimSendScheduler(private val context: Context) : ClaimSendScheduler {

    override fun request(now: Boolean) {
        val policy = if (now) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, workRequest())
    }

    companion object {
        const val NAME = "claim_send"

        fun workRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<ClaimSendWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
    }
}
