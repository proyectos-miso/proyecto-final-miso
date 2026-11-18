package com.solventa.app.data.policies

import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection

internal const val IDENTITY_IN_REVIEW_CODE = "IDENTITY_IN_REVIEW"

/** Un 403 de las rutas del asegurado mientras la identidad de la cuenta sigue en verificación. */
internal fun isIdentityInReview(code: Int, body: String): Boolean =
    code == HttpURLConnection.HTTP_FORBIDDEN && try {
        JSONObject(body).optString("code") == IDENTITY_IN_REVIEW_CODE
    } catch (e: JSONException) {
        false
    }

internal fun HttpURLConnection.identityInReview(): Boolean =
    isIdentityInReview(responseCode, errorStream?.bufferedReader()?.use { it.readText() }.orEmpty())
