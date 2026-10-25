package com.solventa.app.data.policies

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.solventa.app.domain.policies.CoverageLimit
import com.solventa.app.domain.policies.PolicyCard
import com.solventa.app.domain.policies.PolicyStore
import com.solventa.app.domain.policies.StoredCopy
import com.solventa.app.domain.policies.WalletCopy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Copia de la billetera en una base SQLCipher por cuenta (HU-MOV-04, CTL-03). La frase solo existe en
 * claro mientras dura cada operación. Solo una invalidez real descarta la copia; el bloqueo del teléfono,
 * una falla pasajera o una cancelación la conservan (HU-MOV-05).
 */
class EncryptedPolicyStore(
    private val context: Context,
    private val vault: PassphraseVault,
    // La app siempre cifra con SQLCipher; las pruebas en la JVM pasan SQLite sin cifrar para probar el flujo.
    private val openHelper: (ByteArray) -> SupportSQLiteOpenHelper.Factory = ::sqlcipher,
) : PolicyStore {

    // Un turno por cuenta: read, save y delete de la misma cuenta no se cruzan (CTL-10).
    private val turns = ConcurrentHashMap<String, Mutex>()

    override suspend fun save(accountEmail: String, policies: List<PolicyCard>, syncedAt: Instant): Boolean {
        val accountId = PassphraseVault.accountId(accountEmail)
        // Una póliza repetida haría fallar la clave primaria en cada reintento (HU-MOV-07, CTL-07).
        val unique = policies.distinctBy(PolicyCard::policyNumber)
        return inTurn(accountId) {
            val stored = vault.existing(accountId)
            val hadCopy = stored != null || databaseExists(accountId)
            if (stored == Unwrapped.Unavailable) return@inTurn false
            // Con una frase válida solo una base dañada obliga a regenerar; una falla pasajera conserva la anterior.
            if (stored is Unwrapped.Secret) {
                val outcome = write(accountId, stored.bytes, unique, syncedAt)
                if (outcome != Outcome.DAMAGED) return@inTurn outcome == Outcome.DONE
            }
            // Sin frase válida o con la base dañada se empieza de cero (AC-05, AC-06).
            discard(accountId, mark = hadCopy)
            val fresh = vault.create(accountId) ?: return@inTurn false
            val written = write(accountId, fresh, unique, syncedAt) == Outcome.DONE
            if (!written) discard(accountId, mark = hadCopy)
            written
        }
    }

    override suspend fun read(accountEmail: String): StoredCopy {
        val accountId = PassphraseVault.accountId(accountEmail)
        return inTurn(accountId) {
            if (!databaseExists(accountId)) {
                return@inTurn if (vault.wasDiscarded(accountId)) StoredCopy.Discarded else StoredCopy.Missing
            }
            when (val stored = vault.existing(accountId)) {
                Unwrapped.Unavailable -> StoredCopy.Unavailable
                Unwrapped.Invalid, null -> discarded(accountId)
                is Unwrapped.Secret -> when (val opened = withDatabase(accountId, stored.bytes) { it.storedCopy() }) {
                    is Opened.Done -> opened.value
                    Opened.Damaged -> discarded(accountId)
                    Opened.Failed -> StoredCopy.Unavailable
                }
            }
        }
    }

    // El borrado no se puede cancelar, ni mientras espera su turno: un cierre de sesión siempre borra (AC-12).
    override suspend fun delete(accountEmail: String) = withContext(NonCancellable + Dispatchers.IO) {
        val accountId = PassphraseVault.accountId(accountEmail)
        turn(accountId).withLock { discard(accountId, mark = false) }
    }

    // La cancelación se comprueba al tomar el turno, antes de cualquier efecto; después el trabajo termina
    // sin cancelación para no dejar la base a medias (CTL-08).
    private suspend fun <T> inTurn(accountId: String, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        val result = turn(accountId).withLock {
            ensureActive()
            withContext(NonCancellable) { block() }
        }
        ensureActive()
        result
    }

    private fun turn(accountId: String): Mutex = turns.getOrPut(accountId) { Mutex() }

    private suspend fun write(accountId: String, passphrase: ByteArray, policies: List<PolicyCard>, syncedAt: Instant): Outcome {
        val written = withDatabase(accountId, passphrase) { db ->
            db.policyCards().replace(policies.map(PolicyCard::toEntity), syncedAt.toEpochMilli())
        }
        return when (written) {
            is Opened.Done -> {
                vault.clearDiscarded(accountId)
                Outcome.DONE
            }
            Opened.Damaged -> Outcome.DAMAGED
            Opened.Failed -> Outcome.FAILED
        }
    }

    private suspend fun WalletDatabase.storedCopy(): StoredCopy =
        policyCards().syncedAt()?.let { millis ->
            StoredCopy.Found(WalletCopy(policyCards().all().map(PolicyCardEntity::toCard), Instant.ofEpochMilli(millis)))
        } ?: StoredCopy.Missing

    private fun discarded(accountId: String): StoredCopy {
        discard(accountId, mark = true)
        return StoredCopy.Discarded
    }

    private fun discard(accountId: String, mark: Boolean) {
        context.deleteDatabase(fileName(accountId))
        vault.delete(accountId)
        if (mark) vault.markDiscarded(accountId)
    }

    private fun databaseExists(accountId: String) = context.getDatabasePath(fileName(accountId)).exists()

    // Se llama siempre dentro del turno, sin cancelación, así que la frase se pone en cero después de que
    // Room terminó con ella (CTL-07).
    private suspend fun <T> withDatabase(accountId: String, passphrase: ByteArray, block: suspend (WalletDatabase) -> T): Opened<T> {
        var db: WalletDatabase? = null
        return try {
            db = Room.databaseBuilder(context, WalletDatabase::class.java, fileName(accountId))
                .openHelperFactory(openHelper(passphrase))
                .fallbackToDestructiveMigration()
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

    private fun fileName(accountId: String) = "wallet_$accountId.db"

    private sealed interface Opened<out T> {
        class Done<T>(val value: T) : Opened<T>
        data object Damaged : Opened<Nothing>
        data object Failed : Opened<Nothing>
    }

    private enum class Outcome { DONE, DAMAGED, FAILED }

    private companion object {
        const val ROOM_INTEGRITY_FAILURE = "Room cannot verify the data integrity"
        val sqlcipherLoaded: Unit by lazy { System.loadLibrary("sqlcipher") }

        fun sqlcipher(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory {
            sqlcipherLoaded
            return SupportOpenHelperFactory(passphrase)
        }
    }
}

internal fun PolicyCard.toEntity() = PolicyCardEntity(
    policyNumber = policyNumber,
    plan = plan,
    insuredAmount = insuredAmount,
    holderName = holderName,
    coveragesJson = JSONArray(coverages.map { JSONObject().put("name", it.name).put("limit", it.limit) }).toString(),
    validFromEpochMillis = validFrom.toEpochMilli(),
    validUntilEpochMillis = validUntil.toEpochMilli(),
)

internal fun PolicyCardEntity.toCard(): PolicyCard {
    val coverages = JSONArray(coveragesJson)
    return PolicyCard(
        policyNumber = policyNumber,
        plan = plan,
        insuredAmount = insuredAmount,
        holderName = holderName,
        coverages = (0 until coverages.length()).map { i ->
            val coverage = coverages.getJSONObject(i)
            CoverageLimit(coverage.getString("name"), coverage.getDouble("limit"))
        },
        validFrom = Instant.ofEpochMilli(validFromEpochMillis),
        validUntil = Instant.ofEpochMilli(validUntilEpochMillis),
    )
}
