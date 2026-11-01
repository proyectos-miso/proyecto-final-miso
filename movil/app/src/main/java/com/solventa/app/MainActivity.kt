package com.solventa.app

import android.os.Bundle
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solventa.app.data.auth.AndroidBiometricAuthenticator
import com.solventa.app.domain.auth.BiometricAuthenticator
import com.solventa.app.ui.auth.AlternativeLoginScreen
import com.solventa.app.ui.auth.AuthViewModel
import com.solventa.app.ui.auth.BiometricEnrollmentDialog
import com.solventa.app.ui.auth.BiometricLoginScreen
import com.solventa.app.ui.auth.LogoutConfirmationDialog
import com.solventa.app.ui.auth.PasswordRecoveryScreen
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
                AuthViewModel(app.sessionRepository, app.authApi, app.policyStore, app.policySync, app.appScope)
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

    // Al volver de otra app (por ejemplo, de cambiar la hora) se recalcula el aviso de desactualización (HU-MOV-06).
    override fun onResume() {
        super.onResume()
        walletViewModel.refreshClock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val biometricAuthenticator = AndroidBiometricAuthenticator(this)
        val themeStore = (application as SolventaApplication).themeStore
        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(themeStore.darkTheme() ?: systemDark) }
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
                        BrandTopBar(darkTheme = darkTheme, onToggleTheme = {
                            darkTheme = !darkTheme
                            themeStore.saveDarkTheme(darkTheme)
                        })
                    },
                ) { innerPadding ->
                    SolventaMobileRoot(
                        viewModel = authViewModel,
                        walletViewModel = walletViewModel,
                        biometricAuthenticator = biometricAuthenticator,
                        modifier = Modifier.padding(innerPadding),
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
) {
    val uiState by viewModel.uiState.collectAsState()

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
            WalletScreen(
                uiState = walletState,
                fullName = uiState.fullName,
                biometricEnabled = uiState.biometricEnabled,
                onDisableBiometrics = viewModel::disableBiometrics,
                onLogout = viewModel::requestLogout,
                modifier = modifier,
                onRetry = walletViewModel::retry,
            )
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
