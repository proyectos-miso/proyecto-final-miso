package com.solventa.app.data.claims

import com.solventa.app.domain.claims.CaptureFix
import com.solventa.app.domain.claims.ClaimReport
import com.solventa.app.domain.claims.EvidencePhoto
import com.solventa.app.domain.claims.ReportState
import com.solventa.app.domain.claims.SubmissionPhoto
import com.solventa.app.domain.claims.SubmitResult
import com.solventa.app.domain.claims.TimeSource
import com.solventa.app.domain.claims.TrustedTime
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

// Contrato del envío del reporte con sus fotos al BFF del asegurado (HU-MOV-09).
@RunWith(RobolectricTestRunner::class)
class HttpClaimSubmitApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: HttpClaimSubmitApi

    private val first = ByteArray(64) { it.toByte() }
    private val second = ByteArray(32) { (100 + it).toByte() }
    private val report = ClaimReport(
        id = "0b7c8a4e-1d2f-4a6b-9c3d-5e6f7a8b9c0d",
        policyNumber = "POL-2026-00001",
        description = "Golpe en la puerta",
        state = ReportState.QUEUED,
        photos = listOf(
            EvidencePhoto("f1", 1, Instant.parse("2026-11-13T14:00:00Z"), TrustedTime(1L, TimeSource.GNSS), CaptureFix(4.6, -74.0, 12.5f, false), "a".repeat(64), 64),
            EvidencePhoto("f2", 2, Instant.parse("2026-11-13T14:01:00Z"), null, CaptureFix(4.61, -74.01, 8f, true), "b".repeat(64), 32),
        ),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
    private val photos = listOf(SubmissionPhoto(1, first), SubmissionPhoto(2, second))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HttpClaimSubmitApi(server.url("/api/v1/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun answer(code: Int, body: String = "") = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    @Test
    fun `envía un multipart con el reporte y cada foto con su posición y el token`() = runTest {
        answer(201, """{"claim_number":"SIN-2026-00001","status":"REPORTED","reported_at":"2026-11-13T14:05:00Z"}""")

        val result = api.submit("token-de-reporte", report, photos)

        assertEquals(SubmitResult.Received("SIN-2026-00001", Instant.parse("2026-11-13T14:05:00Z")), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/insured/claims", request.path)
        assertEquals("Bearer token-de-reporte", request.getHeader("Authorization"))
        val contentType = request.getHeader("Content-Type")!!
        assertTrue(contentType.startsWith("multipart/form-data; boundary="))
        val parts = parts(request.body.readByteArray(), contentType.substringAfter("boundary="))
        assertEquals(listOf("report", "photo_1", "photo_2"), parts.map { it.first })

        val json = JSONObject(String(parts[0].second, Charsets.UTF_8))
        assertEquals(report.id, json.getString("report_id"))
        assertEquals("POL-2026-00001", json.getString("policy_number"))
        assertEquals("Golpe en la puerta", json.getString("description"))
        val photo = json.getJSONArray("photos").getJSONObject(0)
        assertEquals(1, photo.getInt("position"))
        assertEquals("a".repeat(64), photo.getString("sha256"))
        assertEquals("2026-11-13T14:00:00Z", photo.getString("captured_at"))
        assertEquals("GNSS", photo.getString("trusted_time_source"))
        assertEquals(4.6, photo.getDouble("latitude"), 0.0)
        assertEquals(-74.0, photo.getDouble("longitude"), 0.0)
        assertEquals(12.5, photo.getDouble("accuracy_m"), 0.0)
        assertEquals(false, photo.getBoolean("mock_location"))
        val other = json.getJSONArray("photos").getJSONObject(1)
        assertTrue(other.isNull("trusted_time_source"))
        assertEquals(true, other.getBoolean("mock_location"))

        assertArrayEquals(first, parts[1].second)
        assertArrayEquals(second, parts[2].second)
    }

    @Test
    fun `AC-03 un 200 es el mismo siniestro ya recibido`() = runTest {
        answer(200, """{"claim_number":"SIN-2026-00001","status":"REPORTED","reported_at":"2026-11-13T14:05:00Z"}""")

        assertEquals(SubmitResult.Received("SIN-2026-00001", Instant.parse("2026-11-13T14:05:00Z")), api.submit("t", report, photos))
    }

    // HU-MOV-10 AC-01: la hora oficial del registro llega con zona; sin ella el siniestro igual quedó recibido.
    @Test
    fun `HU-MOV-10 AC-01 lee la hora oficial del registro con su zona y la tolera ausente`() = runTest {
        answer(201, """{"claim_number":"SIN-2026-00002","status":"REPORTED","reported_at":"2026-11-13T09:05:00.123456-05:00"}""")
        answer(201, """{"claim_number":"SIN-2026-00003","status":"REPORTED"}""")
        answer(201, """{"claim_number":"SIN-2026-00004","status":"REPORTED","reported_at":"ayer"}""")

        assertEquals(SubmitResult.Received("SIN-2026-00002", Instant.parse("2026-11-13T14:05:00.123456Z")), api.submit("t", report, photos))
        assertEquals(SubmitResult.Received("SIN-2026-00003", null), api.submit("t", report, photos))
        assertEquals(SubmitResult.Received("SIN-2026-00004", null), api.submit("t", report, photos))
    }

    @Test
    fun `un 401 espera a otra sesión`() = runTest {
        answer(401)

        assertEquals(SubmitResult.Unauthorized, api.submit("t", report, photos))
    }

    @Test
    fun `AC-09 un 404 es un rechazo definitivo que explica qué hacer`() = runTest {
        answer(404, """{"detail":"No encontrado"}""")

        val result = api.submit("t", report, photos) as SubmitResult.Rejected

        assertTrue(result.reason.contains("Descarte este reporte"))
    }

    @Test
    fun `AC-09 un 422 o un 413 rechazan con el motivo del servidor`() = runTest {
        answer(422, """{"detail":"La foto 2 no coincide con su huella."}""")
        answer(413, """{"detail":"La foto 1 pasa de 2 MB."}""")
        answer(422, "sin json")

        assertEquals(SubmitResult.Rejected("La foto 2 no coincide con su huella."), api.submit("t", report, photos))
        assertEquals(SubmitResult.Rejected("La foto 1 pasa de 2 MB."), api.submit("t", report, photos))
        assertTrue(api.submit("t", report, photos) is SubmitResult.Rejected)
    }

    @Test
    fun `AC-04 un error del servidor, un 503 o una respuesta inválida se reintentan`() = runTest {
        answer(500)
        answer(503)
        answer(201, "no es json")

        repeat(3) { assertEquals(SubmitResult.Retry, api.submit("t", report, photos)) }
    }

    @Test
    fun `AC-04 sin red se reintenta`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(SubmitResult.Retry, api.submit("t", report, photos))
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
