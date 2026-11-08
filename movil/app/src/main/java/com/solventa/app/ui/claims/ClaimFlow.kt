package com.solventa.app.ui.claims

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.solventa.app.R
import com.solventa.app.domain.claims.CaptureFix
import com.solventa.app.domain.claims.LocationAccess
import kotlinx.coroutines.delay
import java.time.Clock

/** Permisos y hardware que el reporte revisa antes de abrir la cámara. */
interface ClaimPermissions {
    fun hasCamera(): Boolean
    fun cameraGranted(): Boolean
    fun locationAccess(): LocationAccess
}

class AndroidClaimPermissions(private val context: Context) : ClaimPermissions {
    override fun hasCamera() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    override fun cameraGranted() = granted(Manifest.permission.CAMERA)

    override fun locationAccess() = when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationAccess.PRECISE
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationAccess.APPROXIMATE_ONLY
        else -> LocationAccess.DENIED
    }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

private val PERMISSION_REQUEST = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Reporte del siniestro (HU-MOV-08), como una rama más de la navegación por estado (ADR 0015, enmendada): cada
 * pantalla vuelve atrás con su propio `BackHandler`. Solo abre los ajustes del sistema; nunca la galería ni otra
 * app de cámara (AC-02).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimFlow(
    viewModel: ClaimReportViewModel,
    camera: EvidenceCamera,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    permissions: ClaimPermissions = AndroidClaimPermissions(LocalContext.current),
    clock: Clock = Clock.systemDefaultZone(),
) {
    val state by viewModel.uiState.collectAsState()
    val fix by viewModel.liveFix.collectAsState()
    val context = LocalContext.current
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(state.closed) { if (state.closed) onClosed() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissions(permissions.hasCamera(), permissions.cameraGranted(), permissions.locationAccess())
    }
    val takePhoto: () -> Unit = {
        when {
            !permissions.hasCamera() -> viewModel.showNotice(CaptureNotice.NO_CAMERA)
            permissions.cameraGranted() && permissions.locationAccess() == LocationAccess.PRECISE -> viewModel.openCamera()
            else -> launcher.launch(PERMISSION_REQUEST)
        }
    }

    // Al volver de los ajustes con el permiso concedido, la cámara se abre (UC-02).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state.notice) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val ready = permissions.cameraGranted() && permissions.locationAccess() == LocationAccess.PRECISE
            when (state.notice) {
                CaptureNotice.LOCATION_DENIED, CaptureNotice.LOCATION_APPROXIMATE, CaptureNotice.CAMERA_DENIED -> if (ready) viewModel.openCamera()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = !state.closed) { viewModel.back() }

    Box(modifier) {
        when {
            state.loading || state.unavailable || state.cannotOpen || state.report == null -> ClaimStatusScreen(
                state = state,
                onBack = viewModel::leave,
                onRetry = viewModel::retry,
                onDiscard = { confirmDiscard = true },
            )
            state.screen == ClaimScreen.EVIDENCE -> ClaimEvidenceScreen(
                state = state,
                onBack = viewModel::back,
                onTakePhoto = takePhoto,
                onDelete = viewModel::deletePhoto,
                onContinue = viewModel::continueToDetail,
                onDiscard = { confirmDiscard = true },
            )
            state.screen == ClaimScreen.CAMERA -> CameraStage(viewModel, camera, permissions, state, fix, clock)
            state.screen == ClaimScreen.DETAIL -> ClaimDetailScreen(
                state = state,
                onBack = viewModel::back,
                onSelectPolicy = viewModel::selectPolicy,
                onDescription = viewModel::updateDescription,
                onSend = viewModel::send,
                onDiscard = { confirmDiscard = true },
            )
            else -> ClaimSavedScreen(state, justSaved = state.justSaved, onDone = viewModel::leave, onDiscard = { confirmDiscard = true })
        }
    }

    state.notice?.let { notice ->
        ModalBottomSheet(onDismissRequest = viewModel::dismissNotice, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            NoticeBody(
                notice = notice,
                onAction = {
                    when (notice) {
                        CaptureNotice.LOCATION_DENIED, CaptureNotice.LOCATION_APPROXIMATE, CaptureNotice.CAMERA_DENIED ->
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        CaptureNotice.LOCATION_OFF -> {
                            viewModel.dismissNotice()
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                        else -> viewModel.dismissNotice()
                    }
                },
                onDismiss = viewModel::dismissNotice,
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.claim_discard_title)) },
            text = { Text(stringResource(R.string.claim_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.discard()
                }) { Text(stringResource(R.string.claim_discard_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.claim_discard_cancel)) } },
        )
    }
}

/** La cámara a la vista: la ubicación se escucha solo mientras está en primer plano (AC-20). */
@Composable
private fun CameraStage(
    viewModel: ClaimReportViewModel,
    camera: EvidenceCamera,
    permissions: ClaimPermissions,
    state: ClaimUiState,
    fix: CaptureFix?,
    clock: Clock,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Un permiso de una sola vez pudo vencer a mitad del reporte: se vuelve a explicar (SC-31).
        if (!permissions.cameraGranted() || permissions.locationAccess() != LocationAccess.PRECISE) {
            viewModel.onPermissions(permissions.hasCamera(), permissions.cameraGranted(), permissions.locationAccess())
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.cameraVisible(true)
                Lifecycle.Event.ON_STOP -> viewModel.cameraVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.cameraVisible(true)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.cameraVisible(false)
        }
    }
    var now by remember { mutableStateOf(clock.instant()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = clock.instant()
            delay(1_000)
        }
    }
    ClaimCameraScreen(
        state = state,
        fix = fix,
        now = now,
        preview = { modifier -> camera.Preview(modifier, onUnavailable = { viewModel.showNotice(CaptureNotice.CAPTURE_FAILED) }) },
        onShoot = { viewModel.shoot { camera.capture() } },
        onClose = viewModel::back,
    )
}
