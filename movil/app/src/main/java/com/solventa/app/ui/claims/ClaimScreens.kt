package com.solventa.app.ui.claims

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solventa.app.R
import com.solventa.app.domain.claims.CaptureFix
import com.solventa.app.domain.claims.ClaimRules
import com.solventa.app.domain.claims.EvidencePhoto
import com.solventa.app.domain.claims.Missing
import com.solventa.app.ui.theme.ChipTone
import com.solventa.app.ui.theme.PrimaryButton
import com.solventa.app.ui.theme.SecondaryButton
import com.solventa.app.ui.theme.SecureNote
import com.solventa.app.ui.theme.SectionLabel
import com.solventa.app.ui.theme.SolventaTheme
import com.solventa.app.ui.theme.StatusChip
import com.solventa.app.ui.theme.WarningBanner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val COLOMBIA = Locale.forLanguageTag("es-CO")
private val PHOTO_TIME = DateTimeFormatter.ofPattern("d MMM yyyy · h:mm:ss a", COLOMBIA)

/** Coordenadas como en el mockup: 4,6512° N · 74,0568° O. */
fun formatCoordinates(fix: CaptureFix): String {
    val lat = String.format(COLOMBIA, "%.4f° %s", abs(fix.latitude), if (fix.latitude >= 0) "N" else "S")
    val lon = String.format(COLOMBIA, "%.4f° %s", abs(fix.longitude), if (fix.longitude >= 0) "E" else "O")
    return "$lat · $lon"
}

fun formatPhotoTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = PHOTO_TIME.format(instant.atZone(zone))

private val SPOKEN_TIME = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, h:mm:ss a", COLOMBIA)

/** La hora como la lee TalkBack: 5 de noviembre de 2026, 1:01:08 p. m. (QR-02). */
fun spokenPhotoTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = SPOKEN_TIME.format(instant.atZone(zone))

/** Las coordenadas en palabras para TalkBack, sin «°», «±» ni la «O» que suena como la conjunción (QR-02). */
@Composable
fun spokenPlace(fix: CaptureFix): String = stringResource(
    R.string.claim_spoken_place,
    String.format(COLOMBIA, "%.4f", abs(fix.latitude)),
    stringResource(if (fix.latitude >= 0) R.string.claim_north else R.string.claim_south),
    String.format(COLOMBIA, "%.4f", abs(fix.longitude)),
    stringResource(if (fix.longitude >= 0) R.string.claim_east else R.string.claim_west),
    fix.accuracyMeters.roundToInt(),
)

/** Barra de las pantallas del reporte: atrás o cerrar, título, paso y contador. */
@Composable
fun ClaimTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    @DrawableRes icon: Int = R.drawable.ic_arrow_back,
    @StringRes iconLabel: Int = R.string.claim_back,
    trailing: @Composable (() -> Unit)? = null,
    progress: Float? = null,
) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(end = 12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(icon), contentDescription = stringResource(iconLabel), modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            trailing?.invoke()
        }
        if (progress != null) {
            // Decorativa: el subtítulo ya dice el paso. `LinearProgressIndicator` agranda 10 dp hacia arriba su zona
            // accesible y le quitaba 2 dp de alto al botón Atrás (TC-17).
            Box(Modifier.fillMaxWidth().height(4.dp).background(ProgressIndicatorDefaults.linearTrackColor)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
            }
        } else {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Paso 1: las fotos con su ubicación, hora y cifrado; se pueden borrar (mockup 4, AC-13). */
@Composable
fun ClaimEvidenceScreen(
    state: ClaimUiState,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onDelete: (String) -> Unit,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
) {
    val photos = state.report?.photos.orEmpty()
    val pane = state.savedPhoto?.let { stringResource(R.string.claim_pane_evidence_saved, it) } ?: stringResource(R.string.claim_pane_evidence)
    val countSpoken = stringResource(R.string.claim_photos_attached, photos.size)
    Column(Modifier.fillMaxSize().semantics { paneTitle = pane }) {
        ClaimTopBar(
            title = stringResource(R.string.claim_title),
            subtitle = stringResource(R.string.claim_step_evidence),
            onBack = onBack,
            trailing = {
                StatusChip(
                    stringResource(R.string.claim_photos_count, photos.size),
                    ChipTone.NEUTRAL,
                    Modifier.clearAndSetSemantics { contentDescription = countSpoken },
                )
            },
            progress = 0.5f,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "heading") {
                Column {
                    Text(
                        stringResource(R.string.claim_evidence_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.claim_evidence_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(photos, key = EvidencePhoto::id) { photo ->
                PhotoRow(photo, state.thumbnails[photo.id], photo.id in state.damaged, onDelete = { onDelete(photo.id) })
            }
            item(key = "take") {
                if (state.canAddPhoto) {
                    SecondaryButton(
                        text = if (photos.isEmpty()) stringResource(R.string.claim_take_photo) else stringResource(R.string.claim_take_another, photos.size + 1),
                        onClick = onTakePhoto,
                    )
                } else {
                    Text(stringResource(R.string.claim_max_photos), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "gallery") { SecureNote(stringResource(R.string.claim_gallery_note)) }
            // Un reporte guardado siempre se puede descartar desde la billetera, aun sin fotos (UC-04, SR-13).
            item(key = "discard") {
                TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.claim_discard), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        val missingPhoto = state.showMissing && Missing.PHOTO in state.missing
        val deleted = state.deletedPhoto?.let { stringResource(R.string.claim_photo_deleted, it.position, it.remaining) }
        val status = if (missingPhoto) missingSpoken(listOf(Missing.PHOTO)) else deleted
        Column(Modifier.padding(16.dp)) {
            // Siempre presente junto a «Continuar»: TalkBack anuncia lo que aparece aquí, sea lo que falta o la
            // foto borrada. Un aviso que aparece como nodo nuevo no se anuncia (QR-02).
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 1.dp)
                    .clearAndSetSemantics {
                        liveRegion = LiveRegionMode.Polite
                        status?.let { contentDescription = it }
                    },
            ) {
                when {
                    missingPhoto -> Box(Modifier.padding(bottom = 10.dp)) { MissingBanner(listOf(Missing.PHOTO)) }
                    deleted != null -> Text(
                        deleted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
            PrimaryButton(stringResource(R.string.claim_continue), onContinue)
        }
    }
}

@Composable
private fun PhotoRow(photo: EvidencePhoto, thumbnail: Bitmap?, damaged: Boolean, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        border = BorderStroke(1.dp, if (damaged) colors.error else colors.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(76.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.surfaceVariant),
            ) {
                if (thumbnail != null && !damaged) {
                    Image(thumbnail.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(painterResource(R.drawable.ic_camera), contentDescription = null, tint = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.size(12.dp))
            val spoken = if (damaged) {
                stringResource(R.string.claim_spoken_photo_damaged, photo.position)
            } else {
                stringResource(
                    R.string.claim_spoken_photo,
                    photo.position,
                    spokenPlace(photo.fix),
                    spokenPhotoTime(photo.capturedAt),
                    stringResource(if (photo.fix.mock) R.string.claim_photo_mock else R.string.claim_photo_encrypted),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = spoken },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(stringResource(R.string.claim_photo_label, photo.position), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (damaged) {
                    Text(stringResource(R.string.claim_photo_damaged), style = MaterialTheme.typography.bodySmall, color = colors.error)
                } else {
                    Meta(R.drawable.ic_location, stringResource(R.string.claim_photo_place, formatCoordinates(photo.fix), photo.fix.accuracyMeters.roundToInt()))
                    Meta(R.drawable.ic_clock, formatPhotoTime(photo.capturedAt))
                    if (photo.fix.mock) {
                        StatusChip(stringResource(R.string.claim_photo_mock), ChipTone.WARNING)
                    } else {
                        Meta(R.drawable.ic_lock, stringResource(R.string.claim_photo_encrypted), SolventaTheme.colors.ok)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.claim_photo_delete, photo.position),
                    tint = colors.error,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun Meta(@DrawableRes icon: Int, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

/** Visor con las coordenadas, la precisión y la hora del momento (mockup 2); sin galería (AC-02). */
@Composable
fun ClaimCameraScreen(
    state: ClaimUiState,
    fix: CaptureFix?,
    now: Instant,
    preview: @Composable (Modifier) -> Unit,
    onShoot: () -> Unit,
    onClose: () -> Unit,
) {
    val pane = stringResource(R.string.claim_pane_camera, state.photoCount + 1)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { paneTitle = pane },
    ) {
        preview(Modifier.fillMaxSize())
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.claim_close_camera), tint = Color.White)
                }
                Text(
                    stringResource(R.string.claim_camera_title, state.photoCount + 1),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
            }
            CameraHud(state, fix, now)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 20.dp),
        ) {
            Text(stringResource(R.string.claim_camera_hint), color = Color.White, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.size(14.dp))
            val busy = state.seeking || state.shooting
            val shutterLabel = stringResource(R.string.claim_shutter)
            Surface(
                onClick = onShoot,
                enabled = !busy,
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(5.dp, Color.White.copy(alpha = if (busy) 0.4f else 1f)),
                modifier = Modifier
                    .size(78.dp)
                    .semantics {
                        contentDescription = shutterLabel
                        role = Role.Button
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.shooting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    } else {
                        Box(
                            Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (busy) 0.4f else 1f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraHud(state: ClaimUiState, fix: CaptureFix?, now: Instant) {
    // TalkBack oye solo el estado, que cambia pocas veces; las coordenadas y el reloj cambian cada segundo y
    // sonarían sin parar. Las coordenadas se leen después en la fila de la foto (QR-02).
    val status = when {
        state.seeking -> stringResource(R.string.claim_status_waiting) + " " + stringResource(R.string.claim_seeking_hint)
        state.shooting -> stringResource(R.string.claim_saving_photo)
        fix != null -> stringResource(R.string.claim_status_ready) + if (fix.mock) " " + stringResource(R.string.claim_photo_mock) + "." else ""
        else -> stringResource(R.string.claim_status_waiting)
    }
    Surface(
        color = Color(0xB30F172A),
        contentColor = Color.White,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = status
            },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                state.seeking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.claim_seeking, state.seekSeconds), fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(R.string.claim_seeking_hint), style = MaterialTheme.typography.bodySmall)
                }
                state.shooting -> Text(stringResource(R.string.claim_saving_photo), fontWeight = FontWeight.SemiBold)
                fix != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_location), contentDescription = null, tint = Color(0xFF5EEAD4), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(formatCoordinates(fix), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        StatusChip("± ${fix.accuracyMeters.roundToInt()} m", if (fix.accuracyMeters <= 100f) ChipTone.OK else ChipTone.WARNING)
                    }
                    if (fix.mock) StatusChip(stringResource(R.string.claim_photo_mock), ChipTone.WARNING)
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.claim_waiting_location))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_clock), contentDescription = null, tint = Color(0xFF5EEAD4), modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(8.dp))
                Text(formatPhotoTime(now))
            }
        }
    }
}

/** Paso 2: póliza, lugar y hora de la primera foto y descripción, con lo que falta (mockup 5, AC-04, AC-18). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimDetailScreen(
    state: ClaimUiState,
    onBack: () -> Unit,
    onSelectPolicy: (String) -> Unit,
    onDescription: (String) -> Unit,
    onSend: () -> Unit,
    onDiscard: () -> Unit,
) {
    val report = state.report
    val first = report?.photos?.firstOrNull()
    val pane = stringResource(R.string.claim_pane_detail)
    Column(Modifier.fillMaxSize().semantics { paneTitle = pane }) {
        ClaimTopBar(stringResource(R.string.claim_title), stringResource(R.string.claim_step_detail), onBack, progress = 1f)
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Como el mockup 5: con «Enviar» desactivado, el aviso dice siempre qué falta (AC-04).
            if (state.missing.isNotEmpty()) MissingBanner(state.missing)
            if (state.policies.isEmpty()) {
                WarningBanner(stringResource(R.string.claim_no_active_policies))
                SecondaryButton(stringResource(R.string.claim_discard), onDiscard)
            } else {
                PolicySelector(state, onSelectPolicy)
            }
            if (first != null) {
                val placeTime = stringResource(R.string.claim_spoken_place_time, spokenPlace(first.fix), spokenPhotoTime(first.capturedAt))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics { contentDescription = placeTime },
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel(stringResource(R.string.claim_place_time))
                        Meta(R.drawable.ic_location, stringResource(R.string.claim_photo_place, formatCoordinates(first.fix), first.fix.accuracyMeters.roundToInt()), MaterialTheme.colorScheme.onSurface)
                        Meta(R.drawable.ic_clock, formatPhotoTime(first.capturedAt), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Text(
                stringResource(R.string.claim_photos_attached, state.photoCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val error = state.showMissing && Missing.DESCRIPTION in state.missing
            val descriptionLabel = stringResource(R.string.claim_description_label_spoken)
            val countSpoken = stringResource(R.string.claim_description_count_spoken, state.description.length)
            val help = when {
                error -> R.string.claim_missing_description
                state.description.length >= ClaimRules.MAX_DESCRIPTION -> R.string.claim_description_max
                else -> R.string.claim_description_help
            }
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescription,
                // TalkBack lee «obligatoria», no «asterisco» (QR-02).
                label = {
                    Text(
                        stringResource(R.string.claim_description_label),
                        Modifier.clearAndSetSemantics { text = AnnotatedString(descriptionLabel) },
                    )
                },
                placeholder = { Text(stringResource(R.string.claim_description_hint)) },
                isError = error,
                minLines = 4,
                supportingText = {
                    Row {
                        Text(stringResource(help), modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.claim_description_count, state.description.length),
                            Modifier.clearAndSetSemantics { text = AnnotatedString(countSpoken) },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
            SecondaryButton(stringResource(R.string.claim_back), onBack, Modifier.weight(0.4f))
            PrimaryButton(stringResource(R.string.claim_send), onSend, Modifier.weight(0.6f), enabled = state.missing.isEmpty())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolicySelector(state: ClaimUiState, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.policies.firstOrNull { it.policyNumber == state.report?.policyNumber }
    val error = state.showMissing && (Missing.POLICY in state.missing || Missing.POLICY_NOT_ACTIVE in state.missing)
    val label = stringResource(R.string.claim_policy_label_spoken)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { policyLabel(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.claim_policy_label), Modifier.clearAndSetSemantics { text = AnnotatedString(label) }) },
            placeholder = { Text(stringResource(R.string.claim_policy_choose)) },
            isError = error,
            supportingText = if (error) {
                { Text(stringResource(if (Missing.POLICY_NOT_ACTIVE in state.missing) R.string.claim_missing_policy_inactive else R.string.claim_missing_policy)) }
            } else {
                null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.policies.forEach { option ->
                DropdownMenuItem(
                    text = { Text(policyLabel(option)) },
                    onClick = {
                        onSelect(option.policyNumber)
                        expanded = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun policyLabel(option: PolicyOption): String =
    stringResource(R.string.claim_policy_option, stringResource(planName(option.plan)), option.policyNumber)

@StringRes
private fun planName(plan: String): Int = when (plan) {
    "basic" -> R.string.wallet_plan_basic
    "premium" -> R.string.wallet_plan_premium
    else -> R.string.wallet_plan_standard
}

/** Lo que falta para enviar, como el aviso del mockup 5 (AC-04). */
@Composable
fun MissingBanner(missing: List<Missing>) {
    val spoken = missingSpoken(missing)
    Surface(
        color = SolventaTheme.colors.errorSoft,
        contentColor = MaterialTheme.colorScheme.error,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken
            },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.claim_missing_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            missing.forEach { Text("• " + stringResource(missingText(it)), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Lo que falta como lo lee TalkBack: el título y cada punto, sin viñetas. */
@Composable
private fun missingSpoken(missing: List<Missing>): String =
    (listOf(stringResource(R.string.claim_missing_title) + ":") + missing.map { stringResource(missingText(it)) }).joinToString(" ")

@StringRes
private fun missingText(missing: Missing): Int = when (missing) {
    Missing.PHOTO -> R.string.claim_missing_photo
    Missing.POLICY -> R.string.claim_missing_policy
    Missing.POLICY_NOT_ACTIVE -> R.string.claim_missing_policy_inactive
    Missing.DESCRIPTION -> R.string.claim_missing_description
}

/** Reporte guardado y listo para enviar; uno listo se ve sin poder editarlo y se puede descartar (AC-15, AC-19). */
@Composable
fun ClaimSavedScreen(state: ClaimUiState, justSaved: Boolean, onDone: () -> Unit, onDiscard: () -> Unit) {
    val report = state.report
    val first = report?.photos?.firstOrNull()
    val pane = stringResource(if (justSaved) R.string.claim_saved_heading else R.string.claim_pane_ready)
    Column(Modifier.fillMaxSize().semantics { paneTitle = pane }) {
        ClaimTopBar(stringResource(R.string.claim_saved_title), null, onDone, icon = R.drawable.ic_close, iconLabel = R.string.claim_back_to_wallet)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(SolventaTheme.colors.okSoft),
            ) {
                Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = SolventaTheme.colors.ok, modifier = Modifier.size(44.dp))
            }
            Text(
                stringResource(R.string.claim_saved_heading),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(if (justSaved) R.string.claim_saved_body else R.string.claim_ready_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    SectionLabel(stringResource(R.string.claim_summary))
                    SummaryRow(R.string.claim_summary_policy, report?.policyNumber ?: "—", mono = true)
                    SummaryRow(R.string.claim_summary_photos, stringResource(R.string.claim_photos_count, state.photoCount))
                    first?.let {
                        SummaryRow(R.string.claim_summary_place, formatCoordinates(it.fix), spoken = spokenPlace(it.fix))
                        SummaryRow(R.string.claim_summary_time, formatPhotoTime(it.capturedAt), spoken = spokenPhotoTime(it.capturedAt))
                    }
                    val stateSpoken = stringResource(
                        R.string.claim_spoken_summary,
                        stringResource(R.string.claim_summary_state),
                        stringResource(R.string.claim_state_ready),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clearAndSetSemantics { contentDescription = stateSpoken },
                    ) {
                        Text(stringResource(R.string.claim_summary_state), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        StatusChip(stringResource(R.string.claim_state_ready), ChipTone.NEUTRAL)
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PrimaryButton(stringResource(R.string.claim_back_to_wallet), onDone)
            TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.claim_discard), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// Un nodo por fila («Hora: 5 de noviembre…»); el valor toma el ancho que queda para que la etiqueta no
// desaparezca con la fuente al máximo (QR-02).
@Composable
private fun SummaryRow(@StringRes label: Int, value: String, mono: Boolean = false, spoken: String = value) {
    val name = stringResource(label)
    val description = stringResource(R.string.claim_spoken_summary, name, spoken)
    Row(
        Modifier
            .padding(top = 8.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) FontFamily.Monospace else null,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Mientras abre, si no está disponible o si no se puede abrir (CTL-10: nada se borra solo). */
@Composable
fun ClaimStatusScreen(state: ClaimUiState, onBack: () -> Unit, onRetry: () -> Unit, onDiscard: () -> Unit) {
    val pane = stringResource(
        when {
            state.loading -> R.string.claim_loading
            state.cannotOpen -> R.string.claim_pane_cannot_open
            else -> R.string.claim_pane_unavailable
        },
    )
    Column(Modifier.fillMaxSize().semantics { paneTitle = pane }) {
        ClaimTopBar(stringResource(R.string.claim_title), null, onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.loading -> {
                    val description = stringResource(R.string.claim_loading)
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
                    }
                }
                state.cannotOpen -> {
                    WarningBanner(stringResource(R.string.claim_cannot_open), icon = R.drawable.ic_lock)
                    SecondaryButton(stringResource(R.string.claim_discard), onDiscard)
                }
                else -> {
                    WarningBanner(stringResource(R.string.claim_unavailable), icon = R.drawable.ic_lock)
                    SecondaryButton(stringResource(R.string.claim_retry), onRetry)
                }
            }
        }
    }
}

/** Contenido de la hoja que explica un permiso, la ubicación apagada o una foto que no se guardó (mockup 3). */
data class NoticeContent(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val steps: List<Int>,
    @DrawableRes val icon: Int,
    @StringRes val action: Int?,
)

fun noticeContent(notice: CaptureNotice): NoticeContent = when (notice) {
    CaptureNotice.LOCATION_DENIED -> NoticeContent(
        R.string.claim_notice_location_title, R.string.claim_notice_location_body,
        listOf(R.string.claim_notice_location_step1, R.string.claim_notice_location_step2, R.string.claim_notice_location_step3),
        R.drawable.ic_location_off, R.string.claim_open_settings,
    )
    CaptureNotice.LOCATION_APPROXIMATE -> NoticeContent(
        R.string.claim_notice_approximate_title, R.string.claim_notice_approximate_body,
        listOf(R.string.claim_notice_location_step1, R.string.claim_notice_location_step2, R.string.claim_notice_location_step3),
        R.drawable.ic_location_off, R.string.claim_open_settings,
    )
    CaptureNotice.CAMERA_DENIED -> NoticeContent(
        R.string.claim_notice_camera_title, R.string.claim_notice_camera_body,
        listOf(R.string.claim_notice_location_step1, R.string.claim_notice_camera_step2, R.string.claim_notice_camera_step3),
        R.drawable.ic_camera_off, R.string.claim_open_settings,
    )
    CaptureNotice.NO_CAMERA -> NoticeContent(R.string.claim_notice_no_camera_title, R.string.claim_notice_no_camera_body, emptyList(), R.drawable.ic_camera_off, null)
    CaptureNotice.LOCATION_OFF -> NoticeContent(R.string.claim_notice_off_title, R.string.claim_notice_off_body, emptyList(), R.drawable.ic_location_off, R.string.claim_open_location_settings)
    CaptureNotice.LOCATION_TIMEOUT -> NoticeContent(R.string.claim_notice_timeout_title, R.string.claim_notice_timeout_body, emptyList(), R.drawable.ic_location_off, R.string.claim_try_again)
    CaptureNotice.CAPTURE_FAILED -> NoticeContent(R.string.claim_notice_failed_title, R.string.claim_notice_failed_body, emptyList(), R.drawable.ic_camera_off, R.string.claim_try_again)
}

@Composable
fun NoticeBody(notice: CaptureNotice, onAction: () -> Unit, onDismiss: () -> Unit) {
    val content = noticeContent(notice)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(SolventaTheme.colors.warningSoft),
        ) {
            Icon(painterResource(content.icon), contentDescription = null, tint = SolventaTheme.colors.warning, modifier = Modifier.size(34.dp))
        }
        Text(stringResource(content.title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Text(stringResource(content.body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content.steps.forEachIndexed { index, step ->
            val spokenStep = stringResource(R.string.claim_spoken_step, index + 1, stringResource(step))
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { text = AnnotatedString(spokenStep) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 26.dp, minHeight = 26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 4.dp),
                ) {
                    Text("${index + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.size(10.dp))
                Text(stringResource(step), style = MaterialTheme.typography.bodyMedium)
            }
        }
        content.action?.let { PrimaryButton(stringResource(it), onAction) }
        SecondaryButton(stringResource(R.string.claim_back_to_report), onDismiss)
        SecureNote(stringResource(R.string.claim_report_kept))
        Spacer(Modifier.size(8.dp))
    }
}

