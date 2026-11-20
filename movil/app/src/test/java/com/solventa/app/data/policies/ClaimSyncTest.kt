package com.solventa.app.data.policies

import com.solventa.app.data.session.FakeSessionCrypto
import com.solventa.app.data.session.InMemorySessionStore
import com.solventa.app.data.session.SecureSessionRepository
import com.solventa.app.domain.auth.RefreshResult
import com.solventa.app.domain.auth.SessionTokens
import com.solventa.app.domain.claims.ClaimsCopy
import com.solventa.app.domain.claims.ClaimsResult
import com.solventa.app.domain.policies.CaratulaResult
import com.solventa.app.domain.policies.PoliciesResult
import com.solventa.app.domain.policies.PreferencesResult
import com.solventa.app.domain.policies.PushPreferences
import com.solventa.app.domain.policies.SyncOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

// Siniestros, avisos y acciones en la sincronización (HU-MOV-11: AC-04, AC-08, AC-09, AC-11).
class ClaimSyncTest {

    private val session = SecureSessionRepository(FakeSessionCrypto(), InMemorySessionStore()) { 1_000L }
    private val auth = FakeSyncAuthApi()
    private val policies = ScriptedPoliciesApi()
    private val claimsApi = FakeClaimsApi()
    private val store = InMemoryPolicyStore()
    private val poster = RecordingPoster()
    private val preferences = InMemoryNotificationPreferences()
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private val preferencesApi = ServerPreferencesApi()
    private val sync = SessionPolicySync(
        session, auth, policies, store, FakeScheduler(), Clock.fixed(now, ZoneOffset.UTC),
        claimsApi = claimsApi, notices = poster, preferences = preferences, preferencesApi = preferencesApi,
    )
    private val updatedAt = Instant.parse("2026-11-09T11:00:00Z")

    private fun claimsNow(vararg statuses: Pair<String, String>) {
        claimsApi.result = ClaimsResult.Loaded(ClaimsCopy(statuses.map { (n, s) -> sampleClaim(n, s, s) }, updatedAt))
    }

    @Test
    fun `guarda los siniestros con la copia y los publica`() = runTest {
        session.start(EMAIL, SessionTokens("a", "r"))
        policies.respond = { PoliciesResult.Loaded(emptyList()) }
        claimsNow("SIN-1" to "IN_REVIEW")

        val outcome = sync.sync() as SyncOutcome.Synced

        assertEquals(listOf("SIN-1"), outcome.copy.claims?.claims?.map { it.claimNumber })
        assertEquals(updatedAt, store.readClaims(EMAIL)?.updatedAt)
        assertTrue(poster.posted.isEmpty())
    }

    @Test
    fun `AC-08 un cambio de estado avisa una vez y abre ese siniestro`() = runTest {
        session.start(EMAIL, SessionTokens("a", "r"))
        policies.respond = { PoliciesResult.Loaded(emptyList()) }
        claimsNow("SIN-1" to "IN_REVIEW", "SIN-2" to "IN_REVIEW")
        sync.sync()

        claimsNow("SIN-1" to "PAID", "SIN-2" to "IN_REVIEW", "SIN-3" to "REPORTED")
        sync.sync()
        sync.sync()

        assertEquals(listOf("SIN-1"), poster.posted.map { it.claimNumber })
    }

    @Test
    fun `si los siniestros fallan la billetera se sincroniza y conserva la copia anterior`() = runTest {
        session.start(EMAIL, SessionTokens("a", "r"))
        policies.respond = { PoliciesResult.Loaded(emptyList()) }
        claimsNow("SIN-1" to "IN_REVIEW")
        sync.sync()
        claimsApi.result = ClaimsResult.Unavailable

        val outcome = sync.sync() as SyncOutcome.Synced

        assertNull(outcome.copy.claims)
        assertEquals("SIN-1", store.readClaims(EMAIL)?.claims?.single()?.claimNumber)
    }

    @Test
    fun `AC-09 el aviso de vencimiento sale una sola vez por poliza y vigencia`() = runTest {
        session.start(EMAIL, SessionTokens("a", "r"))
        val soon = samplePolicy("POL-1", "2026-11-30T00:00:00Z")
        val far = samplePolicy("POL-2", "2027-06-01T00:00:00Z")
        policies.respond = { PoliciesResult.Loaded(listOf(soon, far)) }

        sync.sync()
        sync.sync()

        assertEquals(1, poster.posted.size)
        assertTrue(poster.posted.single().title.contains("POL-1"))
        assertNull(poster.posted.single().claimNumber)
    }

    @Test
    fun `AC-11 cerrar sesion borra los siniestros con la copia`() = runTest {
        session.start(EMAIL, SessionTokens("a", "r"))
        policies.respond = { PoliciesResult.Loaded(emptyList()) }
        claimsNow("SIN-1" to "IN_REVIEW")
        sync.sync()

        store.delete(EMAIL)

        assertNull(store.readClaims(EMAIL))
    }

    @Test
    fun `las acciones renuevan la sesion una vez ante un 401`() = runTest {
        session.start(EMAIL, SessionTokens("viejo", "r"))
        auth.refreshResult = { RefreshResult.Renewed(SessionTokens("nuevo", "r2")) }
        val api = object : FakeActionsApi() {
            override suspend fun caratula(accessToken: String, policyNumber: String) =
                if (accessToken == "viejo") CaratulaResult.Unauthorized else CaratulaResult.Ready("https://s3/caratula.pdf")
        }

        val result = SessionPolicyActions(sync, api).caratula("POL-1")

        assertEquals(CaratulaResult.Ready("https://s3/caratula.pdf"), result)
        assertEquals(listOf("r"), auth.refreshed)
    }

    @Test
    fun `sin sesion las acciones no llaman al servidor`() = runTest {
        assertEquals(CaratulaResult.Unauthorized, SessionPolicyActions(sync, FakeActionsApi()).caratula("POL-1"))
    }

    // FE-MOV-04: los avisos locales respetan las categorías apagadas por el asegurado.

    @Test
    fun `FE-MOV-04 con Siniestros apagado un cambio de estado no avisa pero si se guarda`() = runTest {
        session.start(EMAIL, SessionTokens("a", "r"))
        policies.respond = { PoliciesResult.Loaded(emptyList()) }
        preferencesApi.server = PushPreferences(claims = false)
        claimsNow("SIN-1" to "IN_REVIEW")
        sync.sync()

        claimsNow("SIN-1" to "REJECTED")
        sync.sync()

        assertTrue(poster.posted.isEmpty())
        assertEquals("REJECTED", store.readClaims(EMAIL)?.claims?.single()?.status)
    }

    @Test
    fun `FE-MOV-04 un siniestro pagado es un aviso de Pagos, no de Siniestros`() = runTest {
        session.start(EMAIL, SessionTokens("a", "r"))
        policies.respond = { PoliciesResult.Loaded(emptyList()) }
        claimsNow("SIN-1" to "IN_REVIEW", "SIN-2" to "IN_REVIEW")
        sync.sync()

        preferencesApi.server = PushPreferences(payments = false)
        claimsNow("SIN-1" to "PAID", "SIN-2" to "REJECTED")
        sync.sync()
        assertEquals(listOf("SIN-2"), poster.posted.map { it.claimNumber })

        preferencesApi.server = PushPreferences(claims = false)
        claimsNow("SIN-1" to "IN_REVIEW", "SIN-2" to "PAID")
        sync.sync()
        assertEquals(listOf("SIN-2", "SIN-2"), poster.posted.map { it.claimNumber })
    }

    @Test
    fun `FE-MOV-04 con Vencimientos apagado no avisa la poliza por vencer y al encenderlo avisa`() = runTest {
        session.start(EMAIL, SessionTokens("a", "r"))
        policies.respond = { PoliciesResult.Loaded(listOf(samplePolicy("POL-1", "2026-11-30T00:00:00Z"))) }
        preferencesApi.server = PushPreferences(renewals = false)

        sync.sync()
        assertTrue(poster.posted.isEmpty())

        preferencesApi.server = PushPreferences()
        sync.sync()
        assertEquals(1, poster.posted.size)
    }

    @Test
    fun `FE-MOV-04 tras cerrar sesion la sincronizacion trae las preferencias del servidor antes de avisar`() = runTest {
        session.start(EMAIL, SessionTokens("a", "r"))
        preferencesApi.server = PushPreferences(renewals = false)
        policies.respond = { PoliciesResult.Loaded(listOf(samplePolicy("POL-1", "2026-11-30T00:00:00Z"))) }

        sync.sync()

        assertTrue(poster.posted.isEmpty())
        assertEquals(PushPreferences(renewals = false), preferences.read(EMAIL))
    }

    @Test
    fun `FE-MOV-04 un cambio hecho sin red se envia en la siguiente sincronizacion`() = runTest {
        session.start(EMAIL, SessionTokens("a", "r"))
        policies.respond = { PoliciesResult.Loaded(emptyList()) }
        preferences.save(EMAIL, PushPreferences(payments = false), pending = true)

        sync.sync()

        assertEquals(PushPreferences(payments = false), preferencesApi.server)
        assertEquals(false, preferences.pending(EMAIL))
        preferencesApi.available = false
        preferences.save(EMAIL, PushPreferences(claims = false), pending = true)
        sync.sync()
        assertTrue(preferences.pending(EMAIL))
    }

    @Test
    fun `FE-MOV-04 con el ingreso sin red las preferencias quedan sin conexion, no con la sesion vencida`() = runTest {
        session.startOffline(EMAIL, com.solventa.app.domain.auth.DeviceCredential("dispositivo", "secreto"))
        val actions = SessionPolicyActions(sync, FakeActionsApi())

        assertEquals(PreferencesResult.Unavailable, actions.savePushPreferences(PushPreferences(claims = false)))
        session.end()
        assertEquals(PreferencesResult.Unauthorized, actions.pushPreferences())
    }

    @Test
    fun `FE-MOV-04 si la pantalla cambia la copia durante la sincronizacion gana la pantalla`() = runTest {
        session.start(EMAIL, SessionTokens("a", "r"))
        policies.respond = { PoliciesResult.Loaded(emptyList()) }
        preferencesApi.during = { preferences.save(EMAIL, PushPreferences(payments = false), pending = true) }

        sync.sync()

        assertEquals(PushPreferences(payments = false), preferences.read(EMAIL))
        assertTrue(preferences.pending(EMAIL))
    }

    @Test
    fun `FE-MOV-04 si el servidor rechaza lo pendiente se descarta y queda lo del servidor`() = runTest {
        session.start(EMAIL, SessionTokens("a", "r"))
        policies.respond = { PoliciesResult.Loaded(emptyList()) }
        preferencesApi.server = PushPreferences(claims = false)
        preferencesApi.rejectSaves = true
        preferences.save(EMAIL, PushPreferences(payments = false), pending = true)

        sync.sync()

        assertEquals(PushPreferences(claims = false), preferences.read(EMAIL))
        assertEquals(false, preferences.pending(EMAIL))
    }

    private companion object {
        const val EMAIL = "asegurada@example.com"
    }
}

class ServerPreferencesApi : FakeActionsApi() {
    var server = PushPreferences()
    var available = true
    var rejectSaves = false
    var during: () -> Unit = {}

    override suspend fun pushPreferences(accessToken: String): PreferencesResult {
        during()
        return if (available) PreferencesResult.Loaded(server) else PreferencesResult.Unavailable
    }

    override suspend fun savePushPreferences(accessToken: String, preferences: PushPreferences): PreferencesResult {
        if (!available) return PreferencesResult.Unavailable
        if (rejectSaves) return PreferencesResult.Invalid
        server = preferences
        return PreferencesResult.Loaded(server)
    }
}

open class FakeActionsApi : com.solventa.app.domain.policies.PolicyActionsApi {
    override suspend fun caratula(accessToken: String, policyNumber: String): CaratulaResult = error("no se usa")
    override suspend fun renew(accessToken: String, policyNumber: String) = error("no se usa")
    override suspend fun registerPushToken(accessToken: String, token: String): Boolean? = error("no se usa")
    override suspend fun pushPreferences(accessToken: String): PreferencesResult = error("no se usa")
    override suspend fun savePushPreferences(accessToken: String, preferences: PushPreferences): PreferencesResult = error("no se usa")
}
