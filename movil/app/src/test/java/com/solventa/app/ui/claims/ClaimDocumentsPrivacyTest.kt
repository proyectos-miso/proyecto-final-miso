package com.solventa.app.ui.claims

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mejora FE-MOV-05 (CTL-06): las páginas no se escriben en disco, registros ni estado guardado, y el escáner no
 * importa de la galería. Búsqueda en el código, como en HU-MOV-08 y FE-MOV-01.
 */
class ClaimDocumentsPrivacyTest {

    private val main = File(System.getProperty("user.dir")!!, "src/main/java/com/solventa/app")
    private val sources = listOf(
        "ui/claims/ClaimDocumentsViewModel.kt",
        "ui/claims/ClaimDocumentsScreens.kt",
        "ui/claims/DocumentScanner.kt",
        "domain/claims/ClaimDocuments.kt",
        "data/claims/HttpClaimDocumentsApi.kt",
        "data/claims/SessionClaimDocuments.kt",
    ).map { File(main, it) }

    private fun File.code(): String = readText().replace(Regex("""/\*[\s\S]*?\*/|//[^\n]*"""), "")

    @Test
    fun `las paginas nunca se escriben en disco ni se toman de otra app`() {
        assertTrue(sources.all { it.exists() })
        val disk = Regex(
            """FileOutputStream|openFileOutput|cacheDir|filesDir|File\(|createTempFile|SharedPreferences|""" +
                """OutputFileOptions|MediaStore|ACTION_IMAGE_CAPTURE|PickVisualMedia|GetContent|Room|SQLite"""
        )
        sources.forEach { assertTrue("${it.name} escribe en disco o usa otra app", !disk.containsMatchIn(it.code())) }
    }

    @Test
    fun `no registra ni usa el estado guardado`() {
        val forbidden = Regex("""\bLog\.[vdiwe]\(|android\.util\.Log|println\(|printStackTrace\(|rememberSaveable|SavedStateHandle""")
        sources.forEach { assertTrue("${it.name} registra o guarda estado", !forbidden.containsMatchIn(it.code())) }
    }

    @Test
    fun `el escaner de Google no importa de la galeria`() {
        val scanner = File(main, "ui/claims/DocumentScanner.kt").code()

        assertTrue(scanner.contains("setGalleryImportAllowed(false)"))
        assertTrue(scanner.contains("RESULT_FORMAT_JPEG"))
    }
}
