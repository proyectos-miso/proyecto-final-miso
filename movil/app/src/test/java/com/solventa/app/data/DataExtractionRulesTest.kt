package com.solventa.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// Ningún dato de la app sale en copias de seguridad ni en transferencias (HU-MOV-05: TC-08).
class DataExtractionRulesTest {

    private val android = "http://schemas.android.com/apk/res/android"

    @Test
    fun `el manifiesto niega las copias y declara las reglas de extraccion`() {
        val application = parse("src/main/AndroidManifest.xml").getElementsByTagName("application").item(0) as Element

        assertEquals("false", application.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(android, "dataExtractionRules"))
    }

    @Test
    fun `cada seccion excluye los nueve dominios completos y no incluye nada`() {
        val rules = parse("src/main/res/xml/data_extraction_rules.xml").documentElement

        for (section in listOf("cloud-backup", "device-transfer", "cross-platform-transfer")) {
            val element = rules.getElementsByTagName(section).item(0) as Element
            val excluded = element.getElementsByTagName("exclude").let { nodes ->
                (0 until nodes.length).map { nodes.item(it) as Element }.filter { it.getAttribute("path") == "." }.map { it.getAttribute("domain") }
            }

            assertEquals(section, DOMAINS, excluded.toSet())
            assertEquals(section, 0, element.getElementsByTagName("include").length)
        }
    }

    private fun parse(path: String) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(path))

    private companion object {
        val DOMAINS = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}
