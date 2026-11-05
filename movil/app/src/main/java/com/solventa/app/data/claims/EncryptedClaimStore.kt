package com.solventa.app.data.claims

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.solventa.app.data.policies.PassphraseVault
import com.solventa.app.data.policies.Unwrapped
import com.solventa.app.domain.claims.CaptureFix
import com.solventa.app.domain.claims.CapturedPhoto
import com.solventa.app.domain.claims.ClaimReport
import com.solventa.app.domain.claims.ClaimReportStore
import com.solventa.app.domain.claims.ClaimRules
import com.solventa.app.domain.claims.EvidencePhoto
import com.solventa.app.domain.claims.PhotoImage
import com.solventa.app.domain.claims.ReportState
import com.solventa.app.domain.claims.StoredReport
import com.solventa.app.domain.claims.TimeSource
import com.solventa.app.domain.claims.TrustedTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Reporte del siniestro en una base SQLCipher por cuenta, aparte de la billetera, y cada foto en su archivo con
 * AES-256-GCM (HU-MOV-08, ADR 0024). La frase solo existe en claro mientras dura cada operación. Nada se borra
 * solo: una llave invalidada o una base dañada dan CannotOpen y la persona decide descartar (CTL-10).
 */
class EncryptedClaimStore(
    private val context: Context,
    private val vault: PassphraseVault,
    private val cipher: PhotoCipher = PhotoCipher(),
    private val clock: Clock = Clock.systemUTC(),
    // La app siempre cifra con SQLCipher; las pruebas en la JVM pasan SQLite sin cifrar para probar el flujo.
    private val openHelper: (ByteArray) -> SupportSQLiteOpenHelper.Factory = ::sqlcipher,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ClaimReportStore {

    // Un turno por cuenta: ninguna operación de la misma cuenta se cruza con otra, ni con el borrado (CTL-07).
    private val turns = ConcurrentHashMap<String, Mutex>()

    override suspend fun read(accountEmail: String): StoredReport {
        val accountId = PassphraseVault.accountId(accountEmail)
        return inTurn(accountId) { load(accountId) }
    }

    override suspend fun openDraft(accountEmail: String): StoredReport {
        val accountId = PassphraseVault.accountId(accountEmail)
        return inTurn(accountId) {
            val current = load(accountId)
            if (current != StoredReport.None) return@inTurn current
            // Sin reporte se empieza de cero: base, frase y llave nuevas, sin restos de otro reporte.
            removeAll(accountId)
            val passphrase = vault.create(accountId) ?: return@inTurn StoredReport.Unavailable
            val now = clock.instant()
            val entity = ReportEntity(newId(), null, "", ReportState.DRAFT.name, now.toEpochMilli(), now.toEpochMilli())
            when (val created = withDatabase(accountId, passphrase) { it.claims().insertReport(entity) }) {
                is Opened.Done -> StoredReport.Found(entity.toReport(emptyList()))
                else -> {
                    removeAll(accountId)
                    StoredReport.Unavailable
                }
            }
        }
    }

    override suspend fun saveDetails(accountEmail: String, policyNumber: String?, description: String): Boolean {
        val accountId = PassphraseVault.accountId(accountEmail)
        val text = ClaimRules.limitDescription(description)
        return inTurn(accountId) {
            withOpenReport(accountId) { dao, report ->
                dao.updateDetails(report.id, policyNumber, text, clock.millis()) > 0
            } ?: false
        }
    }

    override suspend fun addPhoto(accountEmail: String, photo: CapturedPhoto): EvidencePhoto? {
        val accountId = PassphraseVault.accountId(accountEmail)
        if (photo.jpeg.size > ClaimRules.MAX_PHOTO_BYTES) return null
        return inTurn(accountId) {
            withOpenReport(accountId) { dao, report ->
                val photos = dao.photos(report.id)
                if (report.state != ReportState.DRAFT.name || photos.size >= ClaimRules.MAX_PHOTOS) return@withOpenReport null
                val id = newId()
                val sealed = cipher.seal(photo.jpeg)
                val file = photoFile(accountId, id)
                if (!writeAtomically(file, sealed.ciphertext)) return@withOpenReport null
                val entity = photo.toEntity(id, report.id, photos.size + 1, sealed)
                try {
                    dao.insertPhoto(entity)
                    dao.touch(report.id, clock.millis())
                } catch (e: RuntimeException) {
                    file.delete()
                    throw e
                }
                entity.toPhoto()
            }
        }
    }

    override suspend fun photoImage(accountEmail: String, photoId: String): PhotoImage {
        val accountId = PassphraseVault.accountId(accountEmail)
        return inTurn(accountId) {
            val stored = vault.existing(accountId)
            if (stored !is Unwrapped.Secret) return@inTurn PhotoImage.Unavailable
            when (val opened = withDatabase(accountId, stored.bytes) { it.claims().photo(photoId) }) {
                is Opened.Done -> opened.value?.let { decrypt(accountId, it) } ?: PhotoImage.Damaged
                else -> PhotoImage.Unavailable
            }
        }
    }

    override suspend fun deletePhoto(accountEmail: String, photoId: String): Boolean {
        val accountId = PassphraseVault.accountId(accountEmail)
        return inTurn(accountId) {
            withOpenReport(accountId) { dao, report ->
                if (report.state != ReportState.DRAFT.name) return@withOpenReport false
                val deleted = dao.deletePhoto(report.id, photoId, clock.millis())
                if (deleted) photoFile(accountId, photoId).delete()
                deleted
            } ?: false
        }
    }

    override suspend fun markReady(accountEmail: String): Boolean {
        val accountId = PassphraseVault.accountId(accountEmail)
        return inTurn(accountId) {
            withOpenReport(accountId) { dao, report -> dao.markReady(report.id, clock.millis()) > 0 } ?: false
        }
    }

    // El borrado no se puede cancelar, ni mientras espera su turno: un cierre de sesión siempre borra (AC-12).
    override suspend fun delete(accountEmail: String) = withContext(NonCancellable + Dispatchers.IO) {
        val accountId = PassphraseVault.accountId(accountEmail)
        turn(accountId).withLock { removeAll(accountId) }
    }

    // La cancelación se comprueba al tomar el turno, antes de cualquier efecto; después el trabajo termina sin
    // cancelación para no dejar la base o un archivo a medias.
    private suspend fun <T> inTurn(accountId: String, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        val result = turn(accountId).withLock {
            ensureActive()
            withContext(NonCancellable) { block() }
        }
        ensureActive()
        result
    }

    private fun turn(accountId: String): Mutex = turns.getOrPut(accountId) { Mutex() }

    private suspend fun load(accountId: String): StoredReport {
        if (!databaseExists(accountId)) {
            // Fotos o frase sin base son restos de un borrado interrumpido: no hay reporte que mostrar.
            removeAll(accountId)
            return StoredReport.None
        }
        return when (val stored = vault.existing(accountId)) {
            Unwrapped.Unavailable -> StoredReport.Unavailable
            Unwrapped.Invalid, null -> StoredReport.CannotOpen
            is Unwrapped.Secret -> when (val opened = withDatabase(accountId, stored.bytes) { db -> db.currentReport(accountId) }) {
                is Opened.Done -> opened.value?.let { StoredReport.Found(it) } ?: StoredReport.None
                Opened.Damaged -> StoredReport.CannotOpen
                Opened.Failed -> StoredReport.Unavailable
            }
        }
    }

    // Lee el reporte y borra los archivos de fotos que no tienen fila, por ejemplo tras un cierre a mitad de una foto.
    private suspend fun ClaimDatabase.currentReport(accountId: String): ClaimReport? {
        val dao = claims()
        val known = dao.photoIds().toSet()
        photoDir(accountId).listFiles()?.forEach { file ->
            if (file.name.removeSuffix(PHOTO_SUFFIX) !in known) file.delete()
        }
        val report = dao.report() ?: return null
        return report.toReport(dao.photos(report.id).map(PhotoEntity::toPhoto))
    }

    private suspend fun <T> withOpenReport(accountId: String, block: suspend (ClaimDao, ReportEntity) -> T): T? {
        val stored = vault.existing(accountId)
        if (stored !is Unwrapped.Secret) return null
        val opened = withDatabase(accountId, stored.bytes) { db ->
            val dao = db.claims()
            dao.report()?.let { block(dao, it) }
        }
        return (opened as? Opened.Done)?.value
    }

    private fun decrypt(accountId: String, photo: PhotoEntity): PhotoImage {
        val file = photoFile(accountId, photo.id)
        val ciphertext = try {
            file.readBytes()
        } catch (e: IOException) {
            return PhotoImage.Damaged
        }
        val plain = cipher.open(photo.fileKey, photo.nonce, ciphertext) ?: return PhotoImage.Damaged
        return if (PhotoCipher.sha256(plain) == photo.sha256) PhotoImage.Found(plain) else PhotoImage.Damaged
    }

    // Se escribe primero un archivo temporal, ya cifrado, y se renombra: nunca queda una foto a medias.
    private fun writeAtomically(file: File, bytes: ByteArray): Boolean {
        val dir = file.parentFile ?: return false
        if (!dir.exists() && !dir.mkdirs()) return false
        val partial = File(dir, file.name + PARTIAL_SUFFIX)
        return try {
            partial.writeBytes(bytes)
            partial.renameTo(file) || run { partial.delete(); false }
        } catch (e: IOException) {
            partial.delete()
            false
        }
    }

    private fun removeAll(accountId: String) {
        context.deleteDatabase(fileName(accountId))
        // Room deja un archivo de bloqueo vacío con el nombre de la base; se borra para no dejar ni el nombre.
        File(context.cacheDir, fileName(accountId) + ".lck").delete()
        photoDir(accountId).deleteRecursively()
        vault.delete(accountId)
    }

    private fun databaseExists(accountId: String) = context.getDatabasePath(fileName(accountId)).exists()

    private fun photoDir(accountId: String) = File(File(context.filesDir, PHOTOS_DIR), accountId)

    private fun photoFile(accountId: String, photoId: String) = File(photoDir(accountId), photoId + PHOTO_SUFFIX)

    // Se llama siempre dentro del turno, sin cancelación, así que la frase se pone en cero después de que Room
    // terminó con ella. Nunca usa migraciones destructivas: la evidencia no se puede volver a descargar (CTL-15).
    private suspend fun <T> withDatabase(accountId: String, passphrase: ByteArray, block: suspend (ClaimDatabase) -> T): Opened<T> {
        var db: ClaimDatabase? = null
        return try {
            db = Room.databaseBuilder(context, ClaimDatabase::class.java, fileName(accountId))
                .openHelperFactory(openHelper(passphrase))
                .build()
            Opened.Done(block(db))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            if (e.isDamage()) Opened.Damaged else Opened.Failed
        } finally {
            runCatching { db?.close() }
            passphrase.fill(0)
        }
    }

    private fun RuntimeException.isDamage(): Boolean =
        this is SQLiteNotADatabaseException || this is SQLiteDatabaseCorruptException ||
            (this is IllegalStateException && message?.startsWith(ROOM_INTEGRITY_FAILURE) == true)

    private fun fileName(accountId: String) = "claims_$accountId.db"

    private sealed interface Opened<out T> {
        class Done<T>(val value: T) : Opened<T>
        data object Damaged : Opened<Nothing>
        data object Failed : Opened<Nothing>
    }

    private companion object {
        const val PHOTOS_DIR = "claims"
        const val PHOTO_SUFFIX = ".bin"
        const val PARTIAL_SUFFIX = ".partial"
        const val ROOM_INTEGRITY_FAILURE = "Room cannot verify the data integrity"
        val sqlcipherLoaded: Unit by lazy { System.loadLibrary("sqlcipher") }

        fun sqlcipher(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory {
            sqlcipherLoaded
            return SupportOpenHelperFactory(passphrase)
        }
    }
}

private fun ReportEntity.toReport(photos: List<EvidencePhoto>) = ClaimReport(
    id = id,
    policyNumber = policyNumber,
    description = description,
    state = ReportState.valueOf(state),
    photos = photos,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

private fun PhotoEntity.toPhoto() = EvidencePhoto(
    id = id,
    position = position,
    capturedAt = Instant.ofEpochMilli(capturedAtEpochMillis),
    trustedTime = trustedTimeEpochMillis?.let { millis -> trustedSource?.let { TrustedTime(millis, TimeSource.valueOf(it)) } },
    fix = CaptureFix(latitude, longitude, accuracyMeters, mockLocation),
    sha256 = sha256,
    sizeBytes = sizeBytes,
)

private fun CapturedPhoto.toEntity(id: String, reportId: String, position: Int, sealed: SealedPhoto) = PhotoEntity(
    id = id,
    reportId = reportId,
    position = position,
    capturedAtEpochMillis = capturedAt.toEpochMilli(),
    trustedTimeEpochMillis = trustedTime?.epochMillis,
    trustedSource = trustedTime?.source?.name,
    latitude = fix.latitude,
    longitude = fix.longitude,
    accuracyMeters = fix.accuracyMeters,
    mockLocation = fix.mock,
    sha256 = sha256,
    sizeBytes = jpeg.size,
    fileKey = sealed.key,
    nonce = sealed.nonce,
)
