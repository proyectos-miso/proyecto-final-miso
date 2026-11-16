package com.solventa.app.ui.assistance

import com.solventa.app.data.assistance.FakeAssistance
import com.solventa.app.data.assistance.FakeLocator
import com.solventa.app.data.assistance.InMemoryProvidersCopy
import com.solventa.app.data.assistance.RecordingContact
import com.solventa.app.data.assistance.sampleProvider
import com.solventa.app.domain.assistance.AssistanceResult
import com.solventa.app.domain.assistance.AssistanceService
import com.solventa.app.domain.assistance.CitiesResult
import com.solventa.app.domain.assistance.Coordinates
import com.solventa.app.domain.assistance.ProviderQuery
import com.solventa.app.domain.assistance.ProvidersCopy
import com.solventa.app.domain.assistance.ProvidersResult
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

// Prestadores cercanos, copia sin conexión y «Pedir asistencia» con su llave de idempotencia (mejora FE-MOV-07).
@OptIn(ExperimentalCoroutinesApi::class)
class AssistanceViewModelTest {

    private val assistance = FakeAssistance()
    private val copies = InMemoryProvidersCopy()
    private val locator = FakeLocator()
    private val contact = RecordingContact()
    private var keys = 0
    private lateinit var viewModel: AssistanceViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = AssistanceViewModel(assistance, copies, locator, contact, newKey = { "llave-${++keys}" }, clock = Clock.fixed(NOW, ZoneOffset.UTC))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    @Test
    fun `con permiso busca por la ubicacion aproximada redondeada y guarda la lista en la copia`() {
        viewModel.open(EMAIL, POLICIES)

        assertTrue(state.nearMe)
        assertEquals(listOf(sampleProvider()), state.providers)
        assertEquals(ProviderQuery(Coordinates(4.676, -74.048), null, null), assistance.queries.single())
        assertEquals(4.676, assistance.queries.single().coordinates!!.lat, 0.0)
        assertEquals(ProvidersCopy(listOf(sampleProvider()), null, NOW), copies.copies[EMAIL])
        assertEquals(0, assistance.cityCalls)
    }

    @Test
    fun `sin permiso no pide la ubicacion, carga las ciudades y busca por la elegida`() {
        locator.permission = false

        viewModel.open(EMAIL, POLICIES)

        assertEquals(0, locator.calls)
        assertFalse(state.nearMe)
        assertEquals(listOf("BOGOTA", "CALI"), state.cities.map { it.code })
        assertTrue(assistance.queries.isEmpty())

        viewModel.selectCity("CALI")

        assertEquals(ProviderQuery(null, "CALI", null), assistance.queries.single())
        assertEquals("CALI", state.cityCode)
    }

    @Test
    fun `negar el permiso deja elegir la ciudad y darlo busca alrededor`() {
        locator.permission = false
        viewModel.open(EMAIL, POLICIES)

        viewModel.onLocationPermission(false)
        assertTrue(assistance.queries.isEmpty())

        locator.permission = true
        viewModel.onLocationPermission(true)
        assertTrue(state.nearMe)
        assertEquals(Coordinates(4.676, -74.048), assistance.queries.single().coordinates)
    }

    @Test
    fun `sin ubicacion a tiempo avisa y ofrece las ciudades`() {
        locator.location = null

        viewModel.open(EMAIL, POLICIES)

        assertEquals(AssistanceNotice.LOCATION_UNAVAILABLE, state.notice)
        assertFalse(state.nearMe)
        assertEquals(2, state.cities.size)
    }

    @Test
    fun `el filtro por servicio vuelve a consultar con el servicio`() {
        viewModel.open(EMAIL, POLICIES)

        viewModel.selectService(AssistanceService.CERRAJERIA)
        viewModel.selectService(null)

        assertEquals(listOf(null, AssistanceService.CERRAJERIA, null), assistance.queries.map { it.service })
    }

    @Test
    fun `sin red muestra la ultima lista guardada con su fecha y filtra en el telefono`() {
        val lock = sampleProvider(id = "PRV-BOG-04", services = listOf("CERRAJERIA"))
        copies.copies[EMAIL] = ProvidersCopy(listOf(sampleProvider(), lock), null, EARLIER)
        assistance.providers = ProvidersResult.Offline

        viewModel.open(EMAIL, POLICIES)
        assertEquals(AssistanceNotice.OFFLINE, state.notice)
        assertEquals(EARLIER, state.copySavedAt)
        assertEquals(2, state.providers.size)

        viewModel.selectService(AssistanceService.CERRAJERIA)
        assertEquals(listOf(lock), state.providers)
    }

    @Test
    fun `sin red y sin copia avisa sin lista, y al volver la red se reemplaza el aviso`() {
        assistance.providers = ProvidersResult.Offline
        viewModel.open(EMAIL, POLICIES)
        assertEquals(AssistanceNotice.OFFLINE, state.notice)
        assertNull(state.copySavedAt)
        assertTrue(state.providers.isEmpty())

        assistance.providers = ProvidersResult.Loaded(listOf(sampleProvider()))
        viewModel.retry()

        assertNull(state.notice)
        assertEquals(1, state.providers.size)
    }

    @Test
    fun `la sesion vencida se avisa al buscar prestadores o ciudades`() {
        assistance.providers = ProvidersResult.Unauthorized
        viewModel.open(EMAIL, POLICIES)
        assertEquals(AssistanceNotice.SESSION, state.notice)

        viewModel.close()
        locator.permission = false
        assistance.cities = CitiesResult.Unauthorized
        viewModel.open(EMAIL, POLICIES)
        assertEquals(AssistanceNotice.SESSION, state.notice)
    }

    @Test
    fun `Llamar y Ver en el mapa usan el doble de intents y avisan si no hay app`() {
        viewModel.open(EMAIL, POLICIES)
        val provider = state.providers.single()

        viewModel.call(provider.phone)
        viewModel.showOnMap(provider)
        assertEquals(listOf("+576015550101"), contact.calls)
        assertEquals(listOf(provider), contact.maps)
        assertNull(state.notice)

        contact.opens = false
        viewModel.call(provider.phone)
        assertEquals(AssistanceNotice.CANNOT_OPEN, state.notice)
    }

    @Test
    fun `pedir asistencia envia la poliza, el servicio y la ubicacion y muestra el prestador y el tiempo`() {
        viewModel.open(EMAIL, POLICIES)
        viewModel.selectService(AssistanceService.GRUA)
        viewModel.openForm()
        viewModel.describe("  El carro no enciende.  ")

        viewModel.submit()

        val (key, form) = assistance.requests.single()
        assertEquals("llave-1", key)
        assertEquals("POL-2026-00001", form.policyNumber)
        assertEquals(AssistanceService.GRUA, form.service)
        assertEquals(Coordinates(4.676, -74.048), form.coordinates)
        assertNull(form.cityCode)
        assertEquals("El carro no enciende.", form.description)
        assertEquals(AssistanceResult.Assigned("REQ-1", sampleProvider(), 18), state.form!!.assigned)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `sin ubicacion pide asistencia con la ciudad elegida`() {
        locator.permission = false
        viewModel.open(EMAIL, POLICIES)
        viewModel.selectCity("BOGOTA")
        viewModel.openForm()

        viewModel.submit()

        val form = assistance.requests.single().second
        assertNull(form.coordinates)
        assertEquals("BOGOTA", form.cityCode)
    }

    @Test
    fun `sin ubicacion ni ciudad el formulario no se puede enviar`() {
        locator.permission = false
        viewModel.open(EMAIL, POLICIES)
        viewModel.openForm()

        viewModel.submit()

        assertFalse(state.canSubmit)
        assertTrue(assistance.requests.isEmpty())
    }

    @Test
    fun `reintentar sin cambios conserva la llave y cambiar el formulario la renueva`() {
        viewModel.open(EMAIL, POLICIES)
        viewModel.openForm()
        assistance.result = AssistanceResult.Offline

        viewModel.submit()
        assertEquals(RequestNotice.OFFLINE, state.form!!.notice)
        viewModel.submit()
        viewModel.selectPolicy("POL-2026-00002")
        viewModel.submit()
        viewModel.closeForm()
        viewModel.openForm()
        viewModel.submit()

        assertEquals(listOf("llave-1", "llave-1", "llave-2", "llave-3"), assistance.requests.map { it.first })
    }

    @Test
    fun `cada respuesta del servidor muestra su aviso`() {
        viewModel.open(EMAIL, POLICIES)
        val expected = mapOf(
            AssistanceResult.PolicyNotAvailable to RequestNotice.POLICY,
            AssistanceResult.NoProvider to RequestNotice.NO_PROVIDER,
            AssistanceResult.TooMany to RequestNotice.TOO_MANY,
            AssistanceResult.Unauthorized to RequestNotice.SESSION,
            AssistanceResult.Rejected to RequestNotice.REJECTED,
            AssistanceResult.Offline to RequestNotice.OFFLINE,
        )
        expected.forEach { (result, notice) ->
            viewModel.openForm()
            assistance.result = result
            viewModel.submit()
            assertEquals(notice, state.form!!.notice)
            assertFalse(state.form!!.sending)
            viewModel.closeForm()
        }
    }

    @Test
    fun `la descripcion se corta en 300 caracteres y sin polizas vigentes no se envia`() {
        viewModel.open(EMAIL, emptyList())
        viewModel.openForm()
        viewModel.describe("x".repeat(400))

        viewModel.submit()

        assertEquals(300, state.form!!.description.length)
        assertNull(state.form!!.policyNumber)
        assertTrue(assistance.requests.isEmpty())
    }

    @Test
    fun `cerrar borra la busqueda y la ubicacion de memoria`() {
        viewModel.open(EMAIL, POLICIES)

        viewModel.close()

        assertEquals(AssistanceUiState(), state)
        viewModel.open(EMAIL, POLICIES)
        assertEquals(2, locator.calls)
    }

    private companion object {
        const val EMAIL = "maria@example.com"
        val POLICIES = listOf("POL-2026-00001", "POL-2026-00002")
        val NOW: Instant = Instant.parse("2026-11-15T15:00:00Z")
        val EARLIER: Instant = Instant.parse("2026-11-14T10:00:00Z")
    }
}
