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
import java.net.URLEncoder

/** Prestadores, ciudades y solicitudes de asistencia del asegurado (mejora FE-MOV-07). */
class HttpAssistanceApi(private val baseUrl: String) : AssistanceApi {

    override suspend fun providers(accessToken: String, query: ProviderQuery): ProvidersResult {
        val params = buildList {
            query.coordinates?.let {
                add("lat=${it.lat}")
                add("lng=${it.lng}")
            }
            if (query.coordinates == null) query.cityCode?.let { add("city=${encode(it)}") }
            query.service?.let { add("service=${it.name}") }
        }
        return call("insured/providers?" + params.joinToString("&"), "GET", accessToken, null, emptyMap(), ProvidersResult.Offline) { code, body ->
            when (code) {
                HttpURLConnection.HTTP_OK -> ProvidersResult.Loaded(ProvidersJson.providers(JSONObject(body).getJSONArray("providers")))
                HttpURLConnection.HTTP_UNAUTHORIZED -> ProvidersResult.Unauthorized
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
                    AssistanceResult.Assigned(it.getString("request_id"), ProvidersJson.provider(it.getJSONObject("provider")), it.getInt("eta_minutes"))
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> AssistanceResult.Unauthorized
                HttpURLConnection.HTTP_NOT_FOUND -> AssistanceResult.PolicyNotAvailable
                HttpURLConnection.HTTP_CONFLICT -> AssistanceResult.NoProvider
                HTTP_TOO_MANY_REQUESTS -> AssistanceResult.TooMany
                HttpURLConnection.HTTP_FORBIDDEN, HttpURLConnection.HTTP_BAD_REQUEST, HTTP_UNPROCESSABLE -> AssistanceResult.Rejected
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

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val HTTP_UNPROCESSABLE = 422
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
