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

/** Categorías de avisos que el asegurado puede apagar (FE-MOV-04). */
enum class NoticeCategory(val wire: String) {
    CLAIMS("claims"), RENEWALS("renewals"), PAYMENTS("payments");

    companion object {
        fun fromWire(value: String?): NoticeCategory? = entries.firstOrNull { it.wire == value }
    }
}

/** Qué avisos recibe el asegurado; por defecto todos. */
data class PushPreferences(val claims: Boolean = true, val renewals: Boolean = true, val payments: Boolean = true) {
    fun enabled(category: NoticeCategory): Boolean = when (category) {
        NoticeCategory.CLAIMS -> claims
        NoticeCategory.RENEWALS -> renewals
        NoticeCategory.PAYMENTS -> payments
    }

    fun with(category: NoticeCategory, enabled: Boolean): PushPreferences = when (category) {
        NoticeCategory.CLAIMS -> copy(claims = enabled)
        NoticeCategory.RENEWALS -> copy(renewals = enabled)
        NoticeCategory.PAYMENTS -> copy(payments = enabled)
    }
}

sealed interface PreferencesResult {
    data class Loaded(val preferences: PushPreferences) : PreferencesResult
    data object Unauthorized : PreferencesResult
    data object Invalid : PreferencesResult
    data object Unavailable : PreferencesResult
    data object IdentityInReview : PreferencesResult
}

/** Revocación de la autorización biométrica del registro (mejora FE-MOV-01). */
enum class ConsentRevocationResult { Revoked, Unauthorized, Unavailable }

/** Carátula, renovación, token y preferencias de avisos del asegurado (HU-MOV-11, FE-MOV-04). */
interface PolicyActionsApi {
    suspend fun caratula(accessToken: String, policyNumber: String): CaratulaResult
    suspend fun renew(accessToken: String, policyNumber: String): RenewalResult

    /** null si el servidor respondió 401. */
    suspend fun registerPushToken(accessToken: String, token: String): Boolean?
    suspend fun pushPreferences(accessToken: String): PreferencesResult
    suspend fun savePushPreferences(accessToken: String, preferences: PushPreferences): PreferencesResult

    suspend fun revokeBiometricConsent(accessToken: String): ConsentRevocationResult = ConsentRevocationResult.Unavailable
}

/** Las mismas acciones con la sesión abierta; un 401 renueva la sesión una vez. */
interface PolicyActions {
    suspend fun caratula(policyNumber: String): CaratulaResult
    suspend fun renew(policyNumber: String): RenewalResult
    suspend fun registerPushToken(token: String): Boolean
    suspend fun pushPreferences(): PreferencesResult
    suspend fun savePushPreferences(preferences: PushPreferences): PreferencesResult

    suspend fun revokeBiometricConsent(): ConsentRevocationResult = ConsentRevocationResult.Unavailable
}

/**
 * Última copia conocida de las preferencias de cada cuenta; [pending] marca un cambio que aún no llegó al
 * servidor. Sin copia se asumen todos los avisos encendidos.
 */
interface NotificationPreferences {
    fun read(accountEmail: String): PushPreferences
    fun pending(accountEmail: String): Boolean
    fun save(accountEmail: String, preferences: PushPreferences, pending: Boolean)
    fun delete(accountEmail: String)

    /** Guarda [preferences] como enviadas solo si la copia sigue en [expected] y [expectedPending]; atómico. */
    fun replaceIf(accountEmail: String, expected: PushPreferences, expectedPending: Boolean, preferences: PushPreferences): Boolean

    object None : NotificationPreferences {
        override fun read(accountEmail: String) = PushPreferences()
        override fun pending(accountEmail: String) = false
        override fun save(accountEmail: String, preferences: PushPreferences, pending: Boolean) = Unit
        override fun delete(accountEmail: String) = Unit
        override fun replaceIf(accountEmail: String, expected: PushPreferences, expectedPending: Boolean, preferences: PushPreferences) = false
    }
}

/** Aviso en la barra del teléfono; [claimNumber] abre ese siniestro al tocarlo. */
data class Notice(val key: String, val title: String, val body: String, val claimNumber: String? = null)

interface NotificationPoster {
    fun post(notice: Notice)
}
