package com.solventa.app

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Environment
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
import com.solventa.app.data.assistance.AndroidApproximateLocator
import com.solventa.app.data.assistance.AndroidProviderContact
import com.solventa.app.data.auth.AndroidBiometricAuthenticator
import com.solventa.app.data.claims.AndroidCaptureLocator
import com.solventa.app.data.claims.AndroidTrustedClock
import com.solventa.app.data.notifications.AndroidNotificationPoster
import com.solventa.app.data.notifications.SolventaMessagingService
import com.solventa.app.domain.auth.BiometricAuthenticator
import com.solventa.app.ui.assistance.AssistanceActions
import com.solventa.app.ui.assistance.AssistanceRequestScreen
import com.solventa.app.ui.assistance.AssistanceScreen
import com.solventa.app.ui.assistance.AssistanceViewModel
import com.solventa.app.ui.assistance.RequestFormActions
import com.solventa.app.ui.claims.ClaimDetailScreen
import com.solventa.app.ui.claims.ClaimDocumentsFlow
import com.solventa.app.ui.claims.ClaimDocumentsViewModel
import com.solventa.app.ui.claims.DocumentScanner
import com.solventa.app.ui.claims.MlKitDocumentScanner
import com.solventa.app.ui.claims.ClaimsListScreen
import com.solventa.app.ui.claims.ClaimsViewModel
import com.solventa.app.ui.auth.AlternativeLoginScreen
import com.solventa.app.ui.auth.AuthViewModel
import com.solventa.app.ui.auth.BiometricEnrollmentDialog
import com.solventa.app.ui.auth.BiometricLoginScreen
import com.solventa.app.ui.auth.LogoutConfirmationDialog
import com.solventa.app.ui.auth.PasswordRecoveryScreen
import com.solventa.app.ui.auth.RegistrationFlow
import com.solventa.app.ui.auth.RegistrationViewModel
import com.solventa.app.ui.claims.CameraLens
import com.solventa.app.ui.claims.CameraXEvidenceCamera
import com.solventa.app.ui.claims.ClaimFlow
import com.solventa.app.ui.claims.ClaimReportViewModel
import com.solventa.app.ui.claims.EvidenceCamera
import com.solventa.app.ui.claims.ReportEntry
import com.solventa.app.ui.settings.NotificationPreferencesScreen
import com.solventa.app.ui.settings.NotificationPreferencesViewModel
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
                AuthViewModel(
                    app.sessionRepository, app.authApi, app.policyStore, app.policySync, app.appScope, app.claimStore, app.reportTokens,
                    app.notificationPreferences,
                )
            }
        }
    }

    private val registrationViewModel: RegistrationViewModel by viewModels {
        viewModelFactory {
            initializer { RegistrationViewModel((application as SolventaApplication).authApi) }
        }
    }

    private val preferencesViewModel: NotificationPreferencesViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as SolventaApplication
                NotificationPreferencesViewModel(app.policyActions, app.notificationPreferences)
            }
        }
    }

    private val walletViewModel: WalletViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as SolventaApplication
                WalletViewModel(app.policySync, app.policyStore, actions = app.policyActions)
            }
        }
    }

    private val claimViewModel: ClaimReportViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as SolventaApplication
                ClaimReportViewModel(app.claimStore, AndroidCaptureLocator(app), AndroidTrustedClock(), sendScheduler = app.claimSendScheduler)
            }
        }
    }

    private val claimsViewModel: ClaimsViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as SolventaApplication
                ClaimsViewModel(app.policySync, app.policyStore, app.claimStore)
            }
        }
    }

    private val documentsViewModel: ClaimDocumentsViewModel by viewModels {
        viewModelFactory {
            initializer { ClaimDocumentsViewModel((application as SolventaApplication).claimDocuments) }
        }
    }

    private val assistanceViewModel: AssistanceViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as SolventaApplication
                AssistanceViewModel(app.assistance, app.providersCopy, AndroidApproximateLocator(app), AndroidProviderContact(app))
            }
        }
    }

    // Siniestro que pidió abrir un aviso tocado (HU-MOV-11, AC-08).
    private var pendingClaim by mutableStateOf<String?>(null)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(AndroidNotificationPoster.EXTRA_CLAIM_NUMBER)?.let { pendingClaim = it }
    }

    // Se pide al abrir la billetera; si se niega, los cambios se ven al abrir la app (UC-02).
    private fun requestNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Al volver de otra app (por ejemplo, de cambiar la hora) se recalcula el aviso de desactualización (HU-MOV-06).
    override fun onResume() {
        super.onResume()
        walletViewModel.refreshClock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingClaim = intent?.getStringExtra(AndroidNotificationPoster.EXTRA_CLAIM_NUMBER)
        val app = application as SolventaApplication
        val biometricAuthenticator = AndroidBiometricAuthenticator(this)
        val themeStore = (application as SolventaApplication).themeStore
        val camera = CameraXEvidenceCamera(applicationContext)
        val selfieCamera = CameraXEvidenceCamera(applicationContext, lens = CameraLens.FRONT)
        val documentScanner = MlKitDocumentScanner(applicationContext)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(themeStore.darkTheme() ?: systemDark) }
            // Solo si el reporte está abierto; no guarda nada del reporte (HU-MOV-08, ADR 0015).
            var claimOpen by rememberSaveable { mutableStateOf(false) }
            // Mis siniestros y el siniestro abierto: solo el número, que ya está en la copia cifrada (HU-MOV-11).
            var claimsOpen by rememberSaveable { mutableStateOf(false) }
            var selectedClaim by rememberSaveable { mutableStateOf<String?>(null) }
            // «Agregar documentos» del siniestro abierto: solo la marca; las páginas no se guardan (mejora FE-MOV-05).
            var documentsOpen by rememberSaveable { mutableStateOf(false) }
            // Preferencias de avisos abiertas (FE-MOV-04).
            var noticesOpen by rememberSaveable { mutableStateOf(false) }
            // Prestadores y asistencia: solo la marca; la ubicación y la lista no se guardan en el estado (mejora FE-MOV-07).
            var assistanceOpen by rememberSaveable { mutableStateOf(false) }
            val authState = authViewModel.uiState.collectAsState().value
            val authenticated = authState.isAuthenticated
            LaunchedEffect(authenticated) {
                if (authenticated) {
                    requestNotificationPermission()
                    SolventaMessagingService.registerCurrentToken(app, app::registerPushToken)
                }
            }
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
                        if (!claimOpen && !claimsOpen && !noticesOpen && !assistanceOpen && !(authState.showRegistration && !authenticated)) {
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
                        claimsViewModel = claimsViewModel,
                        claimsOpen = claimsOpen,
                        onClaimsOpenChange = { claimsOpen = it },
                        selectedClaim = selectedClaim,
                        onSelectedClaimChange = {
                            selectedClaim = it
                            documentsOpen = false
                        },
                        pendingClaim = pendingClaim,
                        onPendingClaimConsumed = { pendingClaim = null },
                        walletActions = true,
                        onLoggedOut = { AndroidNotificationPoster.clear(app) },
                        preferencesViewModel = preferencesViewModel,
                        noticesOpen = noticesOpen,
                        onNoticesOpenChange = { noticesOpen = it },
                        registrationViewModel = registrationViewModel,
                        selfieCamera = selfieCamera,
                        documentsViewModel = documentsViewModel,
                        documentScanner = documentScanner,
                        documentsOpen = documentsOpen,
                        onDocumentsOpenChange = { documentsOpen = it },
                        assistanceViewModel = assistanceViewModel,
                        assistanceOpen = assistanceOpen,
                        onAssistanceOpenChange = { assistanceOpen = it },
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
    claimsViewModel: ClaimsViewModel? = null,
    claimsOpen: Boolean = false,
    onClaimsOpenChange: (Boolean) -> Unit = {},
    selectedClaim: String? = null,
    onSelectedClaimChange: (String?) -> Unit = {},
    pendingClaim: String? = null,
    onPendingClaimConsumed: () -> Unit = {},
    walletActions: Boolean = false,
    onLoggedOut: () -> Unit = {},
    preferencesViewModel: NotificationPreferencesViewModel? = null,
    noticesOpen: Boolean = false,
    onNoticesOpenChange: (Boolean) -> Unit = {},
    registrationViewModel: RegistrationViewModel? = null,
    selfieCamera: EvidenceCamera? = null,
    documentsViewModel: ClaimDocumentsViewModel? = null,
    documentScanner: DocumentScanner? = null,
    documentsOpen: Boolean = false,
    onDocumentsOpenChange: (Boolean) -> Unit = {},
    assistanceViewModel: AssistanceViewModel? = null,
    assistanceOpen: Boolean = false,
    onAssistanceOpenChange: (Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val openRegistration: (() -> Unit)? = if (registrationViewModel != null && camera != null && selfieCamera != null) {
        {
            registrationViewModel.open()
            viewModel.openRegistration()
        }
    } else {
        null
    }

    // Sin sesión no hay reporte, siniestros ni preferencias abiertos (HU-MOV-08, HU-MOV-11, FE-MOV-04).
    LaunchedEffect(uiState.isAuthenticated) {
        if (!uiState.isAuthenticated) {
            onClaimOpenChange(false)
            onClaimsOpenChange(false)
            onSelectedClaimChange(null)
            onNoticesOpenChange(false)
            onDocumentsOpenChange(false)
            onAssistanceOpenChange(false)
            claimsViewModel?.close()
            preferencesViewModel?.close()
            documentsViewModel?.close()
            assistanceViewModel?.close()
        }
    }
    LaunchedEffect(uiState.isAuthenticated, pendingClaim) {
        if (uiState.isAuthenticated && pendingClaim != null && claimsViewModel != null) {
            onClaimOpenChange(false)
            onClaimsOpenChange(true)
            onSelectedClaimChange(pendingClaim)
            onPendingClaimConsumed()
        }
    }

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
                claimsViewModel?.close()
                preferencesViewModel?.close()
                documentsViewModel?.close()
                assistanceViewModel?.close()
                onAssistanceOpenChange(false)
                onDocumentsOpenChange(false)
                onClaimOpenChange(false)
                onClaimsOpenChange(false)
                onSelectedClaimChange(null)
                onNoticesOpenChange(false)
                onLoggedOut()
                viewModel.confirmLogout()
            },
            onDismiss = viewModel::cancelLogout,
        )
    }

    when {
        uiState.isAuthenticated -> {
            val walletState by walletViewModel.uiState.collectAsState()
            val receipt by walletViewModel.receipt.collectAsState()
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
            // La carátula se descarga en Descargas; el asegurado decide si la abre (HU-MOV-11, AC-10).
            LaunchedEffect(walletState.caratula) {
                val caratula = walletState.caratula ?: return@LaunchedEffect
                walletViewModel.consumeCaratula()
                val request = DownloadManager.Request(Uri.parse(caratula.url))
                    .setTitle(context.getString(R.string.wallet_caratula_title, caratula.policyNumber))
                    .setMimeType("application/pdf")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Caratula-${caratula.policyNumber}.pdf")
                context.getSystemService(DownloadManager::class.java).enqueue(request)
                Toast.makeText(context, R.string.wallet_caratula_downloading, Toast.LENGTH_LONG).show()
            }
            if (claimOpen && claimViewModel != null && camera != null && email != null) {
                LaunchedEffect(email, walletState.policies) { claimViewModel.open(email, walletState.policies.map { it.policy }) }
                SecureWindow(secureScreens)
                ClaimFlow(claimViewModel, camera, onClosed = { onClaimOpenChange(false) }, modifier = modifier, receipt = receipt)
            } else if (assistanceOpen && assistanceViewModel != null && email != null) {
                val activePolicies = walletState.policies.filter { !it.expired }.map { it.policy.policyNumber }
                LaunchedEffect(email, activePolicies) { assistanceViewModel.open(email, activePolicies) }
                val assistanceState by assistanceViewModel.uiState.collectAsState()
                // Basta la ubicación aproximada; si el asegurado la niega, elige su ciudad.
                val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    assistanceViewModel.onLocationPermission(granted)
                }
                if (assistanceState.form != null) {
                    AssistanceRequestScreen(
                        state = assistanceState,
                        actions = RequestFormActions(
                            onClose = assistanceViewModel::closeForm,
                            onSelectPolicy = assistanceViewModel::selectPolicy,
                            onSelectService = assistanceViewModel::selectFormService,
                            onDescribe = assistanceViewModel::describe,
                            onSubmit = assistanceViewModel::submit,
                            onCall = assistanceViewModel::call,
                        ),
                        modifier = modifier,
                    )
                } else {
                    AssistanceScreen(
                        state = assistanceState,
                        actions = AssistanceActions(
                            onBack = {
                                assistanceViewModel.close()
                                onAssistanceOpenChange(false)
                            },
                            onUseLocation = {
                                if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    assistanceViewModel.useLocation()
                                } else {
                                    locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            },
                            onShowCities = assistanceViewModel::showCities,
                            onSelectCity = assistanceViewModel::selectCity,
                            onSelectService = assistanceViewModel::selectService,
                            onRetry = assistanceViewModel::retry,
                            onCall = assistanceViewModel::call,
                            onShowOnMap = assistanceViewModel::showOnMap,
                            onOpenForm = assistanceViewModel::openForm,
                        ),
                        modifier = modifier,
                    )
                }
            } else if (noticesOpen && preferencesViewModel != null && email != null) {
                LaunchedEffect(email) { preferencesViewModel.open(email) }
                val preferencesState by preferencesViewModel.uiState.collectAsState()
                NotificationPreferencesScreen(
                    state = preferencesState,
                    onBack = { onNoticesOpenChange(false) },
                    onToggle = preferencesViewModel::toggle,
                    onRetry = preferencesViewModel::retry,
                    modifier = modifier,
                    onAskRevocation = preferencesViewModel::askRevocation,
                    onConfirmRevocation = preferencesViewModel::revokeBiometricConsent,
                    onCancelRevocation = preferencesViewModel::cancelRevocation,
                )
            } else if (claimsOpen && claimsViewModel != null && email != null) {
                LaunchedEffect(email) { claimsViewModel.open(email) }
                val claimsState by claimsViewModel.uiState.collectAsState()
                val documentsReady = documentsViewModel != null && documentScanner != null && camera != null
                if (selectedClaim != null && documentsOpen && documentsViewModel != null && documentScanner != null && camera != null) {
                    LaunchedEffect(selectedClaim) { documentsViewModel.open(selectedClaim, documentScanner.available()) }
                    // Páginas de documentos personales: sin capturas ni vista en recientes.
                    SecureWindow(secureScreens)
                    ClaimDocumentsFlow(documentsViewModel, documentScanner, camera, onClose = { onDocumentsOpenChange(false) }, modifier = modifier)
                } else if (selectedClaim != null) {
                    ClaimDetailScreen(
                        claimsState.claim(selectedClaim),
                        onBack = { onSelectedClaimChange(null) },
                        modifier = modifier,
                        onAddDocuments = if (documentsReady) {
                            { onDocumentsOpenChange(true) }
                        } else {
                            null
                        },
                    )
                } else {
                    val canReport = claimViewModel != null && camera != null && entry != ReportEntry.HIDDEN
                    ClaimsListScreen(
                        state = claimsState,
                        onBack = { onClaimsOpenChange(false) },
                        onOpen = onSelectedClaimChange,
                        onReport = if (canReport) {
                            {
                                onClaimsOpenChange(false)
                                onClaimOpenChange(true)
                            }
                        } else {
                            null
                        },
                        modifier = modifier,
                        onOpenPending = if (claimViewModel != null && camera != null) {
                            {
                                onClaimsOpenChange(false)
                                onClaimOpenChange(true)
                            }
                        } else {
                            null
                        },
                    )
                }
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
                    onOpenClaims = if (claimsViewModel != null) {
                        { onClaimsOpenChange(true) }
                    } else {
                        null
                    },
                    cardActions = walletActions,
                    onRenew = walletViewModel::renew,
                    onDownloadCaratula = walletViewModel::downloadCaratula,
                    onDismissNotice = walletViewModel::dismissNotice,
                    receipt = receipt,
                    onOpenReceipt = if (claimsViewModel != null) {
                        { claimNumber ->
                            onClaimsOpenChange(true)
                            onSelectedClaimChange(claimNumber)
                        }
                    } else {
                        null
                    },
                    onDismissReceipt = walletViewModel::dismissReceipt,
                    onOpenNotices = if (preferencesViewModel != null) {
                        { onNoticesOpenChange(true) }
                    } else {
                        null
                    },
                    onOpenAssistance = if (assistanceViewModel != null) {
                        { onAssistanceOpenChange(true) }
                    } else {
                        null
                    },
                )
            }
        }
        uiState.showRegistration && registrationViewModel != null && camera != null && selfieCamera != null -> {
            // Datos personales, código y fotos de identidad: sin capturas ni vista en recientes (mejora FE-MOV-01).
            SecureWindow(secureScreens)
            RegistrationFlow(
                viewModel = registrationViewModel,
                documentCamera = camera,
                selfieCamera = selfieCamera,
                onClose = viewModel::closeRegistration,
                onFinished = { session ->
                    viewModel.completeRegistration(session.email, session.tokens, session.fullName, biometricAuthenticator)
                },
                modifier = modifier,
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
            onCreateAccount = openRegistration,
        )
        else -> BiometricLoginScreen(
            uiState = uiState,
            onAuthenticate = { viewModel.authenticateWithBiometrics(biometricAuthenticator) },
            onUseAlternative = viewModel::onAlternativeAccessSelected,
            onSelectAccount = viewModel::selectAccount,
            modifier = modifier,
            onCreateAccount = openRegistration,
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
