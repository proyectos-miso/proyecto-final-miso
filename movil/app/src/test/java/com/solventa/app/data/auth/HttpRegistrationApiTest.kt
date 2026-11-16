package com.solventa.app.data.auth

import com.solventa.app.domain.auth.DocumentType
import com.solventa.app.domain.auth.IdentityPhotos
import com.solventa.app.domain.auth.OtpResult
import com.solventa.app.domain.auth.RegistrationForm
import com.solventa.app.domain.auth.RegistrationResult
import com.solventa.app.domain.auth.SessionTokens
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Contrato del registro con documento y selfie con el BFF del asegurado (mejora FE-MOV-01).
@RunWith(RobolectricTestRunner::class)
class HttpRegistrationApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: HttpAuthApi

    private val front = ByteArray(64) { it.toByte() }
    private val selfie = ByteArray(48) { (200 + it).toByte() }
    private val form = RegistrationForm(
        email = " ana.registro@example.com ",
        fullName = "Ana Prueba Registro",
        documentType = DocumentType.CE,
        documentNumber = "AB12345",
        phone = "3000000000",
        password = "Registro2026",
        otpCode = "417902",
    )
    private val tokens = """{"access_token":"a","refresh_token":"r","report_token":"rep","token_type":"bearer",""" +
        """"expires_in":900,"full_name":"Ana Prueba Registro","verification_status":"%s"}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HttpAuthApi(server.url("/api/v1/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun answer(code: Int, body: String = "", retryAfter: String? = null) = server.enqueue(
        MockResponse().setResponseCode(code).setBody(body).apply { retryAfter?.let { setHeader("Retry-After", it) } }
    )

    private suspend fun register() = api.register(form, IdentityPhotos(front, selfie))

    @Test
    fun `pide el codigo de registro con el tipo y numero de documento y el celular`() = runTest {
        answer(200, """{"message":"ok","expires_in":300,"debug_code":"417902"}""")

        val result = api.requestRegistrationOtp(DocumentType.CC, "1098765432", "3000000000")

        assertEquals(OtpResult.Sent("417902"), result)
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/register/otp", request.path)
        val json = JSONObject(request.body.readUtf8())
        assertEquals("CC", json.getString("document_type"))
        assertEquals("1098765432", json.getString("document_number"))
        assertEquals("3000000000", json.getString("phone"))
    }

    @Test
    fun `envia un multipart con los datos, la foto del documento y la selfie, sin token`() = runTest {
        answer(201, tokens.format("APPROVED"))

        val result = register() as RegistrationResult.Registered

        assertEquals(SessionTokens("a", "r", "rep"), result.tokens)
        assertEquals("Ana Prueba Registro", result.fullName)
        assertFalse(result.pending)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/register", request.path)
        assertEquals(null, request.getHeader("Authorization"))
        val contentType = request.getHeader("Content-Type")!!
        assertTrue(contentType.startsWith("multipart/form-data; boundary="))
        val parts = parts(request.body.readByteArray(), contentType.substringAfter("boundary="))
        assertEquals(listOf("data", "document_front", "selfie"), parts.map { it.first })
        val data = JSONObject(String(parts[0].second, Charsets.UTF_8))
        assertEquals("ana.registro@example.com", data.getString("email"))
        assertEquals("CE", data.getString("document_type"))
        assertEquals("AB12345", data.getString("document_number"))
        assertEquals("417902", data.getString("otp_code"))
        assertTrue(data.getBoolean("terms_accepted") && data.getBoolean("biometric_consent_accepted"))
        assertArrayEquals(front, parts[1].second)
        assertArrayEquals(selfie, parts[2].second)
    }

    @Test
    fun `202 es una cuenta creada con la identidad en verificacion`() = runTest {
        answer(202, tokens.format("PENDING"))

        val result = register() as RegistrationResult.Registered

        assertTrue(result.pending)
    }

    @Test
    fun `cada rechazo del servidor se traduce por su codigo`() = runTest {
        val cases = listOf(
            Triple(422, """{"detail":"x","code":"IDENTITY_REJECTED"}""", RegistrationResult.Rejected),
            Triple(422, """{"detail":"x","code":"INVALID_CODE"}""", RegistrationResult.InvalidCode),
            Triple(422, """{"detail":"x","code":"WEAK_PASSWORD"}""", RegistrationResult.WeakPassword),
            Triple(422, """{"detail":"x","code":"INVALID_PHOTO"}""", RegistrationResult.InvalidData),
            Triple(422, """{"detail":[{"msg":"x"}]}""", RegistrationResult.InvalidData),
            Triple(413, """{"detail":"x","code":"INVALID_PHOTO"}""", RegistrationResult.InvalidData),
            Triple(409, """{"detail":"x","code":"DUPLICATE"}""", RegistrationResult.Duplicate),
            Triple(503, """{"detail":"x"}""", RegistrationResult.NetworkError),
        )
        cases.forEach { (code, body, expected) ->
            answer(code, body)
            assertEquals("HTTP $code $body", expected, register())
        }
    }

    @Test
    fun `429 trae la espera y sin red es un error de red`() = runTest {
        answer(429, """{"detail":"x"}""", retryAfter = "120")
        assertEquals(RegistrationResult.TooManyRequests(120), register())

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(RegistrationResult.NetworkError, register())
    }

    @Test
    fun `los datos del registro no se imprimen`() {
        assertFalse(form.toString().contains("417902"))
        assertFalse(IdentityPhotos(front, selfie).toString().contains("["))
    }

    // Separa el cuerpo multipart en (nombre, bytes) sin depender de otra librería.
    private fun parts(body: ByteArray, boundary: String): List<Pair<String, ByteArray>> {
        val delimiter = "--$boundary".toByteArray(Charsets.UTF_8)
        val starts = mutableListOf<Int>()
        var i = 0
        while (i <= body.size - delimiter.size) {
            if ((delimiter.indices).all { body[i + it] == delimiter[it] }) {
                starts += i
                i += delimiter.size
            } else {
                i++
            }
        }
        return starts.zipWithNext().map { (from, to) ->
            val chunk = body.copyOfRange(from + delimiter.size + 2, to - 2)
            val text = String(chunk, Charsets.ISO_8859_1)
            val headerEnd = text.indexOf("\r\n\r\n")
            val name = Regex("name=\"([^\"]+)\"").find(text.substring(0, headerEnd))!!.groupValues[1]
            name to chunk.copyOfRange(headerEnd + 4, chunk.size)
        }
    }
}
