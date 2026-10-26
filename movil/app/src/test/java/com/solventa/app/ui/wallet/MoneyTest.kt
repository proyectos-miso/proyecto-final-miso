package com.solventa.app.ui.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

// Montos en pesos con su código (HU-MOV-04: TC-11).
class MoneyTest {

    // Java separa el símbolo con un espacio duro; se normaliza para comparar.
    private fun plain(text: String) = text.replace(Regex("[\\s\\u00A0\\u202F]"), " ")

    @Test
    fun `TC-11 muestra pesos sin centavos y con el codigo COP`() {
        assertEquals("$ 250.000.000 COP", plain(formatCop(2.5e8)))
    }

    @Test
    fun `redondea los centavos`() {
        assertEquals("$ 27.361 COP", plain(formatCop(27_360.6)))
    }
}
