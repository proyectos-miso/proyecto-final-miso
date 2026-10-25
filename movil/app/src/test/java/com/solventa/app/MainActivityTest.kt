package com.solventa.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.data.policies.FakePoliciesApi
import com.solventa.app.data.policies.InMemoryPolicyStore
import com.solventa.app.data.policies.PendingPoliciesApi
import com.solventa.app.data.policies.samplePolicy
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
import com.solventa.app.domain.auth.SessionTokens
import com.solventa.app.domain.policies.PoliciesResult
import com.solventa.app.ui.auth.AuthViewModel
import com.solventa.app.ui.wallet.WalletViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher

// Recorrido completo de las pantallas de acceso con el ViewModel real (HU-MOV-01, 02, 03 y 12).
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class SolventaMobileRootTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    private val api = object : AuthApi {
        override suspend fun requestOtp(email: String) = OtpResult.Sent("417902")
        override suspend fun login(email: String, password: String, otpCode: String): LoginResult =
            LoginResult.Success(SessionTokens("a", "r"), "María Ruiz")
        override suspend fun logout(refreshToken: String) = true
        override suspend fun refresh(refreshToken: String): RefreshResult = RefreshResult.Unavailable
        override suspend fun registerDevice(accessToken: String) = DeviceCredential("device-1", "secreto")
        override suspend fun biometricLogin(credential: DeviceCredential): LoginResult =
            LoginResult.Success(SessionTokens("a2", "r2"), "María Ruiz")
        override suspend fun revokeDevice(accessToken: String, deviceId: String) = true
        override suspend fun forgotPassword(email: String) = OtpResult.Sent("123456")
        override suspend fun resetPassword(email: String, code: String, newPassword: String) = ResetResult.Success
    }

    private val authenticator = object : BiometricAuthenticator {
        override fun checkAvailability() = BiometricAvailability.AVAILABLE
        override suspend fun authenticate(cipher: Cipher): BiometricResult = BiometricResult.Success(cipher)
    }

    private fun inDialog(text: String) = hasText(text) and hasAnyAncestor(isDialog())

    private val policyStore = InMemoryPolicyStore()
    private val wallet = WalletViewModel(FakePoliciesApi(PoliciesResult.Loaded(listOf(samplePolicy()))), policyStore)

    @Test
    fun `ingresar, activar la huella, cerrar sesion, volver con huella y desactivarla`() {
        val viewModel = AuthViewModel(SecureSessionRepository(FakeSessionCrypto(), InMemorySessionStore()), api, policyStore)
        compose.setContent { SolventaMobileRoot(viewModel, wallet, authenticator) }

        compose.onNodeWithText(s(R.string.auth_use_alternative)).performClick()
        compose.onNodeWithText(s(R.string.auth_email)).performTextInput("asegurada@example.com")
        compose.onNodeWithText(s(R.string.auth_otp_send)).performClick()
        compose.onNodeWithText(s(R.string.auth_password)).performTextInput("Solventa2026!")
        compose.onNodeWithText(s(R.string.auth_otp)).performTextInput("417902")
        compose.onNodeWithText(s(R.string.auth_submit)).performClick()

        compose.onNodeWithText(s(R.string.auth_enroll_confirm)).performClick()
        compose.onNodeWithText(s(R.string.home_title_named, "María")).assertIsDisplayed()
        compose.onAllNodesWithText("POL-2026-00001", substring = true).onFirst().assertIsDisplayed()
        compose.onNodeWithText(s(R.string.biometric_disable)).assertIsDisplayed()

        compose.onNodeWithText(s(R.string.logout_button)).performClick()
        compose.onNode(inDialog(s(R.string.logout_message_biometric))).assertIsDisplayed()
        compose.onNode(inDialog(s(R.string.logout_confirm))).performClick()

        compose.onNodeWithText(s(R.string.auth_biometric_account, "María Ruiz")).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.auth_sensor_description)).performClick()
        compose.onNodeWithText(s(R.string.home_title_named, "María")).assertIsDisplayed()

        compose.onNodeWithText(s(R.string.biometric_disable)).performClick()
        compose.onNodeWithText(s(R.string.biometric_disabled)).assertIsDisplayed()
    }

    @Test
    fun `desde el ingreso se abre la recuperacion y se vuelve`() {
        val viewModel = AuthViewModel(SecureSessionRepository(FakeSessionCrypto(), InMemorySessionStore()), api, policyStore)
        compose.setContent { SolventaMobileRoot(viewModel, wallet, authenticator) }

        compose.onNodeWithText(s(R.string.auth_use_alternative)).performClick()
        compose.onNodeWithText(s(R.string.auth_forgot_password)).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.recovery_title)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.recovery_back)).performScrollTo().performClick()

        compose.onNodeWithText(s(R.string.auth_alternative_title)).assertIsDisplayed()
    }

    @Test
    fun `ahora no entra sin activar la huella`() {
        val viewModel = AuthViewModel(SecureSessionRepository(FakeSessionCrypto(), InMemorySessionStore()), api, policyStore)
        compose.setContent { SolventaMobileRoot(viewModel, wallet, authenticator) }

        compose.onNodeWithText(s(R.string.auth_use_alternative)).performClick()
        compose.onNodeWithText(s(R.string.auth_email)).performTextInput("asegurada@example.com")
        compose.onNodeWithText(s(R.string.auth_password)).performTextInput("Solventa2026!")
        compose.onNodeWithText(s(R.string.auth_otp)).performTextInput("417902")
        compose.onNodeWithText(s(R.string.auth_submit)).performClick()
        compose.onNodeWithText(s(R.string.auth_enroll_skip)).performClick()

        compose.onNodeWithText(s(R.string.biometric_disabled)).assertIsDisplayed()
    }

    // HU-MOV-05 TC-12: confirmar el cierre de sesión con la descarga pendiente no deja copia (AC-12).
    @Test
    fun `cerrar sesion con la descarga pendiente no deja copia de la cuenta`() {
        val pending = PendingPoliciesApi()
        val store = InMemoryPolicyStore()
        val viewModel = AuthViewModel(SecureSessionRepository(FakeSessionCrypto(), InMemorySessionStore()), api, store)
        compose.setContent { SolventaMobileRoot(viewModel, WalletViewModel(pending, store), authenticator) }

        compose.onNodeWithText(s(R.string.auth_use_alternative)).performClick()
        compose.onNodeWithText(s(R.string.auth_email)).performTextInput("asegurada@example.com")
        compose.onNodeWithText(s(R.string.auth_password)).performTextInput("Solventa2026!")
        compose.onNodeWithText(s(R.string.auth_otp)).performTextInput("417902")
        compose.onNodeWithText(s(R.string.auth_submit)).performClick()
        compose.onNodeWithText(s(R.string.auth_enroll_skip)).performClick()

        compose.onNodeWithText(s(R.string.logout_button)).performClick()
        compose.onNode(inDialog(s(R.string.logout_confirm))).performClick()
        pending.response.complete(PoliciesResult.Loaded(listOf(samplePolicy())))
        compose.waitForIdle()

        assertTrue(store.copies.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `la app abre en la pantalla de ingreso con huella`() {
        compose.onNodeWithText(compose.activity.getString(R.string.auth_biometric_title)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.auth_device_protected)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.auth_use_alternative)).assertIsDisplayed()
    }
}
