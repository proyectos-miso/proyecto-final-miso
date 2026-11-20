package com.solventa.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.data.claims.InMemoryClaimStore
import com.solventa.app.data.policies.FakePoliciesApi
import com.solventa.app.data.policies.FakeScheduler
import com.solventa.app.data.policies.FakeSyncAuthApi
import com.solventa.app.data.policies.InMemoryPolicyStore
import com.solventa.app.data.policies.SessionPolicySync
import com.solventa.app.data.session.FakeSessionCrypto
import com.solventa.app.data.session.InMemorySessionStore
import com.solventa.app.data.session.SecureSessionRepository
import com.solventa.app.domain.auth.AuthApi
import com.solventa.app.domain.auth.BiometricAuthenticator
import com.solventa.app.domain.auth.BiometricAvailability
import com.solventa.app.domain.auth.BiometricResult
import com.solventa.app.domain.auth.DeviceCredential
import com.solventa.app.domain.auth.LoginResult
import com.solventa.app.domain.auth.OtpResult
import com.solventa.app.domain.auth.RefreshResult
import com.solventa.app.domain.auth.ResetResult
import com.solventa.app.domain.policies.PoliciesResult
import com.solventa.app.ui.auth.AuthViewModel
import com.solventa.app.ui.auth.RegistrationViewModel
import com.solventa.app.ui.claims.EvidenceCamera
import com.solventa.app.ui.wallet.WalletViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher

// «Crear cuenta» depende de la bandera del build: apagada en release, donde el backend no registra.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class RegistrationInAppFlagTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val api = object : AuthApi {
        override suspend fun requestOtp(email: String) = OtpResult.Failed
        override suspend fun login(email: String, password: String, otpCode: String) = LoginResult.NetworkError
        override suspend fun logout(refreshToken: String, keepReporting: Boolean) = false
        override suspend fun refresh(refreshToken: String) = RefreshResult.Unavailable
        override suspend fun registerDevice(accessToken: String): DeviceCredential? = null
        override suspend fun biometricLogin(credential: DeviceCredential) = LoginResult.NetworkError
        override suspend fun revokeDevice(accessToken: String, deviceId: String) = false
        override suspend fun forgotPassword(email: String) = OtpResult.Failed
        override suspend fun resetPassword(email: String, code: String, newPassword: String) = ResetResult.NetworkError
    }

    private val authenticator = object : BiometricAuthenticator {
        override fun checkAvailability() = BiometricAvailability.AVAILABLE
        override suspend fun authenticate(cipher: Cipher): BiometricResult = BiometricResult.Success(cipher)
    }

    private val camera = object : EvidenceCamera {
        @Composable
        override fun Preview(modifier: Modifier, onUnavailable: () -> Unit) = Box(modifier)

        override suspend fun capture(): ByteArray? = null
    }

    private fun show(registrationInApp: Boolean) {
        val session = SecureSessionRepository(FakeSessionCrypto(), InMemorySessionStore()) { 0L }
        val store = InMemoryPolicyStore()
        val sync = SessionPolicySync(session, FakeSyncAuthApi(), FakePoliciesApi(PoliciesResult.Unavailable), store, FakeScheduler())
        val viewModel = AuthViewModel(session, api, store, sync, CoroutineScope(Dispatchers.Main), InMemoryClaimStore())
        compose.setContent {
            SolventaMobileRoot(
                viewModel,
                WalletViewModel(sync, store),
                authenticator,
                camera = camera,
                registrationViewModel = RegistrationViewModel(api),
                selfieCamera = camera,
                registrationInApp = registrationInApp,
            )
        }
    }

    @Test
    fun `con el registro en la app apagado no aparece crear cuenta`() {
        show(registrationInApp = false)

        compose.onNodeWithText(context.getString(R.string.auth_use_alternative)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.register_open)).assertDoesNotExist()
    }

    @Test
    fun `con el registro en la app encendido aparece crear cuenta`() {
        show(registrationInApp = true)

        compose.onNodeWithText(context.getString(R.string.register_open)).assertIsDisplayed()
    }
}
