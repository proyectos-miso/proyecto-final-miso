package com.solventa.app.data.assistance

import com.solventa.app.domain.assistance.AssistanceForm
import com.solventa.app.domain.assistance.AssistanceResult
import com.solventa.app.domain.assistance.AssistanceService
import com.solventa.app.domain.assistance.CitiesResult
import com.solventa.app.domain.assistance.City
import com.solventa.app.domain.assistance.Coordinates
import com.solventa.app.domain.assistance.ProviderQuery
import com.solventa.app.domain.assistance.ProvidersResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Prestadores, ciudades y solicitud de asistencia contra un servidor simulado (mejora FE-MOV-07).
@RunWith(RobolectricTestRunner::class)
class HttpAssistanceApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: HttpAssistanceApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HttpAssistanceApi(server.url("/api/v1/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `los prestadores por ubicacion envian las coordenadas redondeadas en el cuerpo y no en la URL`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"providers\": [$PROVIDER]}"))

        val result = api.providers("t", ProviderQuery(Coordinates(4.67612, -74.04849), "BOGOTA", AssistanceService.GRUA))

        assertEquals(ProvidersResult.Loaded(listOf(sampleProvider())), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/insured/providers/search", request.path)
        assertFalse(listOf("?", "lat", "lng", "4.676", "74.048").any { request.path!!.contains(it) })
        assertEquals("Bearer t", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(4.676, body.getDouble("lat"), 0.0)
        assertEquals(-74.048, body.getDouble("lng"), 0.0)
        assertEquals("GRUA", body.getString("service"))
        assertFalse(body.has("city"))
    }

    @Test
    fun `los prestadores por ciudad no envian coordenadas y aceptan distancia nula`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"providers\": [${PROVIDER.replace("1.2", "null")}]}"))

        val result = api.providers("t", ProviderQuery(null, "BOGOTA", null))

        assertEquals(ProvidersResult.Loaded(listOf(sampleProvider(distanceKm = null))), result)
        val request = server.takeRequest()
        assertEquals("/api/v1/insured/providers/search", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("BOGOTA", body.getString("city"))
        assertFalse(body.has("lat") || body.has("service"))
    }

    @Test
    fun `sesion vencida, error del servidor o respuesta mal formada en prestadores y ciudades`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[{\"code\": \"CALI\", \"name\": \"Cali\", \"id\": 3, \"is_active\": true}]"))
        server.enqueue(MockResponse().setResponseCode(401))

        val query = ProviderQuery(null, "CALI", null)
        assertEquals(ProvidersResult.Unauthorized, api.providers("t", query))
        assertEquals(ProvidersResult.Offline, api.providers("t", query))
        assertEquals(ProvidersResult.Offline, api.providers("t", query))
        assertEquals(CitiesResult.Loaded(listOf(City("CALI", "Cali"))), api.cities("t"))
        assertEquals(CitiesResult.Unauthorized, api.cities("t"))
    }

    @Test
    fun `una busqueda rechazada no es falta de red y la identidad en verificacion tiene su resultado`() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody("{\"detail\": []}"))
        server.enqueue(MockResponse().setResponseCode(403).setBody(IN_REVIEW))
        server.enqueue(MockResponse().setResponseCode(403).setBody(IN_REVIEW))

        val query = ProviderQuery(null, "CALI", null)
        assertEquals(ProvidersResult.Rejected, api.providers("t", query))
        assertEquals(ProvidersResult.IdentityInReview, api.providers("t", query))
        assertEquals(CitiesResult.IdentityInReview, api.cities("t"))
    }

    @Test
    fun `pedir asistencia envia la llave y el cuerpo y lee el prestador y el tiempo estimado`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            "{\"request_id\": \"REQ-1\", \"policy_number\": \"POL-2026-00001\", \"service\": \"GRUA\", \"status\": \"RECEIVED\", " +
                "\"provider\": $PROVIDER, \"eta_minutes\": 18, \"created_at\": \"2026-11-15T15:00:00Z\"}",
        ))

        val result = api.request("t", "llave-0001", FORM)

        assertEquals(AssistanceResult.Received("REQ-1", sampleProvider(), 18), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/insured/assistance", request.path)
        assertEquals("llave-0001", request.getHeader("Idempotency-Key"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("POL-2026-00001", body.getString("policy_number"))
        assertEquals("GRUA", body.getString("service"))
        assertEquals(4.676, body.getDouble("lat"), 0.0)
        assertFalse(body.has("city"))
    }

    @Test
    fun `sin ubicacion la solicitud envia la ciudad`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        api.request("t", "llave-0001", FORM.copy(coordinates = null, cityCode = "CALI"))

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("CALI", body.getString("city"))
        assertFalse(body.has("lat"))
    }

    @Test
    fun `cada codigo de error de la solicitud tiene su resultado`() = runTest {
        val expected = listOf(
            Triple(401, "", AssistanceResult.Unauthorized),
            Triple(404, "", AssistanceResult.PolicyNotAvailable),
            Triple(409, "{\"detail\": \"x\", \"code\": \"NO_PROVIDER\"}", AssistanceResult.NoProvider),
            Triple(409, "{\"detail\": \"x\", \"code\": \"IDEMPOTENCY_KEY_REUSED\"}", AssistanceResult.KeyReused),
            Triple(409, "", AssistanceResult.Rejected),
            Triple(429, "", AssistanceResult.TooMany),
            Triple(403, IN_REVIEW, AssistanceResult.IdentityInReview),
            Triple(403, "", AssistanceResult.Rejected),
            Triple(422, "", AssistanceResult.Rejected),
            Triple(500, "", AssistanceResult.Offline),
        )
        expected.forEach { (code, body, _) -> server.enqueue(MockResponse().setResponseCode(code).setBody(body)) }
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        expected.forEach { (_, _, result) -> assertEquals(result, api.request("t", "llave-0001", FORM)) }
        assertEquals(AssistanceResult.Offline, api.request("t", "llave-0001", FORM))
    }

    @Test
    fun `sin servidor la solicitud queda sin conexion`() = runTest {
        server.shutdown()

        assertEquals(AssistanceResult.Offline, api.request("t", "llave-0001", FORM))
    }

    private companion object {
        const val IN_REVIEW = "{\"detail\": \"Estamos verificando su identidad.\", \"code\": \"IDENTITY_IN_REVIEW\"}"
        val FORM = AssistanceForm("POL-2026-00001", AssistanceService.GRUA, Coordinates(4.676, -74.048), null, "El carro no enciende.")
        const val PROVIDER = "{\"provider_id\": \"PRV-BOG-01\", \"name\": \"Grúas de Prueba\", \"kind\": \"GRUA\", " +
            "\"services\": [\"GRUA\", \"ASISTENCIA_VIAL\"], \"city_code\": \"BOGOTA\", \"address\": \"Calle 100 # 15-20\", " +
            "\"phone\": \"+576015550101\", \"lat\": 4.6867, \"lng\": -74.048, \"distance_km\": 1.2}"
    }
}
