package com.solventa.app.data.policies

import com.solventa.app.domain.policies.CoverageLimit
import com.solventa.app.domain.policies.PoliciesApi
import com.solventa.app.domain.policies.PoliciesResult
import com.solventa.app.domain.policies.PolicyCard
import com.solventa.app.domain.policies.PolicyStore
import com.solventa.app.domain.policies.WalletCopy
import java.time.Instant

class InMemoryPolicyStore : PolicyStore {
    val copies = mutableMapOf<String, List<PolicyCard>>()
    val syncTimes = mutableMapOf<String, Instant>()

    fun put(accountEmail: String, policies: List<PolicyCard>, syncedAt: Instant) {
        copies[accountEmail] = policies
        syncTimes[accountEmail] = syncedAt
    }

    override suspend fun save(accountEmail: String, policies: List<PolicyCard>, syncedAt: Instant) =
        put(accountEmail, policies, syncedAt)

    override suspend fun read(accountEmail: String): WalletCopy? =
        copies[accountEmail]?.let { WalletCopy(it, syncTimes[accountEmail] ?: Instant.EPOCH) }

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

fun samplePolicy(number: String = "POL-2026-00001", validUntil: String = "2027-01-01T00:00:00Z") = PolicyCard(
    policyNumber = number,
    plan = "premium",
    insuredAmount = 2.5e8,
    holderName = "María Ruiz",
    coverages = listOf(CoverageLimit("Muerte por cualquier causa", 2.5e8), CoverageLimit("Incapacidad total y permanente", 2.5e8)),
    validFrom = Instant.parse("2026-01-01T00:00:00Z"),
    validUntil = Instant.parse(validUntil),
)
