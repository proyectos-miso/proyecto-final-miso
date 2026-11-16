package com.solventa.app.data.assistance

import com.solventa.app.data.policies.SessionPolicySync
import com.solventa.app.domain.assistance.Assistance
import com.solventa.app.domain.assistance.AssistanceApi
import com.solventa.app.domain.assistance.AssistanceForm
import com.solventa.app.domain.assistance.AssistanceResult
import com.solventa.app.domain.assistance.CitiesResult
import com.solventa.app.domain.assistance.ProviderQuery
import com.solventa.app.domain.assistance.ProvidersResult

/** Prestadores y asistencia con el token de la sesión abierta; con el ingreso sin red no hay tokens y es falta de red. */
class SessionAssistance(private val sync: SessionPolicySync, private val api: AssistanceApi) : Assistance {

    override suspend fun providers(query: ProviderQuery): ProvidersResult =
        sync.withAccessToken({ it == ProvidersResult.Unauthorized }) { api.providers(it, query) }
            ?: if (sync.hasOpenSession) ProvidersResult.Offline else ProvidersResult.Unauthorized

    override suspend fun cities(): CitiesResult =
        sync.withAccessToken({ it == CitiesResult.Unauthorized }) { api.cities(it) }
            ?: if (sync.hasOpenSession) CitiesResult.Offline else CitiesResult.Unauthorized

    override suspend fun request(idempotencyKey: String, form: AssistanceForm): AssistanceResult =
        sync.withAccessToken({ it == AssistanceResult.Unauthorized }) { api.request(it, idempotencyKey, form) }
            ?: if (sync.hasOpenSession) AssistanceResult.Offline else AssistanceResult.Unauthorized
}
