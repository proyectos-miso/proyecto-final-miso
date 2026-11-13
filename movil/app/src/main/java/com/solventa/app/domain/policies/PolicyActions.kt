package com.solventa.app.domain.policies

import java.time.Instant

sealed interface CaratulaResult {
    data class Ready(val url: String) : CaratulaResult
    data object NotFound : CaratulaResult
    data object Cancelled : CaratulaResult
    data object Generating : CaratulaResult
    data object Unauthorized : CaratulaResult
    data object Unavailable : CaratulaResult
}

sealed interface RenewalResult {
    data class Renewed(val validUntil: Instant) : RenewalResult
    data class NotRenewable(val detail: String?) : RenewalResult
    data object Unauthorized : RenewalResult
    data object Unavailable : RenewalResult
}

/** Carátula, renovación y token de avisos del asegurado (HU-MOV-11). */
interface PolicyActionsApi {
    suspend fun caratula(accessToken: String, policyNumber: String): CaratulaResult
    suspend fun renew(accessToken: String, policyNumber: String): RenewalResult

    /** null si el servidor respondió 401. */
    suspend fun registerPushToken(accessToken: String, token: String): Boolean?
}

/** Las mismas acciones con la sesión abierta; un 401 renueva la sesión una vez. */
interface PolicyActions {
    suspend fun caratula(policyNumber: String): CaratulaResult
    suspend fun renew(policyNumber: String): RenewalResult
    suspend fun registerPushToken(token: String): Boolean
}

/** Aviso en la barra del teléfono; [claimNumber] abre ese siniestro al tocarlo. */
data class Notice(val key: String, val title: String, val body: String, val claimNumber: String? = null)

interface NotificationPoster {
    fun post(notice: Notice)
}
