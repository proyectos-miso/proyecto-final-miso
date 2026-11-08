package com.solventa.app.data.claims

import android.app.KeyguardManager
import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solventa.app.data.policies.KeystoreWalletKeys
import com.solventa.app.data.policies.PassphraseVault
import com.solventa.app.data.policies.Unwrapped
import com.solventa.app.domain.claims.CaptureFix
import com.solventa.app.domain.claims.CapturedPhoto
import com.solventa.app.domain.claims.PhotoImage
import com.solventa.app.domain.claims.StoredReport
import kotlinx.coroutines.test.runTest
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.time.Instant
import javax.crypto.SecretKey

/**
 * HU-MOV-08 TC-21: el reporte con SQLCipher y el Keystore reales del teléfono. La base no es SQLite en claro ni
 * abre con otra frase, la llave del reporte no se exporta y es distinta de la de la billetera, y la foto no es
 * un JPEG legible.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedClaimStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keys = KeystoreWalletKeys(context.getSystemService(KeyguardManager::class.java)::isDeviceLocked)
    private val vault = PassphraseVault.forClaims(context, keys)
    private val store = EncryptedClaimStore(context, vault)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val jpeg = ByteArray(64 * 1024) { ((it * 13) % 251).toByte() }.also {
        it[0] = 0xFF.toByte()
        it[1] = 0xD8.toByte()
    }

    @After
    fun tearDown() = runTest { store.delete(EMAIL) }

    @Test
    fun la_base_no_es_sqlite_legible_ni_abre_con_otra_frase() = runTest {
        store.openDraft(EMAIL)
        store.saveDetails(EMAIL, "POL-2026-00001", DESCRIPTION)

        val bytes = database().readBytes()
        assertFalse(String(bytes.copyOfRange(0, 16), Charsets.US_ASCII).startsWith("SQLite format 3"))
        assertFalse("la descripción no está en claro", String(bytes, Charsets.ISO_8859_1).contains(DESCRIPTION))
        assertTrue(opensWith((vault.existing(id()) as Unwrapped.Secret).bytes))
        assertFalse(opensWith(ByteArray(32) { 1 }))
    }

    @Test
    fun la_llave_del_reporte_esta_en_el_keystore_no_se_exporta_y_no_es_la_de_la_billetera() = runTest {
        store.openDraft(EMAIL)

        val alias = "solventa_claims_${id()}"
        assertTrue(keyStore.containsAlias(alias))
        assertNull((keyStore.getKey(alias, null) as SecretKey).encoded)
        assertFalse("el reporte no crea la llave de la billetera", keyStore.containsAlias("solventa_wallet_${id()}"))
        assertTrue(context.getSharedPreferences("solventa_claims", Context.MODE_PRIVATE).contains("${id()}.ct"))
    }

    @Test
    fun la_foto_queda_cifrada_y_vuelve_igual_con_su_huella() = runTest {
        store.openDraft(EMAIL)

        val photo = store.addPhoto(EMAIL, CapturedPhoto(jpeg, PhotoCipher.sha256(jpeg), Instant.now(), null, FIX))
        assertNotNull(photo)

        val file = File(File(File(context.filesDir, "claims"), id()), photo!!.id + ".bin")
        val stored = file.readBytes()
        assertFalse(stored[0] == 0xFF.toByte() && stored[1] == 0xD8.toByte())
        assertFalse(stored.copyOfRange(16, 80).contentEquals(jpeg.copyOfRange(16, 80)))
        assertArrayEquals(jpeg, (store.photoImage(EMAIL, photo.id) as PhotoImage.Found).jpeg)
    }

    @Test
    fun descartar_borra_la_base_las_fotos_la_frase_y_la_llave() = runTest {
        store.openDraft(EMAIL)
        store.addPhoto(EMAIL, CapturedPhoto(jpeg, PhotoCipher.sha256(jpeg), Instant.now(), null, FIX))

        store.delete(EMAIL)

        assertEquals(StoredReport.None, store.read(EMAIL))
        assertFalse(database().exists())
        assertFalse(File(File(context.filesDir, "claims"), id()).exists())
        assertFalse(keyStore.containsAlias("solventa_claims_${id()}"))
        assertNull(vault.existing(id()))
    }

    @Test
    fun sin_la_llave_no_se_puede_abrir_y_no_se_borra() = runTest {
        store.openDraft(EMAIL)
        keys.delete("solventa_claims_${id()}")

        assertEquals(StoredReport.CannotOpen, store.read(EMAIL))
        assertTrue(database().exists())
    }

    private fun id() = PassphraseVault.accountId(EMAIL)

    private fun database(): File = context.getDatabasePath("claims_${id()}.db")

    // Sin el manejador por defecto de SQLCipher, que borraría el archivo al no poder abrirlo.
    private fun opensWith(passphrase: ByteArray): Boolean = try {
        SQLiteDatabase.openDatabase(
            database().path, passphrase, null, SQLiteDatabase.OPEN_READONLY, DatabaseErrorHandler { _, _ -> }, null as SQLiteDatabaseHook?,
        ).use { it.version }
        true
    } catch (e: SQLiteException) {
        false
    }

    private companion object {
        const val EMAIL = "reporte@example.com"
        const val DESCRIPTION = "Golpe en la puerta trasera izquierda"
        val FIX = CaptureFix(4.6512, -74.0568, 4f, mock = false)
    }
}
