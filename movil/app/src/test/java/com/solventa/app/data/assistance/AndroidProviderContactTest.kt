package com.solventa.app.data.assistance

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

// «Llamar» abre el marcador sin permiso de llamadas y «Ver en el mapa» la app de mapas con geo: (mejora FE-MOV-07).
@RunWith(RobolectricTestRunner::class)
class AndroidProviderContactTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val contact = AndroidProviderContact(app)

    @Test
    fun `Llamar abre el marcador con el numero y no llama directamente`() {
        assertTrue(contact.call("+576015550101"))

        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel", intent.data!!.scheme)
        assertEquals("+576015550101", intent.data!!.schemeSpecificPart)
    }

    @Test
    fun `Ver en el mapa abre geo con las coordenadas y el nombre del prestador`() {
        assertTrue(contact.showOnMap(sampleProvider()))

        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo", intent.data!!.scheme)
        assertEquals("geo:4.686700,-74.048000?q=4.686700%2C-74.048000(Gr%C3%BAas%20de%20Prueba)", intent.data.toString())
    }
}
