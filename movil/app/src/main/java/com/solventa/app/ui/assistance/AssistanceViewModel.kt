package com.solventa.app.ui.assistance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.app.domain.assistance.ApproximateLocator
import com.solventa.app.domain.assistance.Assistance
import com.solventa.app.domain.assistance.AssistanceForm
import com.solventa.app.domain.assistance.AssistanceResult
import com.solventa.app.domain.assistance.AssistanceService
import com.solventa.app.domain.assistance.CitiesResult
import com.solventa.app.domain.assistance.City
import com.solventa.app.domain.assistance.Coordinates
import com.solventa.app.domain.assistance.Provider
import com.solventa.app.domain.assistance.ProviderContact
import com.solventa.app.domain.assistance.ProviderQuery
import com.solventa.app.domain.assistance.ProvidersCopy
import com.solventa.app.domain.assistance.ProvidersCopyStore
import com.solventa.app.domain.assistance.ProvidersResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.UUID

enum class AssistanceNotice { LOCATION_UNAVAILABLE, OFFLINE, SESSION, CANNOT_OPEN }

enum class RequestNotice { OFFLINE, POLICY, NO_PROVIDER, TOO_MANY, SESSION, REJECTED }

data class RequestFormState(
    val policies: List<String>,
    val policyNumber: String?,
    val service: AssistanceService,
    val description: String = "",
    val sending: Boolean = false,
    val assigned: AssistanceResult.Assigned? = null,
    val notice: RequestNotice? = null,
)

data class AssistanceUiState(
    val nearMe: Boolean = false,
    val locating: Boolean = false,
    val cities: List<City> = emptyList(),
    val cityCode: String? = null,
    val service: AssistanceService? = null,
    val providers: List<Provider> = emptyList(),
    val loading: Boolean = false,
    /** Se muestra la última lista guardada, de esta fecha. */
    val copySavedAt: Instant? = null,
    val notice: AssistanceNotice? = null,
    val form: RequestFormState? = null,
) {
    val hasPlace: Boolean get() = nearMe || cityCode != null
    val canSubmit: Boolean
        get() = form != null && form.policyNumber != null && !form.sending && form.assigned == null && hasPlace
}

/**
 * Prestadores cercanos y «Pedir asistencia» (mejora FE-MOV-07). La ubicación es opcional: sin permiso el asegurado
 * elige la ciudad. Las coordenadas solo viven en memoria; la última lista queda en la copia cifrada de la billetera
 * para verla sin conexión. La llave de idempotencia es la del formulario: reintentar la conserva y cambiarlo la renueva.
 */
class AssistanceViewModel(
    private val assistance: Assistance,
    private val copies: ProvidersCopyStore,
    private val locator: ApproximateLocator,
    private val contact: ProviderContact,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistanceUiState())
    val uiState: StateFlow<AssistanceUiState> = _uiState.asStateFlow()

    private var account: String? = null
    private var coordinates: Coordinates? = null
    private var policies: List<String> = emptyList()
    private var idempotencyKey: String? = null
    private var job: Job? = null
    private var sendJob: Job? = null

    /** Con permiso de ubicación busca alrededor; si no, espera a que el asegurado lo dé o elija ciudad. */
    fun open(accountEmail: String, activePolicies: List<String>) {
        if (account == accountEmail) {
            policies = activePolicies
            return
        }
        reset()
        account = accountEmail
        policies = activePolicies
        if (locator.hasPermission()) useLocation() else loadCities()
    }

    fun onLocationPermission(granted: Boolean) {
        if (granted) useLocation() else loadCities()
    }

    fun useLocation() {
        job?.cancel()
        _uiState.value = _uiState.value.copy(locating = true, notice = null)
        job = viewModelScope.launch {
            val found = locator.locate()
            if (found == null) {
                _uiState.value = _uiState.value.copy(locating = false, notice = AssistanceNotice.LOCATION_UNAVAILABLE)
                fetchCities()
                return@launch
            }
            coordinates = found
            _uiState.value = _uiState.value.copy(locating = false, nearMe = true, cityCode = null)
            fetchProviders()
        }
    }

    fun selectCity(code: String) {
        coordinates = null
        _uiState.value = _uiState.value.copy(nearMe = false, cityCode = code, notice = null)
        load()
    }

    fun selectService(service: AssistanceService?) {
        _uiState.value = _uiState.value.copy(service = service)
        if (_uiState.value.hasPlace) load()
    }

    /** Cambiar a buscar por ciudad, aunque haya permiso de ubicación. */
    fun showCities() {
        if (_uiState.value.cities.isEmpty()) viewModelScope.launch { fetchCities() }
    }

    fun retry() {
        if (_uiState.value.hasPlace) load() else loadCities()
    }

    fun call(phone: String) = afterContact(contact.call(phone))

    fun showOnMap(provider: Provider) = afterContact(contact.showOnMap(provider))

    fun dismissNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    fun openForm() {
        val state = _uiState.value
        idempotencyKey = null
        _uiState.value = state.copy(
            form = RequestFormState(policies, policies.firstOrNull(), state.service ?: AssistanceService.GRUA),
        )
    }

    fun selectPolicy(policyNumber: String) = edit { it.copy(policyNumber = policyNumber) }

    fun selectFormService(service: AssistanceService) = edit { it.copy(service = service) }

    fun describe(text: String) = edit { it.copy(description = text.take(AssistanceForm.MAX_DESCRIPTION)) }

    fun submit() {
        val state = _uiState.value
        val form = state.form ?: return
        val policyNumber = form.policyNumber ?: return
        if (!state.canSubmit) return
        val key = idempotencyKey ?: newKey().also { idempotencyKey = it }
        val request = AssistanceForm(policyNumber, form.service, coordinates, if (coordinates == null) state.cityCode else null, form.description.trim())
        _uiState.value = state.copy(form = form.copy(sending = true, notice = null))
        sendJob = viewModelScope.launch {
            val result = assistance.request(key, request)
            val current = _uiState.value.form ?: return@launch
            val next = when (result) {
                is AssistanceResult.Assigned -> current.copy(sending = false, assigned = result)
                AssistanceResult.Offline -> current.copy(sending = false, notice = RequestNotice.OFFLINE)
                AssistanceResult.PolicyNotAvailable -> current.copy(sending = false, notice = RequestNotice.POLICY)
                AssistanceResult.NoProvider -> current.copy(sending = false, notice = RequestNotice.NO_PROVIDER)
                AssistanceResult.TooMany -> current.copy(sending = false, notice = RequestNotice.TOO_MANY)
                AssistanceResult.Unauthorized -> current.copy(sending = false, notice = RequestNotice.SESSION)
                AssistanceResult.Rejected -> current.copy(sending = false, notice = RequestNotice.REJECTED)
            }
            _uiState.value = _uiState.value.copy(form = next)
        }
    }

    fun closeForm() {
        sendJob?.cancel()
        idempotencyKey = null
        _uiState.value = _uiState.value.copy(form = null)
    }

    /** Al salir o cerrar sesión: nada de la búsqueda queda en memoria. */
    fun close() {
        reset()
        _uiState.value = AssistanceUiState()
    }

    override fun onCleared() {
        reset()
    }

    private fun edit(change: (RequestFormState) -> RequestFormState) {
        val form = _uiState.value.form ?: return
        if (form.sending || form.assigned != null) return
        idempotencyKey = null
        _uiState.value = _uiState.value.copy(form = change(form).copy(notice = null))
    }

    private fun afterContact(opened: Boolean) {
        if (!opened) _uiState.value = _uiState.value.copy(notice = AssistanceNotice.CANNOT_OPEN)
    }

    private fun load() {
        job?.cancel()
        job = viewModelScope.launch { fetchProviders() }
    }

    private fun loadCities() {
        job?.cancel()
        job = viewModelScope.launch { fetchCities() }
    }

    private suspend fun fetchCities() {
        if (_uiState.value.cities.isNotEmpty()) return
        _uiState.value = _uiState.value.copy(loading = true)
        _uiState.value = when (val result = assistance.cities()) {
            is CitiesResult.Loaded -> _uiState.value.copy(loading = false, cities = result.cities)
            CitiesResult.Offline -> offline()
            CitiesResult.Unauthorized -> _uiState.value.copy(loading = false, notice = AssistanceNotice.SESSION)
        }
    }

    private suspend fun fetchProviders() {
        val state = _uiState.value
        val query = ProviderQuery(coordinates, if (coordinates == null) state.cityCode else null, state.service)
        _uiState.value = state.copy(loading = true)
        _uiState.value = when (val result = assistance.providers(query)) {
            is ProvidersResult.Loaded -> {
                account?.let { copies.saveProviders(it, ProvidersCopy(result.providers, query.cityCode, clock.instant())) }
                _uiState.value.copy(loading = false, providers = result.providers, copySavedAt = null, notice = null)
            }
            ProvidersResult.Offline -> offline()
            ProvidersResult.Unauthorized -> _uiState.value.copy(loading = false, notice = AssistanceNotice.SESSION)
        }
    }

    // Sin red se muestra la última lista guardada, con el filtro de servicio aplicado en el teléfono.
    private suspend fun offline(): AssistanceUiState {
        val state = _uiState.value
        val copy = account?.let { copies.readProviders(it) }
        val service = state.service?.name
        val providers = copy?.providers?.filter { service == null || service in it.services }.orEmpty()
        return state.copy(loading = false, providers = providers, copySavedAt = copy?.savedAt, notice = AssistanceNotice.OFFLINE)
    }

    private fun reset() {
        job?.cancel()
        sendJob?.cancel()
        account = null
        coordinates = null
        idempotencyKey = null
        policies = emptyList()
    }
}
