package com.solventa.app.ui.auth

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.ui.claims.CameraLens
import com.solventa.app.ui.claims.CameraXEvidenceCamera
import com.solventa.app.ui.claims.selectorFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Mejora FE-MOV-01: la selfie usa la cámara frontal de la app, y el registro no escribe las fotos ni los datos en
 * disco, en registros ni en el estado que guarda el sistema (búsqueda en el código, como en HU-MOV-08).
 */
@RunWith(RobolectricTestRunner::class)
class RegistrationPrivacyTest {

    private val main = File(System.getProperty("user.dir")!!, "src/main/java/com/solventa/app")
    private val sources = listOf(
        "ui/auth/RegistrationViewModel.kt",
        "ui/auth/RegistrationScreens.kt",
        "domain/auth/Registration.kt",
        "ui/claims/EvidenceCamera.kt",
    ).map { File(main, it) }

    private fun File.code(): String = readText().replace(Regex("""/\*[\s\S]*?\*/|//[^\n]*"""), "")

    @Test
    fun `la selfie usa la camara frontal y el documento la trasera`() {
        val context: Context = ApplicationProvider.getApplicationContext()

        assertEquals(CameraSelector.LENS_FACING_FRONT, selectorFor(CameraLens.FRONT).lensFacing)
        assertEquals(CameraSelector.LENS_FACING_BACK, selectorFor(CameraLens.BACK).lensFacing)
        assertEquals(CameraSelector.LENS_FACING_FRONT, CameraXEvidenceCamera(context, lens = CameraLens.FRONT).selector.lensFacing)
        assertEquals(CameraSelector.LENS_FACING_BACK, CameraXEvidenceCamera(context).selector.lensFacing)
    }

    @Test
    fun `las fotos del registro nunca tocan el disco ni salen de la app`() {
        assertTrue(sources.all { it.exists() })
        val disk = Regex(
            """FileOutputStream|openFileOutput|cacheDir|filesDir|File\(|createTempFile|SharedPreferences|""" +
                """OutputFileOptions|MediaStore|ACTION_IMAGE_CAPTURE|PickVisualMedia|GetContent"""
        )
        sources.forEach { assertTrue("${it.name} escribe en disco o usa otra app", !disk.containsMatchIn(it.code())) }
    }

    @Test
    fun `el registro no escribe registros ni usa el estado guardado`() {
        val forbidden = Regex("""\bLog\.[vdiwe]\(|android\.util\.Log|println\(|printStackTrace\(|rememberSaveable|SavedStateHandle""")
        sources.forEach { assertTrue("${it.name} registra o guarda estado", !forbidden.containsMatchIn(it.code())) }
    }
}
