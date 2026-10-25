package com.solventa.app.data.policies

import com.solventa.app.data.session.FakeSessionCrypto
import com.solventa.app.data.session.InMemorySessionStore
import com.solventa.app.data.session.SecureSessionRepository
import com.solventa.app.data.session.SessionCrypto
import com.solventa.app.domain.auth.DeviceCredential
import com.solventa.app.domain.auth.LoginResult
import com.solventa.app.domain.auth.RefreshResult
import com.solventa.app.domain.auth.SessionTokens
import com.solventa.app.domain.policies.PoliciesResult
import com.solventa.app.domain.policies.SessionEndReason
import com.solventa.app.domain.policies.SyncOutcome
import com.solventa.app.domain.policies.WalletCopy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.ProviderException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.Cipher

// Sincronizador de la copia con la sesión en memoria (HU-MOV-07).
class SessionPolicySyncTest {

    private val crypto = FakeSessionCrypto()
    private val sessions = InMemorySessionStore()
    private var now = 1_000L
    private val session = SecureSessionRepository(crypto, sessions) { now }
    private val auth = FakeSyncAuthApi()
    private val policies = ScriptedPoliciesApi()
    private val store = InMemoryPolicyStore()
    private val scheduler = FakeScheduler()
    private val syncedAt = Instant.parse("2026-10-25T15:00:00Z")
    private val sync = SessionPolicySync(session, auth, policies, store, scheduler, Clock.fixed(syncedAt, ZoneOffset.UTC))
    private val old = SessionTokens("access-viejo", "refresh-viejo")
    private val renewed = SessionTokens("access-nuevo", "refresh-nuevo")
    private val credential = DeviceCredential("device-1", "secreto-1")
    private val policy = samplePolicy()

    private fun bind() = session.save(credential, session.encryptionCipher(), EMAIL, "Asegurada")

    // TC-08: sin sesión no se llama al servidor ni se escribe.
    @Test
    fun `sin sesion no llama al servidor ni escribe`() = runTest {
        assertEquals(SyncOutcome.NoSession, sync.sync(online = true))
        assertEquals(0, policies.calls.size)
        assertEquals(0, auth.calls)
        assertTrue(store.copies.isEmpty())
    }

    // TC-01 y TC-02: con tokens guarda la copia sin repetidos y publica el resultado.
    @Test
    fun `con tokens guarda la copia sin repetidos y publica Synced`() = runTest {
        session.start(EMAIL, old)
        policies.respond = { PoliciesResult.Loaded(listOf(policy, policy)) }
        val published = async { sync.outcomes.first() }
        runCurrent()

        val outcome = sync.sync()

        assertEquals(SyncOutcome.Synced(EMAIL, WalletCopy(listOf(policy), syncedAt)), outcome)
        assertEquals(outcome, published.await())
        assertEquals(listOf(policy), store.copies[EMAIL])
    }

    // TC-01 y AC-10: si la copia no se guardó, la sincronización falla y la fecha no cambia.
    @Test
    fun `si la copia no se guarda responde Failed y no publica`() = runTest {
        session.start(EMAIL, old)
        store.canWrite = false
        policies.respond = { PoliciesResult.Loaded(listOf(policy)) }
        val published = mutableListOf<SyncOutcome>()
        backgroundScope.launch { sync.outcomes.toList(published) }
        runCurrent()

        assertEquals(SyncOutcome.Failed, sync.sync())
        assertTrue(store.copies.isEmpty())
        assertTrue(published.isEmpty())
    }

    // TC-04: un 401 renueva una vez, repite con el token nuevo y la sesión queda con el par nuevo.
    @Test
    fun `un 401 renueva una vez y repite con el token nuevo`() = runTest {
        session.start(EMAIL, old)
        policies.respond = { token -> if (token == old.accessToken) PoliciesResult.Unauthorized else PoliciesResult.Loaded(listOf(policy)) }
        auth.refreshResult = { RefreshResult.Renewed(renewed) }

        assertTrue(sync.sync() is SyncOutcome.Synced)
        assertEquals(listOf(old.refreshToken), auth.refreshed)
        assertEquals(listOf(old.accessToken, renewed.accessToken), policies.calls)
        assertEquals(renewed, session.activeSession)
    }

    // TC-04: dos sincronizaciones a la vez van en orden y la segunda usa el par ya renovado.
    @Test
    fun `dos sincronizaciones a la vez van en orden con una sola renovacion`() = runTest {
        session.start(EMAIL, old)
        val gate = CompletableDeferred<RefreshResult>()
        policies.respond = { token -> if (token == old.accessToken) PoliciesResult.Unauthorized else PoliciesResult.Loaded(listOf(policy)) }
        auth.refreshResult = { gate.await() }

        val first = async { sync.sync() }
        val second = async { sync.sync() }
        runCurrent()
        gate.complete(RefreshResult.Renewed(renewed))

        assertTrue(first.await() is SyncOutcome.Synced)
        assertTrue(second.await() is SyncOutcome.Synced)
        assertEquals(1, auth.refreshed.size)
    }

    // TC-04: un cierre de sesión durante la renovación no la revive y el par nuevo se revoca.
    @Test
    fun `cerrar sesion durante la renovacion no revive la sesion y revoca el par nuevo`() = runTest {
        session.start(EMAIL, old)
        val gate = CompletableDeferred<RefreshResult>()
        policies.respond = { PoliciesResult.Unauthorized }
        auth.refreshResult = { gate.await() }

        val running = launch { sync.sync() }
        runCurrent()
        session.end()
        sync.cancel()
        gate.complete(RefreshResult.Renewed(renewed))
        running.join()

        assertNull(session.openSession)
        assertEquals(listOf(renewed.refreshToken), auth.loggedOut)
        assertTrue(store.copies.isEmpty())
        assertEquals(1, scheduler.cancels)
    }

    // TC-04: lo mismo con el ingreso con la credencial del teléfono.
    @Test
    fun `cerrar sesion durante el ingreso con la credencial revoca el par nuevo`() = runTest {
        bind()
        session.startOffline(EMAIL, credential)
        val gate = CompletableDeferred<LoginResult>()
        auth.biometricResult = { gate.await() }

        val running = launch { sync.sync() }
        runCurrent()
        session.end()
        sync.cancel()
        gate.complete(LoginResult.Success(renewed, "Asegurada"))
        running.join()

        assertNull(session.openSession)
        assertEquals(listOf(renewed.refreshToken), auth.loggedOut)
        assertEquals(0, policies.calls.size)
    }

    // TC-05: un 401 de la renovación borra la copia, termina la sesión y publica EXPIRED.
    @Test
    fun `la renovacion rechazada borra la copia y termina la sesion`() = runTest {
        session.start(EMAIL, old)
        store.put(EMAIL, listOf(policy), syncedAt)
        policies.respond = { PoliciesResult.Unauthorized }
        auth.refreshResult = { RefreshResult.Rejected }

        assertEquals(SyncOutcome.SessionEnded(EMAIL, SessionEndReason.EXPIRED), sync.sync())
        assertNull(session.openSession)
        assertTrue(store.copies.isEmpty())
    }

    @Test
    fun `un segundo 401 despues de renovar borra la copia`() = runTest {
        session.start(EMAIL, old)
        store.put(EMAIL, listOf(policy), syncedAt)
        policies.respond = { PoliciesResult.Unauthorized }
        auth.refreshResult = { RefreshResult.Renewed(renewed) }

        assertEquals(SyncOutcome.SessionEnded(EMAIL, SessionEndReason.EXPIRED), sync.sync())
        assertEquals(1, auth.refreshed.size)
        assertTrue(store.copies.isEmpty())
    }

    // TC-05: la credencial rechazada borra la copia y desvincula la huella, aunque la sesión haya cambiado.
    @Test
    fun `la credencial rechazada desvincula la huella siempre`() = runTest {
        bind()
        session.startOffline(EMAIL, credential)
        store.put(EMAIL, listOf(policy), syncedAt)
        auth.biometricResult = { LoginResult.InvalidCredentials }

        assertEquals(SyncOutcome.SessionEnded(EMAIL, SessionEndReason.DEVICE_REJECTED), sync.sync())
        assertFalse(session.isBound(EMAIL))
        assertTrue(store.copies.isEmpty())

        bind()
        session.startOffline(EMAIL, credential)
        val gate = CompletableDeferred<LoginResult>()
        auth.biometricResult = { gate.await() }
        val running = launch { sync.sync() }
        runCurrent()
        session.start(OTHER, old)
        gate.complete(LoginResult.InvalidCredentials)
        running.join()

        assertFalse(session.isBound(EMAIL))
        assertEquals(OTHER, session.openSession?.email)
    }

    // TC-05: la red, el servidor, un 429 o un bloqueo se reintentan sin tocar la copia ni la sesión.
    @Test
    fun `las fallas pasajeras se reintentan sin borrar ni cerrar`() = runTest {
        session.start(EMAIL, old)
        store.put(EMAIL, listOf(policy), syncedAt)
        policies.respond = { PoliciesResult.Unavailable }
        assertEquals(SyncOutcome.Failed, sync.sync())

        policies.respond = { PoliciesResult.Unauthorized }
        auth.refreshResult = { RefreshResult.Unavailable }
        assertEquals(SyncOutcome.Failed, sync.sync())

        session.end()
        bind()
        session.startOffline(EMAIL, credential)
        listOf(LoginResult.NetworkError, LoginResult.TooManyRequests(30), LoginResult.Locked(900)).forEach { result ->
            auth.biometricResult = { result }
            assertEquals(SyncOutcome.Failed, sync.sync())
        }

        assertEquals(EMAIL, session.openSession?.email)
        assertEquals(listOf(policy), store.copies[EMAIL])
        assertTrue(session.isBound(EMAIL))
    }

    // TC-05: tras una renovación sin respuesta, el 401 siguiente pide el ingreso sin borrar la copia.
    @Test
    fun `tras una renovacion sin respuesta el 401 siguiente conserva la copia`() = runTest {
        session.start(EMAIL, old)
        store.put(EMAIL, listOf(policy), syncedAt)
        policies.respond = { PoliciesResult.Unauthorized }
        auth.refreshResult = { RefreshResult.Unanswered }
        assertEquals(SyncOutcome.Failed, sync.sync())

        auth.refreshResult = { RefreshResult.Rejected }
        assertEquals(SyncOutcome.SessionEnded(EMAIL, SessionEndReason.RELOGIN), sync.sync())
        assertNull(session.openSession)
        assertEquals(listOf(policy), store.copies[EMAIL])
    }

    @Test
    fun `una renovacion exitosa limpia la marca y un rechazo posterior borra`() = runTest {
        session.start(EMAIL, old)
        store.put(EMAIL, listOf(policy), syncedAt)
        policies.respond = { token -> if (token == renewed.accessToken) PoliciesResult.Loaded(listOf(policy)) else PoliciesResult.Unauthorized }
        auth.refreshResult = { RefreshResult.Unanswered }
        sync.sync()
        auth.refreshResult = { RefreshResult.Renewed(renewed) }
        assertTrue(sync.sync() is SyncOutcome.Synced)

        policies.respond = { PoliciesResult.Unauthorized }
        auth.refreshResult = { RefreshResult.Rejected }
        assertEquals(SyncOutcome.SessionEnded(EMAIL, SessionEndReason.EXPIRED), sync.sync())
        assertTrue(store.copies.isEmpty())
    }

    // TC-06: dentro del tope entra con la credencial y queda con tokens, sin credencial.
    @Test
    fun `con la credencial vigente entra sin pedir la huella y la suelta`() = runTest {
        bind()
        session.startOffline(EMAIL, credential)
        auth.biometricResult = { LoginResult.Success(renewed, "Asegurada") }
        policies.respond = { PoliciesResult.Loaded(listOf(policy)) }

        assertTrue(sync.sync() is SyncOutcome.Synced)
        assertEquals(listOf(credential), auth.biometricCredentials)
        assertEquals(renewed, session.activeSession)
        assertNull(session.openSession?.credential)
    }

    // TC-06 y TC-07: vencida, sin red no se llama al servidor; con red se pide el ingreso sin borrar la copia.
    @Test
    fun `la credencial vencida no se usa y con red pide el ingreso sin borrar`() = runTest {
        bind()
        session.startOffline(EMAIL, credential)
        store.put(EMAIL, listOf(policy), syncedAt)
        now += 15 * 60 * 1000L

        assertEquals(SyncOutcome.NoSession, sync.sync())
        assertNull(session.openSession?.credential)
        assertEquals(EMAIL, session.openSession?.email)

        assertEquals(SyncOutcome.SessionEnded(EMAIL, SessionEndReason.RELOGIN), sync.sync(online = true))
        assertEquals(0, auth.calls)
        assertNull(session.openSession)
        assertEquals(listOf(policy), store.copies[EMAIL])
        assertTrue(session.isBound(EMAIL))
    }

    // TC-06: con la llave invalidada no se usa la credencial; con el Keystore ocupado se reintenta.
    @Test
    fun `una llave invalidada o no disponible no usa la credencial`() = runTest {
        bind()
        session.startOffline(EMAIL, credential)
        crypto.invalidated = true

        assertEquals(SyncOutcome.SessionEnded(EMAIL, SessionEndReason.RELOGIN), sync.sync(online = true))
        assertEquals(0, auth.calls)
        assertFalse(session.hasStoredSession())

        val busy = object : SessionCrypto by FakeSessionCrypto() {
            override fun decryptionCipher(iv: ByteArray): Cipher? = throw ProviderException("Keystore ocupado")
        }
        val failing = SecureSessionRepository(busy, sessions) { now }
        failing.save(credential, failing.encryptionCipher(), EMAIL, "Asegurada")
        failing.startOffline(EMAIL, credential)
        val other = SessionPolicySync(failing, auth, policies, store, scheduler)

        assertEquals(SyncOutcome.Failed, other.sync(online = true))
        assertTrue(failing.isBound(EMAIL))
        assertEquals(credential, failing.openSession?.credential)
    }

    // TC-13: un cambio de cuenta durante la descarga no guarda en ninguna, y un rechazo tardío no toca la otra.
    @Test
    fun `la sincronizacion solo afecta a su cuenta y su sesion`() = runTest {
        session.start(EMAIL, old)
        store.put(OTHER, listOf(policy), syncedAt)
        val gate = CompletableDeferred<PoliciesResult>()
        policies.respond = { gate.await() }

        val first = async { sync.sync() }
        runCurrent()
        session.start(OTHER, renewed)
        gate.complete(PoliciesResult.Loaded(listOf(samplePolicy("POL-2026-00009"))))

        assertEquals(SyncOutcome.NoSession, first.await())
        assertEquals(null, store.copies[EMAIL])
        assertEquals(listOf(policy), store.copies[OTHER])

        session.start(EMAIL, old)
        val refreshGate = CompletableDeferred<RefreshResult>()
        policies.respond = { PoliciesResult.Unauthorized }
        auth.refreshResult = { refreshGate.await() }
        val second = async { sync.sync() }
        runCurrent()
        session.start(OTHER, renewed)
        refreshGate.complete(RefreshResult.Rejected)

        assertEquals(SyncOutcome.NoSession, second.await())
        assertEquals(OTHER, session.openSession?.email)
        assertEquals(listOf(policy), store.copies[OTHER])
    }

    // TC-10: una excepción no sale de la sincronización ni cierra la app.
    @Test
    fun `una excepcion responde Failed`() = runTest {
        session.start(EMAIL, old)
        policies.respond = { throw IllegalStateException("falla inesperada") }

        assertEquals(SyncOutcome.Failed, sync.sync())
    }

    @Test
    fun `pedir y cancelar delegan en el programador`() = runTest {
        sync.schedule()
        sync.cancel()

        assertEquals(1, scheduler.requests)
        assertEquals(1, scheduler.cancels)
    }

    private companion object {
        const val EMAIL = "asegurada@example.com"
        const val OTHER = "otra.cuenta@example.com"
    }
}
