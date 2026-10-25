package com.solventa.app.data.policies

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solventa.app.domain.policies.CoverageLimit
import com.solventa.app.domain.policies.PolicyCard
import com.solventa.app.domain.policies.WalletCopy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

// Copia cifrada con SQLCipher y el Keystore real del emulador (HU-MOV-04: TC-06).
@RunWith(AndroidJUnit4::class)
class EncryptedPolicyStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keys = KeystoreWalletKeys()
    private val store = EncryptedPolicyStore(context, PassphraseVault(context, keys))
    private val policy = PolicyCard(
        policyNumber = "POL-2026-07777",
        plan = "standard",
        insuredAmount = 1e8,
        holderName = "Asegurado de Prueba",
        coverages = listOf(CoverageLimit("Muerte por cualquier causa", 1e8)),
        validFrom = Instant.parse("2026-01-01T00:00:00Z"),
        validUntil = Instant.parse("2027-01-01T00:00:00Z"),
    )

    @After
    fun tearDown() = runTest {
        store.delete(EMAIL)
        store.delete(OTHER_EMAIL)
    }

    @Test
    fun guarda_y_lee_la_copia_de_cada_cuenta() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)

        assertEquals(WalletCopy(listOf(policy), SYNCED_AT), store.read(EMAIL))
        assertNull(store.read(OTHER_EMAIL))
    }

    // HU-MOV-06 TC-04: la hora se guarda por cuenta y se borra con la copia.
    @Test
    fun cada_cuenta_recupera_su_hora_de_sincronizacion_y_se_borra_con_la_copia() = runTest {
        val other = SYNCED_AT.plusSeconds(3_600)
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        store.save(OTHER_EMAIL, emptyList(), other)

        assertEquals(SYNCED_AT, store.read(EMAIL)!!.syncedAt)
        assertEquals(other, store.read(OTHER_EMAIL)!!.syncedAt)

        store.delete(EMAIL)
        assertNull(store.read(EMAIL))
    }

    // HU-MOV-06 TC-05: alterar la base (y con ella la hora) hace que no abra y se borre.
    @Test
    fun una_base_alterada_no_devuelve_copia_ni_hora_y_se_borra() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        val file = context.getDatabasePath("wallet_${PassphraseVault.accountId(EMAIL)}.db")
        val bytes = file.readBytes()
        for (i in 100 until bytes.size step 97) bytes[i] = (bytes[i].toInt() xor 0xFF).toByte()
        file.writeBytes(bytes)

        assertNull(store.read(EMAIL))
        assertFalse(file.exists())
    }

    @Test
    fun el_archivo_no_es_sqlite_legible_ni_contiene_el_numero_de_poliza() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)

        val file = context.getDatabasePath("wallet_${PassphraseVault.accountId(EMAIL)}.db")
        val bytes = file.readBytes()
        assertFalse(String(bytes.copyOfRange(0, 16), Charsets.US_ASCII).startsWith("SQLite format 3"))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(policy.policyNumber))
        assertFalse(file.name.contains("correo"))
    }

    @Test
    fun sin_la_llave_de_la_cuenta_la_copia_no_abre_y_se_borra() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        keys.delete("solventa_wallet_${PassphraseVault.accountId(EMAIL)}")

        assertNull(store.read(EMAIL))
        assertFalse(context.getDatabasePath("wallet_${PassphraseVault.accountId(EMAIL)}.db").exists())
    }

    @Test
    fun borrar_la_cuenta_elimina_su_archivo_y_conserva_las_demas() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        store.save(OTHER_EMAIL, emptyList(), SYNCED_AT)

        store.delete(EMAIL)

        assertNull(store.read(EMAIL))
        assertTrue(store.read(OTHER_EMAIL)!!.policies.isEmpty())
    }

    private companion object {
        const val EMAIL = "maria.ruiz@correo.co"
        const val OTHER_EMAIL = "otra.cuenta@example.com"
        val SYNCED_AT: Instant = Instant.parse("2026-10-24T15:30:00Z")
    }
}
