package com.solventa.app.ui.assistance

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.solventa.app.R
import com.solventa.app.domain.assistance.AssistanceForm
import com.solventa.app.domain.assistance.AssistanceResult
import com.solventa.app.domain.assistance.AssistanceService
import com.solventa.app.domain.assistance.City
import com.solventa.app.domain.assistance.Provider
import com.solventa.app.ui.claims.ClaimTopBar
import com.solventa.app.ui.theme.PrimaryButton
import com.solventa.app.ui.theme.SecondaryButton
import com.solventa.app.ui.theme.WarningBanner
import com.solventa.app.ui.wallet.formatDateTime
import java.util.Locale

/** Acciones de la pantalla de prestadores. */
data class AssistanceActions(
    val onBack: () -> Unit = {},
    val onUseLocation: () -> Unit = {},
    val onShowCities: () -> Unit = {},
    val onSelectCity: (String) -> Unit = {},
    val onSelectService: (AssistanceService?) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onCall: (String) -> Unit = {},
    val onShowOnMap: (Provider) -> Unit = {},
    val onOpenForm: () -> Unit = {},
)

/** Prestadores por distancia o por ciudad, con el filtro de servicio, «Llamar» y «Ver en el mapa» (mejora FE-MOV-07). */
@Composable
fun AssistanceScreen(state: AssistanceUiState, actions: AssistanceActions, modifier: Modifier = Modifier) {
    BackHandler(onBack = actions.onBack)
    val line = stringResource(R.string.assistance_line_phone)
    Column(modifier.fillMaxSize()) {
        ClaimTopBar(title = stringResource(R.string.assistance_title), subtitle = stringResource(R.string.assistance_subtitle), onBack = actions.onBack)
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "request") { PrimaryButton(stringResource(R.string.assistance_request_button), actions.onOpenForm) }
            if (line.isNotBlank()) item(key = "line") { SecondaryButton(stringResource(R.string.assistance_call_line), { actions.onCall(line) }) }
            state.notice?.let { notice -> item(key = "notice") { Notice(notice, state) } }
            item(key = "place") { Place(state, actions) }
            item(key = "services") { ServiceFilter(state.service, actions.onSelectService) }
            when {
                state.locating -> item(key = "locating") { Loading(R.string.assistance_locating) }
                state.loading -> item(key = "loading") { Loading(R.string.assistance_loading) }
                state.hasPlace && state.providers.isEmpty() && state.notice == null ->
                    item(key = "empty") { Text(stringResource(R.string.assistance_empty), style = MaterialTheme.typography.bodyMedium) }
                else -> items(state.providers, key = { it.id }) { provider ->
                    ProviderCard(provider, onCall = { actions.onCall(provider.phone) }, onMap = { actions.onShowOnMap(provider) })
                }
            }
        }
    }
}

@Composable
private fun Notice(notice: AssistanceNotice, state: AssistanceUiState) {
    val text = when (notice) {
        AssistanceNotice.LOCATION_UNAVAILABLE -> stringResource(R.string.assistance_notice_location)
        AssistanceNotice.OFFLINE -> state.copySavedAt?.let {
            stringResource(R.string.assistance_notice_offline) + " " + stringResource(R.string.assistance_notice_saved_at, formatDateTime(it))
        } ?: stringResource(R.string.assistance_notice_offline_empty)
        AssistanceNotice.SESSION -> stringResource(R.string.assistance_notice_session)
        AssistanceNotice.CANNOT_OPEN -> stringResource(R.string.assistance_notice_cannot_open)
    }
    Box(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) { WarningBanner(text) }
}

@Composable
private fun Place(state: AssistanceUiState, actions: AssistanceActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.nearMe) {
            Text(stringResource(R.string.assistance_near_me), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        } else {
            Text(stringResource(R.string.assistance_location_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SecondaryButton(stringResource(R.string.assistance_use_location), actions.onUseLocation)
        }
        CityPicker(state.cities, state.cityCode, onOpen = actions.onShowCities, onSelect = actions.onSelectCity)
        if (state.notice == AssistanceNotice.OFFLINE) SecondaryButton(stringResource(R.string.assistance_retry), actions.onRetry)
    }
}

@Composable
private fun CityPicker(cities: List<City>, selected: String?, onOpen: () -> Unit, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val name = cities.firstOrNull { it.code == selected }?.name
    Box {
        SecondaryButton(
            text = name?.let { stringResource(R.string.assistance_city_selected, it) } ?: stringResource(R.string.assistance_choose_city),
            onClick = {
                onOpen()
                expanded = true
            },
        )
        DropdownMenu(expanded = expanded && cities.isNotEmpty(), onDismissRequest = { expanded = false }) {
            cities.forEach { city ->
                DropdownMenuItem(
                    text = { Text(city.name) },
                    onClick = {
                        expanded = false
                        onSelect(city.code)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun ServiceFilter(selected: AssistanceService?, onSelect: (AssistanceService?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.assistance_service_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).selectableGroup()) {
            ServiceChip(stringResource(R.string.assistance_service_all), selected == null) { onSelect(null) }
            AssistanceService.entries.forEach { service ->
                ServiceChip(stringResource(serviceLabel(service)), selected == service) { onSelect(service) }
            }
        }
    }
}

@Composable
private fun ServiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun ProviderCard(provider: Provider, onCall: () -> Unit, onMap: () -> Unit) {
    val services = provider.services.mapNotNull { code -> AssistanceService.entries.firstOrNull { it.name == code } }
        .map { stringResource(serviceLabel(it)) }
        .joinToString(", ")
    val distance = provider.distanceKm?.let { stringResource(R.string.assistance_distance, String.format(Locale.forLanguageTag("es-CO"), "%.1f", it)) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(provider.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                Text(services, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(provider.address, style = MaterialTheme.typography.bodyMedium)
                distance?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.heightIn(min = 4.dp))
            val callLabel = stringResource(R.string.assistance_call_provider, provider.name)
            val mapLabel = stringResource(R.string.assistance_map_provider, provider.name)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.assistance_call), onCall, Modifier.weight(1f).semantics { contentDescription = callLabel })
                SecondaryButton(stringResource(R.string.assistance_map), onMap, Modifier.weight(1f).semantics { contentDescription = mapLabel })
            }
        }
    }
}

@Composable
private fun Loading(@StringRes text: Int) {
    val description = stringResource(text)
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** Acciones del formulario «Pedir asistencia». */
data class RequestFormActions(
    val onClose: () -> Unit = {},
    val onSelectPolicy: (String) -> Unit = {},
    val onSelectService: (AssistanceService) -> Unit = {},
    val onDescribe: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onCall: (String) -> Unit = {},
)

/** Póliza, servicio y descripción; muestra el prestador asignado y su tiempo estimado (mejora FE-MOV-07). */
@Composable
fun AssistanceRequestScreen(state: AssistanceUiState, actions: RequestFormActions, modifier: Modifier = Modifier) {
    val form = state.form ?: return
    BackHandler(onBack = actions.onClose)
    val line = stringResource(R.string.assistance_line_phone)
    Column(modifier.fillMaxSize()) {
        ClaimTopBar(
            title = stringResource(R.string.assistance_form_title),
            subtitle = stringResource(R.string.assistance_form_subtitle),
            onBack = actions.onClose,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            form.assigned?.let { assigned ->
                Assigned(assigned, onCall = { actions.onCall(assigned.provider.phone) }, onDone = actions.onClose)
                return@Column
            }
            if (form.policies.isEmpty()) WarningBanner(stringResource(R.string.assistance_form_no_policy))
            if (!state.hasPlace) WarningBanner(stringResource(R.string.assistance_form_needs_place))
            Choices(
                title = stringResource(R.string.assistance_form_policy),
                options = form.policies.map { it to it },
                selected = form.policyNumber,
                onSelect = actions.onSelectPolicy,
            )
            Choices(
                title = stringResource(R.string.assistance_service_label),
                options = AssistanceService.entries.map { it.name to stringResource(serviceLabel(it)) },
                selected = form.service.name,
                onSelect = { name -> actions.onSelectService(AssistanceService.valueOf(name)) },
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = actions.onDescribe,
                label = { Text(stringResource(R.string.assistance_form_description)) },
                supportingText = {
                    Text(stringResource(R.string.assistance_form_description_help) + " ${form.description.length}/${AssistanceForm.MAX_DESCRIPTION}")
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            form.notice?.let { notice ->
                Box(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) { WarningBanner(stringResource(requestNotice(notice))) }
            }
            if (form.sending) {
                Loading(R.string.assistance_form_sending)
            } else {
                PrimaryButton(stringResource(R.string.assistance_form_send), actions.onSubmit, enabled = state.canSubmit)
            }
            if (line.isNotBlank()) SecondaryButton(stringResource(R.string.assistance_call_line), { actions.onCall(line) })
        }
    }
}

@Composable
private fun Assigned(assigned: AssistanceResult.Assigned, onCall: () -> Unit, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Text(
            stringResource(R.string.assistance_assigned_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.assistance_assigned_provider, assigned.provider.name), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.assistance_assigned_eta, assigned.etaMinutes), style = MaterialTheme.typography.bodyLarge)
    }
    val callLabel = stringResource(R.string.assistance_call_provider, assigned.provider.name)
    PrimaryButton(stringResource(R.string.assistance_call), onCall, Modifier.semantics { contentDescription = callLabel })
    SecondaryButton(stringResource(R.string.assistance_done), onDone)
}

@Composable
private fun Choices(title: String, options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    if (options.isEmpty()) return
    Column(Modifier.selectableGroup()) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
        options.forEach { (value, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) }),
            ) {
                RadioButton(selected = value == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@StringRes
internal fun serviceLabel(service: AssistanceService): Int = when (service) {
    AssistanceService.GRUA -> R.string.assistance_service_GRUA
    AssistanceService.ASISTENCIA_VIAL -> R.string.assistance_service_ASISTENCIA_VIAL
    AssistanceService.MECANICA -> R.string.assistance_service_MECANICA
    AssistanceService.CERRAJERIA -> R.string.assistance_service_CERRAJERIA
    AssistanceService.PLOMERIA -> R.string.assistance_service_PLOMERIA
    AssistanceService.ATENCION_MEDICA -> R.string.assistance_service_ATENCION_MEDICA
}

@StringRes
private fun requestNotice(notice: RequestNotice): Int = when (notice) {
    RequestNotice.OFFLINE -> R.string.assistance_request_offline
    RequestNotice.POLICY -> R.string.assistance_request_policy
    RequestNotice.NO_PROVIDER -> R.string.assistance_request_no_provider
    RequestNotice.TOO_MANY -> R.string.assistance_request_too_many
    RequestNotice.SESSION -> R.string.assistance_request_session
    RequestNotice.REJECTED -> R.string.assistance_request_rejected
}
