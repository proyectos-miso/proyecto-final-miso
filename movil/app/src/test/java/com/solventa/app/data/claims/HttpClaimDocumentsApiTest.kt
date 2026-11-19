package com.solventa.app.data.claims

import com.solventa.app.domain.claims.DocumentUploadResult
import com.solventa.app.domain.claims.ScannedPage
import com.solventa.app.domain.claims.sha256Hex
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Mejora FE-MOV-05: contrato del envío de documentos al BFF del asegurado.
@RunWith(RobolectricTestRunner::class)
class HttpClaimDocumentsApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: HttpClaimDocumentsApi

    private val first = ScannedPage(ByteArray(64) { it.toByte() })
    private val second = ScannedPage(ByteArray(32) { (100 + it).toByte() })
    private val uploadId = "0b7c8a4e-1d2f-4a6b-9c3d-5e6f7a8b9c0d"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HttpClaimDocumentsApi(server.url("/api/v1/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun answer(code: Int, body: String = "") = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private suspend fun send() = api.upload("token-de-acceso", "SIN-2026-00001", uploadId, listOf(first, second))

    @Test
    fun `envia un multipart con el manifiesto y cada pagina con su posicion y el token`() = runTest {
        answer(201, """{"claim_number":"SIN-2026-00001","upload_id":"$uploadId","pages":2,"uploaded_at":"2026-11-15T10:00:00Z"}""")

        val result = send()

        assertEquals(DocumentUploadResult.Received(2), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/insured/claims/SIN-2026-00001/documents", request.path)
        assertEquals("Bearer token-de-acceso", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
        val body = request.body.readByteArray()
        val text = String(body, Charsets.ISO_8859_1)
        val manifest = JSONObject(text.substringAfter("application/json; charset=utf-8\r\n\r\n").substringBefore("\r\n"))
        assertEquals(uploadId, manifest.getString("upload_id"))
        val pages = manifest.getJSONArray("pages")
        assertEquals(listOf(1, 2), (0 until pages.length()).map { pages.getJSONObject(it).getInt("position") })
        assertEquals(sha256Hex(first.jpeg), pages.getJSONObject(0).getString("sha256"))
        assertTrue(text.contains("name=\"page_1\"; filename=\"page_1.jpg\"\r\nContent-Type: image/jpeg"))
        assertTrue(text.contains("name=\"page_2\""))
        assertTrue(text.contains(String(second.jpeg, Charsets.ISO_8859_1)))
    }

    @Test
    fun `el envio repetido que el servidor ya tenia tambien es recibido`() = runTest {
        answer(200, """{"claim_number":"SIN-2026-00001","upload_id":"$uploadId","pages":2,"uploaded_at":"2026-11-15T10:00:00Z"}""")

        assertEquals(DocumentUploadResult.Received(2), send())
    }

    @Test
    fun `cada respuesta del servidor tiene su resultado`() = runTest {
        answer(401)
        answer(404, """{"detail":"No encontramos el siniestro."}""")
        answer(409, """{"detail":"El siniestro ya fue decidido y no recibe documentos."}""")
        answer(422, """{"detail":"La página 2 cambió después de escanearla. Escanéela de nuevo."}""")
        answer(413, """{"detail":"Cada página puede pesar máximo 5 MB."}""")
        answer(403, """{"detail":"Estamos verificando su identidad."}""")
        answer(503)
        answer(429)

        val results = List(8) { send() }

        assertEquals(
            listOf(
                DocumentUploadResult.Unauthorized,
                DocumentUploadResult.NotFound,
                DocumentUploadResult.Closed,
                DocumentUploadResult.Rejected("La página 2 cambió después de escanearla. Escanéela de nuevo."),
                DocumentUploadResult.Rejected("Cada página puede pesar máximo 5 MB."),
                DocumentUploadResult.Rejected("Estamos verificando su identidad."),
                DocumentUploadResult.Offline,
                DocumentUploadResult.Offline,
            ),
            results,
        )
    }

    @Test
    fun `sin red el envio queda para reintentar`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(DocumentUploadResult.Offline, send())
    }

    @Test
    fun `un detalle demasiado largo no se muestra`() = runTest {
        answer(422, """{"detail":"${"x".repeat(400)}"}""")

        assertTrue((send() as DocumentUploadResult.Rejected).reason.startsWith("El servidor no aceptó"))
    }
}
