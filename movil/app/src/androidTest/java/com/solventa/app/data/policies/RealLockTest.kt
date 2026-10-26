package com.solventa.app.data.policies

import android.app.KeyguardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solventa.app.domain.policies.CoverageLimit
import com.solventa.app.domain.policies.PolicyCard
import com.solventa.app.domain.policies.StoredCopy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * HU-MOV-05 TC-06: con el teléfono bloqueado de verdad, el Keystore real falla y la copia queda no
 * disponible, sin borrarse. Es manual: apaga la pantalla y espera a que la persona desbloquee. Solo corre
 * con `-Pandroid.testInstrumentationRunnerArguments.realLock=true` en un teléfono con PIN y bloqueo inmediato.
 */
@RunWith(AndroidJUnit4::class)
class RealLockTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val vault = PassphraseVault(context, KeystoreWalletKeys(keyguard::isDeviceLocked))
    private val store = EncryptedPolicyStore(context, vault)
    private val policy = PolicyCard(
        policyNumber = "POL-2026-09999",
        plan = "basic",
        insuredAmount = 5e7,
        holderName = "Asegurado de Prueba",
        coverages = listOf(CoverageLimit("Muerte por cualquier causa", 5e7)),
        validFrom = Instant.parse("2026-01-01T00:00:00Z"),
        validUntil = Instant.parse("2027-01-01T00:00:00Z"),
    )

    @After
    fun tearDown() = runBlocking { store.delete(EMAIL) }

    @Test
    fun con_el_telefono_bloqueado_la_copia_no_esta_disponible_y_se_conserva() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realLock") == "true")
        assumeTrue(keyguard.isDeviceSecure)
        store.save(EMAIL, listOf(policy), Instant.now())
        val alias = "solventa_wallet_${PassphraseVault.accountId(EMAIL)}"
        val prefs = context.getSharedPreferences("solventa_wallet", Context.MODE_PRIVATE)
        val wrapped = WrappedSecret(
            java.util.Base64.getDecoder().decode(prefs.getString("${PassphraseVault.accountId(EMAIL)}.iv", null)),
            java.util.Base64.getDecoder().decode(prefs.getString("${PassphraseVault.accountId(EMAIL)}.ct", null)),
        )

        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
        assertTrue("El teléfono no se bloqueó; active el bloqueo inmediato", waitUntil(15_000) { keyguard.isDeviceLocked })

        // Desbloqueado antes de la operación y el KeyguardManager real después, para que el Keystore real falle.
        assertEquals(Unwrapped.Unavailable, keysThatTryWhileLocked().unwrap(alias, wrapped))
        assertNull(keysThatTryWhileLocked().wrap(alias, byteArrayOf(1, 2, 3)))
        assertEquals(StoredCopy.Unavailable, store.read(EMAIL))
        store.save(EMAIL, emptyList(), Instant.now())

        assertTrue("Desbloquee el teléfono para terminar la prueba", waitUntil(120_000) { !keyguard.isDeviceLocked })
        assertEquals(listOf(policy), (store.read(EMAIL) as StoredCopy.Found).copy.policies)
    }

    private fun keysThatTryWhileLocked(): KeystoreWalletKeys {
        var first = true
        return KeystoreWalletKeys(isDeviceLocked = {
            if (first) {
                first = false
                false
            } else {
                keyguard.isDeviceLocked
            }
        })
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
        const val EMAIL = "bloqueo@example.com"
    }
}
