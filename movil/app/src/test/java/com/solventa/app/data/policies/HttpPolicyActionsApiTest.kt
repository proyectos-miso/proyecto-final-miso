package com.solventa.app.data.policies

import com.solventa.app.domain.policies.CaratulaResult
import com.solventa.app.domain.policies.RenewalResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Respuestas de error de la carátula, la renovación y el token de avisos (HU-MOV-11: AC-09, AC-10).
@RunWith(RobolectricTestRunner::class)
class HttpPolicyActionsApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: HttpPolicyActionsApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HttpPolicyActionsApi(server.url("/api/v1/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `la caratula con sesion vencida, error del servidor o respuesta mal formada`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        assertEquals(CaratulaResult.Unauthorized, api.caratula("t", "POL 2026/1"))
        assertEquals("/api/v1/insured/policies/POL+2026%2F1/caratula", server.takeRequest().path)
        assertEquals(CaratulaResult.Unavailable, api.caratula("t", "POL-1"))
        assertEquals(CaratulaResult.Unavailable, api.caratula("t", "POL-1"))
    }

    @Test
    fun `la renovacion con sesion vencida, error, fecha invalida o rechazo sin detalle`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"valid_until\": \"mañana\"}"))
        server.enqueue(MockResponse().setResponseCode(409).setBody("no es json"))

        assertEquals(RenewalResult.Unauthorized, api.renew("t", "POL-1"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("application/json", request.getHeader("Content-Type"))
        assertEquals("Bearer t", request.getHeader("Authorization"))
        assertEquals(RenewalResult.Unavailable, api.renew("t", "POL-1"))
        assertEquals(RenewalResult.Unavailable, api.renew("t", "POL-1"))
        assertEquals(RenewalResult.NotRenewable(null), api.renew("t", "POL-1"))
    }

    @Test
    fun `el token de avisos acepta 200 y cualquier otro codigo es fallo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(true, api.registerPushToken("t", "fcm-token"))
        assertEquals(false, api.registerPushToken("t", "fcm-token"))
    }

    @Test
    fun `sin red cada accion responde su fallo`() = runTest {
        server.shutdown()

        assertEquals(CaratulaResult.Unavailable, api.caratula("t", "POL-1"))
        assertEquals(RenewalResult.Unavailable, api.renew("t", "POL-1"))
        assertEquals(false, api.registerPushToken("t", "fcm-token"))
    }
}
