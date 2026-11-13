package com.solventa.app.ui.claims

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solventa.app.R
import com.solventa.app.domain.claims.InsuredClaim
import com.solventa.app.ui.theme.ChipTone
import com.solventa.app.ui.theme.PrimaryButton
import com.solventa.app.ui.theme.SectionLabel
import com.solventa.app.ui.theme.StatusChip
import com.solventa.app.ui.theme.WarningBanner
import com.solventa.app.ui.wallet.formatDate
import com.solventa.app.ui.wallet.formatDateTime

/** Lista de siniestros del más reciente al más antiguo (HU-MOV-11, AC-01, AC-04, AC-06). */
@Composable
fun ClaimsListScreen(
    state: ClaimsUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onReport: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        ClaimTopBar(title = stringResource(R.string.claims_title), subtitle = null, onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.offline && !state.needsConnection) item(key = "offline") { WarningBanner(stringResource(R.string.claims_offline)) }
            when {
                state.loading -> item(key = "loading") { Loading() }
                state.needsConnection -> item(key = "needsConnection") { WarningBanner(stringResource(R.string.claims_needs_connection)) }
                state.empty -> item(key = "empty") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                        Text(stringResource(R.string.claims_empty), style = MaterialTheme.typography.bodyLarge)
                        if (onReport != null) {
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(stringResource(R.string.claim_report_button), onReport)
                        }
                    }
                }
                else -> items(state.claims, key = { it.claimNumber }) { ClaimRow(it) { onOpen(it.claimNumber) } }
            }
            state.lastUpdate?.let { item(key = "lastUpdate") { LastUpdate(it) } }
        }
    }
}

/** Línea de tiempo del siniestro con fecha y explicación; el rechazo dice el motivo y cómo pedir revisión (AC-02, AC-07). */
@Composable
fun ClaimDetailScreen(claim: InsuredClaim?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        ClaimTopBar(
            title = claim?.claimNumber ?: stringResource(R.string.claims_title),
            subtitle = claim?.let { stringResource(R.string.claims_detail_subtitle, it.policyNumber) },
            onBack = onBack,
        )
        if (claim == null) {
            Text(stringResource(R.string.claims_not_found), modifier = Modifier.padding(20.dp))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "status") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                    StatusChip(claim.statusLabel, tone(claim.status))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatDateTime(claim.lastChangeAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (claim.rejected) {
                item(key = "rejection") { Rejection(claim) }
            }
            item(key = "timelineTitle") { SectionLabel(stringResource(R.string.claims_timeline_title), Modifier.padding(bottom = 8.dp).semantics { heading() }) }
            itemsIndexed(claim.timeline, key = { i, step -> "${step.step}-$i" }) { i, step ->
                val description = stringResource(R.string.claims_step_description, step.label, formatDateTime(step.at), step.explanation)
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        if (i < claim.timeline.lastIndex) {
                            Box(Modifier.width(2.dp).height(56.dp).background(MaterialTheme.colorScheme.outline))
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 16.dp)) {
                        Text(step.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(formatDateTime(step.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(step.explanation, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Rejection(claim: InsuredClaim) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            claim.rejectionReason?.let {
                Text(stringResource(R.string.claims_rejection_title), fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text(it)
            }
            claim.reviewChannel?.let {
                Text(stringResource(R.string.claims_review_title), fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text(it)
            }
        }
    }
}

@Composable
private fun ClaimRow(claim: InsuredClaim, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.claims_row_description, claim.claimNumber, claim.policyNumber, formatDate(claim.reportedAt), claim.statusLabel)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = stringResource(R.string.claims_open_detail), role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(claim.claimNumber, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.claims_row_detail, claim.policyNumber, formatDate(claim.reportedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            StatusChip(claim.statusLabel, tone(claim.status))
        }
    }
}

@Composable
private fun LastUpdate(at: java.time.Instant) {
    Text(
        stringResource(R.string.claims_last_update, formatDateTime(at)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun Loading() {
    val description = stringResource(R.string.claims_loading)
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}

private fun tone(status: String) = when (status) {
    "REJECTED" -> ChipTone.ERROR
    "SETTLED", "PAID" -> ChipTone.OK
    else -> ChipTone.WARNING
}
