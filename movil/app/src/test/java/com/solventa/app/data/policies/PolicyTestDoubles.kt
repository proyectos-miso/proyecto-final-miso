package com.solventa.app.data.policies

import com.solventa.app.domain.policies.CoverageLimit
import com.solventa.app.domain.policies.PoliciesApi
import com.solventa.app.domain.policies.PoliciesResult
import com.solventa.app.domain.policies.PolicyCard
import com.solventa.app.domain.policies.PolicyStore
import com.solventa.app.domain.policies.StoredCopy
import com.solventa.app.domain.policies.WalletCopy
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant

/** Llaves que envuelven con XOR; la prueba puede perderlas, negar el envoltorio o volverlas no disponibles. */
class XorWalletKeys : WalletKeys {
    val aliases = mutableSetOf<String>()
    var canWrap = true
    var unavailable = false
    var lastWrapped: ByteArray? = null

    override fun wrap(alias: String, plaintext: ByteArray): WrappedSecret? {
        lastWrapped = plaintext
        if (!canWrap || unavailable) return null
        aliases += alias
        return WrappedSecret(ByteArray(12) { 1 }, plaintext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray() + ByteArray(16))
    }

    override fun unwrap(alias: String, wrapped: WrappedSecret): Unwrapped = when {
        unavailable -> Unwrapped.Unavailable
        alias in aliases -> Unwrapped.Secret(wrapped.ciphertext.dropLast(16).map { (it.toInt() xor 0x5A).toByte() }.toByteArray())
        else -> Unwrapped.Invalid
    }

    override fun delete(alias: String) {
        aliases -= alias
    }
}

class InMemoryPolicyStore : PolicyStore {
    val copies = mutableMapOf<String, List<PolicyCard>>()
    val syncTimes = mutableMapOf<String, Instant>()

    /** Lo que responde `read` en lugar de la copia, para simular un descarte o un bloqueo (HU-MOV-05). */
    var readAs: StoredCopy? = null

    fun put(accountEmail: String, policies: List<PolicyCard>, syncedAt: Instant) {
        copies[accountEmail] = policies
        syncTimes[accountEmail] = syncedAt
    }

    override suspend fun save(accountEmail: String, policies: List<PolicyCard>, syncedAt: Instant) =
        put(accountEmail, policies, syncedAt)

    override suspend fun read(accountEmail: String): StoredCopy = readAs
        ?: copies[accountEmail]?.let { StoredCopy.Found(WalletCopy(it, syncTimes[accountEmail] ?: Instant.EPOCH)) }
        ?: StoredCopy.Missing

    override suspend fun delete(accountEmail: String) {
        copies.remove(accountEmail)
        syncTimes.remove(accountEmail)
    }
}

class FakePoliciesApi(var result: PoliciesResult = PoliciesResult.Loaded(emptyList())) : PoliciesApi {
    var lastToken: String? = null

    override suspend fun activePolicies(accessToken: String): PoliciesResult {
        lastToken = accessToken
        return result
    }
}

/** Servidor que responde cuando la prueba completa [response]. */
class PendingPoliciesApi : PoliciesApi {
    val response = CompletableDeferred<PoliciesResult>()

    override suspend fun activePolicies(accessToken: String): PoliciesResult = response.await()
}

fun samplePolicy(number: String = "POL-2026-00001", validUntil: String = "2027-01-01T00:00:00Z") = PolicyCard(
    policyNumber = number,
    plan = "premium",
    insuredAmount = 2.5e8,
    holderName = "María Ruiz",
    coverages = listOf(CoverageLimit("Muerte por cualquier causa", 2.5e8), CoverageLimit("Incapacidad total y permanente", 2.5e8)),
    validFrom = Instant.parse("2026-01-01T00:00:00Z"),
    validUntil = Instant.parse(validUntil),
)
