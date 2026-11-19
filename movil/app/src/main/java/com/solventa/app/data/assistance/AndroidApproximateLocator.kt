package com.solventa.app.data.assistance

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import com.solventa.app.domain.assistance.ApproximateLocator
import com.solventa.app.domain.assistance.Coordinates
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Una sola ubicación aproximada para buscar prestadores (mejora FE-MOV-07), con el `LocationManager` de Android y
 * sin SDK de terceros. Basta el permiso de ubicación aproximada; no queda ningún pedido activo después.
 */
class AndroidApproximateLocator(
    private val context: Context,
    private val locationManager: LocationManager = context.getSystemService(LocationManager::class.java),
    private val executor: Executor = context.mainExecutor,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) : ApproximateLocator {

    override fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)

    override suspend fun locate(): Coordinates? {
        if (!hasPermission() || !locationManager.isLocationEnabled) return null
        val provider = provider() ?: return null
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    locationManager.getCurrentLocation(provider, signal, executor) { location ->
                        if (continuation.isActive) continuation.resume(location?.let { Coordinates(it.latitude, it.longitude) })
                    }
                } catch (e: SecurityException) {
                    continuation.resume(null)
                } catch (e: IllegalArgumentException) {
                    continuation.resume(null)
                }
            }
        }
    }

    private fun provider(): String? =
        listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull(locationManager::hasProvider)

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
