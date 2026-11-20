package com.solventa.app.data.policies

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * HU-MOV-10: la versión 4 de la billetera agrega la constancia sin borrar la copia, los siniestros ni los avisos
 * ya enviados de la versión 3. Mejora FE-MOV-07: la versión 5 agrega la copia de prestadores sin borrar nada.
 */
@RunWith(RobolectricTestRunner::class)
class WalletMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "wallet_migracion.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun `HU-MOV-10 la migracion de la version 3 a la 4 conserva los datos y agrega la constancia`() = runBlocking {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            VERSION_3.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO policy_cards VALUES ('POL-2026-00001', 'basic', 1000000.0, 'Asegurada de Prueba', '[]', 0, 1893456000000)",
            )
            db.execSQL("INSERT INTO wallet_meta VALUES (0, 1700000000000)")
            db.execSQL("INSERT INTO sent_notices VALUES ('renewal:POL-2026-00001')")
            db.version = 3
        }

        val room = Room.databaseBuilder(context, WalletDatabase::class.java, name)
            .addMigrations(WalletDatabase.MIGRATION_3_4, WalletDatabase.MIGRATION_4_5)
            .build()
        try {
            assertEquals(listOf("POL-2026-00001"), room.policyCards().all().map { it.policyNumber })
            assertEquals(1700000000000L, room.policyCards().syncedAt())
            assertEquals(-1L, room.claims().markNotice(SentNoticeEntity("renewal:POL-2026-00001")))
            assertNull(room.claims().receipt())

            val receipt = ClaimReceiptEntity(claimNumber = "SIN-2026-00001", provisionalNumber = "OFF-2026-AB12", policyNumber = null, reportedAtEpochMillis = 1L)
            room.claims().saveReceipt(receipt)
            assertEquals(receipt, room.claims().receipt())
        } finally {
            room.close()
        }
    }

    @Test
    fun `FE-MOV-07 la migracion de la version 4 a la 5 conserva la copia y la constancia y agrega los prestadores`() = runBlocking {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            (VERSION_3 + CLAIM_RECEIPT).forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO policy_cards VALUES ('POL-2026-00001', 'basic', 1000000.0, 'Asegurada de Prueba', '[]', 0, 1893456000000)",
            )
            db.execSQL("INSERT INTO wallet_meta VALUES (0, 1700000000000)")
            db.execSQL("INSERT INTO insured_claims VALUES ('SIN-2026-00001', 0, '{}')")
            db.execSQL("INSERT INTO claim_receipt VALUES (0, 'SIN-2026-00001', 'OFF-2026-AB12', NULL, 1)")
            db.version = 4
        }

        val room = Room.databaseBuilder(context, WalletDatabase::class.java, name)
            .addMigrations(WalletDatabase.MIGRATION_3_4, WalletDatabase.MIGRATION_4_5)
            .build()
        try {
            assertEquals(listOf("POL-2026-00001"), room.policyCards().all().map { it.policyNumber })
            assertEquals(1700000000000L, room.policyCards().syncedAt())
            assertEquals(listOf("SIN-2026-00001"), room.claims().all().map { it.claimNumber })
            assertEquals("OFF-2026-AB12", room.claims().receipt()?.provisionalNumber)
            assertNull(room.providers().read())

            val copy = ProvidersCopyEntity(json = "[]", cityCode = "CALI", savedAtEpochMillis = 2L)
            room.providers().save(copy)
            assertEquals(copy, room.providers().read())
        } finally {
            room.close()
        }
    }

    private companion object {
        // Tabla de la constancia que agregó la versión 4 (HU-MOV-10).
        const val CLAIM_RECEIPT = "CREATE TABLE IF NOT EXISTS `claim_receipt` (`id` INTEGER NOT NULL, `claimNumber` TEXT NOT NULL, " +
            "`provisionalNumber` TEXT NOT NULL, `policyNumber` TEXT, `reportedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        // Esquema de la versión 3 tal como lo creaba Room (HU-MOV-11).
        val VERSION_3 = listOf(
            "CREATE TABLE IF NOT EXISTS `policy_cards` (`policyNumber` TEXT NOT NULL, `plan` TEXT NOT NULL, `insuredAmount` REAL NOT NULL, " +
                "`holderName` TEXT NOT NULL, `coveragesJson` TEXT NOT NULL, `validFromEpochMillis` INTEGER NOT NULL, " +
                "`validUntilEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`policyNumber`))",
            "CREATE TABLE IF NOT EXISTS `wallet_meta` (`id` INTEGER NOT NULL, `savedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `insured_claims` (`claimNumber` TEXT NOT NULL, `position` INTEGER NOT NULL, `json` TEXT NOT NULL, " +
                "PRIMARY KEY(`claimNumber`))",
            "CREATE TABLE IF NOT EXISTS `claims_meta` (`id` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `sent_notices` (`key` TEXT NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '99e1b869b3feaaecaecacdea51464b12')",
        )
    }
}
