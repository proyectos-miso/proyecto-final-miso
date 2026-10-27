package com.solventa.app.data.policies

import com.solventa.app.data.session.KeyState
import com.solventa.app.data.session.OpenSession
import com.solventa.app.data.session.SecureSessionRepository
import com.solventa.app.domain.auth.AuthApi
import com.solventa.app.domain.auth.DeviceCredential
import com.solventa.app.domain.auth.LoginResult
import com.solventa.app.domain.auth.RefreshResult
import com.solventa.app.domain.policies.PoliciesApi
import com.solventa.app.domain.policies.PoliciesResult
import com.solventa.app.domain.policies.PolicyCard
import com.solventa.app.domain.policies.PolicyStore
import com.solventa.app.domain.policies.PolicySync
import com.solventa.app.domain.policies.SessionEndReason
import com.solventa.app.domain.policies.SyncOutcome
import com.solventa.app.domain.policies.SyncScheduler
import com.solventa.app.domain.policies.WalletCopy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Sincroniza la copia con la sesión en memoria (HU-MOV-07). Una sincronización a la vez; cada una toma la
 * sesión al obtener el turno y solo la cambia con compareAndSet. Obtener tokens no se cancela a medias: si
 * la sesión cambió mientras tanto, el par nuevo se revoca (CTL-05).
 */
class SessionPolicySync(
    private val session: SecureSessionRepository,
    private val authApi: AuthApi,
    private val policiesApi: PoliciesApi,
    private val store: PolicyStore,
    private val scheduler: SyncScheduler,
    private val clock: Clock = Clock.systemUTC(),
) : PolicySync {

    private val turn = Mutex()
    private val running: MutableSet<Job> = ConcurrentHashMap.newKeySet()
    private val results = MutableSharedFlow<SyncOutcome>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val outcomes: SharedFlow<SyncOutcome> = results.asSharedFlow()

    override suspend fun sync(online: Boolean): SyncOutcome = try {
        coroutineScope {
            val job = coroutineContext.job
            running += job
            try {
                turn.withLock { syncInTurn(online) }
            } finally {
                running -= job
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SyncOutcome.Failed
    }

    override fun schedule() = scheduler.request()

    override fun cancel() {
        running.forEach { it.cancel() }
        scheduler.cancel()
    }

    private suspend fun syncInTurn(online: Boolean): SyncOutcome {
        val opened = session.openSession ?: return SyncOutcome.NoSession
        if (opened.tokens != null) return fetch(opened)
        return when (val step = signIn(opened, online)) {
            is Step.Ready -> fetch(step.session)
            is Step.Done -> step.outcome
        }
    }

    // Sin tokens: la credencial del ingreso sin red sirve 15 minutos y si la llave sigue válida (AC-07).
    private suspend fun signIn(opened: OpenSession, online: Boolean): Step {
        val credential = opened.credential
        if (credential != null && !session.credentialExpired(opened)) {
            when (session.keyState(opened.email)) {
                KeyState.USABLE -> return login(opened, credential)
                KeyState.UNAVAILABLE -> return Step.Done(SyncOutcome.Failed)
                KeyState.LOST -> Unit
            }
        }
        session.dropCredential(opened.email)
        val dropped = session.openSession?.takeIf { it.id == opened.id } ?: return Step.Done(SyncOutcome.NoSession)
        return Step.Done(if (online) end(dropped, SessionEndReason.RELOGIN) else SyncOutcome.NoSession)
    }

    private suspend fun login(opened: OpenSession, credential: DeviceCredential): Step = withContext(NonCancellable) {
        when (val result = authApi.biometricLogin(credential)) {
            is LoginResult.Success -> adopt(opened, opened.withTokens(result.tokens))
            LoginResult.InvalidCredentials, LoginResult.InvalidFormat -> {
                val outcome = end(opened, SessionEndReason.DEVICE_REJECTED)
                session.unbind(opened.email)
                Step.Done(outcome)
            }
            else -> Step.Done(SyncOutcome.Failed)
        }
    }

    private suspend fun fetch(opened: OpenSession): SyncOutcome {
        var current = opened
        var renewed = false
        while (true) {
            when (val result = policiesApi.activePolicies(checkNotNull(current.tokens).accessToken)) {
                is PoliciesResult.Loaded -> return save(current, result.policies)
                PoliciesResult.Unavailable -> return SyncOutcome.Failed
                PoliciesResult.Unauthorized -> {
                    if (renewed) return end(current, SessionEndReason.EXPIRED)
                    when (val step = renew(current)) {
                        is Step.Ready -> current = step.session
                        is Step.Done -> return step.outcome
                    }
                    renewed = true
                }
            }
        }
    }

    // Una renovación por sincronización. Tras una renovación sin respuesta, un 401 no borra la copia (AC-06).
    private suspend fun renew(current: OpenSession): Step = withContext(NonCancellable) {
        when (val result = authApi.refresh(checkNotNull(current.tokens).refreshToken)) {
            is RefreshResult.Renewed -> adopt(current, current.withTokens(result.tokens))
            RefreshResult.Rejected ->
                Step.Done(end(current, if (current.renewalUnanswered) SessionEndReason.RELOGIN else SessionEndReason.EXPIRED))
            RefreshResult.Unanswered -> {
                session.replace(current, current.withRenewalUnanswered())
                Step.Done(SyncOutcome.Failed)
            }
            RefreshResult.Unavailable -> Step.Done(SyncOutcome.Failed)
        }
    }

    // Si la sesión cambió mientras se obtenían los tokens, nadie los va a usar: se revocan.
    private suspend fun adopt(from: OpenSession, to: OpenSession): Step {
        if (session.replace(from, to)) return Step.Ready(to)
        authApi.logout(checkNotNull(to.tokens).refreshToken)
        return Step.Done(SyncOutcome.NoSession)
    }

    // Un cierre de sesión o un cambio de cuenta durante la descarga no guarda en ninguna cuenta (CTL-09).
    private suspend fun save(current: OpenSession, policies: List<PolicyCard>): SyncOutcome {
        if (session.openSession !== current) return SyncOutcome.NoSession
        val syncedAt = clock.instant()
        if (!store.save(current.email, policies, syncedAt)) return SyncOutcome.Failed
        val copy = WalletCopy(policies.distinctBy(PolicyCard::policyNumber), syncedAt)
        return SyncOutcome.Synced(current.email, copy).also(results::tryEmit)
    }

    // Solo termina la sesión que tomó esta sincronización; un rechazo tardío no toca otra.
    private suspend fun end(current: OpenSession, reason: SessionEndReason): SyncOutcome {
        if (!session.endIf(current)) return SyncOutcome.NoSession
        if (reason != SessionEndReason.RELOGIN) store.delete(current.email)
        scheduler.cancel()
        return SyncOutcome.SessionEnded(current.email, reason).also(results::tryEmit)
    }

    private sealed interface Step {
        class Ready(val session: OpenSession) : Step
        class Done(val outcome: SyncOutcome) : Step
    }
}
