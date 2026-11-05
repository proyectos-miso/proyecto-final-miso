package com.solventa.app.data.claims

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.SystemClock
import com.solventa.app.domain.claims.CaptureFix
import com.solventa.app.domain.claims.CaptureLocator
import com.solventa.app.domain.claims.LocationAccess
import com.solventa.app.domain.claims.LocationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.util.concurrent.Executor

/**
 * Ubicación del disparo con el `LocationManager` de Android, sin SDK de terceros (CTL-02, CTL-11). Usa el
 * proveedor `fused` del sistema o, si el teléfono no lo tiene, `gps` y `network`. Escucha cada segundo solo
 * entre [start] y [stop] (AC-20) y acepta una ubicación de 10 s o menos con precisión de 100 m o mejor (AC-01).
 */
class AndroidCaptureLocator(
    private val context: Context,
    private val locationManager: LocationManager = context.getSystemService(LocationManager::class.java),
    private val executor: Executor = context.mainExecutor,
    private val elapsedRealtimeNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val freshness: Duration = FRESHNESS,
    private val maxAccuracyMeters: Float = MAX_ACCURACY_METERS,
    private val timeout: Duration = TIMEOUT,
) : CaptureLocator {

    private val latest = MutableStateFlow<Location?>(null)
    private val fixes = MutableStateFlow<CaptureFix?>(null)
    override val latestFix: StateFlow<CaptureFix?> = fixes.asStateFlow()
    private val listeners = mutableListOf<LocationListener>()

    override fun access(): LocationAccess = when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationAccess.PRECISE
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationAccess.APPROXIMATE_ONLY
        else -> LocationAccess.DENIED
    }

    override fun isEnabled(): Boolean = locationManager.isLocationEnabled

    @Synchronized
    override fun start() {
        if (listeners.isNotEmpty() || access() != LocationAccess.PRECISE) return
        val request = LocationRequest.Builder(UPDATE_EVERY_MILLIS).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).build()
        for (provider in providers()) {
            val listener = LocationListener { location ->
                latest.value = location
                fixes.value = location.toFix()
            }
            try {
                locationManager.requestLocationUpdates(provider, request, executor, listener)
                listeners += listener
            } catch (e: SecurityException) {
                // El permiso de una sola vez pudo vencer; el disparo lo vuelve a pedir.
            } catch (e: IllegalArgumentException) {
                // El proveedor desapareció entre la consulta y el pedido.
            }
        }
    }

    // Al salir de la cámara o pasar a segundo plano no queda ningún pedido activo ni ubicación guardada (AC-20).
    @Synchronized
    override fun stop() {
        listeners.forEach(locationManager::removeUpdates)
        listeners.clear()
        latest.value = null
        fixes.value = null
    }

    override suspend fun fixForShutter(): LocationResult {
        val access = access()
        if (access != LocationAccess.PRECISE) return LocationResult.NoPermission(access)
        if (!isEnabled()) return LocationResult.Disabled
        latest.value?.takeIf(::isGood)?.let { return LocationResult.Fix(it.toFix()) }
        start()
        val fix = withTimeoutOrNull(timeout.toMillis()) { latest.first { it != null && isGood(it) } }
        return fix?.let { LocationResult.Fix(it.toFix()) } ?: LocationResult.Timeout
    }

    private fun isGood(location: Location): Boolean {
        val age = elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        return age in 0..freshness.toNanos() && location.hasAccuracy() && location.accuracy <= maxAccuracyMeters
    }

    private fun providers(): List<String> =
        if (locationManager.hasProvider(LocationManager.FUSED_PROVIDER)) {
            listOf(LocationManager.FUSED_PROVIDER)
        } else {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter(locationManager::hasProvider)
        }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.toFix() = CaptureFix(latitude, longitude, accuracy, isMock)

    companion object {
        val FRESHNESS: Duration = Duration.ofSeconds(10)
        val TIMEOUT: Duration = Duration.ofSeconds(30)
        const val MAX_ACCURACY_METERS = 100f
        private const val UPDATE_EVERY_MILLIS = 1_000L
    }
}
