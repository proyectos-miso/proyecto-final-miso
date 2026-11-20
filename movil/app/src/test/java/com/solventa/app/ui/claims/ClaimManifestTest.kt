package com.solventa.app.ui.claims

import android.content.Context
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Permisos mínimos del manifiesto combinado (HU-MOV-08: TC-19, SR-07). */
@RunWith(RobolectricTestRunner::class)
class ClaimManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS or PackageManager.GET_CONFIGURATIONS or PackageManager.GET_ACTIVITIES,
    )

    @Test
    fun `TC-19 pide cámara y ubicación precisa y aproximada, sin segundo plano ni almacenamiento`() {
        val requested = info.requestedPermissions.orEmpty().toSet()

        assertTrue(requested.containsAll(setOf("android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")))
        listOf(
            "android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.RECORD_AUDIO",
        ).forEach { assertFalse("no debe pedir $it", it in requested) }
    }

    @Test
    fun `TC-19 la cámara y el GPS no son obligatorios`() {
        val features = info.reqFeatures.orEmpty().associate { it.name to (it.flags and FeatureInfo.FLAG_REQUIRED != 0) }

        assertEquals(false, features["android.hardware.camera.any"])
        assertEquals(false, features["android.hardware.location.gps"])
    }

    // `ComponentActivity` la agrega `ui-test-manifest`, que solo entra en debug para las pruebas de Compose.
    @Test
    fun `TC-19 no hay actividades exportadas nuevas`() {
        val exported = info.activities.orEmpty().filter { it.exported }.map { it.name }.toSet()

        assertEquals(setOf("com.solventa.app.MainActivity", "androidx.activity.ComponentActivity"), exported)
    }
}
