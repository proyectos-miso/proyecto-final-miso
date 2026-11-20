package com.solventa.app.data.claims

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.domain.claims.CaptureFix
import com.solventa.app.domain.claims.LocationAccess
import com.solventa.app.domain.claims.LocationResult
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.util.concurrent.Executor

/** Ubicación del disparo (HU-MOV-08: TC-03, TC-04, TC-05, TC-22). */
@RunWith(RobolectricTestRunner::class)
class AndroidCaptureLocatorTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val shadow = shadowOf(locationManager)
    private var nowNanos = 1_000_000_000_000L
    private val direct = Executor { it.run() }
    private val locator = AndroidCaptureLocator(app, locationManager, direct, { nowNanos }, timeout = Duration.ofMillis(300))
    private val provider get() =
        if (locationManager.hasProvider(LocationManager.FUSED_PROVIDER)) LocationManager.FUSED_PROVIDER else LocationManager.GPS_PROVIDER

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadow.setLocationEnabled(true)
        listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter(locationManager::hasProvider)
            .forEach { shadow.setProviderEnabled(it, true) }
    }

    @Test
    fun `TC-03 el permiso se lee como preciso, solo aproximado o negado`() {
        assertEquals(LocationAccess.PRECISE, locator.access())

        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(LocationAccess.APPROXIMATE_ONLY, locator.access())

        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(LocationAccess.DENIED, locator.access())
    }

    @Test
    fun `TC-03 sin ubicación precisa no hay foto y no se pide la ubicación`() = runBlocking {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        assertEquals(LocationResult.NoPermission(LocationAccess.APPROXIMATE_ONLY), locator.fixForShutter())
        locator.start()
        assertTrue(shadow.getLocationUpdateListeners().isEmpty())
    }

    @Test
    fun `TC-04 con la ubicación del teléfono apagada no hay foto`() = runBlocking {
        shadow.setLocationEnabled(false)

        assertEquals(LocationResult.Disabled, locator.fixForShutter())
    }

    @Test
    fun `TC-04 sin una ubicación buena a tiempo no hay foto`() = runBlocking {
        locator.start()

        assertEquals(LocationResult.Timeout, locator.fixForShutter())
    }

    @Test
    fun `TC-01 con la cámara abierta usa la ubicación fresca y precisa`() = runBlocking {
        locator.start()
        shadow.simulateLocation(location(accuracy = 4f))

        assertEquals(LocationResult.Fix(CaptureFix(4.6512, -74.0568, 4f, mock = false)), locator.fixForShutter())
    }

    @Test
    fun `TC-04 una ubicación de más de 10 s o peor que 100 m espera una buena`() = runBlocking {
        locator.start()
        shadow.simulateLocation(location(accuracy = 4f, ageSeconds = 11))
        val result = async { locator.fixForShutter() }
        yield()
        // Android entrega a lo sumo una ubicación por segundo: cada una llega 1,1 s después de la anterior.
        nowNanos += Duration.ofMillis(1_100).toNanos()
        shadow.simulateLocation(location(accuracy = 150f))
        delay(50)
        nowNanos += Duration.ofMillis(1_100).toNanos()
        shadow.simulateLocation(location(accuracy = 8f))

        assertEquals(LocationResult.Fix(CaptureFix(4.6512, -74.0568, 8f, mock = false)), result.await())
    }

    @Test
    fun `TC-05 una ubicación simulada queda marcada`() = runBlocking {
        locator.start()
        shadow.simulateLocation(location(accuracy = 3f).apply { isMock = true })

        assertEquals(LocationResult.Fix(CaptureFix(4.6512, -74.0568, 3f, mock = true)), locator.fixForShutter())
    }

    @Test
    fun `TC-22 solo escucha la ubicación entre start y stop`() {
        assertTrue(shadow.getLocationUpdateListeners().isEmpty())

        locator.start()
        val listening = shadow.getLocationUpdateListeners().size
        val expected = if (locationManager.hasProvider(LocationManager.FUSED_PROVIDER)) 1 else 2
        assertEquals("fused, o gps y network", expected, listening)
        locator.start()
        assertEquals("un segundo start no duplica", listening, shadow.getLocationUpdateListeners().size)

        locator.stop()
        assertTrue(shadow.getLocationUpdateListeners().isEmpty())
    }

    @Test
    fun `TC-22 al detenerse olvida la última ubicación`() = runBlocking {
        locator.start()
        shadow.simulateLocation(location(accuracy = 4f))
        locator.stop()

        assertEquals(LocationResult.Timeout, locator.fixForShutter())
    }

    private fun location(accuracy: Float, ageSeconds: Long = 0) = Location(provider).apply {
        latitude = 4.6512
        longitude = -74.0568
        this.accuracy = accuracy
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = nowNanos - Duration.ofSeconds(ageSeconds).toNanos()
    }
}
