package com.solventa.app.data.policies

import com.solventa.app.domain.policies.CaratulaResult
import com.solventa.app.domain.policies.PolicyActionsApi
import com.solventa.app.domain.policies.RenewalResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Carátula, renovación y token de avisos (HU-MOV-11). */
class HttpPolicyActionsApi(private val baseUrl: String) : PolicyActionsApi {

    override suspend fun caratula(accessToken: String, policyNumber: String): CaratulaResult =
        call("insured/policies/${encode(policyNumber)}/caratula", "GET", accessToken, null, CaratulaResult.Unavailable) { code, body ->
            when (code) {
                HttpURLConnection.HTTP_OK -> JSONObject(body).getString("url")
                    .takeIf { it.startsWith("https://") }?.let { CaratulaResult.Ready(it) } ?: CaratulaResult.Unavailable
                HttpURLConnection.HTTP_NOT_FOUND -> CaratulaResult.NotFound
                HttpURLConnection.HTTP_CONFLICT -> CaratulaResult.Cancelled
                HttpURLConnection.HTTP_UNAVAILABLE -> CaratulaResult.Generating
                HttpURLConnection.HTTP_UNAUTHORIZED -> CaratulaResult.Unauthorized
                else -> CaratulaResult.Unavailable
            }
        }

    override suspend fun renew(accessToken: String, policyNumber: String): RenewalResult =
        call("insured/policies/${encode(policyNumber)}/renewal", "POST", accessToken, "{}", RenewalResult.Unavailable) { code, body ->
            when (code) {
                HttpURLConnection.HTTP_OK -> RenewalResult.Renewed(OffsetDateTime.parse(JSONObject(body).getString("valid_until")).toInstant())
                HttpURLConnection.HTTP_CONFLICT -> RenewalResult.NotRenewable(runCatching { JSONObject(body).getString("detail") }.getOrNull())
                HttpURLConnection.HTTP_UNAUTHORIZED -> RenewalResult.Unauthorized
                else -> RenewalResult.Unavailable
            }
        }

    override suspend fun registerPushToken(accessToken: String, token: String): Boolean? =
        call<Boolean?>("insured/devices/push-token", "POST", accessToken, JSONObject().put("token", token).toString(), false) { code, _ ->
            when (code) {
                HttpURLConnection.HTTP_NO_CONTENT, HttpURLConnection.HTTP_OK -> true
                HttpURLConnection.HTTP_UNAUTHORIZED -> null
                else -> false
            }
        }

    private suspend fun <T> call(
        path: String,
        method: String,
        accessToken: String,
        body: String?,
        failure: T,
        read: (Int, String) -> T,
    ): T = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
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
        } catch (e: DateTimeParseException) {
            failure
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
