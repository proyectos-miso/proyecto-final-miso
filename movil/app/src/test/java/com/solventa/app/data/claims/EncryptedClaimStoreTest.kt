package com.solventa.app.data.claims

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.data.policies.PassphraseVault
import com.solventa.app.data.policies.XorWalletKeys
import com.solventa.app.domain.claims.CaptureFix
import com.solventa.app.domain.claims.CapturedPhoto
import com.solventa.app.domain.claims.ClaimRules
import com.solventa.app.domain.claims.PhotoImage
import com.solventa.app.domain.claims.ReportState
import com.solventa.app.domain.claims.StoredReport
import com.solventa.app.domain.claims.TimeSource
import com.solventa.app.domain.claims.TrustedTime
import com.solventa.app.domain.claims.sha256Hex
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant

/**
 * Almacén del reporte con SQLite sin cifrar en la JVM (HU-MOV-08: TC-06, TC-07, TC-08, TC-09, TC-10, TC-15 y
 * TC-24). El cifrado con SQLCipher y el Keystore real se prueban en el teléfono, en `EncryptedClaimStoreTest`
 * de androidTest (TC-21).
 */
@RunWith(RobolectricTestRunner::class)
class EncryptedClaimStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keys = XorWalletKeys()
    private val vault = PassphraseVault.forClaims(context, keys)

    // Cada apertura de la base toma la siguiente falla pendiente; sin fallas abre SQLite.
    private val failures = ArrayDeque<RuntimeException>()
    private val store = EncryptedClaimStore(context, vault, openHelper = { _ ->
        failures.removeFirstOrNull()?.let { throw it }
        FrameworkSQLiteOpenHelperFactory()
    })

    @After
    fun tearDown() = runBlocking {
        failures.clear()
        store.delete(EMAIL)
        store.delete(OTHER_EMAIL)
    }

    @Test
    fun `sin reporte no hay nada y al abrir el borrador empieza vacío`() = runBlocking {
        assertEquals(StoredReport.None, store.read(EMAIL))

        val report = (store.openDraft(EMAIL) as StoredReport.Found).report

        assertEquals(ReportState.DRAFT, report.state)
        assertNull(report.policyNumber)
        assertEquals("", report.description)
        assertTrue(report.photos.isEmpty())
        assertEquals(StoredReport.Found(report), store.read(EMAIL))
    }

    @Test
    fun `TC-01 guarda la foto con su ubicación, precisión y horas`() = runBlocking {
        store.openDraft(EMAIL)
        val photo = captured(1, trusted = TrustedTime(1_000L, TimeSource.GNSS))

        val saved = store.addPhoto(EMAIL, photo)
        assertNotNull(saved)

        val stored = report(EMAIL).photos.single()
        assertEquals(saved!!, stored)
        assertEquals(1, stored.position)
        assertEquals(photo.fix, stored.fix)
        assertEquals(photo.capturedAt, stored.capturedAt)
        assertEquals(TrustedTime(1_000L, TimeSource.GNSS), stored.trustedTime)
        assertEquals(photo.jpeg.size, stored.sizeBytes)
    }

    @Test
    fun `TC-01 sin hora independiente la foto se guarda igual`() = runBlocking {
        store.openDraft(EMAIL)

        store.addPhoto(EMAIL, captured(1, trusted = null))

        assertNull(report(EMAIL).photos.single().trustedTime)
    }

    @Test
    fun `TC-05 la ubicación simulada queda marcada en la foto`() = runBlocking {
        store.openDraft(EMAIL)

        store.addPhoto(EMAIL, captured(1, mock = true))

        assertTrue(report(EMAIL).photos.single().fix.mock)
    }

    @Test
    fun `guarda la póliza y la descripción, recortada a 500 caracteres`() = runBlocking {
        store.openDraft(EMAIL)

        assertTrue(store.saveDetails(EMAIL, POLICY, "a".repeat(501)))

        val report = report(EMAIL)
        assertEquals(POLICY, report.policyNumber)
        assertEquals(ClaimRules.MAX_DESCRIPTION, report.description.length)
    }

    @Test
    fun `TC-06 el archivo de la foto está cifrado y no hay copias en claro`() = runBlocking {
        store.openDraft(EMAIL)
        val photo = captured(1)

        val saved = store.addPhoto(EMAIL, photo)!!

        val file = photoFiles(EMAIL).single()
        assertEquals(saved.id + ".bin", file.name)
        val bytes = file.readBytes()
        assertFalse("no empieza como JPEG", bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte())
        assertFalse("no contiene los bytes de la foto", bytes.containsSlice(photo.jpeg.copyOfRange(0, 64)))
        // En la caché solo queda el archivo de bloqueo vacío que Room crea para cada base.
        val cached = context.cacheDir.walkTopDown().filter { it.isFile }.toList()
        assertTrue("solo bloqueos vacíos en la caché: $cached", cached.all { it.name.endsWith(".lck") && it.length() == 0L })
        assertTrue("sin temporales", photoDir(EMAIL).listFiles()!!.none { it.name.endsWith(".partial") })
    }

    @Test
    fun `TC-07 la huella es el SHA-256 de los bytes JPEG y la imagen vuelve igual`() = runBlocking {
        store.openDraft(EMAIL)
        val photo = captured(1)

        val saved = store.addPhoto(EMAIL, photo)!!

        assertEquals(PhotoCipher.sha256(photo.jpeg), saved.sha256)
        val image = store.photoImage(EMAIL, saved.id) as PhotoImage.Found
        assertArrayEquals(photo.jpeg, image.jpeg)
    }

    // CTL-04: el descifrado con su llave no basta; la foto debe coincidir con la huella guardada al tomarla.
    @Test
    fun `TC-07 una foto que se descifra pero no coincide con su huella queda dañada`() = runBlocking {
        store.openDraft(EMAIL)
        val photo = store.addPhoto(EMAIL, captured(1))!!
        val path = context.getDatabasePath("claims_${PassphraseVault.accountId(EMAIL)}.db").path
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE photos SET sha256 = ? WHERE id = ?", arrayOf(sha256Hex(byteArrayOf(9)), photo.id))
        }

        assertEquals(PhotoImage.Damaged, store.photoImage(EMAIL, photo.id))
    }

    @Test
    fun `TC-07 una foto alterada, cambiada por otra o sin archivo queda dañada`() = runBlocking {
        store.openDraft(EMAIL)
        val first = store.addPhoto(EMAIL, captured(1))!!
        val second = store.addPhoto(EMAIL, captured(2))!!
        val third = store.addPhoto(EMAIL, captured(3))!!

        val firstFile = File(photoDir(EMAIL), first.id + ".bin")
        firstFile.writeBytes(firstFile.readBytes().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() })
        val secondFile = File(photoDir(EMAIL), second.id + ".bin")
        val thirdFile = File(photoDir(EMAIL), third.id + ".bin")
        val thirdBytes = thirdFile.readBytes()
        thirdFile.writeBytes(secondFile.readBytes())
        secondFile.writeBytes(thirdBytes)

        assertEquals(PhotoImage.Damaged, store.photoImage(EMAIL, first.id))
        assertEquals(PhotoImage.Damaged, store.photoImage(EMAIL, second.id))
        assertEquals(PhotoImage.Damaged, store.photoImage(EMAIL, third.id))
        secondFile.delete()
        assertEquals(PhotoImage.Damaged, store.photoImage(EMAIL, second.id))
        assertEquals(3, report(EMAIL).photos.size)
    }

    @Test
    fun `TC-09 no entra una sexta foto ni una de más de 2 MB`() = runBlocking {
        store.openDraft(EMAIL)
        repeat(ClaimRules.MAX_PHOTOS) { assertNotNull(store.addPhoto(EMAIL, captured(it))) }

        assertNull(store.addPhoto(EMAIL, captured(6)))
        assertEquals(ClaimRules.MAX_PHOTOS, report(EMAIL).photos.size)
        assertEquals(ClaimRules.MAX_PHOTOS, photoFiles(EMAIL).size)

        store.delete(EMAIL)
        store.openDraft(EMAIL)
        val tooBig = CapturedPhoto(ByteArray(ClaimRules.MAX_PHOTO_BYTES + 1), "x", NOW, null, FIX)
        assertNull(store.addPhoto(EMAIL, tooBig))
        assertTrue(photoFiles(EMAIL).isEmpty())
    }

    @Test
    fun `TC-09 dos fotos a la vez con 4 guardadas solo dejan entrar una quinta`() = runBlocking {
        store.openDraft(EMAIL)
        repeat(4) { store.addPhoto(EMAIL, captured(it)) }

        val results = listOf(async { store.addPhoto(EMAIL, captured(10)) }, async { store.addPhoto(EMAIL, captured(11)) }).awaitAll()

        assertEquals(1, results.count { it != null })
        assertEquals(listOf(1, 2, 3, 4, 5), report(EMAIL).photos.map { it.position })
        assertEquals(5, photoFiles(EMAIL).size)
    }

    @Test
    fun `TC-10 borrar la segunda foto borra su archivo y deja 2 en orden`() = runBlocking {
        store.openDraft(EMAIL)
        val first = store.addPhoto(EMAIL, captured(1))!!
        val second = store.addPhoto(EMAIL, captured(2))!!
        val third = store.addPhoto(EMAIL, captured(3))!!

        assertTrue(store.deletePhoto(EMAIL, second.id))

        val photos = report(EMAIL).photos
        assertEquals(listOf(first.id, third.id), photos.map { it.id })
        assertEquals(listOf(1, 2), photos.map { it.position })
        assertEquals(setOf(first.id + ".bin", third.id + ".bin"), photoFiles(EMAIL).map { it.name }.toSet())
    }

    @Test
    fun `TC-10 descartar borra la base, las fotos, la frase y la llave`() = runBlocking {
        store.openDraft(EMAIL)
        store.addPhoto(EMAIL, captured(1))

        store.delete(EMAIL)

        assertEquals(StoredReport.None, store.read(EMAIL))
        assertFalse(context.getDatabasePath(DB).exists())
        assertFalse(photoDir(EMAIL).exists())
        assertFalse("sin el bloqueo de Room", File(context.cacheDir, "$DB.lck").exists())
        assertNull(vault.existing(PassphraseVault.accountId(EMAIL)))
        assertTrue(keys.aliases.none { it.startsWith("solventa_claims_") })
    }

    @Test
    fun `TC-10 un archivo de foto sin fila se borra al abrir el reporte`() = runBlocking {
        store.openDraft(EMAIL)
        val kept = store.addPhoto(EMAIL, captured(1))!!
        File(photoDir(EMAIL), "huerfana.bin").writeBytes(byteArrayOf(1, 2, 3))

        store.read(EMAIL)

        assertEquals(listOf(kept.id + ".bin"), photoFiles(EMAIL).map { it.name })
    }

    @Test
    fun `TC-08 cada cuenta tiene su reporte, su frase y su llave`() = runBlocking {
        store.openDraft(EMAIL)
        store.addPhoto(EMAIL, captured(1))
        store.saveDetails(EMAIL, POLICY, "Golpe en la puerta")

        assertEquals(StoredReport.None, store.read(OTHER_EMAIL))
        val other = (store.openDraft(OTHER_EMAIL) as StoredReport.Found).report
        assertTrue(other.photos.isEmpty())
        assertEquals("", other.description)
        assertEquals(2, keys.aliases.count { it.startsWith("solventa_claims_") })
        assertTrue("no comparte llave con la billetera", keys.aliases.none { it.startsWith("solventa_wallet_") })

        store.delete(OTHER_EMAIL)
        assertEquals("Golpe en la puerta", report(EMAIL).description)
    }

    @Test
    fun `TC-08 un borrado durante una foto no deja la foto después del borrado`() = runBlocking {
        store.openDraft(EMAIL)

        listOf(async { store.addPhoto(EMAIL, captured(1)) }, async { store.delete(EMAIL) }).awaitAll()

        assertEquals(StoredReport.None, store.read(EMAIL))
        assertFalse(photoDir(EMAIL).exists())
    }

    @Test
    fun `TC-24 un reporte listo no se edita ni recibe ni pierde fotos`() = runBlocking {
        store.openDraft(EMAIL)
        val photo = store.addPhoto(EMAIL, captured(1))!!
        store.saveDetails(EMAIL, POLICY, "Golpe en la puerta")

        assertTrue(store.markReady(EMAIL))

        assertFalse(store.markReady(EMAIL))
        assertFalse(store.saveDetails(EMAIL, OTHER_POLICY, "otra cosa"))
        assertNull(store.addPhoto(EMAIL, captured(2)))
        assertFalse(store.deletePhoto(EMAIL, photo.id))
        val report = report(EMAIL)
        assertEquals(ReportState.READY, report.state)
        assertEquals(POLICY, report.policyNumber)
        assertEquals(1, report.photos.size)
        assertEquals(StoredReport.Found(report), store.openDraft(EMAIL))
    }

    @Test
    fun `TC-15 con la llave invalidada no se puede abrir y no se borra`() = runBlocking {
        store.openDraft(EMAIL)
        store.addPhoto(EMAIL, captured(1))
        keys.aliases.clear()

        assertEquals(StoredReport.CannotOpen, store.read(EMAIL))
        assertEquals(StoredReport.CannotOpen, store.openDraft(EMAIL))

        assertTrue(context.getDatabasePath(DB).exists())
        assertEquals(1, photoFiles(EMAIL).size)
    }

    @Test
    fun `TC-15 con la base dañada no se puede abrir y no se borra`() = runBlocking {
        store.openDraft(EMAIL)
        store.addPhoto(EMAIL, captured(1))
        failures += SQLiteDatabaseCorruptException("dañada")

        assertEquals(StoredReport.CannotOpen, store.read(EMAIL))

        assertTrue(context.getDatabasePath(DB).exists())
        assertEquals(1, photoFiles(EMAIL).size)
    }

    @Test
    fun `TC-15 con el teléfono bloqueado o una falla pasajera queda no disponible y se conserva`() = runBlocking {
        store.openDraft(EMAIL)
        store.addPhoto(EMAIL, captured(1))

        keys.unavailable = true
        assertEquals(StoredReport.Unavailable, store.read(EMAIL))
        assertNull(store.addPhoto(EMAIL, captured(2)))
        keys.unavailable = false
        failures += IllegalStateException("pasajera")
        assertEquals(StoredReport.Unavailable, store.read(EMAIL))

        assertEquals(1, report(EMAIL).photos.size)
    }

    @Test
    fun `TC-15 si no se puede escribir el archivo no queda nada a medias y el reporte sigue`() = runBlocking {
        store.openDraft(EMAIL)
        val kept = store.addPhoto(EMAIL, captured(1))!!
        val dir = photoDir(EMAIL)
        dir.setWritable(false)
        try {
            assertNull(store.addPhoto(EMAIL, captured(2)))
        } finally {
            dir.setWritable(true)
        }

        assertEquals(listOf(kept.id), report(EMAIL).photos.map { it.id })
        assertEquals(listOf(kept.id + ".bin"), photoFiles(EMAIL).map { it.name })
    }

    @Test
    fun `sin poder usar la llave no se crea el borrador`() = runBlocking {
        keys.canWrap = false

        assertEquals(StoredReport.Unavailable, store.openDraft(EMAIL))
        assertEquals(StoredReport.None, store.read(EMAIL))
    }

    private suspend fun report(email: String) = (store.read(email) as StoredReport.Found).report

    private fun photoDir(email: String) = File(File(context.filesDir, "claims"), PassphraseVault.accountId(email))

    private fun photoFiles(email: String): List<File> = photoDir(email).listFiles()?.filter { it.isFile }.orEmpty()

    private fun ByteArray.containsSlice(slice: ByteArray): Boolean =
        (0..size - slice.size).any { start -> slice.indices.all { this[start + it] == slice[it] } }

    private companion object {
        const val EMAIL = "asegurado@correo.test"
        const val OTHER_EMAIL = "otra.cuenta@correo.test"
        const val POLICY = "POL-2026-00001"
        const val OTHER_POLICY = "POL-2026-00002"
        val NOW: Instant = Instant.parse("2026-11-05T14:00:00Z")
        val FIX = CaptureFix(4.6512, -74.0568, 4f, mock = false)
        val DB = "claims_${PassphraseVault.accountId(EMAIL)}.db"

        fun captured(seed: Int, trusted: TrustedTime? = null, mock: Boolean = false): CapturedPhoto {
            val jpeg = ByteArray(4_096) { ((it * 31 + seed * 7) % 251).toByte() }.also {
                it[0] = 0xFF.toByte()
                it[1] = 0xD8.toByte()
            }
            return CapturedPhoto(jpeg, PhotoCipher.sha256(jpeg), NOW, trusted, FIX.copy(mock = mock))
        }
    }
}
