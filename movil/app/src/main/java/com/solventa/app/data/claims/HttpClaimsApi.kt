package com.solventa.app.data.claims

import com.solventa.app.domain.claims.ClaimTimelineStep
import com.solventa.app.domain.claims.ClaimsApi
import com.solventa.app.domain.claims.ClaimsCopy
import com.solventa.app.domain.claims.ClaimsResult
import com.solventa.app.domain.claims.InsuredClaim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Siniestros del asegurado desde el BFF (HU-MOV-11). */
class HttpClaimsApi(private val baseUrl: String) : ClaimsApi {

    override suspend fun claims(accessToken: String): ClaimsResult = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl + "insured/claims").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    ClaimsResult.Loaded(ClaimsJson.parse(JSONObject(body)))
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> ClaimsResult.Unauthorized
                else -> ClaimsResult.Unavailable
            }
        } catch (e: IOException) {
            ClaimsResult.Unavailable
        } catch (e: JSONException) {
            ClaimsResult.Unavailable
        } catch (e: DateTimeParseException) {
            ClaimsResult.Unavailable
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}

/** Formato de un siniestro en la respuesta del BFF y en la copia local; los campos que sobran se descartan. */
internal object ClaimsJson {

    fun parse(json: JSONObject): ClaimsCopy {
        val array = json.getJSONArray("claims")
        return ClaimsCopy(
            claims = (0 until array.length()).map { claim(array.getJSONObject(it)) }.sortedByDescending(InsuredClaim::reportedAt),
            updatedAt = instant(json.getString("updated_at")),
        )
    }

    fun claim(json: JSONObject): InsuredClaim {
        val timeline = json.getJSONArray("timeline")
        return InsuredClaim(
            claimNumber = json.getString("claim_number"),
            policyNumber = json.getString("policy_number"),
            reportedAt = instant(json.getString("reported_at")),
            status = json.getString("status"),
            statusLabel = json.getString("status_label"),
            lastChangeAt = instant(json.getString("last_change_at")),
            timeline = (0 until timeline.length()).map { i ->
                val step = timeline.getJSONObject(i)
                ClaimTimelineStep(step.getString("step"), step.getString("label"), step.getString("explanation"), instant(step.getString("at")))
            },
            rejectionReason = json.optStringOrNull("rejection_reason"),
            reviewChannel = json.optStringOrNull("review_channel"),
        )
    }

    fun toJson(claim: InsuredClaim): JSONObject = JSONObject()
        .put("claim_number", claim.claimNumber)
        .put("policy_number", claim.policyNumber)
        .put("reported_at", claim.reportedAt.toString())
        .put("status", claim.status)
        .put("status_label", claim.statusLabel)
        .put("last_change_at", claim.lastChangeAt.toString())
        .put(
            "timeline",
            JSONArray(
                claim.timeline.map {
                    JSONObject().put("step", it.step).put("label", it.label).put("explanation", it.explanation).put("at", it.at.toString())
                },
            ),
        )
        .put("rejection_reason", claim.rejectionReason ?: JSONObject.NULL)
        .put("review_channel", claim.reviewChannel ?: JSONObject.NULL)

    private fun instant(text: String): Instant = OffsetDateTime.parse(text).toInstant()

    private fun JSONObject.optStringOrNull(name: String): String? = if (!has(name) || isNull(name)) null else getString(name)
}
