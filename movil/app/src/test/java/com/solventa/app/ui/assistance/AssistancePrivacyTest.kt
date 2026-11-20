package com.solventa.app.ui.assistance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mejora FE-MOV-07: la ubicación del asegurado no se registra ni va al estado guardado, se pide una sola vez sin
 * escuchar en segundo plano, y no hay mapa embebido ni llave de API. Búsqueda en el código, como en FE-MOV-05.
 */
class AssistancePrivacyTest {

    private val module = File(System.getProperty("user.dir")!!)
    private val main = File(module, "src/main/java/com/solventa/app")
    private val sources = listOf(
        "ui/assistance/AssistanceViewModel.kt",
        "ui/assistance/AssistanceScreens.kt",
        "domain/assistance/Assistance.kt",
        "data/assistance/HttpAssistanceApi.kt",
        "data/assistance/SessionAssistance.kt",
        "data/assistance/AndroidApproximateLocator.kt",
        "data/assistance/AndroidProviderContact.kt",
    ).map { File(main, it) }

    private fun File.code(): String = readText().replace(Regex("""/\*[\s\S]*?\*/|//[^\n]*"""), "")

    @Test
    fun `la ubicacion no se registra ni se guarda en el estado guardado o en preferencias`() {
        assertTrue(sources.all { it.exists() })
        val forbidden = Regex(
            """\bLog\.[vdiwe]\(|android\.util\.Log|println\(|printStackTrace\(|rememberSaveable|SavedStateHandle|SharedPreferences"""
        )
        sources.forEach { assertFalse("${it.name} registra o guarda estado", forbidden.containsMatchIn(it.code())) }
    }

    @Test
    fun `la ubicacion se pide una vez, sin escuchar actualizaciones ni pedir la precisa`() {
        val locator = File(main, "data/assistance/AndroidApproximateLocator.kt").code()

        assertTrue(locator.contains("getCurrentLocation("))
        assertFalse(locator.contains("requestLocationUpdates"))
        assertFalse(File(main, "MainActivity.kt").code().contains("launch(Manifest.permission.ACCESS_FINE_LOCATION)"))
    }

    @Test
    fun `Llamar abre el marcador sin permiso de llamadas y no hay mapa embebido ni llave de API`() {
        val manifest = File(module, "src/main/AndroidManifest.xml").readText()
        val build = File(module, "build.gradle.kts").readText() + File(module, "../gradle/libs.versions.toml").readText()

        assertFalse(manifest.contains("CALL_PHONE"))
        assertFalse(manifest.contains("com.google.android.geo.API_KEY"))
        assertFalse(build.contains("play-services-maps") || build.contains("maps-compose"))
        assertTrue(File(main, "data/assistance/AndroidProviderContact.kt").code().contains("Intent.ACTION_DIAL"))
    }

    @Test
    fun `la linea ficticia de Solventa solo existe en depuracion`() {
        val line = Regex("""name="assistance_line_phone"[^>]*>([^<]*)<""")
        val release = line.find(File(module, "src/main/res/values/strings.xml").readText())!!.groupValues[1]
        val debug = line.find(File(module, "src/debug/res/values/assistance.xml").readText())!!.groupValues[1]

        assertTrue(release.isEmpty())
        assertTrue(debug.contains("555"))
    }
}
