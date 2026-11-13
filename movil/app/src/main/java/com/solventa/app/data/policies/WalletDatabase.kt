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
}

// Versión 3 (HU-MOV-11): siniestros y avisos. La copia es un caché; una migración destructiva solo obliga a sincronizar.
@Database(
    entities = [PolicyCardEntity::class, WalletMetaEntity::class, InsuredClaimEntity::class, ClaimsMetaEntity::class, SentNoticeEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun policyCards(): PolicyCardDao
    abstract fun claims(): InsuredClaimDao
}
