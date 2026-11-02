package com.solventa.app.ui.wallet

import com.solventa.app.data.policies.FakePoliciesApi
import com.solventa.app.data.policies.FakeScheduler
import com.solventa.app.data.policies.FakeSyncAuthApi
import com.solventa.app.data.policies.InMemoryPolicyStore
import com.solventa.app.data.policies.PendingPoliciesApi
import com.solventa.app.data.policies.ScriptedPoliciesApi
import com.solventa.app.data.policies.SessionPolicySync
import com.solventa.app.data.policies.samplePolicy
import com.solventa.app.data.session.FakeSessionCrypto
import com.solventa.app.data.session.InMemorySessionStore
import com.solventa.app.data.session.SecureSessionRepository
import com.solventa.app.domain.auth.RefreshResult
import com.solventa.app.domain.auth.SessionTokens
import com.solventa.app.domain.policies.PoliciesApi
import com.solventa.app.domain.policies.PoliciesResult
import com.solventa.app.domain.policies.SessionEndReason
import com.solventa.app.domain.policies.StoredCopy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

// Billetera de pólizas con y sin conexión (HU-MOV-04: TC-04, TC-05, TC-07; HU-MOV-07: TC-01, TC-13, TC-19).
@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {

    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val active = samplePolicy("POL-2026-00001", "2027-01-01T00:00:00Z")
    private val other = samplePolicy("POL-2026-00002", "2027-03-01T00:00:00Z")
    private val tokens = SessionTokens("token", "refresh")

    private lateinit var api: FakePoliciesApi
    private lateinit var auth: FakeSyncAuthApi
    private lateinit var store: InMemoryPolicyStore
    private lateinit var session: SecureSessionRepository
    private lateinit var scheduler: FakeScheduler
    private lateinit var sync: SessionPolicySync
    private lateinit var viewModel: WalletViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api = FakePoliciesApi()
        auth = FakeSyncAuthApi()
        store = InMemoryPolicyStore()
        session = SecureSessionRepository(FakeSessionCrypto(), InMemorySessionStore()) { 0L }
        session.start(EMAIL, tokens)
        scheduler = FakeScheduler()
        viewModel = newViewModel(Clock.fixed(now, ZoneOffset.UTC))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(clock: Clock, policies: PoliciesApi = api): WalletViewModel {
        sync = SessionPolicySync(session, auth, policies, store, scheduler, clock)
        return WalletViewModel(sync, store, clock)
    }

    // Sin red la sincronización falla y la billetera muestra la copia.
    private fun openOffline(email: String = EMAIL) {
        api.result = PoliciesResult.Unavailable
        viewModel.open(email)
    }

    @Test
    fun `TC-04 con red muestra las polizas del servidor y guarda la copia de la cuenta`() {
        api.result = PoliciesResult.Loaded(listOf(active, other))

        viewModel.open(EMAIL)

        val state = viewModel.uiState.value
        assertEquals(listOf(active, other), state.policies.map { it.policy })
        assertFalse(state.offline)
        assertFalse(state.loading)
        assertEquals("token", api.lastToken)
        assertEquals(listOf(active, other), store.copies[EMAIL])
    }

    @Test
    fun `TC-04 sin polizas vigentes lo indica y la copia queda vacia`() {
        api.result = PoliciesResult.Loaded(emptyList())

        viewModel.open(EMAIL)

        assertTrue(viewModel.uiState.value.empty)
        assertEquals(emptyList<Any>(), store.copies[EMAIL])
    }

    @Test
    fun `TC-05 sin red y con copia muestra la copia con el aviso de sin conexion`() {
        store.copies[EMAIL] = listOf(active)

        openOffline()

        val state = viewModel.uiState.value
        assertTrue(state.offline)
        assertFalse(state.needsConnection)
        assertEquals(listOf(active), state.policies.map { it.policy })
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun `TC-05 sin red y sin copia indica que necesita conexion para la primera descarga`() {
        openOffline()

        val state = viewModel.uiState.value
        assertTrue(state.needsConnection)
        assertFalse(state.empty)
    }

    @Test
    fun `TC-05 una poliza de la copia que vencio sin red aparece como vencida`() {
        val expired = samplePolicy("POL-2025-00009", "2026-10-22T12:00:00Z")
        store.copies[EMAIL] = listOf(expired, active)

        openOffline()

        assertEquals(listOf(true, false), viewModel.uiState.value.policies.map { it.expired })
    }

    @Test
    fun `TC-07 un 401 borra la copia de la cuenta y marca la sesion vencida`() {
        api.result = PoliciesResult.Unauthorized
        auth.refreshResult = { RefreshResult.Rejected }
        store.copies[EMAIL] = listOf(active)
        store.copies[OTHER_EMAIL] = listOf(other)

        viewModel.open(EMAIL)

        val state = viewModel.uiState.value
        assertEquals(SessionEndReason.EXPIRED, state.sessionEnded)
        assertTrue(state.policies.isEmpty())
        assertFalse(EMAIL in store.copies)
        assertTrue(OTHER_EMAIL in store.copies)
    }

    @Test
    fun `TC-07 cada cuenta ve solo su copia`() {
        store.copies[EMAIL] = listOf(active)
        store.copies[OTHER_EMAIL] = listOf(other)
        session.start(OTHER_EMAIL, tokens)

        openOffline(OTHER_EMAIL)

        assertEquals(listOf(other), viewModel.uiState.value.policies.map { it.policy })
    }

    // HU-MOV-06: última sincronización y aviso de desactualización (TC-01, TC-02, TC-03).

    @Test
    fun `TC-01 sin red muestra la hora de la copia sin aviso si tiene menos de 24 horas`() {
        val syncedAt = now.minus(Duration.ofHours(2))
        store.put(EMAIL, listOf(active), syncedAt)

        openOffline()

        assertEquals(syncedAt, viewModel.uiState.value.lastSync)
        assertFalse(viewModel.uiState.value.stale)
    }

    @Test
    fun `TC-01 una sincronizacion exitosa guarda y muestra la hora actual y quita el aviso`() {
        store.put(EMAIL, listOf(active), now.minus(Duration.ofHours(30)))
        api.result = PoliciesResult.Loaded(listOf(active, other))

        viewModel.open(EMAIL)

        val state = viewModel.uiState.value
        assertEquals(now, state.lastSync)
        assertFalse(state.stale)
        assertEquals(now, store.syncTimes[EMAIL])
    }

    @Test
    fun `TC-01 sin red y sin copia no hay hora`() {
        openOffline()

        assertNull(viewModel.uiState.value.lastSync)
        assertFalse(viewModel.uiState.value.stale)
    }

    @Test
    fun `TC-02 una copia de mas de 24 horas se marca desactualizada`() {
        store.put(EMAIL, listOf(active), now.minus(Duration.ofHours(25)))

        openOffline()

        assertTrue(viewModel.uiState.value.stale)
    }

    @Test
    fun `TC-02 con exactamente 24 horas todavia no hay aviso`() {
        store.put(EMAIL, listOf(active), now.minus(Duration.ofHours(24)))

        openOffline()

        assertFalse(viewModel.uiState.value.stale)
    }

    @Test
    fun `TC-02 con el servidor caido se conserva la hora de la ultima sincronizacion exitosa`() {
        val syncedAt = now.minus(Duration.ofHours(30))
        store.put(EMAIL, listOf(active), syncedAt)

        openOffline()

        val state = viewModel.uiState.value
        assertEquals(syncedAt, state.lastSync)
        assertTrue(state.stale)
        assertEquals(syncedAt, store.syncTimes[EMAIL])
    }

    @Test
    fun `TC-03 una hora guardada en el futuro por un reloj atrasado se marca desactualizada`() {
        store.put(EMAIL, listOf(active), now.plus(Duration.ofDays(2)))

        openOffline()

        assertTrue(viewModel.uiState.value.stale)
    }

    // AC-08 de HU-MOV-06: al volver a la app se recalcula con la hora actual (TC-07).

    private class MovableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneOffset = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    @Test
    fun `TC-07 al volver a la app despues de 25 horas aparece el aviso sin cambiar la fecha mostrada`() {
        val clock = MovableClock(now)
        val vm = newViewModel(clock)
        api.result = PoliciesResult.Loaded(listOf(active))
        vm.open(EMAIL)

        clock.now = now.plus(Duration.ofHours(25))
        vm.refreshClock()

        assertTrue(vm.uiState.value.stale)
        assertEquals(now, vm.uiState.value.lastSync)
    }

    @Test
    fun `TC-07 al volver a la app con el reloj atrasado aparece el aviso`() {
        val clock = MovableClock(now)
        val vm = newViewModel(clock)
        store.put(EMAIL, listOf(active), now.minus(Duration.ofHours(1)))
        api.result = PoliciesResult.Unavailable
        vm.open(EMAIL)
        assertFalse(vm.uiState.value.stale)

        clock.now = now.minus(Duration.ofDays(2))
        vm.refreshClock()

        assertTrue(vm.uiState.value.stale)
    }

    @Test
    fun `TC-07 al volver a la app una poliza que vencio se marca vencida`() {
        val clock = MovableClock(now)
        val vm = newViewModel(clock)
        store.put(EMAIL, listOf(active), now)
        api.result = PoliciesResult.Unavailable
        vm.open(EMAIL)

        clock.now = active.validUntil.plusSeconds(1)
        vm.refreshClock()

        assertEquals(listOf(true), vm.uiState.value.policies.map { it.expired })
    }

    // HU-MOV-05: copia descartada, copia no disponible y cierre de la billetera.

    @Test
    fun `sin red o con el servidor caido y la copia descartada pide conexion con su aviso`() {
        store.readAs = StoredCopy.Discarded

        openOffline()

        assertTrue(viewModel.uiState.value.needsConnection)
        assertTrue(viewModel.uiState.value.copyDiscarded)
    }

    @Test
    fun `sin red y sin copia pide conexion sin el aviso de copia descartada`() {
        openOffline()

        assertTrue(viewModel.uiState.value.needsConnection)
        assertFalse(viewModel.uiState.value.copyDiscarded)
    }

    @Test
    fun `tras una copia descartada, con red guarda las polizas y quita el aviso`() {
        store.readAs = StoredCopy.Discarded
        openOffline()

        store.readAs = null
        api.result = PoliciesResult.Loaded(listOf(active))
        viewModel.open(EMAIL)

        assertFalse(viewModel.uiState.value.copyDiscarded)
        assertEquals(listOf(active), store.copies[EMAIL])
    }

    @Test
    fun `una copia no disponible muestra su aviso y al reintentar o volver a la app muestra la copia`() {
        store.readAs = StoredCopy.Unavailable
        openOffline()

        val state = viewModel.uiState.value
        assertTrue(state.copyUnavailable)
        assertFalse(state.needsConnection)
        assertFalse(state.copyDiscarded)
        assertFalse(state.empty)

        store.readAs = null
        store.copies[EMAIL] = listOf(active)
        viewModel.retry()
        assertEquals(listOf(active), viewModel.uiState.value.policies.map { it.policy })

        store.readAs = StoredCopy.Unavailable
        viewModel.open(EMAIL)
        store.readAs = null
        viewModel.refreshClock()
        assertEquals(listOf(active), viewModel.uiState.value.policies.map { it.policy })
    }

    @Test
    fun `cerrar la billetera antes de que responda el servidor no guarda la copia`() {
        val pending = PendingPoliciesApi()
        val vm = newViewModel(Clock.fixed(now, ZoneOffset.UTC), pending)
        vm.open(EMAIL)

        vm.close()
        pending.response.complete(PoliciesResult.Loaded(listOf(active)))

        assertNull(store.copies[EMAIL])
        assertTrue(vm.uiState.value.loading)
    }

    // HU-MOV-07 TC-01: sin red pide la sincronización; cuando el trabajo sincroniza, la billetera se actualiza.

    @Test
    fun `al sincronizar en segundo plano la billetera muestra las polizas sin el aviso`() = runBlocking {
        store.put(EMAIL, listOf(active), now.minus(Duration.ofHours(30)))
        openOffline()
        assertTrue(viewModel.uiState.value.offline)

        api.result = PoliciesResult.Loaded(listOf(active, other))
        sync.sync(online = true)

        val state = viewModel.uiState.value
        assertFalse(state.offline)
        assertEquals(listOf(active, other), state.policies.map { it.policy })
        assertEquals(now, state.lastSync)
        assertFalse(state.stale)
    }

    @Test
    fun `al sincronizar en segundo plano una billetera que pedia conexion muestra las polizas`() = runBlocking {
        openOffline()
        assertTrue(viewModel.uiState.value.needsConnection)

        api.result = PoliciesResult.Loaded(listOf(active))
        sync.sync(online = true)

        assertFalse(viewModel.uiState.value.needsConnection)
        assertEquals(listOf(active), viewModel.uiState.value.policies.map { it.policy })
    }

    @Test
    fun `si la copia no se pudo guardar la billetera conserva la fecha anterior`() = runBlocking {
        val syncedAt = now.minus(Duration.ofHours(3))
        store.put(EMAIL, listOf(active), syncedAt)
        openOffline()
        store.canWrite = false

        api.result = PoliciesResult.Loaded(listOf(active, other))
        sync.sync(online = true)

        assertEquals(syncedAt, viewModel.uiState.value.lastSync)
        assertTrue(viewModel.uiState.value.offline)
    }

    @Test
    fun `una sesion terminada en segundo plano llega a la billetera con su motivo`() = runBlocking {
        store.copies[EMAIL] = listOf(active)
        openOffline()

        api.result = PoliciesResult.Unauthorized
        auth.refreshResult = { RefreshResult.Rejected }
        sync.sync(online = true)

        assertEquals(SessionEndReason.EXPIRED, viewModel.uiState.value.sessionEnded)
    }

    // HU-MOV-07 TC-13: la billetera solo aplica los resultados de la cuenta abierta.
    @Test
    fun `el resultado de otra cuenta no cambia la billetera`() = runBlocking {
        store.copies[EMAIL] = listOf(active)
        openOffline()

        session.start(OTHER_EMAIL, tokens)
        api.result = PoliciesResult.Loaded(listOf(other))
        sync.sync(online = true)

        assertTrue(viewModel.uiState.value.offline)
        assertEquals(listOf(active), viewModel.uiState.value.policies.map { it.policy })
    }

    // HU-MOV-07 TC-19: al rotar o volver a la app la copia sigue a la vista mientras otra sincronización ocupa el turno.
    @Test
    fun `al rotar o volver a la app sigue la copia mientras otra sincronizacion ocupa el turno`() = runBlocking {
        store.put(EMAIL, listOf(active), now)
        val clock = MovableClock(now)
        val gate = CompletableDeferred<PoliciesResult>()
        val policies = ScriptedPoliciesApi().apply { respond = { if (calls.size == 1) PoliciesResult.Unavailable else gate.await() } }
        val vm = newViewModel(clock, policies)
        vm.open(EMAIL)
        assertTrue(vm.uiState.value.offline)
        val worker = launch { sync.sync(online = true) }
        yield()

        vm.open(EMAIL)
        assertFalse(vm.uiState.value.loading)
        assertEquals(listOf(active), vm.uiState.value.policies.map { it.policy })
        clock.now = now.plusSeconds(60)
        vm.refreshClock()
        assertEquals(listOf(active), vm.uiState.value.policies.map { it.policy })

        gate.complete(PoliciesResult.Loaded(listOf(active, other)))
        worker.join()
        assertEquals(listOf(active, other), vm.uiState.value.policies.map { it.policy })
        assertFalse(vm.uiState.value.offline)
    }

    @Test
    fun `al volver a la app sin conexion sincroniza a lo sumo cada 30 s y con conexion no llama`() {
        val clock = MovableClock(now)
        val vm = newViewModel(clock)
        store.put(EMAIL, listOf(active), now)
        api.result = PoliciesResult.Unavailable
        vm.open(EMAIL)
        val calls = api.calls

        clock.now = now.plusSeconds(10)
        vm.refreshClock()
        assertEquals(calls, api.calls)

        clock.now = now.plusSeconds(31)
        vm.refreshClock()
        assertEquals(calls + 1, api.calls)
        assertEquals(listOf(active), vm.uiState.value.policies.map { it.policy })

        api.result = PoliciesResult.Loaded(listOf(active))
        clock.now = now.plusSeconds(70)
        vm.refreshClock()
        assertFalse(vm.uiState.value.offline)

        clock.now = now.plusSeconds(200)
        vm.refreshClock()
        assertEquals(calls + 2, api.calls)
    }

    private companion object {
        const val EMAIL = "asegurada@example.com"
        const val OTHER_EMAIL = "otra.cuenta@example.com"
    }
}
