package com.solventa.app.data.claims

import com.solventa.app.domain.claims.TimeSource
import com.solventa.app.domain.claims.TrustedTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DateTimeException

/** Hora que no depende del reloj que ajusta la persona (HU-MOV-08: TC-01, TC-05). */
class AndroidTrustedClockTest {

    private val missing: () -> Long = { throw DateTimeException("sin hora") }

    @Test
    fun `prefiere la hora del GNSS`() {
        assertEquals(TrustedTime(1_000L, TimeSource.GNSS), AndroidTrustedClock({ 1_000L }, { 2_000L }).now())
    }

    @Test
    fun `sin GNSS usa la hora de red de Android`() {
        assertEquals(TrustedTime(2_000L, TimeSource.NETWORK), AndroidTrustedClock(missing, { 2_000L }).now())
    }

    @Test
    fun `sin ninguna no hay hora independiente`() {
        assertNull(AndroidTrustedClock(missing, missing).now())
    }

    @Test
    fun `TC-05 no la corrige con el reloj del teléfono adelantado`() {
        val adelantado = System.currentTimeMillis() + 3_600_000L
        val red = adelantado - 3_600_000L

        val now = AndroidTrustedClock(missing, { red }).now()

        assertEquals(TrustedTime(red, TimeSource.NETWORK), now)
    }
}
