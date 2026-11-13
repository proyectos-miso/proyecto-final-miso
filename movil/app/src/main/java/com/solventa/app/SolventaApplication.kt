package com.solventa.app

import android.app.Application
import android.app.KeyguardManager
import com.solventa.app.data.auth.HttpAuthApi
import com.solventa.app.data.claims.EncryptedClaimStore
import com.solventa.app.data.claims.HttpClaimsApi
import com.solventa.app.data.notifications.AndroidNotificationPoster
import com.solventa.app.data.policies.HttpPolicyActionsApi
import com.solventa.app.data.policies.SessionPolicyActions
import com.solventa.app.domain.policies.NotificationPoster
import com.solventa.app.domain.policies.PolicyActions
import kotlinx.coroutines.launch
import com.solventa.app.data.policies.EncryptedPolicyStore
import com.solventa.app.data.policies.HttpPoliciesApi
import com.solventa.app.data.policies.KeystoreWalletKeys
import com.solventa.app.data.policies.PassphraseVault
import com.solventa.app.data.policies.PolicySyncHost
import com.solventa.app.data.policies.SessionPolicySync
import com.solventa.app.data.policies.WorkManagerSyncScheduler
import com.solventa.app.data.session.KeystoreSessionCrypto
import com.solventa.app.data.settings.ThemePreferenceStore
import com.solventa.app.data.session.SecureSessionRepository
import com.solventa.app.data.session.SharedPreferencesSessionStore
import com.solventa.app.domain.auth.AuthApi
import com.solventa.app.domain.claims.ClaimReportStore
import com.solventa.app.domain.policies.PoliciesApi
import com.solventa.app.domain.policies.PolicyStore
import com.solventa.app.domain.policies.PolicySync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget

class SolventaApplication : Application(), PolicySyncHost {

    // Trabajo que debe terminar aunque se cierre la pantalla, como borrar la copia al cerrar sesión (HU-MOV-07).
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionRepository: SecureSessionRepository by lazy {
        SecureSessionRepository(KeystoreSessionCrypto(), SharedPreferencesSessionStore(this))
    }

    val authApi: AuthApi by lazy { HttpAuthApi(BuildConfig.API_BASE_URL) }

    val themeStore: ThemePreferenceStore by lazy { ThemePreferenceStore(this) }

    val policiesApi: PoliciesApi by lazy { HttpPoliciesApi(BuildConfig.API_BASE_URL) }

    val policyStore: PolicyStore by lazy {
        val keyguard = getSystemService(KeyguardManager::class.java)
        EncryptedPolicyStore(this, PassphraseVault(this, KeystoreWalletKeys(isDeviceLocked = keyguard::isDeviceLocked)))
    }

    // Reporte de siniestro con su propia base, frase y llave por cuenta, aparte de la billetera (HU-MOV-08, ADR 0024).
    val claimStore: ClaimReportStore by lazy {
        val keyguard = getSystemService(KeyguardManager::class.java)
        EncryptedClaimStore(this, PassphraseVault.forClaims(this, KeystoreWalletKeys(isDeviceLocked = keyguard::isDeviceLocked)))
    }

    val notificationPoster: NotificationPoster by lazy { AndroidNotificationPoster(this) }

    private val sessionSync: SessionPolicySync by lazy {
        SessionPolicySync(
            sessionRepository, authApi, policiesApi, policyStore, WorkManagerSyncScheduler(this),
            onDeviceRejected = { claimStore.delete(it) },
            claimsApi = HttpClaimsApi(BuildConfig.API_BASE_URL),
            notices = notificationPoster,
        )
    }

    override val policySync: PolicySync get() = sessionSync

    val policyActions: PolicyActions by lazy { SessionPolicyActions(sessionSync, HttpPolicyActionsApi(BuildConfig.API_BASE_URL)) }

    /** Registra el token de avisos para la cuenta con sesión; sin sesión no hace nada (HU-MOV-11). */
    fun registerPushToken(token: String) {
        if (sessionRepository.openSession == null) return
        appScope.launch { runCatching { policyActions.registerPushToken(token) } }
    }

    override fun onCreate() {
        super.onCreate()
        // SQLCipher escribe en logcat la ruta de la base cuando no abre; la app no registra nada de la copia (CTL-06).
        Logger.setTarget(NoopTarget())
        AndroidNotificationPoster.ensureChannel(this)
    }
}
