package com.solventa.app.domain.policies

import com.solventa.app.domain.claims.ClaimsCopy
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

    /** Faltan 30 días o menos para el fin de la vigencia (HU-MOV-11, AC-09). */
    fun expiresSoonAt(now: Instant): Boolean = !isExpiredAt(now) && !validUntil.isAfter(now.plus(RENEWAL_WINDOW))

    companion object {
        val RENEWAL_WINDOW: java.time.Duration = java.time.Duration.ofDays(30)
    }
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
data class WalletCopy(val policies: List<PolicyCard>, val syncedAt: Instant, val claims: ClaimsCopy? = null)

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
    /** true solo si la copia quedó escrita (HU-MOV-07). */
    suspend fun save(accountEmail: String, policies: List<PolicyCard>, syncedAt: Instant): Boolean
    suspend fun read(accountEmail: String): StoredCopy
    suspend fun delete(accountEmail: String)

    /** Siniestros de la cuenta en la misma copia cifrada; se borran con ella (HU-MOV-11, AC-11). */
    suspend fun saveClaims(accountEmail: String, claims: ClaimsCopy): Boolean = false
    suspend fun readClaims(accountEmail: String): ClaimsCopy? = null

    /** true solo la primera vez que se marca [key]: cada aviso sale una sola vez (HU-MOV-11, AC-09). */
    suspend fun markNotified(accountEmail: String, key: String): Boolean = false
}
