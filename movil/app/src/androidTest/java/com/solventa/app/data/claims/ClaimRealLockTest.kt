package com.solventa.app.data.claims

import android.app.KeyguardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solventa.app.data.policies.KeystoreWalletKeys
import com.solventa.app.data.policies.PassphraseVault
import com.solventa.app.domain.claims.StoredReport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * HU-MOV-08 TC-25: con el teléfono bloqueado de verdad, la llave del reporte no se usa y el reporte queda no
 * disponible, sin borrarse; al desbloquear vuelve. Es manual: apaga la pantalla y espera a que la persona
 * desbloquee. Solo corre con `-Pandroid.testInstrumentationRunnerArguments.realLock=true` en un teléfono con
 * PIN y bloqueo inmediato, como `RealLockTest` de HU-MOV-05.
 */
@RunWith(AndroidJUnit4::class)
class ClaimRealLockTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val store = EncryptedClaimStore(context, PassphraseVault.forClaims(context, KeystoreWalletKeys(keyguard::isDeviceLocked)))

    @After
    fun tearDown() = runBlocking { store.delete(EMAIL) }

    @Test
    fun con_el_telefono_bloqueado_el_reporte_no_esta_disponible_y_se_conserva() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realLock") == "true")
        assumeTrue(keyguard.isDeviceSecure)
        store.openDraft(EMAIL)
        store.saveDetails(EMAIL, "POL-2026-00001", "Golpe en la puerta")

        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
        assertTrue("El teléfono no se bloqueó; active el bloqueo inmediato", waitUntil(15_000) { keyguard.isDeviceLocked })

        assertEquals(StoredReport.Unavailable, store.read(EMAIL))
        assertEquals(StoredReport.Unavailable, store.openDraft(EMAIL))

        assertTrue("Desbloquee el teléfono para terminar la prueba", waitUntil(120_000) { !keyguard.isDeviceLocked })
        assertEquals("Golpe en la puerta", (store.read(EMAIL) as StoredReport.Found).report.description)
    }

    private fun waitUntil(millis: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(250)
        }
        return condition()
    }

    private companion object {
        const val EMAIL = "bloqueo.reporte@example.com"
    }
}
