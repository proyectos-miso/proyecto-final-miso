package com.solventa.app.data.policies

import com.solventa.app.domain.policies.CaratulaResult
import com.solventa.app.domain.policies.ConsentRevocationResult
import com.solventa.app.domain.policies.PreferencesResult
import com.solventa.app.domain.policies.PushPreferences
import com.solventa.app.domain.policies.RenewalResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
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
        assertEquals(PreferencesResult.Unavailable, api.pushPreferences("t"))
        assertEquals(PreferencesResult.Unavailable, api.savePushPreferences("t", PushPreferences()))
    }

    // FE-MOV-04: lectura y guardado de las preferencias de avisos.

    @Test
    fun `FE-MOV-04 las preferencias de avisos se leen con GET y se guardan con PUT y su cuerpo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"claims": true, "renewals": false, "payments": true}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"claims": false, "renewals": false, "payments": true}"""))

        val read = api.pushPreferences("t")
        val get = server.takeRequest()
        assertEquals(PreferencesResult.Loaded(PushPreferences(claims = true, renewals = false, payments = true)), read)
        assertEquals("GET", get.method)
        assertEquals("/api/v1/insured/devices/push-preferences", get.path)
        assertEquals("Bearer t", get.getHeader("Authorization"))

        val saved = api.savePushPreferences("t", PushPreferences(claims = false, renewals = false, payments = true))
        val put = server.takeRequest()
        assertEquals(PreferencesResult.Loaded(PushPreferences(claims = false, renewals = false, payments = true)), saved)
        assertEquals("PUT", put.method)
        assertEquals("/api/v1/insured/devices/push-preferences", put.path)
        assertEquals("application/json", put.getHeader("Content-Type"))
        assertEquals("Bearer t", put.getHeader("Authorization"))
        val body = JSONObject(put.body.readUtf8())
        assertEquals(false, body.getBoolean("claims"))
        assertEquals(false, body.getBoolean("renewals"))
        assertEquals(true, body.getBoolean("payments"))
    }

    @Test
    fun `FE-MOV-04 las preferencias con sesion vencida, cuerpo rechazado, error o respuesta mal formada`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"detail": "cuerpo inválido"}"""))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"claims": true}"""))
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(PreferencesResult.Unauthorized, api.savePushPreferences("t", PushPreferences()))
        assertEquals(PreferencesResult.Invalid, api.savePushPreferences("t", PushPreferences()))
        assertEquals(PreferencesResult.Unavailable, api.savePushPreferences("t", PushPreferences()))
        assertEquals(PreferencesResult.Unavailable, api.pushPreferences("t"))
        assertEquals(PreferencesResult.Unauthorized, api.pushPreferences("t"))
    }

    @Test
    fun `FE-MOV-01 la revocacion biometrica es un POST con el token y entiende 200, 401 y error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"message": "Revocada"}"""))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(ConsentRevocationResult.Revoked, api.revokeBiometricConsent("t"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/insured/consents/biometric/revoke", request.path)
        assertEquals("Bearer t", request.getHeader("Authorization"))
        assertEquals(ConsentRevocationResult.Unauthorized, api.revokeBiometricConsent("t"))
        assertEquals(ConsentRevocationResult.Unavailable, api.revokeBiometricConsent("t"))
    }
}
