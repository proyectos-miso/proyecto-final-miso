package com.solventa.app.data.claims

import com.solventa.app.domain.claims.ClaimReport
import com.solventa.app.domain.claims.ClaimSubmitApi
import com.solventa.app.domain.claims.SubmissionPhoto
import com.solventa.app.domain.claims.SubmitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Envía el reporte con sus fotos en un solo multipart a `POST insured/claims` (HU-MOV-09). El servidor recalcula
 * la huella de cada foto y registra un solo siniestro por `report_id`, así que reenviar es seguro (AC-03, AC-06).
 */
class HttpClaimSubmitApi(private val baseUrl: String) : ClaimSubmitApi {

    override suspend fun submit(token: String, report: ClaimReport, photos: List<SubmissionPhoto>): SubmitResult =
        withContext(Dispatchers.IO) {
            val boundary = "solventa-" + UUID.randomUUID()
            val body = try {
                multipart(boundary, report, photos)
            } catch (e: JSONException) {
                return@withContext SubmitResult.Rejected(INVALID_REPORT)
            }
            val connection = URL(baseUrl + "insured/claims").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> {
                        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        SubmitResult.Received(json.getString("claim_number"))
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> SubmitResult.Unauthorized
                    HttpURLConnection.HTTP_NOT_FOUND -> SubmitResult.Rejected(POLICY_NOT_ACTIVE)
                    HTTP_PAYLOAD_TOO_LARGE, HTTP_UNPROCESSABLE_ENTITY -> SubmitResult.Rejected(detail(connection) ?: INVALID_REPORT)
                    else -> if (code >= 500 || code == HTTP_TOO_MANY_REQUESTS || code == HttpURLConnection.HTTP_CLIENT_TIMEOUT) {
                        SubmitResult.Retry
                    } else {
                        SubmitResult.Rejected(INVALID_REPORT)
                    }
                }
            } catch (e: IOException) {
                SubmitResult.Retry
            } catch (e: JSONException) {
                SubmitResult.Retry
            } finally {
                connection.disconnect()
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

    private fun multipart(boundary: String, report: ClaimReport, photos: List<SubmissionPhoto>): ByteArray {
        val out = ByteArrayOutputStream()
        fun line(text: String = "") = out.write((text + "\r\n").toByteArray(Charsets.UTF_8))
        line("--$boundary")
        line("Content-Disposition: form-data; name=\"report\"")
        line("Content-Type: application/json; charset=utf-8")
        line()
        line(reportJson(report).toString())
        photos.forEach { photo ->
            line("--$boundary")
            line("Content-Disposition: form-data; name=\"photo_${photo.position}\"; filename=\"photo_${photo.position}.jpg\"")
            line("Content-Type: image/jpeg")
            line()
            out.write(photo.jpeg)
            line()
        }
        line("--$boundary--")
        return out.toByteArray()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        // Hasta 5 fotos de 2 MB: la subida y la verificación del servidor tardan más que una consulta.
        const val READ_TIMEOUT_MS = 30_000
        const val HTTP_PAYLOAD_TOO_LARGE = 413
        const val HTTP_UNPROCESSABLE_ENTITY = 422
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MAX_DETAIL = 300
        const val POLICY_NOT_ACTIVE = "La póliza del reporte no está vigente o no es suya. Descarte este reporte y repórtelo de nuevo sobre una póliza vigente."
        const val INVALID_REPORT = "El servidor no aceptó el reporte. Descártelo y repórtelo de nuevo."
    }
}

/** Datos del reporte como los espera el servidor; las fotos van aparte, en el mismo envío. */
internal fun reportJson(report: ClaimReport): JSONObject = JSONObject()
    .put("report_id", report.id)
    .put("policy_number", report.policyNumber)
    .put("description", report.description)
    .put(
        "photos",
        JSONArray().apply {
            report.photos.forEach { photo ->
                put(
                    JSONObject()
                        .put("position", photo.position)
                        .put("sha256", photo.sha256)
                        .put("captured_at", photo.capturedAt.toString())
                        .put("trusted_time_source", photo.trustedTime?.source?.name ?: JSONObject.NULL)
                        .put("latitude", photo.fix.latitude)
                        .put("longitude", photo.fix.longitude)
                        .put("accuracy_m", photo.fix.accuracyMeters.toDouble())
                        .put("mock_location", photo.fix.mock),
                )
            }
        },
    )
