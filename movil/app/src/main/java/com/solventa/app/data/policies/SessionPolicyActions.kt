package com.solventa.app.data.policies

import com.solventa.app.domain.policies.CaratulaResult
import com.solventa.app.domain.policies.PolicyActions
import com.solventa.app.domain.policies.PolicyActionsApi
import com.solventa.app.domain.policies.PreferencesResult
import com.solventa.app.domain.policies.PushPreferences
import com.solventa.app.domain.policies.RenewalResult

/** Acciones de la tarjeta con el token de la sesión abierta (HU-MOV-11). */
class SessionPolicyActions(private val sync: SessionPolicySync, private val api: PolicyActionsApi) : PolicyActions {

    override suspend fun caratula(policyNumber: String): CaratulaResult =
        sync.withAccessToken({ it == CaratulaResult.Unauthorized }) { api.caratula(it, policyNumber) } ?: CaratulaResult.Unauthorized

    override suspend fun renew(policyNumber: String): RenewalResult =
        sync.withAccessToken({ it == RenewalResult.Unauthorized }) { api.renew(it, policyNumber) } ?: RenewalResult.Unauthorized

    override suspend fun registerPushToken(token: String): Boolean =
        sync.withAccessToken<Boolean?>({ it == null }) { api.registerPushToken(it, token) } == true

    override suspend fun pushPreferences(): PreferencesResult =
        sync.withAccessToken({ it == PreferencesResult.Unauthorized }) { api.pushPreferences(it) } ?: withoutTokens()

    override suspend fun savePushPreferences(preferences: PushPreferences): PreferencesResult =
        sync.withAccessToken({ it == PreferencesResult.Unauthorized }) { api.savePushPreferences(it, preferences) } ?: withoutTokens()

    // Con el ingreso sin red hay sesión pero no tokens: es falta de red, no una sesión vencida (FE-MOV-04, AC-04).
    private fun withoutTokens() = if (sync.hasOpenSession) PreferencesResult.Unavailable else PreferencesResult.Unauthorized
}
