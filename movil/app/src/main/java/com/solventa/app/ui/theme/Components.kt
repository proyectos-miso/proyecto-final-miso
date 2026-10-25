package com.solventa.app.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solventa.app.R

/** Barra superior con la marca y el cambio de tema, como `.app-barra-movil` del mockup. */
@Composable
fun BrandTopBar(darkTheme: Boolean, onToggleTheme: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 4.dp),
        ) {
            BrandMark(size = 28)
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleTheme) {
                Icon(
                    painter = painterResource(if (darkTheme) R.drawable.ic_sun else R.drawable.ic_moon),
                    contentDescription = stringResource(if (darkTheme) R.string.theme_to_light else R.string.theme_to_dark),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun BrandMark(size: Int, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.29f).dp))
            .background(SolventaTheme.colors.brand),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shield),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Botón secundario del mockup: superficie gris con borde, no un contorno transparente. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

enum class ChipTone { OK, WARNING, ERROR, NEUTRAL }

@Composable
fun StatusChip(text: String, tone: ChipTone, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    val colors = SolventaTheme.colors
    val (background, content) = when (tone) {
        ChipTone.OK -> colors.okSoft to colors.ok
        ChipTone.WARNING -> colors.warningSoft to colors.warning
        ChipTone.ERROR -> colors.errorSoft to MaterialTheme.colorScheme.error
        ChipTone.NEUTRAL -> colors.neutralSoft to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = background, contentColor = content, shape = CircleShape, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            icon?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(14.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Aviso de sin conexión del mockup, anunciado por TalkBack al aparecer. */
@Composable
fun WarningBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = SolventaTheme.colors.warningSoft,
        contentColor = SolventaTheme.colors.warning,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_cloud_off), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SecureNote(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            painterResource(R.drawable.ic_lock),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
