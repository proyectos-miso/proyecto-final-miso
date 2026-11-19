package com.solventa.app.data.claims

import com.solventa.app.domain.claims.ClaimDocumentsApi
import com.solventa.app.domain.claims.DocumentUploadResult
import com.solventa.app.domain.claims.ScannedPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Envía las páginas en un solo multipart a `POST insured/claims/{n}/documents` (mejora FE-MOV-05). El cuerpo se
 * arma en memoria; el servidor recalcula la huella de cada página y registra un solo envío por `upload_id`.
 */
class HttpClaimDocumentsApi(private val baseUrl: String) : ClaimDocumentsApi {

    override suspend fun upload(
        accessToken: String,
        claimNumber: String,
        uploadId: String,
        pages: List<ScannedPage>,
    ): DocumentUploadResult = withContext(Dispatchers.IO) {
        val boundary = "solventa-" + UUID.randomUUID()
        val body = multipart(boundary, uploadId, pages)
        val path = "insured/claims/${URLEncoder.encode(claimNumber, Charsets.UTF_8)}/documents"
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> {
                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    DocumentUploadResult.Received(json.getInt("pages"))
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> DocumentUploadResult.Unauthorized
                HttpURLConnection.HTTP_NOT_FOUND -> DocumentUploadResult.NotFound
                HttpURLConnection.HTTP_CONFLICT -> DocumentUploadResult.Closed
                HttpURLConnection.HTTP_FORBIDDEN, HTTP_PAYLOAD_TOO_LARGE, HTTP_UNPROCESSABLE_ENTITY ->
                    DocumentUploadResult.Rejected(detail(connection) ?: INVALID)
                else -> if (code >= 500 || code == HTTP_TOO_MANY_REQUESTS || code == HttpURLConnection.HTTP_CLIENT_TIMEOUT) {
                    DocumentUploadResult.Offline
                } else {
                    DocumentUploadResult.Rejected(INVALID)
                }
            }
        } catch (e: IOException) {
            DocumentUploadResult.Offline
        } catch (e: JSONException) {
            DocumentUploadResult.Offline
        } finally {
            connection.disconnect()
            body.fill(0)
        }
    }

    private fun detail(connection: HttpURLConnection): String? = try {
        val text = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: return null
        JSONObject(text).optString("detail").takeIf { it.isNotBlank() && it.length <= MAX_DETAIL }
    } catch (e: IOException) {
        null
    } catch (e: JSONException) {
        null
    }

    private fun multipart(boundary: String, uploadId: String, pages: List<ScannedPage>): ByteArray {
        val out = ByteArrayOutputStream()
        fun line(text: String = "") = out.write((text + "\r\n").toByteArray(Charsets.UTF_8))
        line("--$boundary")
        line("Content-Disposition: form-data; name=\"manifest\"")
        line("Content-Type: application/json; charset=utf-8")
        line()
        line(manifestJson(uploadId, pages).toString())
        pages.forEachIndexed { index, page ->
            val position = index + 1
            line("--$boundary")
            line("Content-Disposition: form-data; name=\"page_$position\"; filename=\"page_$position.jpg\"")
            line("Content-Type: image/jpeg")
            line()
            out.write(page.jpeg)
            line()
        }
        line("--$boundary--")
        return out.toByteArray()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        // Hasta 10 páginas: la subida tarda más que una consulta.
        const val READ_TIMEOUT_MS = 60_000
        const val HTTP_PAYLOAD_TOO_LARGE = 413
        const val HTTP_UNPROCESSABLE_ENTITY = 422
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MAX_DETAIL = 300
        const val INVALID = "El servidor no aceptó los documentos. Escanéelos de nuevo."
    }
}

/** Manifiesto del envío: el id y la huella de cada página en orden. */
internal fun manifestJson(uploadId: String, pages: List<ScannedPage>): JSONObject = JSONObject()
    .put("upload_id", uploadId)
    .put(
        "pages",
        JSONArray().apply {
            pages.forEachIndexed { index, page -> put(JSONObject().put("position", index + 1).put("sha256", page.sha256)) }
        },
    )
