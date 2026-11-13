package com.solventa.app

import android.app.DownloadManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.data.claims.InMemoryClaimStore
import com.solventa.app.data.policies.FakePoliciesApi
import com.solventa.app.data.policies.FakePolicyActions
import com.solventa.app.data.policies.FakePolicySync
import com.solventa.app.data.policies.FakeScheduler
import com.solventa.app.data.policies.FakeSyncAuthApi
import com.solventa.app.data.policies.InMemoryPolicyStore
import com.solventa.app.data.policies.SessionPolicySync
import com.solventa.app.data.policies.sampleClaim
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
import com.solventa.app.domain.claims.ClaimsCopy
import com.solventa.app.domain.policies.CaratulaResult
import com.solventa.app.domain.policies.PoliciesResult
import com.solventa.app.domain.policies.SyncOutcome
import com.solventa.app.ui.auth.AuthViewModel
import com.solventa.app.ui.claims.ClaimsViewModel
import com.solventa.app.ui.wallet.WalletViewModel
import com.solventa.app.ui.wallet.formatDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.Cipher

// Mis siniestros, el aviso tocado y la carátula desde la raíz de la app (HU-MOV-11: AC-01, AC-02, AC-08, AC-10, AC-11).
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class SolventaMobileRootClaimsTest {

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

    private val session = SecureSessionRepository(FakeSessionCrypto(), InMemorySessionStore()) { 0L }
    private val policyStore = InMemoryPolicyStore()
    private val sync = SessionPolicySync(session, FakeSyncAuthApi(), FakePoliciesApi(PoliciesResult.Loaded(listOf(samplePolicy()))), policyStore, FakeScheduler())
    private val actions = FakePolicyActions()
    private val wallet = WalletViewModel(sync, policyStore, Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC), actions)
    private val claimsSync = FakePolicySync().apply { next = { SyncOutcome.Failed } }
    private val claim = sampleClaim()
    private val auth = AuthViewModel(session, api, policyStore, sync, CoroutineScope(Dispatchers.Main), InMemoryClaimStore())

    private var claimsOpen by mutableStateOf(false)
    private var selectedClaim by mutableStateOf<String?>(null)
    private var pendingClaim by mutableStateOf<String?>(null)
    private var loggedOut = 0

    private fun start() {
        policyStore.claims[EMAIL] = ClaimsCopy(listOf(claim), Instant.parse("2026-11-08T09:00:00Z"))
        val claims = ClaimsViewModel(claimsSync, policyStore)
        compose.setContent {
            SolventaMobileRoot(
                viewModel = auth,
                walletViewModel = wallet,
                biometricAuthenticator = authenticator,
                claimsViewModel = claims,
                claimsOpen = claimsOpen,
                onClaimsOpenChange = { claimsOpen = it },
                selectedClaim = selectedClaim,
                onSelectedClaimChange = { selectedClaim = it },
                pendingClaim = pendingClaim,
                onPendingClaimConsumed = { pendingClaim = null },
                walletActions = true,
                onLoggedOut = { loggedOut++ },
            )
        }
        compose.onNodeWithText(s(R.string.auth_use_alternative)).performClick()
        compose.onNodeWithText(s(R.string.auth_email)).performTextInput(EMAIL)
        compose.onNodeWithText(s(R.string.auth_password)).performTextInput("Solventa2026!")
        compose.onNodeWithText(s(R.string.auth_otp)).performTextInput("417902")
        compose.onNodeWithText(s(R.string.auth_submit)).performClick()
        compose.onNodeWithText(s(R.string.auth_enroll_skip)).performClick()
    }

    private val row get() = s(R.string.claims_row_description, claim.claimNumber, claim.policyNumber, formatDate(claim.reportedAt), claim.statusLabel)

    @Test
    fun `AC-01 y AC-02 desde la billetera se abre la lista, el detalle y se vuelve`() {
        start()

        compose.onNodeWithText(s(R.string.claims_open_button)).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.claims_offline)).assertIsDisplayed()
        compose.onNodeWithContentDescription(row).performClick()

        assertEquals(claim.claimNumber, selectedClaim)
        compose.onNodeWithText(s(R.string.claims_detail_subtitle, claim.policyNumber)).assertIsDisplayed()

        compose.onNodeWithContentDescription(s(R.string.claim_back)).performClick()
        compose.onNodeWithContentDescription(row).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.claim_back)).performClick()

        assertEquals(false, claimsOpen)
        compose.onNodeWithText(s(R.string.home_title_named, "María")).assertIsDisplayed()
    }

    @Test
    fun `AC-08 un aviso tocado abre el detalle de su siniestro`() {
        start()

        pendingClaim = claim.claimNumber
        compose.waitForIdle()

        assertTrue(claimsOpen)
        assertNull(pendingClaim)
        compose.onNodeWithText(s(R.string.claims_detail_subtitle, claim.policyNumber)).assertIsDisplayed()
    }

    @Test
    fun `AC-10 la caratula lista se descarga en Descargas`() {
        actions.caratula = CaratulaResult.Ready("https://s3.example/caratula.pdf")
        start()

        compose.onNodeWithText(s(R.string.wallet_download_caratula)).performScrollTo().performClick()
        compose.waitForIdle()

        val downloads = shadowOf(context.getSystemService(DownloadManager::class.java))
        assertEquals(1, downloads.requestCount)
        assertNull(wallet.uiState.value.caratula)
    }

    @Test
    fun `AC-11 cerrar sesion borra los avisos y cierra Mis siniestros`() {
        start()
        claimsOpen = true
        compose.waitForIdle()
        claimsOpen = false
        compose.waitForIdle()

        compose.onNodeWithText(s(R.string.logout_button)).performScrollTo().performClick()
        compose.onNode(hasText(s(R.string.logout_confirm)) and hasAnyAncestor(isDialog())).performClick()

        assertEquals(1, loggedOut)
        assertEquals(false, claimsOpen)
        assertNull(selectedClaim)
    }

    private companion object {
        const val EMAIL = "asegurada@example.com"
    }
}
