package com.solventa.app.data.claims

import com.solventa.app.data.policies.FakeScheduler
import com.solventa.app.data.policies.FakeSyncAuthApi
import com.solventa.app.data.policies.InMemoryPolicyStore
import com.solventa.app.data.policies.PassphraseVault
import com.solventa.app.data.policies.ScriptedPoliciesApi
import com.solventa.app.data.policies.SessionPolicySync
import com.solventa.app.data.session.FakeSessionCrypto
import com.solventa.app.data.session.InMemorySessionStore
import com.solventa.app.data.session.ReportTokens
import com.solventa.app.data.session.SecureSessionRepository
import com.solventa.app.domain.auth.RefreshResult
import com.solventa.app.domain.auth.SessionTokens
import com.solventa.app.domain.claims.CaptureFix
import com.solventa.app.domain.claims.ClaimOutbox
import com.solventa.app.domain.claims.ClaimReport
import com.solventa.app.domain.claims.ClaimSubmitApi
import com.solventa.app.domain.claims.EvidencePhoto
import com.solventa.app.domain.claims.PhotoImage
import com.solventa.app.domain.claims.ReportState
import com.solventa.app.domain.claims.StoredReport
import com.solventa.app.domain.claims.SubmissionPhoto
import com.solventa.app.domain.claims.SubmitResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Cola en memoria por id de cuenta, con las reglas del almacén real. */
class InMemoryOutbox : ClaimOutbox {
    val reports = mutableMapOf<String, ClaimReport>()
    val images = mutableMapOf<String, ByteArray>()
    val damaged = mutableSetOf<String>()
    val attempts = mutableMapOf<String, Int>()
    var unavailable = false

    override fun accounts(): List<String> = reports.keys.toList()

    override suspend fun queued(accountId: String): StoredReport = when {
        unavailable -> StoredReport.Unavailable
        else -> reports[accountId]?.takeIf { it.state == ReportState.QUEUED }?.let { StoredReport.Found(it) } ?: StoredReport.None
    }

    override suspend fun photo(accountId: String, photoId: String): PhotoImage = when (photoId) {
        in damaged -> PhotoImage.Damaged
        else -> images[photoId]?.let { PhotoImage.Found(it) } ?: PhotoImage.Unavailable
    }

    override suspend fun countAttempt(accountId: String, reportId: String) {
        attempts[reportId] = (attempts[reportId] ?: 0) + 1
    }

    override suspend fun reject(accountId: String, reportId: String, reason: String): Boolean {
        val report = reports[accountId]?.takeIf { it.id == reportId && it.state == ReportState.QUEUED } ?: return false
        reports[accountId] = report.copy(state = ReportState.REJECTED, rejectionReason = reason)
        return true
    }

    override suspend fun received(accountId: String, reportId: String) {
        if (reports[accountId]?.id == reportId) reports.remove(accountId)
    }
}

class RecordingSubmitApi : ClaimSubmitApi {
    var respond: (String) -> SubmitResult = { SubmitResult.Received("SIN-2026-00001") }
    val tokens = mutableListOf<String>()
    val sent = mutableListOf<List<SubmissionPhoto>>()

    override suspend fun submit(token: String, report: ClaimReport, photos: List<SubmissionPhoto>): SubmitResult {
        tokens += token
        sent += photos
        return respond(token)
    }
}

class MapReportTokens : ReportTokens {
    val tokens = mutableMapOf<String, String>()
    override fun save(accountEmail: String, token: String) {
        tokens[PassphraseVault.accountId(accountEmail)] = token
    }
    override fun read(accountId: String): String? = tokens[accountId]
    override fun delete(accountEmail: String) {
        tokens.remove(PassphraseVault.accountId(accountEmail))
    }
}

// Envío de la cola: recibido, reintento, rechazo definitivo y elección del token (HU-MOV-09).
class ClaimSenderTest {

    private val outbox = InMemoryOutbox()
    private val api = RecordingSubmitApi()
    private val reportTokens = MapReportTokens()
    private var sessionToken: String? = null
    private var syncs = 0
    private val account = PassphraseVault.accountId(EMAIL)

    private val sender = ClaimSender(
        outbox, api, reportTokens,
        session = { id, call -> sessionToken?.takeIf { id == account }?.let { call(it) } },
        onReceived = { syncs++ },
    )

    private fun queue(photos: Int = 2) {
        val list = (1..photos).map { EvidencePhoto("f$it", it, Instant.EPOCH, null, CaptureFix(4.6, -74.0, 5f, false), "h$it", 3) }
        list.forEach { outbox.images[it.id] = byteArrayOf(1, 2, it.position.toByte()) }
        outbox.reports[account] = ClaimReport("r-1", "POL-2026-00001", "Golpe", ReportState.QUEUED, list, Instant.EPOCH, Instant.EPOCH)
        reportTokens.tokens[account] = "token-de-reporte"
    }

    @Test
    fun `AC-08 recibido borra la copia local y pide sincronizar Mis siniestros`() = runTest {
        queue()

        assertEquals(SendRun.DONE, sender.sendQueued())

        assertTrue(outbox.reports.isEmpty())
        assertEquals(1, syncs)
        assertEquals(listOf(1, 2), api.sent.single().map { it.position })
        assertEquals(listOf(1, 2), api.sent.single().map { it.jpeg[2].toInt() })
    }

    @Test
    fun `AC-02 sin sesión abierta usa el token de reporte guardado`() = runTest {
        queue()

        sender.sendQueued()

        assertEquals(listOf("token-de-reporte"), api.tokens)
    }

    @Test
    fun `con la sesión de la cuenta abierta usa su token de acceso`() = runTest {
        queue()
        sessionToken = "token-de-acceso"

        sender.sendQueued()

        assertEquals(listOf("token-de-acceso"), api.tokens)
    }

    @Test
    fun `AC-04 un error del servidor reintenta, cuenta el intento y conserva el reporte`() = runTest {
        queue()
        api.respond = { SubmitResult.Retry }

        assertEquals(SendRun.RETRY, sender.sendQueued())

        assertEquals(ReportState.QUEUED, outbox.reports[account]?.state)
        assertEquals(1, outbox.attempts["r-1"])
        assertEquals(0, syncs)
    }

    @Test
    fun `AC-09 un rechazo definitivo deja de reintentar y guarda el motivo`() = runTest {
        queue()
        api.respond = { SubmitResult.Rejected("La póliza no está vigente.") }

        assertEquals(SendRun.DONE, sender.sendQueued())

        val report = outbox.reports.getValue(account)
        assertEquals(ReportState.REJECTED, report.state)
        assertEquals("La póliza no está vigente.", report.rejectionReason)

        assertEquals(SendRun.DONE, sender.sendQueued())
        assertEquals("un rechazado no se vuelve a enviar", 1, api.tokens.size)
    }

    @Test
    fun `una foto dañada en el teléfono rechaza el reporte sin enviarlo y pide tomarlas de nuevo`() = runTest {
        queue()
        outbox.damaged += "f2"

        assertEquals(SendRun.DONE, sender.sendQueued())

        assertTrue(api.tokens.isEmpty())
        assertEquals(ReportState.REJECTED, outbox.reports.getValue(account).state)
        assertTrue(outbox.reports.getValue(account).rejectionReason!!.contains("tome las fotos de nuevo"))
    }

    @Test
    fun `un token rechazado deja el reporte en cola hasta el próximo ingreso`() = runTest {
        queue()
        api.respond = { SubmitResult.Unauthorized }

        assertEquals(SendRun.DONE, sender.sendQueued())

        assertEquals(ReportState.QUEUED, outbox.reports.getValue(account).state)
    }

    @Test
    fun `sin token de reporte ni sesión no llama al servidor y espera`() = runTest {
        queue()
        reportTokens.tokens.clear()

        assertEquals(SendRun.DONE, sender.sendQueued())

        assertTrue(api.tokens.isEmpty())
        assertEquals(ReportState.QUEUED, outbox.reports.getValue(account).state)
    }

    @Test
    fun `con el teléfono bloqueado reintenta sin tocar el reporte`() = runTest {
        queue()
        outbox.unavailable = true

        assertEquals(SendRun.RETRY, sender.sendQueued())
        assertTrue(api.tokens.isEmpty())
    }

    @Test
    fun `un borrador no se envía`() = runTest {
        queue()
        outbox.reports[account] = outbox.reports.getValue(account).copy(state = ReportState.DRAFT)

        assertEquals(SendRun.DONE, sender.sendQueued())
        assertTrue(api.tokens.isEmpty())
    }

    @Test
    fun `la sesión abierta solo presta su token a su propia cuenta y renueva una vez ante 401`() = runTest {
        val session = SecureSessionRepository(FakeSessionCrypto(), InMemorySessionStore()) { 0L }
        val auth = FakeSyncAuthApi()
        val sync = SessionPolicySync(session, auth, ScriptedPoliciesApi(), InMemoryPolicyStore(), FakeScheduler())
        val submitter = OpenSessionSubmitter(session, sync)
        val used = mutableListOf<String>()

        assertEquals(null, submitter.submit(account) { SubmitResult.Retry })

        session.start(EMAIL, SessionTokens("access-viejo", "refresh-viejo"))
        assertEquals(null, submitter.submit(PassphraseVault.accountId("otra.cuenta@correo.test")) { SubmitResult.Retry })

        auth.refreshResult = { RefreshResult.Renewed(SessionTokens("access-nuevo", "refresh-nuevo")) }
        val result = submitter.submit(account) { token ->
            used += token
            if (token == "access-viejo") SubmitResult.Unauthorized else SubmitResult.Received("SIN-2026-00001")
        }

        assertEquals(SubmitResult.Received("SIN-2026-00001"), result)
        assertEquals(listOf("access-viejo", "access-nuevo"), used)
    }

    private companion object {
        const val EMAIL = "asegurado@correo.test"
    }
}
