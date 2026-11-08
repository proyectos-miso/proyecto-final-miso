package com.solventa.app.data.claims

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "policy_number") val policyNumber: String?,
    val description: String,
    val state: String,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)

/** Datos de una foto; la imagen va cifrada en su archivo con la llave y el nonce de esta fila (CRY-03). */
@Entity(
    tableName = "photos",
    foreignKeys = [ForeignKey(entity = ReportEntity::class, parentColumns = ["id"], childColumns = ["report_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("report_id")],
)
class PhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "report_id") val reportId: String,
    val position: Int,
    @ColumnInfo(name = "captured_at") val capturedAtEpochMillis: Long,
    @ColumnInfo(name = "trusted_time") val trustedTimeEpochMillis: Long?,
    @ColumnInfo(name = "trusted_source") val trustedSource: String?,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "accuracy_m") val accuracyMeters: Float,
    @ColumnInfo(name = "mock_location") val mockLocation: Boolean,
    val sha256: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Int,
    @ColumnInfo(name = "file_key") val fileKey: ByteArray,
    val nonce: ByteArray,
)

@Dao
interface ClaimDao {
    @Query("SELECT * FROM reports LIMIT 1")
    suspend fun report(): ReportEntity?

    @Query("SELECT * FROM photos WHERE report_id = :reportId ORDER BY position")
    suspend fun photos(reportId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun photo(id: String): PhotoEntity?

    @Query("SELECT id FROM photos")
    suspend fun photoIds(): List<String>

    @Insert
    suspend fun insertReport(report: ReportEntity)

    @Insert
    suspend fun insertPhoto(photo: PhotoEntity)

    // Solo un borrador cambia; un reporte listo no se edita (AC-19).
    @Query("UPDATE reports SET policy_number = :policyNumber, description = :description, updated_at = :now WHERE id = :id AND state = 'DRAFT'")
    suspend fun updateDetails(id: String, policyNumber: String?, description: String, now: Long): Int

    @Query("UPDATE reports SET state = 'READY', updated_at = :now WHERE id = :id AND state = 'DRAFT'")
    suspend fun markReady(id: String, now: Long): Int

    @Query("UPDATE reports SET updated_at = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhotoRow(id: String): Int

    @Query("UPDATE photos SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    /** Borra la foto y deja las demás numeradas de 1 en adelante, en el orden en que se tomaron (AC-13). */
    @Transaction
    suspend fun deletePhoto(reportId: String, photoId: String, now: Long): Boolean {
        if (deletePhotoRow(photoId) == 0) return false
        photos(reportId).forEachIndexed { index, photo -> setPosition(photo.id, index + 1) }
        touch(reportId, now)
        return true
    }
}

// Versión 1. Exporta su esquema a movil/app/schemas y cambia solo con migraciones que conservan los datos (CTL-15).
@Database(entities = [ReportEntity::class, PhotoEntity::class], version = 1, exportSchema = true)
abstract class ClaimDatabase : RoomDatabase() {
    abstract fun claims(): ClaimDao
}
