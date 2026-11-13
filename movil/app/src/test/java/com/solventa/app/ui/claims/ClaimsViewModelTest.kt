package com.solventa.app.ui.claims

import com.solventa.app.data.notifications.SolventaMessagingService
import com.solventa.app.data.policies.FakePolicyActions
import com.solventa.app.data.policies.FakePolicySync
import com.solventa.app.data.policies.InMemoryPolicyStore
import com.solventa.app.data.policies.sampleClaim
import com.solventa.app.data.policies.samplePolicy
import com.solventa.app.domain.claims.ClaimsCopy
import com.solventa.app.domain.policies.CaratulaResult
import com.solventa.app.domain.policies.RenewalResult
import com.solventa.app.domain.policies.SyncOutcome
import com.solventa.app.domain.policies.WalletCopy
import com.solventa.app.ui.wallet.CaratulaDownload
import com.solventa.app.ui.wallet.WalletNotice
import com.solventa.app.ui.wallet.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.time.Instant
import java.time.ZoneOffset

// Mis siniestros y las acciones de la tarjeta (HU-MOV-11: AC-01, AC-04, AC-06, AC-09, AC-10).
@OptIn(ExperimentalCoroutinesApi::class)
class ClaimsViewModelTest {

    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private val updatedAt = Instant.parse("2026-11-08T09:00:00Z")
    private val sync = FakePolicySync()
    private val store = InMemoryPolicyStore()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `AC-01 con conexion muestra los siniestros sincronizados`() {
        val claims = ClaimsCopy(listOf(sampleClaim("SIN-2"), sampleClaim("SIN-1")), updatedAt)
        sync.next = { SyncOutcome.Synced(EMAIL, WalletCopy(emptyList(), now, claims)) }
        val viewModel = ClaimsViewModel(sync, store)

        viewModel.open(EMAIL)

        val state = viewModel.uiState.value
        assertFalse(state.offline)
        assertEquals(listOf("SIN-2", "SIN-1"), state.claims.map { it.claimNumber })
        assertEquals("SIN-1", state.claim("SIN-1")?.claimNumber)
    }

    @Test
    fun `AC-04 sin conexion muestra la copia con la fecha de la ultima actualizacion`() {
        store.claims[EMAIL] = ClaimsCopy(listOf(sampleClaim()), updatedAt)
        sync.next = { SyncOutcome.Failed }
        val viewModel = ClaimsViewModel(sync, store)

        viewModel.open(EMAIL)

        val state = viewModel.uiState.value
        assertTrue(state.offline)
        assertEquals(updatedAt, state.lastUpdate)
        assertEquals(1, state.claims.size)
        assertEquals(1, sync.schedules)
    }

    @Test
    fun `sin conexion ni copia pide conexion`() {
        sync.next = { SyncOutcome.Failed }
        val viewModel = ClaimsViewModel(sync, store)

        viewModel.open(EMAIL)

        assertTrue(viewModel.uiState.value.needsConnection)
        assertFalse(viewModel.uiState.value.empty)
    }

    @Test
    fun `AC-06 sin siniestros queda vacio`() {
        sync.next = { SyncOutcome.Synced(EMAIL, WalletCopy(emptyList(), now, ClaimsCopy(emptyList(), updatedAt))) }
        val viewModel = ClaimsViewModel(sync, store)

        viewModel.open(EMAIL)

        assertTrue(viewModel.uiState.value.empty)
        viewModel.close()
        assertTrue(viewModel.uiState.value.loading)
    }

    @Test
    fun `AC-09 la tarjeta marca el vencimiento y la renovacion vuelve a sincronizar`() {
        val soon = samplePolicy("POL-1", "2026-11-30T00:00:00Z")
        sync.next = { SyncOutcome.Synced(EMAIL, WalletCopy(listOf(soon), now)) }
        val actions = FakePolicyActions().apply { renewal = RenewalResult.Renewed(Instant.parse("2027-11-30T00:00:00Z")) }
        val wallet = WalletViewModel(sync, store, Clock.fixed(now, ZoneOffset.UTC), actions)
        wallet.open(EMAIL)
        assertTrue(wallet.uiState.value.policies.single().expiresSoon)

        sync.next = { SyncOutcome.Synced(EMAIL, WalletCopy(listOf(samplePolicy("POL-1", "2027-11-30T00:00:00Z")), now)) }
        wallet.renew("POL-1")

        assertFalse(wallet.uiState.value.policies.single().expiresSoon)
        assertEquals(listOf("POL-1"), actions.renewed)
        assertEquals(WalletNotice.RENEWED, wallet.uiState.value.notice)
        assertEquals(2, sync.onlineCalls.size)
        actions.renewal = RenewalResult.NotRenewable("Faltan más de 30 días")
        wallet.renew("POL-1")
        assertEquals(WalletNotice.NOT_RENEWABLE, wallet.uiState.value.notice)
    }

    @Test
    fun `AC-10 la caratula entrega el enlace una vez o un mensaje claro`() {
        sync.next = { SyncOutcome.Synced(EMAIL, WalletCopy(listOf(samplePolicy()), now)) }
        val actions = FakePolicyActions().apply { caratula = CaratulaResult.Ready("https://s3.example/c.pdf") }
        val wallet = WalletViewModel(sync, store, Clock.fixed(now, ZoneOffset.UTC), actions)
        wallet.open(EMAIL)
        assertFalse(wallet.uiState.value.policies.single().expiresSoon)

        wallet.downloadCaratula("POL-2026-00001")
        assertEquals(CaratulaDownload("POL-2026-00001", "https://s3.example/c.pdf"), wallet.uiState.value.caratula)
        wallet.consumeCaratula()
        assertNull(wallet.uiState.value.caratula)

        val expected = mapOf(
            CaratulaResult.NotFound to WalletNotice.CARATULA_NOT_FOUND,
            CaratulaResult.Cancelled to WalletNotice.CARATULA_CANCELLED,
            CaratulaResult.Generating to WalletNotice.CARATULA_GENERATING,
            CaratulaResult.Unavailable to WalletNotice.OFFLINE,
        )
        expected.forEach { (result, notice) ->
            actions.caratula = result
            wallet.downloadCaratula("POL-2026-00001")
            assertEquals(notice, wallet.uiState.value.notice)
        }
        wallet.dismissNotice()
        assertNull(wallet.uiState.value.notice)
    }

    @Test
    fun `los avisos push solo aceptan los tipos conocidos`() {
        val claim = SolventaMessagingService.pushNotice(mapOf("type" to "CLAIM_STATUS", "claim_number" to "SIN-1", "title" to "t", "body" to "b"))
        assertEquals("SIN-1", claim?.claimNumber)
        assertNull(SolventaMessagingService.pushNotice(mapOf("type" to "OTRO", "title" to "t", "body" to "b")))
        assertNull(SolventaMessagingService.pushNotice(mapOf("type" to "RENEWAL_REMINDER", "title" to "t", "body" to "b")))
    }

    private companion object {
        const val EMAIL = "asegurada@example.com"
    }
}
