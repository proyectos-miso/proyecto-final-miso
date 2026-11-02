package com.solventa.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// La release solo usa HTTPS; HTTP sin TLS solo hacia el backend local y solo en debug (HU-MOV-07: TC-18).
class NetworkSecurityConfigTest {

    @Test
    fun `la configuracion de release no permite HTTP sin TLS a ningun host`() {
        val config = parse("src/main/res/xml/network_security_config.xml")

        assertEquals("false", (config.getElementsByTagName("base-config").item(0) as Element).getAttribute("cleartextTrafficPermitted"))
        assertEquals(0, config.getElementsByTagName("domain-config").length)
    }

    @Test
    fun `la de debug solo permite HTTP sin TLS hacia el backend local`() {
        val config = parse("src/debug/res/xml/network_security_config.xml")
        val domains = config.getElementsByTagName("domain").let { nodes -> (0 until nodes.length).map { nodes.item(it).textContent.trim() } }

        assertEquals(listOf("10.0.2.2", "localhost"), domains)
        assertEquals(0, config.getElementsByTagName("base-config").length)
        (0 until config.getElementsByTagName("domain").length).forEach { i ->
            assertNotEquals("true", (config.getElementsByTagName("domain").item(i) as Element).getAttribute("includeSubdomains"))
        }
    }

    private fun parse(path: String): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
}
