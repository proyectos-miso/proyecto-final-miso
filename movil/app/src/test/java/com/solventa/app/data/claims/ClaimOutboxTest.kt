package com.solventa.app.data.claims

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.data.policies.PassphraseVault
import com.solventa.app.data.policies.XorWalletKeys
import com.solventa.app.domain.claims.CaptureFix
import com.solventa.app.domain.claims.CapturedPhoto
import com.solventa.app.domain.claims.PhotoImage
import com.solventa.app.domain.claims.ReportState
import com.solventa.app.domain.claims.StoredReport
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Cola de envío en la base cifrada del reporte, por id de cuenta y con SQLite sin cifrar en la JVM (HU-MOV-09). */
@RunWith(RobolectricTestRunner::class)
class ClaimOutboxTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keys = XorWalletKeys()
    private val vault = PassphraseVault.forClaims(context, keys)
    private val store = EncryptedClaimStore(context, vault, clock = Clock.fixed(NOW, ZoneOffset.UTC), openHelper = { FrameworkSQLiteOpenHelperFactory() })
    private val account = PassphraseVault.accountId(EMAIL)

    @After
    fun tearDown() = runBlocking {
        store.delete(EMAIL)
        store.delete(OTHER_EMAIL)
    }

    private suspend fun queuedReport(): String {
        store.openDraft(EMAIL)
        store.addPhoto(EMAIL, captured(1))
        store.saveDetails(EMAIL, POLICY, "Golpe en la puerta")
        assertTrue(store.queue(EMAIL))
        return (store.read(EMAIL) as StoredReport.Found).report.id
    }

    @Test
    fun `AC-01 enviar deja el reporte en cola con su hora y ya no se edita`() = runBlocking {
        queuedReport()

        val report = (store.queued(account) as StoredReport.Found).report
        assertEquals(ReportState.QUEUED, report.state)
        assertEquals(NOW, report.queuedAt)
        assertFalse(report.editable)
        assertFalse(store.saveDetails(EMAIL, POLICY, "otra cosa"))
        assertEquals(null, store.addPhoto(EMAIL, captured(2)))
    }

    @Test
    fun `un borrador no está en cola y las cuentas salen de las bases del teléfono`() = runBlocking {
        store.openDraft(EMAIL)
        store.openDraft(OTHER_EMAIL)

        assertEquals(StoredReport.None, store.queued(account))
        assertEquals(setOf(account, PassphraseVault.accountId(OTHER_EMAIL)), store.accounts().toSet())
    }

    @Test
    fun `la foto se lee por el id de la cuenta y coincide con su huella`() = runBlocking {
        queuedReport()
        val photo = (store.queued(account) as StoredReport.Found).report.photos.single()

        val image = store.photo(account, photo.id) as PhotoImage.Found

        assertArrayEquals(captured(1).jpeg, image.jpeg)
    }

    @Test
    fun `AC-09 un rechazo guarda el motivo, sale de la cola y se conserva para descartarlo`() = runBlocking {
        val id = queuedReport()

        assertFalse("otro reporte no se rechaza", store.reject(account, "otro", "motivo"))
        assertTrue(store.reject(account, id, "La póliza no está vigente."))

        assertEquals(StoredReport.None, store.queued(account))
        val report = (store.read(EMAIL) as StoredReport.Found).report
        assertEquals(ReportState.REJECTED, report.state)
        assertEquals("La póliza no está vigente.", report.rejectionReason)
        assertFalse("un rechazado no vuelve a la cola", store.queue(EMAIL))
    }

    @Test
    fun `AC-04 cada intento fallido se cuenta y el reporte sigue en cola`() = runBlocking {
        val id = queuedReport()

        store.countAttempt(account, id)
        store.countAttempt(account, id)

        assertEquals(ReportState.QUEUED, (store.queued(account) as StoredReport.Found).report.state)
        assertEquals(2, attempts())
    }

    @Test
    fun `AC-08 recibido borra la base, las fotos y la frase, pero no un reporte nuevo`() = runBlocking {
        val id = queuedReport()

        store.received(account, "otro-reporte")
        assertTrue(store.read(EMAIL) is StoredReport.Found)

        store.received(account, id)
        assertEquals(StoredReport.None, store.read(EMAIL))
        assertFalse(context.getDatabasePath(DB).exists())
        assertFalse(File(File(context.filesDir, "claims"), account).exists())
        assertEquals(null, vault.existing(account))
        assertTrue(store.accounts().isEmpty())
    }

    @Test
    fun `la migración a la versión 2 conserva el reporte listo y lo deja en cola`() = runBlocking {
        assertNotNull(vault.create(account))
        writeVersionOne()

        val report = (store.queued(account) as StoredReport.Found).report

        assertEquals(ReportState.QUEUED, report.state)
        assertEquals("r-1", report.id)
        assertEquals(POLICY, report.policyNumber)
        assertEquals("Golpe en la puerta", report.description)
        assertEquals(Instant.ofEpochMilli(2_000L), report.queuedAt)
        assertEquals(null, report.rejectionReason)
        assertEquals(0, attempts())
    }

    @Test
    fun `el esquema de la versión 2 está exportado con las columnas del envío`() {
        val module = File(System.getProperty("user.dir")!!)
        val schema = File(module, "schemas/com.solventa.app.data.claims.ClaimDatabase/2.json")

        val database = JSONObject(schema.readText()).getJSONObject("database")
        assertEquals(2, database.getInt("version"))
        val reports = database.getJSONArray("entities").getJSONObject(0)
        val columns = (0 until reports.getJSONArray("fields").length()).map { reports.getJSONArray("fields").getJSONObject(it).getString("columnName") }
        assertTrue(columns.containsAll(listOf("queued_at", "rejection_reason", "attempts")))
    }

    // La base tal como la dejó HU-MOV-08: el esquema exportado de la versión 1 con un reporte listo.
    private fun writeVersionOne() {
        val module = File(System.getProperty("user.dir")!!)
        val v1 = JSONObject(File(module, "schemas/com.solventa.app.data.claims.ClaimDatabase/1.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(DB).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = v1.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = v1.getJSONArray("setupQueries")
            for (j in 0 until setup.length()) db.execSQL(setup.getString(j))
            db.execSQL("INSERT INTO reports VALUES ('r-1', '$POLICY', 'Golpe en la puerta', 'READY', 1000, 2000)")
            db.version = 1
        }
    }

    private fun attempts(): Int = SQLiteDatabase.openDatabase(context.getDatabasePath(DB).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT attempts FROM reports", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
    }

    private companion object {
        const val EMAIL = "asegurado@correo.test"
        const val OTHER_EMAIL = "otra.cuenta@correo.test"
        const val POLICY = "POL-2026-00001"
        val NOW: Instant = Instant.parse("2026-11-13T14:00:00Z")
        val DB = "claims_${PassphraseVault.accountId(EMAIL)}.db"

        fun captured(seed: Int): CapturedPhoto {
            val jpeg = ByteArray(4_096) { ((it * 31 + seed * 7) % 251).toByte() }.also {
                it[0] = 0xFF.toByte()
                it[1] = 0xD8.toByte()
            }
            return CapturedPhoto(jpeg, PhotoCipher.sha256(jpeg), NOW, null, CaptureFix(4.6512, -74.0568, 4f, mock = false))
        }
    }
}
