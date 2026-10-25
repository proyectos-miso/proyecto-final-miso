package com.solventa.app

import android.app.Application
import android.app.KeyguardManager
import com.solventa.app.data.auth.HttpAuthApi
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
import com.solventa.app.domain.policies.PoliciesApi
import com.solventa.app.domain.policies.PolicyStore
import com.solventa.app.domain.policies.PolicySync
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget

class SolventaApplication : Application(), PolicySyncHost {

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

    override val policySync: PolicySync by lazy {
        SessionPolicySync(sessionRepository, authApi, policiesApi, policyStore, WorkManagerSyncScheduler(this))
    }

    override fun onCreate() {
        super.onCreate()
        // SQLCipher escribe en logcat la ruta de la base cuando no abre; la app no registra nada de la copia (CTL-06).
        Logger.setTarget(NoopTarget())
    }
}
