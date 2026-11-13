package com.solventa.app.data.claims

import com.solventa.app.data.policies.HttpPolicyActionsApi
import com.solventa.app.domain.claims.ClaimsResult
import com.solventa.app.domain.policies.CaratulaResult
import com.solventa.app.domain.policies.RenewalResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

// Contrato de los siniestros, la carátula y la renovación con el BFF del asegurado (HU-MOV-11).
@RunWith(RobolectricTestRunner::class)
class HttpClaimsApiTest {

    private lateinit var server: MockWebServer
    private lateinit var claims: HttpClaimsApi
    private lateinit var actions: HttpPolicyActionsApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        claims = HttpClaimsApi(server.url("/api/v1/").toString())
        actions = HttpPolicyActionsApi(server.url("/api/v1/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `lista los siniestros del mas reciente al mas antiguo con su historial`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"updated_at": "2026-11-09T12:00:00Z", "claims": [
                  {"claim_number": "SIN-2026-00001", "policy_number": "POL-2026-00001", "reported_at": "2026-10-01T10:00:00Z",
                   "status": "REJECTED", "status_label": "Rechazado", "last_change_at": "2026-10-05T10:00:00+00:00",
                   "timeline": [{"step": "RECEIVED", "label": "Recibido", "explanation": "Recibimos su reporte.", "at": "2026-10-01T10:00:00Z"},
                                {"step": "REJECTED", "label": "Rechazado", "explanation": "No aplica.", "at": "2026-10-05T10:00:00Z"}],
                   "rejection_reason": "La póliza no cubre este evento.", "review_channel": "Escriba a revisiones."},
                  {"claim_number": "SIN-2026-00002", "policy_number": "POL-2026-00001", "reported_at": "2026-11-01T10:00:00Z",
                   "status": "IN_REVIEW", "status_label": "En evaluación", "last_change_at": "2026-11-01T10:00:00Z",
                   "timeline": [], "rejection_reason": null, "review_channel": null, "analyst_notes": "interno"}
                ]}
                """.trimIndent(),
            ),
        )

        val copy = (claims.claims("token-de-prueba") as ClaimsResult.Loaded).copy

        val request = server.takeRequest()
        assertEquals("/api/v1/insured/claims", request.path)
        assertEquals("Bearer token-de-prueba", request.getHeader("Authorization"))
        assertEquals(listOf("SIN-2026-00002", "SIN-2026-00001"), copy.claims.map { it.claimNumber })
        assertEquals(Instant.parse("2026-11-09T12:00:00Z"), copy.updatedAt)
        val rejected = copy.claims[1]
        assertTrue(rejected.rejected)
        assertEquals("La póliza no cubre este evento.", rejected.rejectionReason)
        assertEquals(2, rejected.timeline.size)
        assertNull(copy.claims[0].reviewChannel)
        assertEquals(rejected, ClaimsJson.claim(JSONObject(ClaimsJson.toJson(rejected).toString())))
        assertTrue(!ClaimsJson.toJson(copy.claims[0]).toString().contains("interno"))
    }

    @Test
    fun `401 es sesion vencida y los errores son sin conexion`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"claims\": [{}]}"))

        assertEquals(ClaimsResult.Unauthorized, claims.claims("t"))
        assertEquals(ClaimsResult.Unavailable, claims.claims("t"))
        assertEquals(ClaimsResult.Unavailable, claims.claims("t"))
    }

    @Test
    fun `AC-10 la caratula devuelve el enlace https o un resultado claro`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"url\": \"https://s3.example/c.pdf\", \"expires_at\": \"2026-11-09T12:15:00Z\"}"))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "30"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"url\": \"http://inseguro/c.pdf\"}"))

        assertEquals(CaratulaResult.Ready("https://s3.example/c.pdf"), actions.caratula("t", "POL-2026-00001"))
        assertEquals("/api/v1/insured/policies/POL-2026-00001/caratula", server.takeRequest().path)
        assertEquals(CaratulaResult.NotFound, actions.caratula("t", "POL-X"))
        assertEquals(CaratulaResult.Cancelled, actions.caratula("t", "POL-X"))
        assertEquals(CaratulaResult.Generating, actions.caratula("t", "POL-X"))
        assertEquals(CaratulaResult.Unavailable, actions.caratula("t", "POL-X"))
    }

    @Test
    fun `la renovacion devuelve la nueva vigencia o el motivo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"policy_number\": \"POL-1\", \"status\": \"ACTIVE\", \"valid_until\": \"2027-12-01T00:00:00Z\", \"version\": 2}"))
        server.enqueue(MockResponse().setResponseCode(409).setBody("{\"detail\": \"Faltan más de 30 días\"}"))

        assertEquals(RenewalResult.Renewed(Instant.parse("2027-12-01T00:00:00Z")), actions.renew("t", "POL-1"))
        assertEquals("POST", server.takeRequest().method)
        assertEquals(RenewalResult.NotRenewable("Faltan más de 30 días"), actions.renew("t", "POL-1"))
    }

    @Test
    fun `registra el token de avisos`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(true, actions.registerPushToken("t", "fcm-token"))
        val request = server.takeRequest()
        assertEquals("/api/v1/insured/devices/push-token", request.path)
        assertEquals("fcm-token", JSONObject(request.body.readUtf8()).getString("token"))
        assertNull(actions.registerPushToken("t", "fcm-token"))
    }
}
