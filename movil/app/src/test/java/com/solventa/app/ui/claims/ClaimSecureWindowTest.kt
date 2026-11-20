package com.solventa.app.ui.claims

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.solventa.app.SecureWindow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Capturas bloqueadas mientras se muestra el reporte (HU-MOV-08: TC-13). */
@RunWith(RobolectricTestRunner::class)
class ClaimSecureWindowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `TC-13 la ventana es segura solo mientras se muestra el reporte, y nunca si está apagada`() {
        var showing by mutableStateOf(true)
        var enabled by mutableStateOf(false)
        compose.setContent { if (showing) SecureWindow(enabled) }
        compose.waitForIdle()
        assertFalse("apagada, como en debug, no se marca la ventana", secure())

        enabled = true
        compose.waitForIdle()
        assertTrue(secure())

        showing = false
        compose.waitForIdle()
        assertFalse("al volver a la billetera se quita", secure())
    }

    private fun secure() = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}
