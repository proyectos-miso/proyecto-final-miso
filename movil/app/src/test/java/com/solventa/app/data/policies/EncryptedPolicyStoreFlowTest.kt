package com.solventa.app.data.policies

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.domain.policies.StoredCopy
import com.solventa.app.domain.policies.WalletCopy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant

/**
 * Qué conserva, descarta o regenera el almacén, con SQLite sin cifrar en la JVM (HU-MOV-05). El cifrado
 * con SQLCipher y el Keystore real se prueban en el teléfono, en `EncryptedPolicyStoreTest`.
 */
@RunWith(RobolectricTestRunner::class)
class EncryptedPolicyStoreFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keys = XorWalletKeys()
    private val vault = PassphraseVault(context, keys)

    // Cada apertura de la base toma la siguiente falla pendiente; sin fallas abre SQLite.
    private val failures = ArrayDeque<RuntimeException>()
    private val passphrases = mutableListOf<ByteArray>()
    private val store = EncryptedPolicyStore(context, vault) { passphrase ->
        passphrases += passphrase
        failures.removeFirstOrNull()?.let { throw it }
        FrameworkSQLiteOpenHelperFactory()
    }
    private val policy = samplePolicy()
    private val renewed = samplePolicy(number = "POL-2026-00002")

    @After
    fun tearDown() = runBlocking {
        failures.clear()
        store.delete(EMAIL)
        store.delete(OTHER_EMAIL)
    }

    @Test
    fun `guarda la copia de cada cuenta y la reemplaza en el siguiente guardado`() = runBlocking {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        store.save(EMAIL, listOf(renewed), LATER)

        assertEquals(StoredCopy.Found(WalletCopy(listOf(renewed), LATER)), store.read(EMAIL))
        assertEquals(StoredCopy.Missing, store.read(OTHER_EMAIL))
    }

    // HU-MOV-07 TC-02: la copia se reemplaza sin duplicar, una póliza repetida se guarda una vez y save informa.
    @Test
    fun `reemplaza sin duplicar, quita repetidas e informa si escribio`() = runBlocking {
        val third = samplePolicy(number = "POL-2026-00003", validUntil = "2027-06-01T00:00:00Z")

        assertTrue(store.save(EMAIL, listOf(policy, renewed), SYNCED_AT))
        assertTrue(store.save(EMAIL, listOf(renewed, third), LATER))
        assertEquals(StoredCopy.Found(WalletCopy(listOf(renewed, third), LATER)), store.read(EMAIL))

        assertTrue(store.save(EMAIL, listOf(third, third, renewed), LATER))
        assertEquals(StoredCopy.Found(WalletCopy(listOf(renewed, third), LATER)), store.read(EMAIL))

        keys.unavailable = true
        assertFalse(store.save(EMAIL, listOf(policy), LATER))
        keys.unavailable = false
        failures += SQLiteException("disk I/O error")
        assertFalse(store.save(EMAIL, listOf(policy), LATER))
        keys.canWrap = false
        assertFalse(store.save(OTHER_EMAIL, listOf(policy), LATER))
    }

    @Test
    fun `borrar elimina la base y la frase de la cuenta sin tocar la otra`() = runBlocking {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        store.save(OTHER_EMAIL, listOf(renewed), SYNCED_AT)

        store.delete(EMAIL)

        assertEquals(StoredCopy.Missing, store.read(EMAIL))
        assertFalse(databaseOf(EMAIL).exists())
        assertNull(vault.existing(ACCOUNT))
        assertEquals(StoredCopy.Found(WalletCopy(listOf(renewed), SYNCED_AT)), store.read(OTHER_EMAIL))
    }

    @Test
    fun `con la llave no disponible la copia se conserva y no se crea ninguna`() = runBlocking {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        keys.unavailable = true

        assertEquals(StoredCopy.Unavailable, store.read(EMAIL))
        store.save(EMAIL, listOf(renewed), LATER)
        store.save(OTHER_EMAIL, listOf(renewed), LATER)

        keys.unavailable = false
        assertEquals(StoredCopy.Found(WalletCopy(listOf(policy), SYNCED_AT)), store.read(EMAIL))
        assertEquals(StoredCopy.Missing, store.read(OTHER_EMAIL))
        assertFalse(databaseOf(OTHER_EMAIL).exists())
    }

    @Test
    fun `sin la llave la copia se descarta, se avisa hasta el siguiente guardado y este la regenera`() = runBlocking {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        keys.aliases.clear()

        assertEquals(StoredCopy.Discarded, store.read(EMAIL))
        assertFalse(databaseOf(EMAIL).exists())
        assertEquals(StoredCopy.Discarded, store.read(EMAIL))

        store.save(EMAIL, listOf(renewed), LATER)
        assertEquals(StoredCopy.Found(WalletCopy(listOf(renewed), LATER)), store.read(EMAIL))
        assertFalse(vault.wasDiscarded(ACCOUNT))
    }

    @Test
    fun `una base que no abre o con otro esquema se descarta al leer`() = runBlocking {
        listOf(
            SQLiteNotADatabaseException("file is not a database"),
            SQLiteDatabaseCorruptException("database disk image is malformed"),
            IllegalStateException("Room cannot verify the data integrity. Looks like you've changed schema."),
        ).forEach { damage ->
            store.save(EMAIL, listOf(policy), SYNCED_AT)
            failures += damage

            assertEquals(damage.javaClass.simpleName, StoredCopy.Discarded, store.read(EMAIL))
            assertFalse(databaseOf(EMAIL).exists())
        }
    }

    @Test
    fun `una falla pasajera al leer deja la copia no disponible y la conserva`() = runBlocking {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        failures += SQLiteException("disk I/O error")
        failures += IllegalStateException("Cannot perform this operation because the connection pool has been closed.")

        assertEquals(StoredCopy.Unavailable, store.read(EMAIL))
        assertEquals(StoredCopy.Unavailable, store.read(EMAIL))
        assertEquals(StoredCopy.Found(WalletCopy(listOf(policy), SYNCED_AT)), store.read(EMAIL))
    }

    @Test
    fun `una base danada al guardar se regenera con otra frase y sin aviso`() = runBlocking {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        val before = (vault.existing(ACCOUNT) as Unwrapped.Secret).bytes
        failures += SQLiteDatabaseCorruptException("database disk image is malformed")

        store.save(EMAIL, listOf(renewed), LATER)

        assertFalse(before.contentEquals((vault.existing(ACCOUNT) as Unwrapped.Secret).bytes))
        assertEquals(StoredCopy.Found(WalletCopy(listOf(renewed), LATER)), store.read(EMAIL))
        assertFalse(vault.wasDiscarded(ACCOUNT))
    }

    @Test
    fun `una falla pasajera al guardar conserva la copia anterior`() = runBlocking {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        failures += SQLiteException("disk I/O error")

        store.save(EMAIL, listOf(renewed), LATER)

        assertEquals(StoredCopy.Found(WalletCopy(listOf(policy), SYNCED_AT)), store.read(EMAIL))
    }

    @Test
    fun `si la base regenerada tampoco se escribe no queda copia y se avisa el descarte`() = runBlocking {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        failures += SQLiteDatabaseCorruptException("database disk image is malformed")
        failures += SQLiteException("disk I/O error")

        store.save(EMAIL, listOf(renewed), LATER)

        assertFalse(databaseOf(EMAIL).exists())
        assertNull(vault.existing(ACCOUNT))
        assertEquals(StoredCopy.Discarded, store.read(EMAIL))
    }

    @Test
    fun `si tras perder la llave la frase nueva no se puede envolver se avisa el descarte`() = runBlocking {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        keys.aliases.clear()
        keys.canWrap = false

        store.save(EMAIL, listOf(renewed), LATER)

        assertFalse(databaseOf(EMAIL).exists())
        assertEquals(StoredCopy.Discarded, store.read(EMAIL))
    }

    @Test
    fun `el primer guardado sin poder envolver la frase no deja nada`() = runBlocking {
        keys.canWrap = false

        store.save(EMAIL, listOf(policy), SYNCED_AT)

        assertEquals(StoredCopy.Missing, store.read(EMAIL))
        assertFalse(databaseOf(EMAIL).exists())
    }

    // CTL-07: la frase en claro queda en ceros después de cada apertura, también si la base falla o se cancela.
    @Test
    fun `la frase queda en ceros despues de cada apertura y la cancelacion no se clasifica`() = runBlocking {
        store.save(EMAIL, listOf(policy), SYNCED_AT)
        store.read(EMAIL)
        failures += SQLiteException("disk I/O error")
        store.read(EMAIL)
        failures += CancellationException("cancelada")

        assertTrue(runCatching { store.read(EMAIL) }.exceptionOrNull() is CancellationException)
        assertEquals(StoredCopy.Found(WalletCopy(listOf(policy), SYNCED_AT)), store.read(EMAIL))
        assertEquals(5, passphrases.size)
        assertTrue(passphrases.all { bytes -> bytes.all { it == 0.toByte() } })
    }

    private fun databaseOf(email: String): File =
        context.getDatabasePath("wallet_${PassphraseVault.accountId(email)}.db")

    private companion object {
        const val EMAIL = "asegurada@example.com"
        const val OTHER_EMAIL = "otra.cuenta@example.com"
        val ACCOUNT = PassphraseVault.accountId(EMAIL)
        val SYNCED_AT: Instant = Instant.parse("2026-10-20T15:00:00Z")
        val LATER: Instant = Instant.parse("2026-10-21T15:00:00Z")
    }
}
