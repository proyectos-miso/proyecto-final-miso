package com.solventa.app.domain.claims

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// Radicado provisional del reporte en cola (HU-MOV-10, AC-02).
class ProvisionalNumberTest {

    private val created = Instant.parse("2026-11-14T10:00:00Z")
    private val report = ClaimReport(
        id = "ab12cd34-0000-4000-8000-000000000001",
        policyNumber = "POL-2026-00001",
        description = "Golpe en la puerta",
        state = ReportState.QUEUED,
        photos = emptyList(),
        createdAt = created,
        updatedAt = created,
        queuedAt = Instant.parse("2027-01-01T00:00:05Z"),
    )

    @Test
    fun `HU-MOV-10 AC-02 el provisional tiene el formato OFF-año-4 caracteres del id`() {
        val number = ProvisionalNumber.of(report)

        assertEquals("OFF-2026-AB12", number)
        assertTrue(Regex("""OFF-\d{4}-[0-9A-Z]{4}""").matches(number))
    }

    @Test
    fun `HU-MOV-10 AC-02 el mismo reporte da siempre el mismo provisional y otro reporte da otro`() {
        assertEquals(ProvisionalNumber.of(report), ProvisionalNumber.of(report.copy(state = ReportState.REJECTED, updatedAt = Instant.now())))
        assertNotEquals(ProvisionalNumber.of(report), ProvisionalNumber.of(report.copy(id = "9f01e2d3-0000-4000-8000-000000000002")))
    }

    @Test
    fun `HU-MOV-10 AC-02 un id corto o con guiones al inicio no rompe el formato`() {
        assertEquals("OFF-2026-R100", ProvisionalNumber.of("r1", created))
        assertEquals("OFF-2026-AB12", ProvisionalNumber.of("--ab-12", created))
    }

    @Test
    fun `HU-MOV-10 AC-02 el pendiente de Mis siniestros lleva el provisional y el borrador no aparece`() {
        assertEquals("OFF-2026-AB12", PendingReport.of(report)!!.provisionalNumber)
        assertEquals("OFF-2026-AB12", PendingReport.of(report.copy(state = ReportState.REJECTED, rejectionReason = "x"))!!.provisionalNumber)
        assertNull(PendingReport.of(report.copy(state = ReportState.DRAFT)))
    }
}
