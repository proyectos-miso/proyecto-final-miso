package com.solventa.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.data.auth.AndroidBiometricAuthenticator
import com.solventa.app.data.claims.AndroidCaptureLocator
import com.solventa.app.data.claims.AndroidTrustedClock
import com.solventa.app.domain.auth.BiometricAuthenticator
import com.solventa.app.ui.auth.AlternativeLoginScreen
import com.solventa.app.ui.auth.AuthViewModel
import com.solventa.app.ui.auth.BiometricEnrollmentDialog
import com.solventa.app.ui.auth.BiometricLoginScreen
import com.solventa.app.ui.auth.LogoutConfirmationDialog
import com.solventa.app.ui.auth.PasswordRecoveryScreen
import com.solventa.app.ui.claims.CameraXEvidenceCamera
import com.solventa.app.ui.claims.ClaimFlow
import com.solventa.app.ui.claims.ClaimReportViewModel
import com.solventa.app.ui.claims.EvidenceCamera
import com.solventa.app.ui.claims.ReportEntry
import com.solventa.app.ui.theme.BrandTopBar
import com.solventa.app.ui.theme.SolventaTheme
import com.solventa.app.ui.wallet.WalletScreen
import com.solventa.app.ui.wallet.WalletViewModel

// BiometricPrompt requiere una FragmentActivity para mostrar el diálogo del sistema.
class MainActivity : FragmentActivity() {

    private val authViewModel: AuthViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as SolventaApplication
                AuthViewModel(app.sessionRepository, app.authApi, app.policyStore, app.policySync, app.appScope, app.claimStore)
            }
        }
    }

    private val walletViewModel: WalletViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as SolventaApplication
                WalletViewModel(app.policySync, app.policyStore)
            }
        }
    }

    private val claimViewModel: ClaimReportViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as SolventaApplication
                ClaimReportViewModel(app.claimStore, AndroidCaptureLocator(app), AndroidTrustedClock())
            }
        }
    }

    // Al volver de otra app (por ejemplo, de cambiar la hora) se recalcula el aviso de desactualización (HU-MOV-06).
    override fun onResume() {
        super.onResume()
        walletViewModel.refreshClock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val biometricAuthenticator = AndroidBiometricAuthenticator(this)
        val themeStore = (application as SolventaApplication).themeStore
        val camera = CameraXEvidenceCamera(applicationContext)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(themeStore.darkTheme() ?: systemDark) }
            // Solo si el reporte está abierto; no guarda nada del reporte (HU-MOV-08, ADR 0015).
            var claimOpen by rememberSaveable { mutableStateOf(false) }
            // Barras del sistema transparentes y con íconos que contrastan con el tema elegido.
            DisposableEffect(darkTheme) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (darkTheme) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            SolventaTheme(darkTheme = darkTheme) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        // El reporte de siniestro tiene su propia barra con «atrás» y el paso.
                        if (!claimOpen) {
                            BrandTopBar(darkTheme = darkTheme, onToggleTheme = {
                                darkTheme = !darkTheme
                                themeStore.saveDarkTheme(darkTheme)
                            })
                        }
                    },
                ) { innerPadding ->
                    SolventaMobileRoot(
                        viewModel = authViewModel,
                        walletViewModel = walletViewModel,
                        biometricAuthenticator = biometricAuthenticator,
                        modifier = Modifier.padding(innerPadding),
                        claimViewModel = claimViewModel,
                        camera = camera,
                        claimOpen = claimOpen,
                        onClaimOpenChange = { claimOpen = it },
                        secureScreens = BuildConfig.SECURE_SCREENS,
                    )
                }
            }
        }
    }
}

@Composable
fun SolventaMobileRoot(
    viewModel: AuthViewModel,
    walletViewModel: WalletViewModel,
    biometricAuthenticator: BiometricAuthenticator,
    modifier: Modifier = Modifier,
    claimViewModel: ClaimReportViewModel? = null,
    camera: EvidenceCamera? = null,
    claimOpen: Boolean = false,
    onClaimOpenChange: (Boolean) -> Unit = {},
    secureScreens: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsState()

    // Sin sesión no hay reporte abierto (HU-MOV-08).
    LaunchedEffect(uiState.isAuthenticated) { if (!uiState.isAuthenticated) onClaimOpenChange(false) }

    if (uiState.offerBiometricEnrollment) {
        BiometricEnrollmentDialog(
            onEnable = { viewModel.enableBiometricUnlock(biometricAuthenticator) },
            onSkip = viewModel::skipBiometricEnrollment,
        )
    }

    if (uiState.showLogoutConfirmation) {
        LogoutConfirmationDialog(
            biometricEnabled = uiState.biometricEnabled,
            // La billetera se cierra primero para que ninguna descarga guarde la copia después del borrado (AC-12).
            onConfirm = {
                walletViewModel.close()
                claimViewModel?.close()
                onClaimOpenChange(false)
                viewModel.confirmLogout()
            },
            onDismiss = viewModel::cancelLogout,
        )
    }

    when {
        uiState.isAuthenticated -> {
            val walletState by walletViewModel.uiState.collectAsState()
            LaunchedEffect(uiState.currentAccountEmail, uiState.offline) {
                uiState.currentAccountEmail?.let(walletViewModel::open)
            }
            LaunchedEffect(walletState.sessionEnded) {
                walletState.sessionEnded?.let(viewModel::onSessionEnded)
            }
            val email = uiState.currentAccountEmail
            val hasActivePolicy = walletState.policies.any { !it.expired }
            val entry = claimViewModel?.entry?.collectAsState()?.value ?: ReportEntry.HIDDEN
            LaunchedEffect(email, hasActivePolicy, claimOpen) {
                if (email != null && !claimOpen) claimViewModel?.refreshEntry(email, hasActivePolicy)
            }
            if (claimOpen && claimViewModel != null && camera != null && email != null) {
                LaunchedEffect(email, walletState.policies) { claimViewModel.open(email, walletState.policies.map { it.policy }) }
                SecureWindow(secureScreens)
                ClaimFlow(claimViewModel, camera, onClosed = { onClaimOpenChange(false) }, modifier = modifier)
            } else {
                WalletScreen(
                    uiState = walletState,
                    fullName = uiState.fullName,
                    biometricEnabled = uiState.biometricEnabled,
                    onDisableBiometrics = viewModel::disableBiometrics,
                    onLogout = viewModel::requestLogout,
                    modifier = modifier,
                    onRetry = walletViewModel::retry,
                    reportEntry = if (claimViewModel != null && camera != null) entry else ReportEntry.HIDDEN,
                    onReport = { onClaimOpenChange(true) },
                )
            }
        }
        uiState.showPasswordRecovery -> PasswordRecoveryScreen(
            uiState = uiState,
            onRequestCode = viewModel::requestRecoveryCode,
            onSubmit = viewModel::resetPassword,
            onClose = viewModel::closePasswordRecovery,
            modifier = modifier,
        )
        uiState.showAlternativeAccess -> AlternativeLoginScreen(
            uiState = uiState,
            onRequestOtp = viewModel::requestOtp,
            onSubmit = { email, password, otpCode ->
                viewModel.authenticateWithPassword(email, password, otpCode, biometricAuthenticator)
            },
            onBack = viewModel::onBackToBiometrics,
            onForgotPassword = viewModel::openPasswordRecovery,
            modifier = modifier,
        )
        else -> BiometricLoginScreen(
            uiState = uiState,
            onAuthenticate = { viewModel.authenticateWithBiometrics(biometricAuthenticator) },
            onUseAlternative = viewModel::onAlternativeAccessSelected,
            onSelectAccount = viewModel::selectAccount,
            modifier = modifier,
        )
    }
}

/**
 * Ventana segura mientras se muestra el reporte: sin capturas ni contenido en las apps recientes (HU-MOV-08,
 * AC-16). Solo en release, para poder grabar el video de la entrega en debug (DEC-03).
 */
@Composable
internal fun SecureWindow(enabled: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, enabled) {
        if (enabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (enabled) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
