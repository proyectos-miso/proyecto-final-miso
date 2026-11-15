package com.solventa.app.ui.claims

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solventa.app.R
import com.solventa.app.domain.claims.ClaimReceipt
import com.solventa.app.ui.theme.ChipTone
import com.solventa.app.ui.theme.SectionLabel
import com.solventa.app.ui.theme.SolventaTheme
import com.solventa.app.ui.theme.StatusChip
import com.solventa.app.ui.wallet.formatDate
import com.solventa.app.ui.wallet.formatDateTime
import java.time.Instant

/**
 * Constancia provisional del reporte en cola (HU-MOV-10, AC-02): radicado `OFF-…`, fecha y póliza, y que se
 * reemplaza por el oficial. TalkBack la lee como un solo nodo (QR-02).
 */
@Composable
fun ProvisionalReceiptCard(provisionalNumber: String, date: Instant, policyNumber: String?, modifier: Modifier = Modifier) {
    val policy = policyNumber ?: "—"
    val spoken = stringResource(R.string.receipt_provisional_spoken, provisionalNumber, formatDate(date), policy)
    ReceiptSurface(modifier.clearAndSetSemantics { contentDescription = spoken }) {
        SectionLabel(stringResource(R.string.receipt_provisional_title))
        ReceiptNumber(R.string.receipt_provisional_number, provisionalNumber)
        StatusChip(stringResource(R.string.receipt_provisional_note), ChipTone.WARNING)
        ReceiptDatum(R.string.receipt_date, formatDate(date))
        ReceiptDatum(R.string.receipt_policy, policy, mono = true)
    }
}

/**
 * Constancia oficial del reporte recibido (HU-MOV-10, AC-01): radicado `SIN-…` que reemplaza al provisional, fecha y
 * hora del registro y póliza. Se anuncia al aparecer; sus acciones miden al menos 48 dp (QR-02).
 */
@Composable
fun OfficialReceiptCard(
    receipt: ClaimReceipt,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val policy = receipt.policyNumber ?: "—"
    val date = formatDateTime(receipt.reportedAt)
    val spoken = stringResource(R.string.receipt_official_spoken, receipt.claimNumber, receipt.provisionalNumber, date, policy)
    ReceiptSurface(modifier.semantics { liveRegion = LiveRegionMode.Polite }, highlight = true) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        ) {
            Text(stringResource(R.string.receipt_received_heading), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            SectionLabel(stringResource(R.string.receipt_official_title))
            ReceiptNumber(R.string.receipt_official_number, receipt.claimNumber)
            Text(
                stringResource(R.string.receipt_replaces, receipt.provisionalNumber),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReceiptDatum(R.string.receipt_date, date)
            ReceiptDatum(R.string.receipt_policy, policy, mono = true)
        }
        if (onOpen != null || onDismiss != null) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                onDismiss?.let {
                    TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.receipt_dismiss)) }
                }
                onOpen?.let {
                    TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.receipt_open)) }
                }
            }
        }
    }
}

@Composable
private fun ReceiptSurface(modifier: Modifier, highlight: Boolean = false, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (highlight) SolventaTheme.colors.okSoft else colors.surface,
        border = BorderStroke(1.dp, if (highlight) SolventaTheme.colors.ok else colors.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun ReceiptNumber(@StringRes label: Int, number: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(number, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ReceiptDatum(@StringRes label: Int, value: String, mono: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) FontFamily.Monospace else null,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
