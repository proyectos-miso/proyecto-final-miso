package com.solventa.app.data.assistance

import com.solventa.app.domain.assistance.AssistanceApi
import com.solventa.app.domain.assistance.AssistanceForm
import com.solventa.app.domain.assistance.AssistanceResult
import com.solventa.app.domain.assistance.CitiesResult
import com.solventa.app.domain.assistance.City
import com.solventa.app.domain.assistance.ProviderQuery
import com.solventa.app.domain.assistance.ProvidersResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Prestadores, ciudades y solicitudes de asistencia del asegurado (mejora FE-MOV-07). */
class HttpAssistanceApi(private val baseUrl: String) : AssistanceApi {

    // La ubicación va en el cuerpo de un POST: en la URL quedaría en los registros de acceso (RISK-01).
    override suspend fun providers(accessToken: String, query: ProviderQuery): ProvidersResult {
        val body = JSONObject()
        query.coordinates?.let { body.put("lat", it.lat).put("lng", it.lng) } ?: query.cityCode?.let { body.put("city", it) }
        query.service?.let { body.put("service", it.name) }
        return call("insured/providers/search", "POST", accessToken, body.toString(), emptyMap(), ProvidersResult.Offline) { code, text ->
            when (code) {
                HttpURLConnection.HTTP_OK -> ProvidersResult.Loaded(ProvidersJson.providers(JSONObject(text).getJSONArray("providers")))
                HttpURLConnection.HTTP_UNAUTHORIZED -> ProvidersResult.Unauthorized
                HttpURLConnection.HTTP_FORBIDDEN -> if (errorCode(text) == IDENTITY_IN_REVIEW) ProvidersResult.IdentityInReview else ProvidersResult.Rejected
                HttpURLConnection.HTTP_BAD_REQUEST, HTTP_UNPROCESSABLE -> ProvidersResult.Rejected
                else -> ProvidersResult.Offline
            }
        }
    }

    override suspend fun cities(accessToken: String): CitiesResult =
        call("insured/catalogs/cities", "GET", accessToken, null, emptyMap(), CitiesResult.Offline) { code, body ->
            when (code) {
                HttpURLConnection.HTTP_OK -> JSONArray(body).let { array ->
                    CitiesResult.Loaded((0 until array.length()).map { i -> array.getJSONObject(i).let { City(it.getString("code"), it.getString("name")) } })
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> CitiesResult.Unauthorized
                HttpURLConnection.HTTP_FORBIDDEN -> if (errorCode(body) == IDENTITY_IN_REVIEW) CitiesResult.IdentityInReview else CitiesResult.Offline
                else -> CitiesResult.Offline
            }
        }

    override suspend fun request(accessToken: String, idempotencyKey: String, form: AssistanceForm): AssistanceResult {
        val body = JSONObject()
            .put("policy_number", form.policyNumber)
            .put("service", form.service.name)
            .put("description", form.description)
        form.coordinates?.let { body.put("lat", it.lat).put("lng", it.lng) } ?: body.put("city", form.cityCode)
        val headers = mapOf("Idempotency-Key" to idempotencyKey)
        return call("insured/assistance", "POST", accessToken, body.toString(), headers, AssistanceResult.Offline) { code, text ->
            when (code) {
                HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> JSONObject(text).let {
                    AssistanceResult.Received(it.getString("request_id"), ProvidersJson.provider(it.getJSONObject("provider")), it.getInt("eta_minutes"))
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> AssistanceResult.Unauthorized
                HttpURLConnection.HTTP_NOT_FOUND -> AssistanceResult.PolicyNotAvailable
                HttpURLConnection.HTTP_CONFLICT -> when (errorCode(text)) {
                    NO_PROVIDER -> AssistanceResult.NoProvider
                    KEY_REUSED -> AssistanceResult.KeyReused
                    else -> AssistanceResult.Rejected
                }
                HTTP_TOO_MANY_REQUESTS -> AssistanceResult.TooMany
                HttpURLConnection.HTTP_FORBIDDEN -> if (errorCode(text) == IDENTITY_IN_REVIEW) AssistanceResult.IdentityInReview else AssistanceResult.Rejected
                HttpURLConnection.HTTP_BAD_REQUEST, HTTP_UNPROCESSABLE -> AssistanceResult.Rejected
                else -> AssistanceResult.Offline
            }
        }
    }

    private suspend fun <T> call(
        path: String,
        method: String,
        accessToken: String,
        body: String?,
        headers: Map<String, String>,
        failure: T,
        read: (Int, String) -> T,
    ): T = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code < 400) connection.inputStream else connection.errorStream
            read(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } catch (e: IOException) {
            failure
        } catch (e: JSONException) {
            failure
        } finally {
            connection.disconnect()
        }
    }

    // Código estable del cuerpo de error (`{detail, code}`); vacío si no viene o no es JSON.
    private fun errorCode(body: String): String = try {
        JSONObject(body).optString("code")
    } catch (e: JSONException) {
        ""
    }

    private companion object {
        const val IDENTITY_IN_REVIEW = "IDENTITY_IN_REVIEW"
        const val NO_PROVIDER = "NO_PROVIDER"
        const val KEY_REUSED = "IDEMPOTENCY_KEY_REUSED"
        const val TIMEOUT_MS = 10_000
        const val HTTP_UNPROCESSABLE = 422
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
