package com.solventa.app.domain.claims

import java.time.Instant

/** Paso de la línea de tiempo de un siniestro, con la explicación en lenguaje claro (HU-MOV-11). */
data class ClaimTimelineStep(val step: String, val label: String, val explanation: String, val at: Instant)

/** Siniestro del asegurado como lo ve en la app: sin fotos ni montos del dictamen (HU-MOV-11). */
data class InsuredClaim(
    val claimNumber: String,
    val policyNumber: String,
    val reportedAt: Instant,
    val status: String,
    val statusLabel: String,
    val lastChangeAt: Instant,
    val timeline: List<ClaimTimelineStep>,
    val rejectionReason: String? = null,
    val reviewChannel: String? = null,
) {
    val rejected: Boolean get() = status == "REJECTED"
}

/** Siniestros de la cuenta con la fecha de su última actualización, del más reciente al más antiguo. */
data class ClaimsCopy(val claims: List<InsuredClaim>, val updatedAt: Instant)

interface ClaimsApi {
    suspend fun claims(accessToken: String): ClaimsResult
}

sealed interface ClaimsResult {
    data class Loaded(val copy: ClaimsCopy) : ClaimsResult
    data object Unauthorized : ClaimsResult
    data object Unavailable : ClaimsResult
}
