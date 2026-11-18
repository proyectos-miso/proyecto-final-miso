package com.solventa.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solventa.app.R
import com.solventa.app.domain.policies.NoticeCategory
import com.solventa.app.ui.claims.ClaimTopBar
import com.solventa.app.ui.theme.SecondaryButton
import com.solventa.app.ui.theme.WarningBanner

/** Tres interruptores con su explicación; cada cambio se guarda al instante (FE-MOV-04). */
@Composable
fun NotificationPreferencesScreen(
    state: NotificationPreferencesUiState,
    onBack: () -> Unit,
    onToggle: (NoticeCategory, Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        ClaimTopBar(title = stringResource(R.string.notices_title), subtitle = stringResource(R.string.notices_subtitle), onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            if (state.offline) {
                WarningBanner(stringResource(R.string.notices_offline), icon = R.drawable.ic_cloud_off)
                if (!state.saving) SecondaryButton(stringResource(R.string.notices_retry), onRetry)
            }
            state.error?.let { error ->
                val text = when (error) {
                    PreferencesError.SESSION -> R.string.notices_error_session
                    PreferencesError.INVALID -> R.string.notices_error_invalid
                    PreferencesError.UNAVAILABLE -> R.string.notices_error
                }
                WarningBanner(stringResource(text), icon = R.drawable.ic_cloud_off)
                SecondaryButton(stringResource(R.string.notices_retry), onRetry)
            }
            if (state.loading) {
                Loading()
            } else {
                NoticeCategory.entries.forEach { category ->
                    PreferenceRow(category, state.preferences.enabled(category)) { onToggle(category, it) }
                }
            }
            Status(state)
        }
    }
}

@Composable
private fun PreferenceRow(category: NoticeCategory, checked: Boolean, onChange: (Boolean) -> Unit) {
    val label = stringResource(label(category))
    val help = stringResource(help(category))
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            // Toda la fila conmuta y TalkBack la lee como un interruptor con su nombre, su ayuda y su estado.
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .semantics(mergeDescendants = true) { contentDescription = "$label. $help" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun Status(state: NotificationPreferencesUiState) {
    val text = when {
        state.saving -> stringResource(R.string.notices_saving)
        state.saved -> stringResource(R.string.notices_saved)
        else -> null
    }
    Text(
        text = text.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun Loading() {
    val description = stringResource(R.string.notices_loading)
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}

@StringRes
private fun label(category: NoticeCategory): Int = when (category) {
    NoticeCategory.CLAIMS -> R.string.notices_claims
    NoticeCategory.RENEWALS -> R.string.notices_renewals
    NoticeCategory.PAYMENTS -> R.string.notices_payments
}

@StringRes
private fun help(category: NoticeCategory): Int = when (category) {
    NoticeCategory.CLAIMS -> R.string.notices_claims_help
    NoticeCategory.RENEWALS -> R.string.notices_renewals_help
    NoticeCategory.PAYMENTS -> R.string.notices_payments_help
}
