package com.solventa.app.data.policies

import android.app.KeyguardManager
import android.content.Context
import android.database.sqlite.SQLiteException
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solventa.app.domain.policies.CoverageLimit
import com.solventa.app.domain.policies.PolicyCard
import com.solventa.app.domain.policies.StoredCopy
import com.solventa.app.domain.policies.WalletCopy
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey
import kotlin.system.measureTimeMillis

// Copia cifrada con SQLCipher y el Keystore real del teléfono (HU-MOV-04: TC-06; HU-MOV-05).
@RunWith(AndroidJUnit4::class)
class EncryptedPolicyStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keys = KeystoreWalletKeys(context.getSystemService(KeyguardManager::class.java)::isDeviceLocked)
    private val vault = PassphraseVault(context, keys)
    private val store = EncryptedPolicyStore(context, vault)
    private val prefs = context.getSharedPreferences("solventa_wallet", Context.MODE_PRIVATE)
    private val policy = PolicyCard(
        policyNumber = "POL-2026-07777",
        plan = "standard",
        insuredAmount = 1e8,
        holderName = "Asegurado de Prueba",
        coverages = listOf(CoverageLimit("Muerte por cualquier causa", 1e8)),
        validFrom = Instant.parse("2026-01-01T00:00:00Z"),
        validUntil = Instant.parse("2027-01-01T00:00:00Z"),
    )
    private val renewed = policy.copy(policyNumber = "POL-2026-08888")

    @After
    fun tearDown() = runTest {
        store.delete(EMAIL)
        store.delete(OTHER_EMAIL)
    }

    @Test
    fun guarda_y_lee_la_copia_de_cada_cuenta() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)

        assertEquals(StoredCopy.Found(WalletCopy(listOf(policy), SYNCED_AT)), store.read(EMAIL))
        assertEquals(StoredCopy.Missing, store.read(OTHER_EMAIL))
    }

    // HU-MOV-07 TC-02: con SQLCipher y el Keystore reales, una póliza repetida se guarda una vez y save informa.
    @Test
    fun una_poliza_repetida_se_guarda_una_vez_y_save_informa_que_escribio() = runTest {
        assertTrue(store.save(EMAIL, listOf(policy, renewed, policy), SYNCED_AT))

        val copy = (store.read(EMAIL) as StoredCopy.Found).copy
        assertEquals(setOf(policy, renewed), copy.policies.toSet())
        assertEquals(2, copy.policies.size)
    }

    // HU-MOV-06 TC-04: la hora se guarda por cuenta y se borra con la copia.
    @Test
    fun cada_cuenta_recupera_su_hora_de_sincronizacion_y_se_borra_con_la_copia() = runTest {
        val other = SYNCED_AT.plusSeconds(3_600)
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        store.save(OTHER_EMAIL, emptyList(), other)

        assertEquals(SYNCED_AT, (store.read(EMAIL) as StoredCopy.Found).copy.syncedAt)
        assertEquals(other, (store.read(OTHER_EMAIL) as StoredCopy.Found).copy.syncedAt)

        store.delete(EMAIL)
        assertEquals(StoredCopy.Missing, store.read(EMAIL))
    }

    // HU-MOV-06 TC-05 y HU-MOV-05 TC-03: alterar la base (y con ella la hora) hace que se descarte.
    @Test
    fun una_base_alterada_no_devuelve_copia_ni_hora_y_se_borra() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        alter(EMAIL)

        assertEquals(StoredCopy.Discarded, store.read(EMAIL))
        assertFalse(file(EMAIL).exists())
    }

    @Test
    fun el_archivo_no_es_sqlite_legible_ni_contiene_el_numero_de_poliza() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)

        val bytes = file(EMAIL).readBytes()
        assertFalse(String(bytes.copyOfRange(0, 16), Charsets.US_ASCII).startsWith("SQLite format 3"))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(policy.policyNumber))
        assertFalse(file(EMAIL).name.contains("asegurada"))
    }

    @Test
    fun sin_la_llave_de_la_cuenta_la_copia_no_abre_y_se_borra() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        keys.delete(alias(EMAIL))

        assertEquals(StoredCopy.Discarded, store.read(EMAIL))
        assertFalse(file(EMAIL).exists())
    }

    @Test
    fun borrar_la_cuenta_elimina_su_archivo_y_conserva_las_demas() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        store.save(OTHER_EMAIL, emptyList(), SYNCED_AT)

        store.delete(EMAIL)

        assertEquals(StoredCopy.Missing, store.read(EMAIL))
        assertTrue((store.read(OTHER_EMAIL) as StoredCopy.Found).copy.policies.isEmpty())
    }

    // HU-MOV-05 TC-01: solo la frase de la cuenta abre la base, y la llave no sale del Keystore.
    @Test
    fun el_archivo_no_abre_con_otra_frase_y_la_llave_no_se_puede_exportar() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)

        assertTrue(opensWith(EMAIL, passphrase(EMAIL)))
        assertFalse(opensWith(EMAIL, ByteArray(32) { 1 }))
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias(EMAIL), null) as SecretKey
        assertNull(key.encoded)
    }

    // HU-MOV-05 TC-02: el aviso dura hasta la siguiente sincronización exitosa.
    @Test
    fun sin_la_llave_se_descarta_hasta_el_siguiente_guardado() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        keys.delete(alias(EMAIL))

        assertEquals(StoredCopy.Discarded, store.read(EMAIL))
        assertEquals(StoredCopy.Discarded, store.read(EMAIL))
        assertFalse(file(EMAIL).exists())

        store.save(EMAIL, listOf(renewed), SYNCED_AT)
        assertEquals(listOf(renewed), (store.read(EMAIL) as StoredCopy.Found).copy.policies)

        context.deleteDatabase(file(EMAIL).name)
        assertEquals(StoredCopy.Missing, store.read(EMAIL))
    }

    // HU-MOV-05 TC-02: perder la frase envuelta es perder la llave, y borrar después no falla.
    @Test
    fun sin_la_frase_envuelta_la_copia_se_descarta_y_borrar_no_falla() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        prefs.edit().remove("${id(EMAIL)}.iv").remove("${id(EMAIL)}.ct").commit()

        assertEquals(StoredCopy.Discarded, store.read(EMAIL))

        store.delete(EMAIL)
        assertEquals(StoredCopy.Missing, store.read(EMAIL))
    }

    // HU-MOV-05 TC-03: guardar sobre una base alterada la reemplaza sin lanzar.
    @Test
    fun una_base_alterada_se_reemplaza_al_guardar() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        alter(EMAIL)

        store.save(EMAIL, listOf(renewed), SYNCED_AT)

        assertEquals(listOf(renewed), (store.read(EMAIL) as StoredCopy.Found).copy.policies)
        assertFalse(vault.wasDiscarded(id(EMAIL)))
    }

    // HU-MOV-05 TC-03: un primer guardado que no puede escribir no deja frase, marca ni copia.
    @Test
    fun sin_permiso_de_escritura_el_primer_guardado_no_deja_nada() = runTest {
        store.save(OTHER_EMAIL, emptyList(), SYNCED_AT)

        withReadOnlyDatabases { store.save(EMAIL, listOf(policy), SYNCED_AT) }

        assertEquals(StoredCopy.Missing, store.read(EMAIL))
        assertNull(vault.existing(id(EMAIL)))
        assertFalse(vault.wasDiscarded(id(EMAIL)))
    }

    // HU-MOV-05 TC-03: una base que no coincide con el esquema de Room cuenta como dañada.
    @Test
    fun una_base_con_otro_esquema_se_descarta() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        open(EMAIL, passphrase(EMAIL), SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE room_master_table SET identity_hash = 'otro esquema'")
        }

        assertEquals(StoredCopy.Discarded, store.read(EMAIL))
    }

    // HU-MOV-05 TC-04: el Keystore real rechaza una frase envuelta alterada.
    @Test
    fun una_frase_envuelta_alterada_es_invalida_con_el_keystore_real() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        val ciphertext = Base64.getDecoder().decode(prefs.getString("${id(EMAIL)}.ct", null))
        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        prefs.edit().putString("${id(EMAIL)}.ct", Base64.getEncoder().encodeToString(ciphertext)).commit()

        assertEquals(Unwrapped.Invalid, vault.existing(id(EMAIL)))
    }

    // HU-MOV-05 TC-04: una frase guardada ilegible descarta la copia y el siguiente guardado la regenera.
    @Test
    fun una_frase_ilegible_descarta_la_copia_y_el_guardado_la_regenera() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        prefs.edit().putString("${id(EMAIL)}.iv", "no-es-base64!").commit()

        assertEquals(StoredCopy.Discarded, store.read(EMAIL))

        store.save(EMAIL, listOf(renewed), SYNCED_AT)
        assertEquals(listOf(renewed), (store.read(EMAIL) as StoredCopy.Found).copy.policies)
    }

    // HU-MOV-05 TC-05: con la llave no disponible la copia no se lee ni se borra.
    @Test
    fun con_la_llave_no_disponible_la_copia_se_conserva() = runTest {
        val controlled = ControlledWalletKeys(keys)
        val store = EncryptedPolicyStore(context, PassphraseVault(context, controlled))
        store.save(EMAIL, listOf(policy), SYNCED_AT)

        controlled.unavailable = true
        assertEquals(StoredCopy.Unavailable, store.read(EMAIL))
        store.save(EMAIL, listOf(renewed), SYNCED_AT)

        controlled.unavailable = false
        assertEquals(listOf(policy), (store.read(EMAIL) as StoredCopy.Found).copy.policies)
    }

    // HU-MOV-05 TC-05: con la llave no disponible el primer guardado no crea nada.
    @Test
    fun con_la_llave_no_disponible_el_primer_guardado_no_crea_nada() = runTest {
        val controlled = ControlledWalletKeys(keys).apply { unavailable = true }

        EncryptedPolicyStore(context, PassphraseVault(context, controlled)).save(EMAIL, listOf(policy), SYNCED_AT)

        assertFalse(file(EMAIL).exists())
        assertNull(vault.existing(id(EMAIL)))
        assertFalse(vault.wasDiscarded(id(EMAIL)))
    }

    // HU-MOV-05 TC-05: una falla pasajera al escribir conserva la copia anterior.
    @Test
    fun una_falla_pasajera_al_escribir_conserva_la_copia_anterior() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)

        withReadOnlyDatabases { store.save(EMAIL, listOf(renewed), SYNCED_AT) }

        assertEquals(listOf(policy), (store.read(EMAIL) as StoredCopy.Found).copy.policies)
        assertFalse(vault.wasDiscarded(id(EMAIL)))
    }

    // HU-MOV-05 TC-10: la base de otra cuenta no abre con esta frase y solo se descarta la reemplazada.
    @Test
    fun una_base_de_otra_cuenta_no_abre_y_solo_se_descarta_esa() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        store.save(OTHER_EMAIL, listOf(renewed), SYNCED_AT)
        file(EMAIL).copyTo(file(OTHER_EMAIL), overwrite = true)

        assertEquals(StoredCopy.Discarded, store.read(OTHER_EMAIL))
        assertEquals(listOf(policy), (store.read(EMAIL) as StoredCopy.Found).copy.policies)
    }

    // HU-MOV-05 TC-10: perder la llave de una cuenta no toca la otra.
    @Test
    fun borrar_la_llave_de_una_cuenta_no_toca_la_otra() = runTest {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        store.save(OTHER_EMAIL, listOf(renewed), SYNCED_AT)
        keys.delete(alias(OTHER_EMAIL))

        assertEquals(StoredCopy.Discarded, store.read(OTHER_EMAIL))
        assertEquals(listOf(policy), (store.read(EMAIL) as StoredCopy.Found).copy.policies)
    }

    // HU-MOV-05 TC-11: cancelar en cualquier momento no borra nada ni deja la base cifrada con ceros.
    @Test
    fun cancelar_lecturas_y_guardados_no_borra_ni_deja_la_base_con_ceros() = runBlocking {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        val readMillis = measureTimeMillis { store.read(EMAIL) }.coerceAtLeast(1)
        val saveMillis = measureTimeMillis { store.save(EMAIL, listOf(policy), SYNCED_AT) }.coerceAtLeast(1)

        repeat(CANCELLATIONS) { i ->
            val job = launch(Dispatchers.Default) { store.read(EMAIL) }
            delay(readMillis * i / CANCELLATIONS)
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertTrue(opensWith(EMAIL, passphrase(EMAIL)))
            assertFalse(opensWith(EMAIL, ByteArray(32)))
        }
        repeat(CANCELLATIONS) { i ->
            store.delete(OTHER_EMAIL)
            listOf(EMAIL, OTHER_EMAIL).forEach { email ->
                val job = launch(Dispatchers.Default) { store.save(email, listOf(policy), SYNCED_AT) }
                delay(saveMillis * i / CANCELLATIONS)
                job.cancelAndJoin()
                if (file(email).exists()) {
                    assertTrue(opensWith(email, passphrase(email)))
                    assertFalse(opensWith(email, ByteArray(32)))
                }
            }
        }
        assertEquals(listOf(policy), (store.read(EMAIL) as StoredCopy.Found).copy.policies)
    }

    // HU-MOV-05 TC-12: el borrado espera al guardado que ya escribe, aunque cancelen a quien lo pidió.
    @Test
    fun el_borrado_espera_al_guardado_en_curso_aunque_se_cancele() = runBlocking {
        for (cancelDelete in listOf(false, true)) {
            val controlled = ControlledWalletKeys(keys)
            val store = EncryptedPolicyStore(context, PassphraseVault(context, controlled))
            store.save(EMAIL, listOf(policy), SYNCED_AT)
            controlled.hold = CountDownLatch(1)

            val saving = async(Dispatchers.Default) { store.save(EMAIL, listOf(renewed), SYNCED_AT) }
            assertTrue(controlled.holding.await(5, TimeUnit.SECONDS))
            val deleting = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { store.delete(EMAIL) }
            if (cancelDelete) deleting.cancel()
            controlled.hold!!.countDown()
            controlled.hold = null
            saving.await()
            deleting.join()

            assertEquals(StoredCopy.Missing, store.read(EMAIL))
        }
    }

    private fun id(email: String) = PassphraseVault.accountId(email)
    private fun alias(email: String) = "solventa_wallet_${id(email)}"
    private fun file(email: String): File = context.getDatabasePath("wallet_${id(email)}.db")
    private fun passphrase(email: String) = (vault.existing(id(email)) as Unwrapped.Secret).bytes

    private fun alter(email: String) {
        val bytes = file(email).readBytes()
        for (i in 100 until bytes.size step 97) bytes[i] = (bytes[i].toInt() xor 0xFF).toByte()
        file(email).writeBytes(bytes)
    }

    // Sin el manejador por defecto de SQLCipher, que borraría el archivo al no poder abrirlo.
    private fun open(email: String, passphrase: ByteArray, flags: Int): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file(email).path, passphrase, null, flags, DatabaseErrorHandler { _, _ -> }, null as SQLiteDatabaseHook?)

    private fun opensWith(email: String, passphrase: ByteArray): Boolean = try {
        open(email, passphrase, SQLiteDatabase.OPEN_READONLY).use { it.version }
        true
    } catch (e: SQLiteException) {
        false
    }

    private suspend fun withReadOnlyDatabases(block: suspend () -> Unit) {
        val dir = file(EMAIL).parentFile!!
        val mode = Os.stat(dir.path).st_mode and 0b111_111_111
        Os.chmod(dir.path, mode and 0b101_101_101)
        try {
            block()
        } finally {
            Os.chmod(dir.path, mode)
        }
    }

    private companion object {
        const val EMAIL = "asegurada@example.com"
        const val OTHER_EMAIL = "otra.cuenta@example.com"
        const val CANCELLATIONS = 50
        val SYNCED_AT: Instant = Instant.parse("2026-10-24T15:30:00Z")
    }
}
