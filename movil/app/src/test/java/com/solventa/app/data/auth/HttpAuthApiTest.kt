package com.solventa.app.data.auth

import com.solventa.app.domain.auth.DeviceCredential
import com.solventa.app.domain.auth.LoginResult
import com.solventa.app.domain.auth.OtpResult
import com.solventa.app.domain.auth.RefreshResult
import com.solventa.app.domain.auth.ResetResult
import com.solventa.app.domain.auth.SessionTokens
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy

// Contrato HTTP de la app con el BFF del asegurado contra un servidor local de prueba.
@RunWith(RobolectricTestRunner::class)
class HttpAuthApiTest {

    private class Reply(val status: Int, val body: String = "", val retryAfter: String? = null, val policy: SocketPolicy? = null)

    private class Received(val method: String, val path: String, val body: String, val authorization: String?)

    private lateinit var server: MockWebServer
    private lateinit var api: HttpAuthApi
    private val replies = mutableMapOf<String, Reply>()
    private val received = mutableListOf<Received>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath.removePrefix("/api/v1/")
                received += Received(
                    request.method!!,
                    path,
                    request.body.readUtf8(),
                    request.getHeader("Authorization"),
                )
                val reply = replies[path] ?: Reply(500)
                return MockResponse().setResponseCode(reply.status).setBody(reply.body).apply {
                    reply.retryAfter?.let { setHeader("Retry-After", it) }
                    reply.policy?.let { socketPolicy = it }
                }
            }
        }
        server.start()
        api = HttpAuthApi(server.url("/api/v1/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tokensJson(name: String = "María Ruiz") =
        """{"access_token":"a","refresh_token":"r","token_type":"bearer","expires_in":900,"full_name":"$name"}"""

    @Test
    fun `pedir codigo devuelve el codigo de prueba o nada`() = runTest {
        replies["auth/otp"] = Reply(200, """{"message":"ok","expires_in":300,"debug_code":"417902"}""")
        assertEquals(OtpResult.Sent("417902"), api.requestOtp("maria.ruiz@correo.co"))
        assertEquals("maria.ruiz@correo.co", JSONObject(received.last().body).getString("email"))

        replies["auth/otp"] = Reply(200, """{"message":"ok","expires_in":300,"debug_code":null}""")
        assertEquals(OtpResult.Sent(null), api.requestOtp("maria.ruiz@correo.co"))
    }

    @Test
    fun `pedir codigo traduce limite, falla del SMS y errores`() = runTest {
        replies["auth/otp"] = Reply(429, """{"detail":"x"}""", retryAfter = "42")
        assertEquals(OtpResult.TooManyRequests(42), api.requestOtp("a@b.co"))

        replies["auth/otp"] = Reply(503, """{"detail":"x"}""")
        assertEquals(OtpResult.SendFailed, api.requestOtp("a@b.co"))

        replies["auth/otp"] = Reply(500)
        assertEquals(OtpResult.Failed, api.requestOtp("a@b.co"))

        replies["auth/otp"] = Reply(200, "no-es-json")
        assertEquals(OtpResult.Failed, api.requestOtp("a@b.co"))
    }

    @Test
    fun `recuperar contrasena usa su propio endpoint`() = runTest {
        replies["auth/password/forgot"] = Reply(200, """{"message":"ok","expires_in":300,"debug_code":"123456"}""")

        assertEquals(OtpResult.Sent("123456"), api.forgotPassword("a@b.co"))
        assertEquals("auth/password/forgot", received.last().path)
    }

    @Test
    fun `login traduce cada respuesta del servidor`() = runTest {
        replies["auth/login"] = Reply(200, tokensJson())
        val success = api.login("a@b.co", "clave", "123456") as LoginResult.Success
        assertEquals(SessionTokens("a", "r"), success.tokens)
        assertEquals("María Ruiz", success.fullName)
        val sent = JSONObject(received.last().body)
        assertEquals("123456", sent.getString("otp_code"))

        replies["auth/login"] = Reply(401, """{"detail":"x"}""")
        assertEquals(LoginResult.InvalidCredentials, api.login("a@b.co", "x", "123456"))

        replies["auth/login"] = Reply(422, """{"detail":"x"}""")
        assertEquals(LoginResult.InvalidFormat, api.login("a@b.co", "x", "12"))

        replies["auth/login"] = Reply(423, """{"detail":"x"}""", retryAfter = "900")
        assertEquals(LoginResult.Locked(900), api.login("a@b.co", "x", "123456"))

        replies["auth/login"] = Reply(429, """{"detail":"x"}""")
        assertEquals(LoginResult.TooManyRequests(60), api.login("a@b.co", "x", "123456"))

        replies["auth/login"] = Reply(500)
        assertEquals(LoginResult.NetworkError, api.login("a@b.co", "x", "123456"))

        replies["auth/login"] = Reply(200, "no-es-json")
        assertEquals(LoginResult.NetworkError, api.login("a@b.co", "x", "123456"))
    }

    @Test
    fun `huella envia la credencial del telefono`() = runTest {
        replies["auth/biometric"] = Reply(200, tokensJson("Pedro Gómez"))

        val result = api.biometricLogin(DeviceCredential("device-1", "secreto")) as LoginResult.Success

        assertEquals("Pedro Gómez", result.fullName)
        val sent = JSONObject(received.last().body)
        assertEquals("device-1", sent.getString("device_id"))
        assertEquals("secreto", sent.getString("device_secret"))
    }

    @Test
    fun `vincular y desvincular el telefono con el access token`() = runTest {
        replies["auth/devices"] = Reply(201, """{"device_id":"device-1","device_secret":"secreto"}""")
        assertEquals(DeviceCredential("device-1", "secreto"), api.registerDevice("token-1"))
        assertEquals("Bearer token-1", received.last().authorization)

        replies["auth/devices"] = Reply(401, """{"detail":"x"}""")
        assertNull(api.registerDevice("token-1"))

        replies["auth/devices"] = Reply(201, "no-es-json")
        assertNull(api.registerDevice("token-1"))

        replies["auth/devices/device-1"] = Reply(204)
        assertTrue(api.revokeDevice("token-1", "device-1"))
        assertEquals("DELETE", received.last().method)

        replies["auth/devices/device-1"] = Reply(500)
        assertFalse(api.revokeDevice("token-1", "device-1"))
    }

    @Test
    fun `cerrar sesion revoca el refresh token`() = runTest {
        replies["auth/logout"] = Reply(204)
        assertTrue(api.logout("refresh-1"))
        assertEquals("refresh-1", JSONObject(received.last().body).getString("refresh_token"))

        replies["auth/logout"] = Reply(401, """{"detail":"x"}""")
        assertFalse(api.logout("refresh-1"))
    }

    @Test
    fun `cambiar la contrasena traduce cada respuesta`() = runTest {
        val cases = listOf(
            Reply(200, """{"message":"ok"}""") to ResetResult.Success,
            Reply(401, """{"detail":"x"}""") to ResetResult.InvalidCode,
            Reply(422, """{"detail":"x"}""") to ResetResult.WeakPassword,
            Reply(409, """{"detail":"x"}""") to ResetResult.SamePassword,
            Reply(429, """{"detail":"x"}""", retryAfter = "15") to ResetResult.TooManyRequests(15),
            Reply(500) to ResetResult.NetworkError,
        )
        cases.forEach { (reply, expected) ->
            replies["auth/password/reset"] = reply
            assertEquals(expected, api.resetPassword("a@b.co", "123456", "NuevaClave2026"))
        }
    }

    // HU-MOV-07 TC-04: la renovación distingue lo que no llegó al servidor de lo que pudo rotar el token.
    @Test
    fun `renovar traduce cada respuesta del servidor`() = runTest {
        replies["auth/refresh"] = Reply(200, tokensJson())
        assertEquals(SessionTokens("a", "r"), (api.refresh("refresh-1") as RefreshResult.Renewed).tokens)
        assertEquals("refresh-1", JSONObject(received.last().body).getString("refresh_token"))

        val cases = listOf(
            Reply(200, """{"access_token":"a","full_name":"x"}""") to RefreshResult.Unanswered,
            Reply(200, """{"access_token":"","refresh_token":"r"}""") to RefreshResult.Unanswered,
            Reply(401, """{"detail":"x"}""") to RefreshResult.Rejected,
            Reply(423, """{"detail":"x"}""", retryAfter = "60") to RefreshResult.Unavailable,
            Reply(500) to RefreshResult.Unanswered,
            Reply(502) to RefreshResult.Unanswered,
        )
        cases.forEach { (reply, expected) ->
            replies["auth/refresh"] = reply
            assertEquals(expected, api.refresh("refresh-1"))
        }
    }

    @Test
    fun `la renovacion va con longitud fija y no se reenvia si se corta la respuesta`() = runTest {
        replies["auth/refresh"] = Reply(200, policy = SocketPolicy.DISCONNECT_AFTER_REQUEST)

        assertEquals(RefreshResult.Unanswered, api.refresh("refresh-1"))
        assertEquals(1, received.count { it.path == "auth/refresh" })
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Transfer-Encoding"))
        assertEquals(recorded.bodySize.toString(), recorded.getHeader("Content-Length"))
    }

    @Test
    fun `sin servidor todo responde como error de red`() = runTest {
        server.shutdown()

        assertEquals(RefreshResult.Unavailable, api.refresh("r"))

        assertEquals(OtpResult.Failed, api.requestOtp("a@b.co"))
        assertEquals(LoginResult.NetworkError, api.login("a@b.co", "x", "123456"))
        assertFalse(api.logout("r"))
        assertNull(api.registerDevice("t"))
        assertFalse(api.revokeDevice("t", "d"))
        assertEquals(ResetResult.NetworkError, api.resetPassword("a@b.co", "123456", "NuevaClave2026"))
    }
}
