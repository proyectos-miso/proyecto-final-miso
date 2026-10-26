package com.solventa.app.domain.policies

import java.time.Instant

data class CoverageLimit(val name: String, val limit: Double)

/** Tarjeta de la billetera: solo lo que se muestra, sin documento, beneficiarios ni prima (HU-MOV-04). */
data class PolicyCard(
    val policyNumber: String,
    val plan: String,
    val insuredAmount: Double,
    val holderName: String,
    val coverages: List<CoverageLimit>,
    val validFrom: Instant,
    val validUntil: Instant,
) {
    fun isExpiredAt(now: Instant): Boolean = !validUntil.isAfter(now)
}

interface PoliciesApi {
    suspend fun activePolicies(accessToken: String): PoliciesResult
}

sealed interface PoliciesResult {
    data class Loaded(val policies: List<PolicyCard>) : PoliciesResult
    data object Unauthorized : PoliciesResult
    data object Unavailable : PoliciesResult
}

/** Copia de la billetera con la hora de la sincronización que la trajo (HU-MOV-06). */
data class WalletCopy(val policies: List<PolicyCard>, val syncedAt: Instant)

/** Lectura de la copia local de una cuenta (HU-MOV-05). */
sealed interface StoredCopy {
    data class Found(val copy: WalletCopy) : StoredCopy

    /** La cuenta no tiene copia. */
    data object Missing : StoredCopy

    /** Se borró una copia que existía porque su llave, su frase o su base ya no servían. */
    data object Discarded : StoredCopy

    /** El teléfono está bloqueado o hubo una falla pasajera; la copia se conserva. */
    data object Unavailable : StoredCopy
}

/** Copia local por cuenta. Ninguna operación lanza por la llave ni por la base, salvo la cancelación. */
interface PolicyStore {
    suspend fun save(accountEmail: String, policies: List<PolicyCard>, syncedAt: Instant)
    suspend fun read(accountEmail: String): StoredCopy
    suspend fun delete(accountEmail: String)
}
