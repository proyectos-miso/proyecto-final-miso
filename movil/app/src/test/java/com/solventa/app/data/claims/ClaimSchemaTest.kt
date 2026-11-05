package com.solventa.app.data.claims

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * HU-MOV-08 TC-23: la base del reporte exporta su esquema y nunca usa una migración destructiva, porque la
 * evidencia no se puede volver a descargar (CTL-15, ADR 0021 punto 6).
 */
@RunWith(RobolectricTestRunner::class)
class ClaimSchemaTest {

    private val module = File(System.getProperty("user.dir")!!)
    private val sources = File(module, "src/main/java/com/solventa/app/data/claims")

    @Test
    fun `el esquema de la versión 1 está exportado en el repositorio`() {
        val schema = File(module, "schemas/com.solventa.app.data.claims.ClaimDatabase/1.json")

        assertTrue("falta ${schema.path}", schema.isFile)
        val database = JSONObject(schema.readText()).getJSONObject("database")
        assertEquals(1, database.getInt("version"))
        val tables = (0 until database.getJSONArray("entities").length())
            .map { database.getJSONArray("entities").getJSONObject(it).getString("tableName") }
        assertEquals(listOf("reports", "photos"), tables)
    }

    @Test
    fun `la base declara exportSchema y el almacén no usa migraciones destructivas`() {
        assertTrue(File(sources, "ClaimDatabase.kt").readText().contains("exportSchema = true"))
        sources.listFiles()!!.filter { it.extension == "kt" }.forEach { file ->
            assertFalse("${file.name} usa una migración destructiva", file.readText().contains("fallbackToDestructiveMigration("))
        }
    }
}
