package com.solventa.app.data.policies

import com.solventa.app.domain.claims.ClaimTimelineStep
import com.solventa.app.domain.claims.ClaimsApi
import com.solventa.app.domain.claims.ClaimsCopy
import com.solventa.app.domain.claims.ClaimsResult
import com.solventa.app.domain.claims.InsuredClaim
import com.solventa.app.domain.policies.CaratulaResult
import com.solventa.app.domain.policies.CoverageLimit
import com.solventa.app.domain.policies.Notice
import com.solventa.app.domain.policies.NotificationPoster
import com.solventa.app.domain.policies.PolicyActions
import com.solventa.app.domain.policies.RenewalResult
import com.solventa.app.domain.policies.PoliciesApi
import com.solventa.app.domain.policies.PoliciesResult
import com.solventa.app.domain.policies.PolicyCard
import com.solventa.app.domain.policies.PolicyStore
import com.solventa.app.domain.policies.StoredCopy
import com.solventa.app.domain.policies.WalletCopy
import com.solventa.app.domain.auth.AuthApi
import com.solventa.app.domain.auth.DeviceCredential
import com.solventa.app.domain.auth.LoginResult
import com.solventa.app.domain.auth.OtpResult
import com.solventa.app.domain.auth.RefreshResult
import com.solventa.app.domain.auth.ResetResult
import com.solventa.app.domain.policies.PolicySync
import com.solventa.app.domain.policies.SyncOutcome
import com.solventa.app.domain.policies.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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

    /** false simula una copia que no se puede escribir, como con el teléfono bloqueado (HU-MOV-07). */
    var canWrite = true

    /** Si existe, el borrado espera a que la prueba lo complete (HU-MOV-07). */
    var deleteGate: CompletableDeferred<Unit>? = null

    fun put(accountEmail: String, policies: List<PolicyCard>, syncedAt: Instant) {
        copies[accountEmail] = policies
        syncTimes[accountEmail] = syncedAt
    }

    override suspend fun save(accountEmail: String, policies: List<PolicyCard>, syncedAt: Instant): Boolean {
        if (canWrite) put(accountEmail, policies.distinctBy(PolicyCard::policyNumber), syncedAt)
        return canWrite
    }

    override suspend fun read(accountEmail: String): StoredCopy = readAs
        ?: copies[accountEmail]?.let { StoredCopy.Found(WalletCopy(it, syncTimes[accountEmail] ?: Instant.EPOCH)) }
        ?: StoredCopy.Missing

    override suspend fun delete(accountEmail: String) {
        deleteGate?.await()
        copies.remove(accountEmail)
        syncTimes.remove(accountEmail)
        claims.remove(accountEmail)
        notified.remove(accountEmail)
    }

    val claims = mutableMapOf<String, ClaimsCopy>()
    val notified = mutableMapOf<String, MutableSet<String>>()

    override suspend fun saveClaims(accountEmail: String, claims: ClaimsCopy): Boolean {
        if (canWrite) this.claims[accountEmail] = claims
        return canWrite
    }

    override suspend fun readClaims(accountEmail: String): ClaimsCopy? = claims[accountEmail]

    override suspend fun markNotified(accountEmail: String, key: String): Boolean = notified.getOrPut(accountEmail) { mutableSetOf() }.add(key)
}

class FakePoliciesApi(var result: PoliciesResult = PoliciesResult.Loaded(emptyList())) : PoliciesApi {
    var lastToken: String? = null
    var calls = 0

    override suspend fun activePolicies(accessToken: String): PoliciesResult {
        calls++
        lastToken = accessToken
        return result
    }
}

/** Servidor que responde cuando la prueba completa [response]. */
class PendingPoliciesApi : PoliciesApi {
    val response = CompletableDeferred<PoliciesResult>()

    override suspend fun activePolicies(accessToken: String): PoliciesResult = response.await()
}

/** Autenticación del sincronizador: renovación, ingreso con la credencial y revocación (HU-MOV-07). */
class FakeSyncAuthApi : AuthApi {
    var refreshResult: suspend () -> RefreshResult = { RefreshResult.Unavailable }
    var biometricResult: suspend () -> LoginResult = { LoginResult.NetworkError }
    val refreshed = mutableListOf<String>()
    val loggedOut = mutableListOf<String>()
    val biometricCredentials = mutableListOf<DeviceCredential>()
    val calls: Int get() = refreshed.size + biometricCredentials.size

    override suspend fun refresh(refreshToken: String): RefreshResult {
        refreshed += refreshToken
        return refreshResult()
    }

    override suspend fun biometricLogin(credential: DeviceCredential): LoginResult {
        biometricCredentials += credential
        return biometricResult()
    }

    override suspend fun logout(refreshToken: String): Boolean {
        loggedOut += refreshToken
        return true
    }

    override suspend fun requestOtp(email: String): OtpResult = error("no se usa")
    override suspend fun login(email: String, password: String, otpCode: String): LoginResult = error("no se usa")
    override suspend fun registerDevice(accessToken: String): DeviceCredential? = error("no se usa")
    override suspend fun revokeDevice(accessToken: String, deviceId: String): Boolean = error("no se usa")
    override suspend fun forgotPassword(email: String): OtpResult = error("no se usa")
    override suspend fun resetPassword(email: String, code: String, newPassword: String): ResetResult = error("no se usa")
}

/** Servidor de pólizas que responde según el access token recibido. */
class ScriptedPoliciesApi : PoliciesApi {
    var respond: suspend (String) -> PoliciesResult = { PoliciesResult.Unavailable }
    val calls = mutableListOf<String>()

    override suspend fun activePolicies(accessToken: String): PoliciesResult {
        calls += accessToken
        return respond(accessToken)
    }
}

/** Sincronizador doble para las pantallas y el trabajo: responde lo que la prueba indique y cuenta las llamadas. */
class FakePolicySync : PolicySync {
    var next: suspend () -> SyncOutcome = { SyncOutcome.NoSession }
    val onlineCalls = mutableListOf<Boolean>()
    var schedules = 0
    var cancels = 0
    val results = MutableSharedFlow<SyncOutcome>(extraBufferCapacity = 1)
    override val outcomes: SharedFlow<SyncOutcome> = results

    override suspend fun sync(online: Boolean): SyncOutcome {
        onlineCalls += online
        return next()
    }

    override fun schedule() {
        schedules++
    }

    override fun cancel() {
        cancels++
    }
}

class FakeScheduler : SyncScheduler {
    var requests = 0
    var cancels = 0

    override fun request() {
        requests++
    }

    override fun cancel() {
        cancels++
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

class FakeClaimsApi(var result: ClaimsResult = ClaimsResult.Unavailable) : ClaimsApi {
    override suspend fun claims(accessToken: String): ClaimsResult = result
}

class RecordingPoster : NotificationPoster {
    val posted = mutableListOf<Notice>()

    override fun post(notice: Notice) {
        posted += notice
    }
}

class FakePolicyActions : PolicyActions {
    var caratula: CaratulaResult = CaratulaResult.Unavailable
    var renewal: RenewalResult = RenewalResult.Unavailable
    val renewed = mutableListOf<String>()

    override suspend fun caratula(policyNumber: String): CaratulaResult = caratula

    override suspend fun renew(policyNumber: String): RenewalResult {
        renewed += policyNumber
        return renewal
    }

    override suspend fun registerPushToken(token: String): Boolean = true
}

fun sampleClaim(number: String = "SIN-2026-00001", status: String = "IN_REVIEW", label: String = "En evaluación", reportedAt: String = "2026-10-01T10:00:00Z") =
    InsuredClaim(
        claimNumber = number,
        policyNumber = "POL-2026-00001",
        reportedAt = Instant.parse(reportedAt),
        status = status,
        statusLabel = label,
        lastChangeAt = Instant.parse(reportedAt),
        timeline = listOf(ClaimTimelineStep("RECEIVED", "Recibido", "Recibimos su reporte.", Instant.parse(reportedAt))),
    )
