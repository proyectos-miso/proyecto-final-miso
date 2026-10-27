package com.solventa.app.data.policies

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
import com.solventa.app.domain.policies.PolicySync
import com.solventa.app.domain.policies.SyncOutcome
import com.solventa.app.domain.policies.SyncScheduler
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** La aplicación entrega el sincronizador al trabajo, que WorkManager crea sin argumentos (ADR 0014). */
interface PolicySyncHost {
    val policySync: PolicySync
}

/** Sincroniza cuando hay red; si falla, WorkManager reintenta con espera exponencial (HU-MOV-07). */
class PolicySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    // Una excepción no llega a logcat: WorkManager registra las que escapan con su mensaje (CTL-02).
    override suspend fun doWork(): Result = try {
        when ((applicationContext as PolicySyncHost).policySync.sync(online = true)) {
            SyncOutcome.Failed -> Result.retry()
            else -> Result.success()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }
}

/** Un solo trabajo pendiente, que exige red y espera cada vez más; no lleva datos de la cuenta (CTL-01, CTL-02). */
class WorkManagerSyncScheduler(private val context: Context) : SyncScheduler {

    override fun request() {
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, workRequest())
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    companion object {
        const val NAME = "policy_sync"

        fun workRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<PolicySyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
    }
}
