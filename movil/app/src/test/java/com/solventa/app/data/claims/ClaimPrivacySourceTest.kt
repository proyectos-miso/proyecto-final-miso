package com.solventa.app.data.claims

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * HU-MOV-08 TC-14 (búsqueda en el código): el reporte no escribe registros ni guarda nada en el estado que conserva
 * el sistema; la descripción, la ubicación, las rutas y las llaves quedan fuera de logcat y del `Bundle` (CTL-09).
 * Tampoco abre la galería ni otra app de cámara (TC-02, CTL-01).
 */
class ClaimPrivacySourceTest {

    private val main = File(System.getProperty("user.dir")!!, "src/main/java/com/solventa/app")
    private val sources = listOf("data/claims", "ui/claims", "domain/claims")
        .flatMap { File(main, it).listFiles()!!.filter { file -> file.extension == "kt" } }

    // Los comentarios pueden nombrar lo que no se usa y por qué; solo cuenta el código.
    private fun File.code(): String = readText().replace(Regex("""/\*[\s\S]*?\*/|//[^\n]*"""), "")

    @Test
    fun `el reporte no escribe registros`() {
        assertTrue(sources.isNotEmpty())
        val logging = Regex("""\bLog\.[vdiwe]\(|android\.util\.Log|println\(|printStackTrace\(""")
        sources.forEach { assertTrue("${it.name} escribe registros", !logging.containsMatchIn(it.code())) }
    }

    @Test
    fun `el reporte no guarda nada en el estado que conserva el sistema`() {
        val saved = Regex("""rememberSaveable|SavedStateHandle|onSaveInstanceState""")
        sources.forEach { assertTrue("${it.name} usa el estado guardado", !saved.containsMatchIn(it.code())) }
    }

    @Test
    fun `el reporte no abre la galería ni otra app de cámara ni escribe la foto en claro`() {
        val external = Regex("""ACTION_IMAGE_CAPTURE|ACTION_GET_CONTENT|ACTION_PICK|PickVisualMedia|GetContent|OutputFileOptions|MediaStore""")
        sources.forEach { assertTrue("${it.name} usa una vía externa o escribe la foto en claro", !external.containsMatchIn(it.code())) }
    }
}
