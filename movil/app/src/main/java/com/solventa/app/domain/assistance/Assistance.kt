package com.solventa.app.domain.assistance

import java.time.Instant
import kotlin.math.roundToLong

/** Servicios que ofrecen los prestadores; el nombre es el código del servidor (mejora FE-MOV-07). */
enum class AssistanceService { GRUA, ASISTENCIA_VIAL, MECANICA, CERRAJERIA, PLOMERIA, ATENCION_MEDICA }

data class Provider(
    val id: String,
    val name: String,
    val kind: String,
    val services: List<String>,
    val cityCode: String,
    val address: String,
    val phone: String,
    val lat: Double,
    val lng: Double,
    val distanceKm: Double? = null,
)

data class City(val code: String, val name: String)

/** Ubicación aproximada del asegurado: se redondea a tres decimales (unos 110 m) antes de salir del teléfono. */
class Coordinates(lat: Double, lng: Double) {
    val lat: Double = round3(lat)
    val lng: Double = round3(lng)

    override fun equals(other: Any?) = other is Coordinates && other.lat == lat && other.lng == lng

    override fun hashCode() = 31 * lat.hashCode() + lng.hashCode()

    // La ubicación no se escribe en registros ni en mensajes de error.
    override fun toString() = "Coordinates(…)"

    private companion object {
        fun round3(value: Double) = (value * 1000).roundToLong() / 1000.0
    }
}

/** Con [coordinates] el servidor ordena por distancia; si no, filtra por [cityCode]. */
data class ProviderQuery(val coordinates: Coordinates?, val cityCode: String?, val service: AssistanceService?)

sealed interface ProvidersResult {
    data class Loaded(val providers: List<Provider>) : ProvidersResult
    data object Offline : ProvidersResult
    data object Unauthorized : ProvidersResult

    /** El servidor no aceptó la búsqueda (422): reintentar igual no sirve, no es falta de red. */
    data object Rejected : ProvidersResult

    /** La identidad del asegurado sigue en verificación (403 con `IDENTITY_IN_REVIEW`). */
    data object IdentityInReview : ProvidersResult
}

sealed interface CitiesResult {
    data class Loaded(val cities: List<City>) : CitiesResult
    data object Offline : CitiesResult
    data object Unauthorized : CitiesResult
    data object IdentityInReview : CitiesResult
}

/** Lo que envía «Pedir asistencia»: ubicación o ciudad, nunca ambas. */
data class AssistanceForm(
    val policyNumber: String,
    val service: AssistanceService,
    val coordinates: Coordinates?,
    val cityCode: String?,
    val description: String,
) {
    companion object {
        const val MAX_DESCRIPTION = 300
    }
}

sealed interface AssistanceResult {
    /**
     * Solicitud recibida. [provider] es el prestador sugerido más cercano y [etaMinutes] un tiempo estimado si el
     * asegurado lo contacta: nadie lo despacha todavía (RISK-05).
     */
    data class Received(val requestId: String, val provider: Provider, val etaMinutes: Int) : AssistanceResult

    /** Sin red, tiempo agotado o error del servidor: se reintenta con la misma llave. */
    data object Offline : AssistanceResult

    /** La póliza no es del asegurado o no está vigente. */
    data object PolicyNotAvailable : AssistanceResult

    /** Ningún prestador ofrece ese servicio a 50 km o en la ciudad (409 con `NO_PROVIDER`). */
    data object NoProvider : AssistanceResult

    /** La llave ya se usó con otra solicitud (409 con `IDEMPOTENCY_KEY_REUSED`): se envía con una llave nueva. */
    data object KeyReused : AssistanceResult

    data object IdentityInReview : AssistanceResult

    /** Demasiadas solicitudes en la última hora. */
    data object TooMany : AssistanceResult

    data object Unauthorized : AssistanceResult
    data object Rejected : AssistanceResult
}

interface AssistanceApi {
    suspend fun providers(accessToken: String, query: ProviderQuery): ProvidersResult
    suspend fun cities(accessToken: String): CitiesResult
    suspend fun request(accessToken: String, idempotencyKey: String, form: AssistanceForm): AssistanceResult
}

/** Las mismas llamadas con el token de la sesión abierta. */
interface Assistance {
    suspend fun providers(query: ProviderQuery): ProvidersResult
    suspend fun cities(): CitiesResult
    suspend fun request(idempotencyKey: String, form: AssistanceForm): AssistanceResult
}

/** Última lista de prestadores, para verla sin conexión. */
data class ProvidersCopy(val providers: List<Provider>, val cityCode: String?, val savedAt: Instant)

/** La copia vive en la base cifrada de la billetera de la cuenta y se borra con ella. */
interface ProvidersCopyStore {
    suspend fun saveProviders(accountEmail: String, copy: ProvidersCopy): Boolean
    suspend fun readProviders(accountEmail: String): ProvidersCopy?
}

/** Ubicación aproximada del teléfono, solo cuando se pide; null sin permiso, apagada o sin respuesta a tiempo. */
interface ApproximateLocator {
    fun hasPermission(): Boolean
    suspend fun locate(): Coordinates?
}

/** Abre el marcador o la app de mapas del teléfono; false si no hay app que lo atienda. */
interface ProviderContact {
    fun call(phone: String): Boolean
    fun showOnMap(provider: Provider): Boolean
}
