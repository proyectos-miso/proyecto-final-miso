package com.solventa.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.domain.policies.PushPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Copia local de las preferencias de avisos por cuenta (FE-MOV-04).
@RunWith(RobolectricTestRunner::class)
class NotificationPreferenceStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = NotificationPreferenceStore(context)
    private val prefs = context.getSharedPreferences("solventa_notices", Context.MODE_PRIVATE)

    @Test
    fun `sin copia todos los avisos estan encendidos y nada queda pendiente`() {
        assertEquals(PushPreferences(claims = true, renewals = true, payments = true), store.read(MARIA))
        assertFalse(store.pending(MARIA))
    }

    @Test
    fun `guarda las preferencias de cada cuenta con su marca de pendiente y sin el correo`() {
        store.save(MARIA, PushPreferences(claims = false, renewals = true, payments = false), pending = true)
        store.save(PEDRO, PushPreferences(claims = true, renewals = false, payments = true), pending = false)

        assertEquals(PushPreferences(claims = false, renewals = true, payments = false), store.read(MARIA))
        assertTrue(store.pending(MARIA))
        assertEquals(PushPreferences(claims = true, renewals = false, payments = true), store.read(PEDRO))
        assertFalse(store.pending(PEDRO))
        assertFalse("el correo no queda en el teléfono", prefs.all.keys.any { it.contains("@") })
    }

    @Test
    fun `al borrar la cuenta vuelve a los valores por defecto sin tocar las demas`() {
        store.save(MARIA, PushPreferences(claims = false, renewals = false, payments = false), pending = true)
        store.save(PEDRO, PushPreferences(claims = false, renewals = true, payments = true), pending = false)

        store.delete(MARIA)

        assertEquals(PushPreferences(), store.read(MARIA))
        assertFalse(store.pending(MARIA))
        assertEquals(PushPreferences(claims = false, renewals = true, payments = true), store.read(PEDRO))
        assertEquals(4, prefs.all.size)
    }

    private companion object {
        const val MARIA = "maria@correo.test"
        const val PEDRO = "pedro@correo.test"
    }
}
