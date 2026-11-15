package com.solventa.app.data.claims

import android.app.Application
import android.content.Context
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
import com.solventa.app.data.policies.PassphraseVault
import com.solventa.app.domain.claims.ClaimReport
import com.solventa.app.domain.claims.ClaimSubmitApi
import com.solventa.app.domain.claims.ReportState
import com.solventa.app.domain.claims.SubmissionPhoto
import com.solventa.app.domain.claims.SubmitResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Aplicación de prueba que entrega al trabajo un remitente con cola y servidor dobles. */
class ClaimSendTestApplication : Application(), ClaimSendHost {
    val outbox = InMemoryOutbox()
    var respond: () -> SubmitResult = { SubmitResult.Received("SIN-2026-00001") }
    var fail = false
    private val api = object : ClaimSubmitApi {
        override suspend fun submit(token: String, report: ClaimReport, photos: List<SubmissionPhoto>): SubmitResult {
            if (fail) throw IllegalStateException("falla inesperada")
            return respond()
        }
    }
    private val tokens = MapReportTokens().apply { tokens[ACCOUNT] = "token-de-reporte" }
    override val claimSender = ClaimSender(outbox, api, tokens, { _, _ -> null })

    companion object {
        val ACCOUNT = PassphraseVault.accountId("asegurado@correo.test")
    }
}

// Trabajo de envío de la cola con WorkManager (HU-MOV-09: AC-02, AC-04, QR-01).
@RunWith(RobolectricTestRunner::class)
@Config(application = ClaimSendTestApplication::class)
class ClaimSendWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app = context as ClaimSendTestApplication
    private val scheduler = WorkManagerClaimSendScheduler(context)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        app.outbox.reports[ClaimSendTestApplication.ACCOUNT] =
            ClaimReport("r-1", "POL-2026-00001", "Golpe", ReportState.QUEUED, emptyList(), Instant.EPOCH, Instant.EPOCH)
    }

    private fun unique(): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkManagerClaimSendScheduler.NAME).get()

    @Test
    fun `AC-02 un solo trabajo que espera la red y al tenerla envía la cola`() {
        repeat(3) { scheduler.request(now = false) }
        val pending = unique().single()
        assertEquals(WorkInfo.State.ENQUEUED, pending.state)
        assertTrue(app.outbox.reports.isNotEmpty())

        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(pending.id)

        assertTrue(app.outbox.reports.isEmpty())
        assertEquals(WorkInfo.State.SUCCEEDED, unique().single().state)
    }

    @Test
    fun `pedir el envío ya reemplaza la espera pendiente`() {
        scheduler.request(now = false)
        val first = unique().single().id

        scheduler.request(now = true)

        val infos = unique()
        assertTrue(infos.none { it.id == first && it.state == WorkInfo.State.ENQUEUED })
        assertEquals(1, infos.count { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun `QR-01 exige red, reintenta con espera exponencial desde 10 s y no lleva datos de la cuenta`() {
        val request = WorkManagerClaimSendScheduler.workRequest()

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(10_000L, request.workSpec.backoffDelayDuration)
        assertEquals(Data.EMPTY, request.workSpec.input)
    }

    @Test
    fun `AC-04 un reintento o una excepción piden retry y lo demás termina`() = runBlocking {
        val worker = TestListenableWorkerBuilder<ClaimSendWorker>(context).build()

        app.respond = { SubmitResult.Retry }
        assertEquals(ListenableWorker.Result.retry(), worker.doWork())

        app.fail = true
        assertEquals(ListenableWorker.Result.retry(), worker.doWork())

        app.fail = false
        app.respond = { SubmitResult.Rejected("motivo") }
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
