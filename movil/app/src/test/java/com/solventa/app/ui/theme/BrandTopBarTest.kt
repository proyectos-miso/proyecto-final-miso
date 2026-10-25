package com.solventa.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.R
import com.solventa.app.data.settings.ThemePreferenceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Barra de la marca con el cambio de tema del mockup (HU-MOV-04: TC-12).
@RunWith(RobolectricTestRunner::class)
class BrandTopBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun s(id: Int): String = context.getString(id)

    @Test
    fun `TC-12 la barra muestra la marca y alterna entre tema claro y oscuro`() {
        var dark by mutableStateOf(false)
        compose.setContent { SolventaTheme(darkTheme = dark) { BrandTopBar(dark, onToggleTheme = { dark = !dark }) } }

        compose.onNodeWithText(s(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.theme_to_dark)).performClick()

        assertEquals(true, dark)
        compose.onNodeWithContentDescription(s(R.string.theme_to_light)).assertIsDisplayed()
    }

    @Test
    fun `TC-12 el tema elegido se conserva y sin eleccion se sigue el del sistema`() {
        context.getSharedPreferences("solventa_ui", Context.MODE_PRIVATE).edit().clear().commit()
        val store = ThemePreferenceStore(context)
        assertNull(store.darkTheme())

        store.saveDarkTheme(true)

        assertEquals(true, ThemePreferenceStore(context).darkTheme())
    }
}
