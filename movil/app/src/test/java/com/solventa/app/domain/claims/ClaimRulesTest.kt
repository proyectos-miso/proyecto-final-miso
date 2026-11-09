package com.solventa.app.domain.claims

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Reglas del reporte y lo que falta (HU-MOV-08: TC-09, TC-24). */
class ClaimRulesTest {

    private val photo = EvidencePhoto("f1", 1, Instant.EPOCH, null, CaptureFix(4.6, -74.0, 5f, false), "aa", 10)
    private val empty = ClaimReport("r1", null, "", ReportState.DRAFT, emptyList(), Instant.EPOCH, Instant.EPOCH)
    private val active = setOf("POL-2026-00001")

    @Test
    fun `un reporte vacío pide foto, póliza y descripción, en ese orden`() {
        assertEquals(listOf(Missing.PHOTO, Missing.POLICY, Missing.DESCRIPTION), ClaimRules.missing(empty, active))
    }

    @Test
    fun `con una foto, póliza vigente y descripción no falta nada`() {
        val complete = empty.copy(photos = listOf(photo), policyNumber = "POL-2026-00001", description = "Golpe")

        assertEquals(emptyList<Missing>(), ClaimRules.missing(complete, active))
    }

    @Test
    fun `solo espacios cuenta como sin descripción`() {
        val blank = empty.copy(photos = listOf(photo), policyNumber = "POL-2026-00001", description = "   \n ")

        assertEquals(listOf(Missing.DESCRIPTION), ClaimRules.missing(blank, active))
        assertFalse(ClaimRules.hasDescription(" "))
        assertTrue(ClaimRules.hasDescription(" a "))
    }

    @Test
    fun `TC-24 una póliza que ya no está vigente se pide de nuevo`() {
        val expired = empty.copy(photos = listOf(photo), policyNumber = "POL-2025-00009", description = "Golpe")

        assertEquals(listOf(Missing.POLICY_NOT_ACTIVE), ClaimRules.missing(expired, active))
        assertEquals(listOf(Missing.POLICY_NOT_ACTIVE), ClaimRules.missing(expired.copy(policyNumber = "POL-2026-00001"), emptySet()))
    }

    @Test
    fun `la descripción se recorta en 500 caracteres y con 500 queda igual`() {
        assertEquals(500, ClaimRules.limitDescription("a".repeat(501)).length)
        assertEquals("b".repeat(500), ClaimRules.limitDescription("b".repeat(500)))
        assertEquals("con emojis 🚗 y\nsaltos", ClaimRules.limitDescription("con emojis 🚗 y\nsaltos"))
    }

    @Test
    fun `se pueden agregar fotos hasta 5 y no en un reporte listo`() {
        val four = empty.copy(photos = List(4) { photo.copy(id = "f$it", position = it + 1) })

        assertTrue(ClaimRules.canAddPhoto(empty))
        assertTrue(ClaimRules.canAddPhoto(four))
        assertFalse(ClaimRules.canAddPhoto(four.copy(photos = four.photos + photo.copy(id = "f9", position = 5))))
        assertFalse(ClaimRules.canAddPhoto(empty.copy(state = ReportState.READY)))
    }
}
