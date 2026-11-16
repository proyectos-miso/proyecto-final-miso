package com.solventa.app.ui.auth

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.solventa.app.R
import com.solventa.app.domain.auth.DocumentType
import com.solventa.app.ui.claims.ClaimTopBar
import com.solventa.app.ui.claims.EvidenceCamera
import com.solventa.app.ui.theme.PrimaryButton

private const val CODE_LENGTH = 6
private val STEPS = RegistrationStep.entries.size - 1

/**
 * Registro con documento y selfie (mejora FE-MOV-01), como una rama más de la navegación por estado. El documento
 * se toma con la cámara trasera y la selfie con la frontal, ambas de la app: nunca la galería ni otra app de cámara.
 */
@Composable
fun RegistrationFlow(
    viewModel: RegistrationViewModel,
    documentCamera: EvidenceCamera,
    selfieCamera: EvidenceCamera,
    onClose: () -> Unit,
    onFinished: (RegisteredSession) -> Unit,
    modifier: Modifier = Modifier,
    cameraGranted: (Context) -> Boolean = { it.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED },
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val leave = {
        viewModel.close()
        onClose()
    }
    BackHandler { if (!viewModel.back()) leave() }

    var granted by remember { mutableStateOf(cameraGranted(context)) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        asked = true
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = cameraGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier.fillMaxSize()) {
        when (state.step) {
            RegistrationStep.DATA -> RegistrationDataScreen(
                state = state,
                onChange = viewModel::updateDraft,
                onSubmit = viewModel::submitData,
                onBack = leave,
            )
            RegistrationStep.CODE -> RegistrationCodeScreen(
                state = state,
                onSubmit = viewModel::submitCode,
                onResend = viewModel::resendCode,
                onBack = { viewModel.back() },
            )
            RegistrationStep.DOCUMENT, RegistrationStep.SELFIE -> {
                val document = state.step == RegistrationStep.DOCUMENT
                val camera = if (document) documentCamera else selfieCamera
                if (granted) {
                    IdentityCameraScreen(
                        state = state,
                        title = if (document) R.string.register_document_title else R.string.register_selfie_title,
                        hint = if (document) R.string.register_document_hint else R.string.register_selfie_hint,
                        preview = { previewModifier ->
                            camera.Preview(previewModifier, onUnavailable = { viewModel.capture { null } })
                        },
                        onShoot = { viewModel.capture { camera.capture() } },
                        onBack = { viewModel.back() },
                    )
                } else {
                    CameraPermissionScreen(
                        state = state,
                        denied = asked,
                        onAllow = { launcher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            )
                        },
                        onBack = { viewModel.back() },
                    )
                }
            }
            RegistrationStep.CONSENT -> BiometricConsentScreen(
                state = state,
                onConsent = viewModel::setConsent,
                onSubmit = viewModel::submit,
                onBack = { viewModel.back() },
            )
            RegistrationStep.RESULT -> RegistrationResultScreen(
                outcome = state.outcome ?: RegistrationOutcome.REJECTED,
                onEnter = {
                    val session = viewModel.takeSession()
                    viewModel.close()
                    if (session != null) onFinished(session) else onClose()
                },
                onClose = leave,
            )
        }
    }
}

@Composable
private fun StepScaffold(
    state: RegistrationUiState,
    @StringRes title: Int,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val pane = stringResource(title)
    Column(Modifier.fillMaxSize().semantics { paneTitle = pane }) {
        ClaimTopBar(
            title = stringResource(title),
            subtitle = stringResource(R.string.register_step, state.stepNumber, STEPS),
            onBack = onBack,
            iconLabel = R.string.register_back,
            progress = state.stepNumber.toFloat() / STEPS,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
fun RegistrationDataScreen(
    state: RegistrationUiState,
    onChange: (RegistrationDraft) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val draft = state.draft
    StepScaffold(state, R.string.register_title, onBack) {
        Text(stringResource(R.string.register_data_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = draft.fullName,
            onValueChange = { onChange(draft.copy(fullName = it.take(150))) },
            label = { Text(stringResource(R.string.register_full_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.email,
            onValueChange = { onChange(draft.copy(email = it.take(254))) },
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.register_document_type),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { heading() },
        )
        Column(Modifier.selectableGroup()) {
            DocumentType.entries.forEach { type ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = draft.documentType == type,
                            onClick = { onChange(draft.copy(documentType = type)) },
                            role = Role.RadioButton,
                        ),
                ) {
                    RadioButton(selected = draft.documentType == type, onClick = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(type.label()))
                }
            }
        }
        OutlinedTextField(
            value = draft.documentNumber,
            onValueChange = { value -> onChange(draft.copy(documentNumber = value.uppercase().filter(Char::isLetterOrDigit).take(12))) },
            label = { Text(stringResource(R.string.register_document_number)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (draft.documentType == DocumentType.CC) KeyboardType.Number else KeyboardType.Ascii,
            ),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.phone,
            onValueChange = { value -> onChange(draft.copy(phone = value.filter(Char::isDigit).take(10))) },
            label = { Text(stringResource(R.string.register_phone)) },
            supportingText = { Text(stringResource(R.string.register_phone_help)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(
            value = draft.password,
            onValueChange = { onChange(draft.copy(password = it.take(128))) },
            label = stringResource(R.string.auth_password),
            modifier = Modifier.fillMaxWidth(),
        )
        NewPasswordRequirements(newPassword = draft.password)
        PasswordField(
            value = draft.confirmation,
            onValueChange = { onChange(draft.copy(confirmation = it.take(128))) },
            label = stringResource(R.string.recovery_confirm_password),
            modifier = Modifier.fillMaxWidth(),
        )
        MatchRequirement(newPassword = draft.password, confirmation = draft.confirmation)
        CheckRow(
            text = stringResource(R.string.register_terms),
            checked = draft.termsAccepted,
            onChange = { onChange(draft.copy(termsAccepted = it)) },
        )
        RegistrationErrorText(state.error)
        PrimaryButton(
            text = stringResource(if (state.busy) R.string.register_sending else R.string.register_continue),
            onClick = onSubmit,
            enabled = draft.complete && !state.busy,
        )
    }
}

@Composable
fun RegistrationCodeScreen(
    state: RegistrationUiState,
    onSubmit: (String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    StepScaffold(state, R.string.register_code_title, onBack) {
        Text(stringResource(R.string.register_code_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.debugCode?.let {
            Text(
                stringResource(R.string.auth_otp_sent_debug, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = code,
            onValueChange = { value -> code = value.filter(Char::isDigit).take(CODE_LENGTH) },
            label = { Text(stringResource(R.string.register_code_title)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        RegistrationErrorText(state.error)
        PrimaryButton(
            text = stringResource(R.string.register_continue),
            onClick = { onSubmit(code) },
            enabled = code.length == CODE_LENGTH && !state.busy,
        )
        TextButton(
            onClick = onResend,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.register_code_resend)) }
    }
}

@Composable
fun IdentityCameraScreen(
    state: RegistrationUiState,
    @StringRes title: Int,
    @StringRes hint: Int,
    preview: @Composable (Modifier) -> Unit,
    onShoot: () -> Unit,
    onBack: () -> Unit,
) {
    val pane = stringResource(title)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { paneTitle = pane },
    ) {
        preview(Modifier.fillMaxSize())
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.register_back), tint = Color.White)
            }
            Text(
                pane,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Text(stringResource(hint), color = Color.White, style = MaterialTheme.typography.bodySmall)
            state.error?.let {
                Text(
                    stringResource(R.string.register_error_capture),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(Modifier.size(14.dp))
            val shutterLabel = stringResource(R.string.register_shutter)
            Surface(
                onClick = onShoot,
                enabled = !state.busy,
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(5.dp, Color.White.copy(alpha = if (state.busy) 0.4f else 1f)),
                modifier = Modifier
                    .size(78.dp)
                    .semantics {
                        contentDescription = shutterLabel
                        role = Role.Button
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionScreen(
    state: RegistrationUiState,
    denied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val title = if (state.step == RegistrationStep.DOCUMENT) R.string.register_document_title else R.string.register_selfie_title
    StepScaffold(state, title, onBack) {
        Text(stringResource(R.string.register_camera_denied))
        if (denied) {
            PrimaryButton(stringResource(R.string.register_camera_settings), onOpenSettings)
        } else {
            PrimaryButton(stringResource(R.string.register_camera_allow), onAllow)
        }
    }
}

@Composable
fun BiometricConsentScreen(
    state: RegistrationUiState,
    onConsent: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    StepScaffold(state, R.string.register_consent_title, onBack) {
        Text(stringResource(R.string.register_consent_body))
        CheckRow(
            text = stringResource(R.string.register_consent_check),
            checked = state.consentAccepted,
            onChange = onConsent,
        )
        RegistrationErrorText(state.error)
        PrimaryButton(
            text = stringResource(if (state.busy) R.string.register_submitting else R.string.register_submit),
            onClick = onSubmit,
            enabled = state.consentAccepted && !state.busy,
        )
    }
}

@Composable
fun RegistrationResultScreen(outcome: RegistrationOutcome, onEnter: () -> Unit, onClose: () -> Unit) {
    val (title, body) = when (outcome) {
        RegistrationOutcome.APPROVED -> R.string.register_approved_title to R.string.register_approved_body
        RegistrationOutcome.PENDING -> R.string.register_pending_title to R.string.register_pending_body
        RegistrationOutcome.REJECTED -> R.string.register_rejected_title to R.string.register_rejected_body
    }
    val pane = stringResource(title)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics { paneTitle = pane },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            pane,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(stringResource(body))
        Spacer(Modifier.height(8.dp))
        if (outcome == RegistrationOutcome.REJECTED) {
            PrimaryButton(stringResource(R.string.register_close), onClose)
        } else {
            PrimaryButton(stringResource(R.string.register_enter), onEnter)
        }
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Checkbox),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RegistrationErrorText(error: RegistrationError?) {
    val message = when (error) {
        null -> return
        RegistrationError.INVALID_DATA -> R.string.register_error_invalid
        RegistrationError.INVALID_CODE -> R.string.register_error_code
        RegistrationError.WEAK_PASSWORD -> R.string.recovery_error_weak
        RegistrationError.DUPLICATE -> R.string.register_error_duplicate
        RegistrationError.TOO_MANY_REQUESTS -> R.string.auth_error_too_many
        RegistrationError.SMS_UNAVAILABLE -> R.string.auth_error_sms
        RegistrationError.NETWORK -> R.string.auth_error_network
        RegistrationError.CAPTURE_FAILED -> R.string.register_error_capture
    }
    Text(
        stringResource(message),
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@StringRes
private fun DocumentType.label(): Int = when (this) {
    DocumentType.CC -> R.string.register_document_cc
    DocumentType.CE -> R.string.register_document_ce
    DocumentType.PA -> R.string.register_document_pa
}

/** El enlace «Crear cuenta» de las pantallas de ingreso. */
@Composable
fun CreateAccountButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.register_open), fontWeight = FontWeight.SemiBold)
    }
}
