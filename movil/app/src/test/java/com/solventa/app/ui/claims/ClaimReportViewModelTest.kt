package com.solventa.app.ui.claims

import android.graphics.Bitmap
import com.solventa.app.data.claims.FakeCaptureLocator
import com.solventa.app.data.claims.InMemoryClaimStore
import com.solventa.app.data.policies.samplePolicy
import com.solventa.app.domain.claims.CaptureFix
import com.solventa.app.domain.claims.LocationAccess
import com.solventa.app.domain.claims.LocationResult
import com.solventa.app.domain.claims.Missing
import com.solventa.app.domain.claims.ReportState
import com.solventa.app.domain.claims.StoredReport
import com.solventa.app.domain.claims.TimeSource
import com.solventa.app.domain.claims.TrustedTime
import com.solventa.app.domain.claims.sha256Hex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

/** Reporte con fotos (HU-MOV-08: TC-01, TC-03, TC-04, TC-09, TC-11, TC-12, TC-15, TC-22, TC-24). */
@OptIn(ExperimentalCoroutinesApi::class)
class ClaimReportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = InMemoryClaimStore()
    private val locator = FakeCaptureLocator()
    private var trusted: TrustedTime? = TrustedTime(1_730_000_000_000L, TimeSource.GNSS)
    private val now = Instant.parse("2026-11-05T15:00:00Z")
    private lateinit var vm: ClaimReportViewModel

    private val active = samplePolicy(number = "POL-2026-00001")
    private val other = samplePolicy(number = "POL-2026-00002")
    private val expired = samplePolicy(number = "POL-2025-00009").copy(validUntil = now.minusSeconds(60))
    private val jpeg = ByteArray(1_024) { it.toByte() }
    private val capture: suspend () -> ByteArray? = { jpeg }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = ClaimReportViewModel(store, locator, { trusted }, Clock.fixed(now, ZoneOffset.UTC), thumbnail = { null as Bitmap? }, background = dispatcher)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun open(policies: List<com.solventa.app.domain.policies.PolicyCard> = listOf(active, other, expired)) {
        vm.open(EMAIL, policies)
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun shoot() {
        vm.openCamera()
        vm.shoot(capture)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `TC-12 abre un borrador con solo las pólizas vigentes de la copia`() = runTest(dispatcher) {
        open()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(ReportState.DRAFT, state.report?.state)
        assertEquals(listOf("POL-2026-00001", "POL-2026-00002"), state.policies.map { it.policyNumber })
        assertEquals(ClaimScreen.EVIDENCE, state.screen)
    }

    @Test
    fun `TC-12 la billetera ofrece reportar, retomar o ver el listo, y nada sin pólizas ni reporte`() = runTest(dispatcher) {
        vm.refreshEntry(EMAIL, hasActivePolicy = false); advanceUntilIdle()
        assertEquals(ReportEntry.HIDDEN, vm.entry.value)
        vm.refreshEntry(EMAIL, hasActivePolicy = true); advanceUntilIdle()
        assertEquals(ReportEntry.NEW, vm.entry.value)

        open()
        vm.refreshEntry(EMAIL, hasActivePolicy = false); advanceUntilIdle()
        assertEquals("un reporte guardado se retoma aunque no haya pólizas vigentes", ReportEntry.RESUME, vm.entry.value)

        store.readAs = StoredReport.CannotOpen
        vm.refreshEntry(EMAIL, hasActivePolicy = false); advanceUntilIdle()
        assertEquals(ReportEntry.RESUME, vm.entry.value)
        store.readAs = null

        store.markReady(EMAIL)
        vm.refreshEntry(EMAIL, hasActivePolicy = true); advanceUntilIdle()
        assertEquals(ReportEntry.READY, vm.entry.value)
    }

    @Test
    fun `TC-01 la foto se guarda con la ubicación, la hora del teléfono y la independiente`() = runTest(dispatcher) {
        open()
        val fix = CaptureFix(4.6, -74.08, 6f, mock = false)
        locator.results += LocationResult.Fix(fix)

        shoot()

        val photo = store.reports[EMAIL]!!.photos.single()
        assertEquals(fix, photo.fix)
        assertEquals(now, photo.capturedAt)
        assertEquals(trusted, photo.trustedTime)
        assertEquals(sha256Hex(jpeg), photo.sha256)
        assertEquals(ClaimScreen.EVIDENCE, vm.uiState.value.screen)
        assertEquals(1, vm.uiState.value.photoCount)
    }

    @Test
    fun `TC-01 sin hora independiente la foto se guarda igual`() = runTest(dispatcher) {
        trusted = null
        open()

        shoot()

        assertNull(store.reports[EMAIL]!!.photos.single().trustedTime)
    }

    @Test
    fun `TC-04 sin ubicación no hay foto y se dice cómo resolverlo`() = runTest(dispatcher) {
        open()
        val cases = listOf(
            LocationResult.Timeout to CaptureNotice.LOCATION_TIMEOUT,
            LocationResult.NoPermission(LocationAccess.APPROXIMATE_ONLY) to CaptureNotice.LOCATION_APPROXIMATE,
            LocationResult.NoPermission(LocationAccess.DENIED) to CaptureNotice.LOCATION_DENIED,
        )
        for ((result, notice) in cases) {
            locator.results += result
            shoot()
            assertEquals(notice, vm.uiState.value.notice)
            vm.dismissNotice()
        }
        locator.enabled = false
        shoot()
        assertEquals(CaptureNotice.LOCATION_OFF, vm.uiState.value.notice)

        assertTrue(store.reports[EMAIL]!!.photos.isEmpty())
    }

    @Test
    fun `TC-04 mientras busca la ubicación cuenta los segundos y no deja disparar otra vez`() {
        // Sin `runTest`: si un segundo disparo dejara un contador sin cancelar, la prueba falla en vez de colgarse.
        val scheduler = dispatcher.scheduler
        open()
        val gate = CompletableDeferred<LocationResult>()
        locator.gate = gate
        vm.openCamera()

        vm.shoot(capture)
        scheduler.advanceTimeBy(3_500)
        scheduler.runCurrent()
        assertTrue(vm.uiState.value.seeking)
        assertEquals(3, vm.uiState.value.seekSeconds)
        vm.shoot(capture)
        assertEquals("el segundo disparo no reinicia la búsqueda", 3, vm.uiState.value.seekSeconds)

        gate.complete(LocationResult.Fix(FakeCaptureLocator.FIX))
        scheduler.advanceTimeBy(100)
        scheduler.runCurrent()
        assertFalse(vm.uiState.value.seeking)
        assertEquals(1, store.reports[EMAIL]!!.photos.size)
        scheduler.advanceTimeBy(5_000)
        scheduler.runCurrent()
        assertEquals("el contador se detuvo", 3, vm.uiState.value.seekSeconds)
    }

    // TC-17: la pantalla anuncia la foto recién guardada y la recién borrada; al salir de la evidencia se olvidan.
    @Test
    fun `TC-17 recuerda la foto guardada y la borrada para anunciarlas`() = runTest(dispatcher) {
        open()
        shoot()
        assertEquals(1, vm.uiState.value.savedPhoto)
        shoot()
        assertEquals(2, vm.uiState.value.savedPhoto)

        vm.continueToDetail()
        assertNull(vm.uiState.value.savedPhoto)
        vm.back()
        vm.continueToDetail()
        vm.back()
        val first = store.reports[EMAIL]!!.photos.first()
        vm.deletePhoto(first.id); advanceUntilIdle()

        assertEquals(DeletedPhoto(position = 1, remaining = 1), vm.uiState.value.deletedPhoto)
        vm.openCamera()
        assertNull(vm.uiState.value.deletedPhoto)
    }

    @Test
    fun `borrar la última foto anuncia el borrado y no lo que falta`() = runTest(dispatcher) {
        open()
        vm.continueToDetail()
        assertTrue(vm.uiState.value.showMissing)
        shoot()
        vm.deletePhoto(store.reports[EMAIL]!!.photos.single().id); advanceUntilIdle()

        assertFalse(vm.uiState.value.showMissing)
        assertEquals(DeletedPhoto(1, 0), vm.uiState.value.deletedPhoto)
    }

    @Test
    fun `si la cámara falla no hay foto y se avisa`() = runTest(dispatcher) {
        open()
        vm.openCamera()

        vm.shoot { null }
        advanceUntilIdle()

        assertEquals(CaptureNotice.CAPTURE_FAILED, vm.uiState.value.notice)
        assertTrue(store.reports[EMAIL]!!.photos.isEmpty())
    }

    @Test
    fun `TC-09 con 5 fotos ya no abre la cámara ni guarda otra`() = runTest(dispatcher) {
        open()
        repeat(5) { shoot() }

        vm.openCamera()
        assertEquals(ClaimScreen.EVIDENCE, vm.uiState.value.screen)
        vm.shoot(capture); advanceUntilIdle()
        assertEquals(5, store.reports[EMAIL]!!.photos.size)
        assertFalse(vm.uiState.value.canAddPhoto)
    }

    @Test
    fun `TC-09 sin fotos no pasa al detalle y dice que falta la foto`() = runTest(dispatcher) {
        open()

        vm.continueToDetail()

        assertEquals(ClaimScreen.EVIDENCE, vm.uiState.value.screen)
        assertTrue(vm.uiState.value.showMissing)
        assertTrue(Missing.PHOTO in vm.uiState.value.missing)
    }

    @Test
    fun `TC-09 la descripción se recorta en 500 y se guarda medio segundo después`() = runTest(dispatcher) {
        open()

        vm.updateDescription("a".repeat(510))
        advanceTimeBy(400)
        runCurrent()
        assertEquals("", store.reports[EMAIL]!!.description)
        advanceTimeBy(200)
        runCurrent()

        assertEquals(500, vm.uiState.value.description.length)
        assertEquals(500, store.reports[EMAIL]!!.description.length)
    }

    @Test
    fun `TC-11 enviar con todo deja el reporte listo y muestra el guardado`() = runTest(dispatcher) {
        open()
        shoot()
        vm.continueToDetail()
        vm.selectPolicy("POL-2026-00001")
        vm.updateDescription("Golpe en la puerta")

        vm.send(); advanceUntilIdle()

        assertEquals(ReportState.READY, store.reports[EMAIL]!!.state)
        assertEquals("Golpe en la puerta", store.reports[EMAIL]!!.description)
        assertEquals(ClaimScreen.SAVED, vm.uiState.value.screen)
        assertTrue(vm.uiState.value.justSaved)
    }

    @Test
    fun `TC-09 enviar sin descripción o sin póliza dice qué falta y no lo deja listo`() = runTest(dispatcher) {
        open()
        shoot()
        vm.continueToDetail()

        vm.updateDescription("   ")
        vm.send(); advanceUntilIdle()

        assertTrue(vm.uiState.value.showMissing)
        assertEquals(listOf(Missing.POLICY, Missing.DESCRIPTION), vm.uiState.value.missing)
        assertEquals(ReportState.DRAFT, store.reports[EMAIL]!!.state)
    }

    @Test
    fun `TC-24 una póliza que dejó de estar vigente se pide de nuevo al enviar`() = runTest(dispatcher) {
        open()
        shoot()
        vm.selectPolicy("POL-2026-00002")
        vm.updateDescription("Golpe")
        vm.open(EMAIL, listOf(active))
        advanceUntilIdle()

        vm.send(); advanceUntilIdle()

        assertEquals(listOf(Missing.POLICY_NOT_ACTIVE), vm.uiState.value.missing)
        assertEquals(ReportState.DRAFT, store.reports[EMAIL]!!.state)
    }

    @Test
    fun `TC-11 al volver a abrir encuentra las fotos, la póliza y la descripción`() = runTest(dispatcher) {
        open()
        shoot()
        vm.selectPolicy("POL-2026-00001")
        vm.updateDescription("Golpe en la puerta")
        vm.leave(); advanceUntilIdle()
        assertTrue(vm.uiState.value.closed)

        val again = ClaimReportViewModel(store, locator, { trusted }, Clock.fixed(now, ZoneOffset.UTC), thumbnail = { null }, background = dispatcher)
        again.open(EMAIL, listOf(active, other))
        advanceUntilIdle()

        val state = again.uiState.value
        assertEquals(1, state.photoCount)
        assertEquals("POL-2026-00001", state.report?.policyNumber)
        assertEquals("Golpe en la puerta", state.description)
    }

    @Test
    fun `TC-24 un reporte listo se abre en el guardado y se puede descartar`() = runTest(dispatcher) {
        open()
        shoot()
        store.markReady(EMAIL)
        vm.leave(); advanceUntilIdle()

        open()
        assertEquals(ClaimScreen.SAVED, vm.uiState.value.screen)
        assertFalse(vm.uiState.value.justSaved)
        vm.openCamera()
        assertEquals(ClaimScreen.SAVED, vm.uiState.value.screen)

        vm.discard(); advanceUntilIdle()
        assertEquals(listOf(EMAIL), store.deleted)
        assertTrue(vm.uiState.value.closed)
    }

    @Test
    fun `TC-15 no disponible o sin poder abrir se avisa y nada se borra solo`() = runTest(dispatcher) {
        store.readAs = StoredReport.Unavailable
        open()
        assertTrue(vm.uiState.value.unavailable)

        vm.leave(); advanceUntilIdle()
        store.readAs = StoredReport.CannotOpen
        open()
        assertTrue(vm.uiState.value.cannotOpen)
        assertTrue(store.deleted.isEmpty())
    }

    @Test
    fun `TC-03 los permisos que faltan se explican y con todos se abre la cámara`() = runTest(dispatcher) {
        open()
        val cases = listOf(
            Triple(false, true, LocationAccess.PRECISE) to CaptureNotice.NO_CAMERA,
            Triple(true, false, LocationAccess.PRECISE) to CaptureNotice.CAMERA_DENIED,
            Triple(true, true, LocationAccess.APPROXIMATE_ONLY) to CaptureNotice.LOCATION_APPROXIMATE,
            Triple(true, true, LocationAccess.DENIED) to CaptureNotice.LOCATION_DENIED,
        )
        for ((permissions, notice) in cases) {
            vm.onPermissions(permissions.first, permissions.second, permissions.third)
            assertEquals(notice, vm.uiState.value.notice)
            assertEquals(ClaimScreen.EVIDENCE, vm.uiState.value.screen)
        }
        vm.onPermissions(hasCamera = true, cameraGranted = true, access = LocationAccess.PRECISE)
        assertNull(vm.uiState.value.notice)
        assertEquals(ClaimScreen.CAMERA, vm.uiState.value.screen)
    }

    @Test
    fun `TC-22 la ubicación se escucha solo con la cámara a la vista`() = runTest(dispatcher) {
        open()
        vm.openCamera()

        vm.cameraVisible(true)
        assertTrue(locator.started)
        vm.cameraVisible(false)
        assertFalse(locator.started)
        vm.cameraVisible(true)
        vm.back()
        vm.leave(); advanceUntilIdle()
        assertFalse("al salir del reporte se detiene", locator.started)
    }

    @Test
    fun `atrás va de la cámara y del detalle a la evidencia, y de la evidencia a la billetera`() = runTest(dispatcher) {
        open()
        shoot()
        vm.openCamera()
        vm.back()
        assertEquals(ClaimScreen.EVIDENCE, vm.uiState.value.screen)
        vm.continueToDetail()
        vm.back()
        assertEquals(ClaimScreen.EVIDENCE, vm.uiState.value.screen)
        vm.back(); advanceUntilIdle()
        assertTrue(vm.uiState.value.closed)
    }

    @Test
    fun `al cerrar sesión no queda nada del reporte en memoria`() = runTest(dispatcher) {
        open()
        shoot()
        vm.updateDescription("Golpe")

        vm.close()

        assertNull(vm.uiState.value.report)
        assertEquals("", vm.uiState.value.description)
        assertEquals(ReportEntry.HIDDEN, vm.entry.value)
        assertFalse(locator.started)
    }

    private companion object {
        const val EMAIL = "asegurado@correo.test"
    }
}
