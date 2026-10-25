package com.solventa.app.data.policies

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.solventa.app.domain.policies.PolicySync
import com.solventa.app.domain.policies.SessionEndReason
import com.solventa.app.domain.policies.SyncOutcome
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Aplicación de prueba que entrega un sincronizador doble al trabajo. */
class SyncTestApplication : Application(), PolicySyncHost {
    override val policySync = FakePolicySync()
}

class FakePolicySync : PolicySync {
    var next: suspend () -> SyncOutcome = { SyncOutcome.NoSession }
    val onlineCalls = mutableListOf<Boolean>()
    override val outcomes: SharedFlow<SyncOutcome> = MutableSharedFlow()

    override suspend fun sync(online: Boolean): SyncOutcome {
        onlineCalls += online
        return next()
    }

    override fun schedule() = Unit
    override fun cancel() = Unit
}

// Trabajo en segundo plano de la sincronización (HU-MOV-07: TC-03, TC-10, TC-11).
@RunWith(RobolectricTestRunner::class)
@Config(application = SyncTestApplication::class)
class PolicySyncWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sync = (context as SyncTestApplication).policySync
    private val scheduler = WorkManagerSyncScheduler(context)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun unique(): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkManagerSyncScheduler.NAME).get()

    // TC-03: un solo trabajo, que no corre sin red y que al tener red sincroniza con online.
    @Test
    fun `un solo trabajo que espera la red y luego sincroniza`() {
        repeat(5) { scheduler.request() }
        val pending = unique().single()

        assertEquals(WorkInfo.State.ENQUEUED, pending.state)
        assertTrue(sync.onlineCalls.isEmpty())

        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(pending.id)

        assertEquals(listOf(true), sync.onlineCalls)
        assertEquals(WorkInfo.State.SUCCEEDED, unique().single().state)
    }

    @Test
    fun `cancelar quita el trabajo pendiente`() {
        scheduler.request()
        scheduler.cancel()

        assertEquals(WorkInfo.State.CANCELLED, unique().single().state)
    }

    // TC-03: exige red y reintenta con espera exponencial desde 10 s.
    @Test
    fun `el trabajo exige red y reintenta con espera exponencial`() {
        val spec = WorkManagerSyncScheduler.workRequest().workSpec

        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(10_000L, spec.backoffDelayDuration)
    }

    // TC-03 y TC-10: una falla o una excepción se reintentan sin escapar; lo demás termina.
    @Test
    fun `una falla o una excepcion se reintentan y lo demas termina`() = runBlocking {
        val worker = TestListenableWorkerBuilder<PolicySyncWorker>(context).build()
        // null representa una excepción inesperada de la sincronización.
        val cases = listOf(
            SyncOutcome.Failed to ListenableWorker.Result.retry(),
            null to ListenableWorker.Result.retry(),
            SyncOutcome.NoSession to ListenableWorker.Result.success(),
            SyncOutcome.SessionEnded("asegurada@example.com", SessionEndReason.RELOGIN) to ListenableWorker.Result.success(),
        )
        cases.forEach { (outcome, expected) ->
            sync.next = { outcome ?: throw IllegalStateException("falla inesperada") }
            assertEquals(expected, worker.doWork())
        }
    }

    // TC-10: el trabajo no lleva datos de la cuenta.
    @Test
    fun `el trabajo no lleva datos de entrada ni etiquetas propias`() {
        val request = WorkManagerSyncScheduler.workRequest()

        assertEquals(Data.EMPTY, request.workSpec.input)
        assertEquals(setOf(PolicySyncWorker::class.java.name), request.tags)
    }

    // TC-11: el manifiesto final no pide los permisos que no se usan ni exporta más de lo que protege el sistema.
    @Test
    fun `el manifiesto final no pide ni exporta de mas`() {
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        val permissions = info.requestedPermissions.orEmpty().toSet()

        assertTrue(Manifest.permission.ACCESS_NETWORK_STATE in permissions)
        assertTrue(Manifest.permission.WAKE_LOCK in permissions)
        assertFalse(Manifest.permission.RECEIVE_BOOT_COMPLETED in permissions)
        assertFalse(Manifest.permission.FOREGROUND_SERVICE in permissions)

        val exported = info.services.orEmpty().filter { it.exported }.associate { it.name to it.permission } +
            info.receivers.orEmpty().filter { it.exported }.associate { it.name to it.permission }
        assertEquals(
            mapOf(
                "androidx.work.impl.background.systemjob.SystemJobService" to "android.permission.BIND_JOB_SERVICE",
                "androidx.work.impl.diagnostics.DiagnosticsReceiver" to Manifest.permission.DUMP,
            ),
            exported.filterKeys { it.startsWith("androidx.work.") },
        )
        assertTrue(exported.values.all { it != null })
    }
}
