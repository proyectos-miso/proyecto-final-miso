package com.solventa.app.ui.wallet

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solventa.app.R
import com.solventa.app.domain.policies.CoverageLimit
import com.solventa.app.domain.policies.PolicyCard
import com.solventa.app.ui.theme.ChipTone
import com.solventa.app.ui.theme.SecondaryButton
import com.solventa.app.ui.theme.SectionLabel
import com.solventa.app.ui.theme.SolventaTheme
import com.solventa.app.ui.theme.StatusChip
import com.solventa.app.ui.theme.WarningBanner
import java.time.Instant

/** Billetera del mockup (vistas M3 y M4): saludo, carné que se voltea a las coberturas y lista de pólizas. */
@Composable
fun WalletScreen(
    uiState: WalletUiState,
    fullName: String?,
    biometricEnabled: Boolean,
    onDisableBiometrics: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var flipped by rememberSaveable { mutableStateOf(false) }
    val current = uiState.policies.getOrNull(selected) ?: uiState.policies.firstOrNull()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Greeting(fullName, uiState) }
        if (uiState.offline && !uiState.needsConnection) {
            item { WarningBanner(stringResource(R.string.wallet_offline)) }
        }
        if (uiState.stale) {
            item { WarningBanner(stringResource(R.string.wallet_stale), icon = R.drawable.ic_clock) }
        }
        when {
            uiState.loading -> item { Loading() }
            uiState.needsConnection -> item { Message(R.string.wallet_needs_connection, R.drawable.ic_cloud_off) }
            uiState.empty -> item { Message(R.string.wallet_empty, R.drawable.ic_shield) }
            current != null -> {
                item {
                    PolicyCarne(card = current, flipped = flipped, onFlip = { flipped = !flipped })
                    Text(
                        text = stringResource(if (flipped) R.string.wallet_flip_back_hint else R.string.wallet_flip_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        SectionLabel(
                            stringResource(R.string.wallet_title),
                            Modifier
                                .weight(1f)
                                .semantics { heading() },
                        )
                        Text(
                            text = pluralStringResource(R.plurals.wallet_policy_count, uiState.policies.size, uiState.policies.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(uiState.policies, key = { _, card -> card.policy.policyNumber }) { index, card ->
                    PolicyRow(
                        card = card,
                        selected = card == current,
                        onSelect = {
                            selected = index
                            flipped = false
                        },
                    )
                }
            }
        }
        uiState.lastSync?.let { lastSync ->
            item { LastSync(lastSync) }
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                SecondaryButton(stringResource(R.string.logout_button), onLogout)
                if (biometricEnabled && !uiState.offline) {
                    TextButton(onClick = onDisableBiometrics, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.biometric_disable))
                    }
                } else if (!biometricEnabled) {
                    Text(
                        text = stringResource(R.string.biometric_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LastSync(lastSync: Instant) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_clock),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.wallet_last_sync, formatDateTime(lastSync)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Greeting(fullName: String?, uiState: WalletUiState) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fullName?.substringBefore(' ')?.let { stringResource(R.string.home_title_named, it) }
                    ?: stringResource(R.string.wallet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.wallet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!uiState.loading) {
            if (uiState.offline) {
                StatusChip(stringResource(R.string.wallet_chip_offline), ChipTone.WARNING, icon = R.drawable.ic_cloud_off)
            } else {
                StatusChip(stringResource(R.string.wallet_chip_online), ChipTone.OK, icon = R.drawable.ic_check_circle)
            }
        }
    }
}

@Composable
private fun PolicyCarne(card: WalletCard, flipped: Boolean, onFlip: () -> Unit) {
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(600), label = "carne")
    val showFront = rotation <= 90f
    val density = LocalDensity.current.density
    val shape = MaterialTheme.shapes.large
    val colors = SolventaTheme.colors
    val state = stringResource(if (flipped) R.string.wallet_face_back else R.string.wallet_face_front)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 210.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .shadow(10.dp, shape)
            .clip(shape)
            .background(if (showFront) colors.cardFront else colors.cardBack)
            .clickable(
                onClickLabel = stringResource(if (flipped) R.string.wallet_flip_show_front else R.string.wallet_flip_show_coverages),
                role = Role.Button,
                onClick = onFlip,
            )
            .semantics(mergeDescendants = true) { stateDescription = state }
            .padding(20.dp),
    ) {
        if (showFront) {
            CarneFront(card)
        } else {
            Box(Modifier.graphicsLayer { rotationY = 180f }) { CarneBack(card) }
        }
    }
}

@Composable
private fun CarneFront(card: WalletCard) {
    val policy = card.policy
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_shield), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            OnCardChip(stringResource(R.string.wallet_plan, stringResource(planName(policy.plan))))
        }
        Column {
            Text(
                text = policy.policyNumber,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 1.5.sp,
            )
            Text(text = policy.holderName, color = Color.White, fontWeight = FontWeight.SemiBold)
            if (card.expired) {
                Spacer(Modifier.height(6.dp))
                StatusChip(stringResource(R.string.wallet_expired), ChipTone.ERROR)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            CardDatum(R.string.wallet_insured_amount, formatCop(policy.insuredAmount), Modifier.weight(1f))
            CardDatum(R.string.wallet_valid_until, formatDate(policy.validUntil), alignEnd = true)
        }
    }
}

@Composable
private fun CarneBack(card: WalletCard) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.wallet_coverages_title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (card.expired) {
                StatusChip(stringResource(R.string.wallet_expired), ChipTone.ERROR)
            } else {
                StatusChip(stringResource(R.string.wallet_active), ChipTone.OK)
            }
        }
        card.policy.coverages.forEach { coverage ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = Color(0xFF6EE7B7),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.wallet_coverage, coverage.name, formatCop(coverage.limit)),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            text = stringResource(R.string.wallet_validity, formatDate(card.policy.validFrom), formatDate(card.policy.validUntil)),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CardDatum(@StringRes label: Int, value: String, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(label).uppercase(),
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.6.sp,
        )
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OnCardChip(text: String) {
    Surface(color = Color.White.copy(alpha = 0.2f), contentColor = Color.White, shape = CircleShape) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun PolicyRow(card: WalletCard, selected: Boolean, onSelect: () -> Unit) {
    val policy = card.policy
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = stringResource(R.string.wallet_select_policy), role = Role.Button, onClick = onSelect)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.primaryContainer),
            ) {
                Icon(painterResource(R.drawable.ic_shield), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wallet_plan, stringResource(planName(policy.plan))),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.wallet_row_detail, policy.policyNumber, formatCop(policy.insuredAmount)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (card.expired) {
                StatusChip(stringResource(R.string.wallet_expired), ChipTone.ERROR)
            } else {
                StatusChip(stringResource(R.string.wallet_active), ChipTone.OK)
            }
        }
    }
}

@Composable
private fun Message(@StringRes text: Int, icon: Int) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(text), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Loading() {
    val description = stringResource(R.string.wallet_loading)
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
    }
}

@StringRes
private fun planName(plan: String): Int = when (plan) {
    "basic" -> R.string.wallet_plan_basic
    "premium" -> R.string.wallet_plan_premium
    else -> R.string.wallet_plan_standard
}

@Preview(showBackground = true)
@Composable
private fun WalletScreenPreview() {
    val policy = PolicyCard(
        policyNumber = "POL-2026-00001",
        plan = "premium",
        insuredAmount = 2.5e8,
        holderName = "Asegurada de Prueba",
        coverages = listOf(CoverageLimit("Muerte por cualquier causa", 2.5e8), CoverageLimit("Enfermedades graves", 1.25e8)),
        validFrom = Instant.parse("2026-01-01T00:00:00Z"),
        validUntil = Instant.parse("2027-01-01T00:00:00Z"),
    )
    SolventaTheme {
        WalletScreen(
            uiState = WalletUiState(loading = false, offline = true, policies = listOf(WalletCard(policy, false))),
            fullName = "Asegurada de Prueba",
            biometricEnabled = true,
            onDisableBiometrics = {},
            onLogout = {},
        )
    }
}
