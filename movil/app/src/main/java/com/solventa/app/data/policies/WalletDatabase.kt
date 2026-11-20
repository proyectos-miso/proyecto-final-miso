package com.solventa.app.data.policies

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "policy_cards")
data class PolicyCardEntity(
    @PrimaryKey val policyNumber: String,
    val plan: String,
    val insuredAmount: Double,
    val holderName: String,
    val coveragesJson: String,
    val validFromEpochMillis: Long,
    val validUntilEpochMillis: Long,
)

@Entity(tableName = "wallet_meta")
data class WalletMetaEntity(
    @PrimaryKey val id: Int = 0,
    val savedAtEpochMillis: Long,
)

@Dao
interface PolicyCardDao {
    @Query("SELECT * FROM policy_cards ORDER BY validUntilEpochMillis")
    suspend fun all(): List<PolicyCardEntity>

    @Query("SELECT savedAtEpochMillis FROM wallet_meta WHERE id = 0")
    suspend fun syncedAt(): Long?

    @Transaction
    suspend fun replace(cards: List<PolicyCardEntity>, savedAtEpochMillis: Long) {
        deleteCards()
        insertCards(cards)
        saveMeta(WalletMetaEntity(savedAtEpochMillis = savedAtEpochMillis))
    }

    @Query("DELETE FROM policy_cards")
    suspend fun deleteCards()

    @Insert
    suspend fun insertCards(cards: List<PolicyCardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMeta(meta: WalletMetaEntity)
}

/** Siniestro de la cuenta en JSON, en el orden en que llegó (HU-MOV-11). */
@Entity(tableName = "insured_claims")
data class InsuredClaimEntity(
    @PrimaryKey val claimNumber: String,
    val position: Int,
    val json: String,
)

@Entity(tableName = "claims_meta")
data class ClaimsMetaEntity(
    @PrimaryKey val id: Int = 0,
    val updatedAtEpochMillis: Long,
)

/** Avisos que ya salieron, para no repetirlos (HU-MOV-11, AC-09). */
@Entity(tableName = "sent_notices")
data class SentNoticeEntity(@PrimaryKey val key: String)

@Dao
interface InsuredClaimDao {
    @Query("SELECT * FROM insured_claims ORDER BY position")
    suspend fun all(): List<InsuredClaimEntity>

    @Query("SELECT updatedAtEpochMillis FROM claims_meta WHERE id = 0")
    suspend fun updatedAt(): Long?

    @Transaction
    suspend fun replace(claims: List<InsuredClaimEntity>, updatedAtEpochMillis: Long) {
        deleteClaims()
        insertClaims(claims)
        saveMeta(ClaimsMetaEntity(updatedAtEpochMillis = updatedAtEpochMillis))
    }

    @Query("DELETE FROM insured_claims")
    suspend fun deleteClaims()

    @Insert
    suspend fun insertClaims(claims: List<InsuredClaimEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMeta(meta: ClaimsMetaEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markNotice(notice: SentNoticeEntity): Long

    @Query("SELECT * FROM claim_receipt WHERE id = 0")
    suspend fun receipt(): ClaimReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReceipt(receipt: ClaimReceiptEntity)

    @Query("DELETE FROM claim_receipt")
    suspend fun deleteReceipt(): Int
}

/** Constancia oficial del último reporte recibido, hasta que el asegurado la cierra (HU-MOV-10, AC-01). */
@Entity(tableName = "claim_receipt")
data class ClaimReceiptEntity(
    @PrimaryKey val id: Int = 0,
    val claimNumber: String,
    val provisionalNumber: String,
    val policyNumber: String?,
    val reportedAtEpochMillis: Long,
)

/** Última lista de prestadores en JSON, para verla sin conexión (mejora FE-MOV-07). */
@Entity(tableName = "providers_copy")
data class ProvidersCopyEntity(
    @PrimaryKey val id: Int = 0,
    val json: String,
    val cityCode: String?,
    val savedAtEpochMillis: Long,
)

@Dao
interface ProvidersCopyDao {
    @Query("SELECT * FROM providers_copy WHERE id = 0")
    suspend fun read(): ProvidersCopyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(copy: ProvidersCopyEntity)
}

// Versión 3 (HU-MOV-11): siniestros y avisos. La copia es un caché; una migración destructiva solo obliga a sincronizar.
// Versión 4 (HU-MOV-10): la constancia, que no se vuelve a descargar, llega con una migración que conserva los datos.
// Versión 5 (mejora FE-MOV-07): la copia de prestadores, con una migración que conserva lo anterior.
@Database(
    entities = [
        PolicyCardEntity::class, WalletMetaEntity::class, InsuredClaimEntity::class, ClaimsMetaEntity::class, SentNoticeEntity::class,
        ClaimReceiptEntity::class, ProvidersCopyEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun policyCards(): PolicyCardDao
    abstract fun claims(): InsuredClaimDao
    abstract fun providers(): ProvidersCopyDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `claim_receipt` (`id` INTEGER NOT NULL, `claimNumber` TEXT NOT NULL, " +
                        "`provisionalNumber` TEXT NOT NULL, `policyNumber` TEXT, `reportedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `providers_copy` (`id` INTEGER NOT NULL, `json` TEXT NOT NULL, `cityCode` TEXT, " +
                        "`savedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }
    }
}
