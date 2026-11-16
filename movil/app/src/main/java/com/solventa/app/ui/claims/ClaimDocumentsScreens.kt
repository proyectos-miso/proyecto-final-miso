package com.solventa.app.ui.claims

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.unit.dp
import com.solventa.app.R
import com.solventa.app.domain.claims.ScannedPage
import com.solventa.app.ui.theme.PrimaryButton
import com.solventa.app.ui.theme.SecondaryButton
import com.solventa.app.ui.theme.SecureNote
import com.solventa.app.ui.theme.SectionLabel
import com.solventa.app.ui.theme.WarningBanner

/**
 * Flujo de «Agregar documentos» (mejora FE-MOV-05): el escáner de Google si está, o la cámara trasera de la app
 * si no. Las páginas no salen de la memoria del ViewModel.
 */
@Composable
fun ClaimDocumentsFlow(
    viewModel: ClaimDocumentsViewModel,
    scanner: DocumentScanner,
    camera: EvidenceCamera,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    cameraGranted: (Context) -> Boolean = { it.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED },
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scan = scanner.rememberLauncher(viewModel::onScan)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.openCamera() else viewModel.onCameraDenied()
    }
    val close = {
        viewModel.close()
        onClose()
    }
    if (state.cameraOpen) {
        DocumentCameraScreen(
            pageNumber = state.pages.size + 1,
            capturing = state.capturing,
            preview = { camera.Preview(it, onUnavailable = viewModel::onCameraDenied) },
            onShoot = { viewModel.capture(camera) },
            onClose = viewModel::closeCamera,
            modifier = modifier,
        )
    } else {
        ClaimDocumentsScreen(
            state = state,
            onBack = close,
            onScan = { scan(state.remaining) },
            onPhoto = { if (cameraGranted(context)) viewModel.openCamera() else permission.launch(Manifest.permission.CAMERA) },
            onRemove = viewModel::remove,
            onSend = viewModel::send,
            onDismissNotice = viewModel::dismissNotice,
            modifier = modifier,
        )
    }
}

@Composable
fun ClaimDocumentsScreen(
    state: ClaimDocumentsUiState,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onPhoto: () -> Unit,
    onRemove: (Long) -> Unit,
    onSend: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        ClaimTopBar(
            title = stringResource(R.string.documents_title),
            subtitle = state.claimNumber?.let { stringResource(R.string.documents_subtitle, it) },
            onBack = onBack,
        )
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "intro") { Text(stringResource(R.string.documents_intro), style = MaterialTheme.typography.bodyMedium) }
            if (state.useCamera) item(key = "cameraMode") { WarningBanner(stringResource(R.string.documents_camera_mode)) }
            state.notice?.let { notice -> item(key = "notice") { Notice(noticeText(notice, state.rejection), onDismissNotice) } }
            state.sentPages?.let { sent ->
                item(key = "sent") {
                    Text(
                        stringResource(R.string.documents_sent, sent),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            item(key = "pagesTitle") {
                SectionLabel(stringResource(R.string.documents_pages_title, state.pages.size), Modifier.semantics { heading() })
            }
            if (state.pages.isEmpty()) {
                item(key = "empty") { Text(stringResource(R.string.documents_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            itemsIndexed(state.pages, key = { _, page -> page.id }) { index, page ->
                PageRow(index + 1, page, enabled = !state.sending) { onRemove(page.id) }
            }
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (state.remaining == 0) Text(stringResource(R.string.documents_limit), style = MaterialTheme.typography.bodySmall)
                    if (state.useCamera) {
                        SecondaryButton(stringResource(R.string.documents_photo), onPhoto, Modifier.fillMaxWidth(), enabled = state.canAdd)
                    } else {
                        SecondaryButton(stringResource(R.string.documents_scan), onScan, Modifier.fillMaxWidth(), enabled = state.canAdd)
                    }
                    if (state.sending) {
                        val sending = stringResource(R.string.documents_sending)
                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(Modifier.semantics { contentDescription = sending })
                        }
                    } else {
                        val label = if (state.pages.size == 1) {
                            stringResource(R.string.documents_send_one)
                        } else {
                            stringResource(R.string.documents_send, state.pages.size)
                        }
                        PrimaryButton(label, onSend, Modifier.fillMaxWidth(), enabled = state.canSend)
                    }
                    SecureNote(stringResource(R.string.documents_memory_note))
                }
            }
        }
    }
}

@Composable
private fun PageRow(position: Int, page: ScannedPage, enabled: Boolean, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val preview = stringResource(R.string.documents_page_preview, position)
    val thumbnail = remember(page.id) { BitmapFactory.decodeByteArray(page.jpeg, 0, page.jpeg.size)?.asImageBitmap() }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 72.dp).padding(8.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.surfaceVariant)
                    .semantics { contentDescription = preview },
            ) {
                if (thumbnail != null) {
                    Image(thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(painterResource(R.drawable.ic_camera), contentDescription = null, tint = colors.onSurfaceVariant)
                }
            }
            Text(
                stringResource(R.string.documents_page, position),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.documents_remove_page, position))
            }
        }
    }
}

@Composable
private fun Notice(text: String, onDismiss: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp)) {
            Text(text, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.documents_dismiss))
            }
        }
    }
}

@Composable
private fun noticeText(notice: DocumentsNotice, rejection: String?): String = when (notice) {
    DocumentsNotice.SCANNER_UNAVAILABLE -> stringResource(R.string.documents_notice_scanner_unavailable)
    DocumentsNotice.SCAN_FAILED -> stringResource(R.string.documents_notice_scan_failed)
    DocumentsNotice.CAPTURE_FAILED -> stringResource(R.string.documents_notice_capture_failed)
    DocumentsNotice.CAMERA_DENIED -> stringResource(R.string.documents_notice_camera_denied)
    DocumentsNotice.OFFLINE -> stringResource(R.string.documents_notice_offline)
    DocumentsNotice.NOT_FOUND -> stringResource(R.string.documents_notice_not_found)
    DocumentsNotice.CLOSED -> stringResource(R.string.documents_notice_closed)
    DocumentsNotice.SESSION -> stringResource(R.string.documents_notice_session)
    DocumentsNotice.REJECTED -> rejection ?: stringResource(R.string.documents_notice_scan_failed)
}

/** Cámara trasera de la app para fotografiar una página cuando no hay escáner; sin recorte automático. */
@Composable
fun DocumentCameraScreen(
    pageNumber: Int,
    capturing: Boolean,
    preview: @Composable (Modifier) -> Unit,
    onShoot: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val pane = stringResource(R.string.documents_camera_title, pageNumber)
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { paneTitle = pane },
    ) {
        preview(Modifier.fillMaxSize())
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.documents_close_camera), tint = Color.White)
            }
            Text(pane, color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 20.dp),
        ) {
            Text(stringResource(R.string.documents_camera_hint), color = Color.White, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.size(14.dp))
            val shutter = stringResource(R.string.documents_shutter)
            Surface(
                onClick = onShoot,
                enabled = !capturing,
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(5.dp, Color.White.copy(alpha = if (capturing) 0.4f else 1f)),
                modifier = Modifier
                    .size(78.dp)
                    .semantics {
                        contentDescription = shutter
                        role = Role.Button
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (capturing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    } else {
                        Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White))
                    }
                }
            }
        }
    }
}
