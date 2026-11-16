package com.solventa.app.data.claims

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.domain.claims.ScanResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Mejora FE-MOV-05 (CTL-06): las páginas que ML Kit deja en la caché pasan a memoria y su archivo se borra
 * siempre, también si la lectura falla o el escaneo se cancela.
 */
@RunWith(RobolectricTestRunner::class)
class ScannedPageReaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun scannedFile(name: String, content: ByteArray): File =
        File(File(context.cacheDir, "mlkit_docscan").apply { mkdirs() }, name).apply { writeBytes(content) }

    @Test
    fun `lee las paginas a memoria, las recodifica y borra los archivos`() = runTest {
        val files = listOf(scannedFile("page1.jpg", byteArrayOf(1, 2, 3)), scannedFile("page2.jpg", byteArrayOf(4, 5)))
        val reader = ScannedPageReader(context) { raw -> raw.map { (it * 10).toByte() }.toByteArray() }

        val result = reader.read(files.map(Uri::fromFile)) as ScanResult.Pages

        assertArrayEquals(byteArrayOf(10, 20, 30), result.pages[0])
        assertArrayEquals(byteArrayOf(40, 50), result.pages[1])
        assertTrue(files.none(File::exists))
    }

    @Test
    fun `si una pagina no se puede recodificar falla y borra todas`() = runTest {
        val files = listOf(scannedFile("page1.jpg", byteArrayOf(1)), scannedFile("page2.jpg", byteArrayOf(2)))
        val reader = ScannedPageReader(context) { raw -> if (raw[0] == 2.toByte()) null else raw }

        assertEquals(ScanResult.Failed, reader.read(files.map(Uri::fromFile)))
        assertTrue(files.none(File::exists))
    }

    @Test
    fun `si una pagina posterior falla las ya leidas quedan en cero`() = runTest {
        val files = listOf(scannedFile("page1.jpg", byteArrayOf(1)), scannedFile("page2.jpg", byteArrayOf(2)))
        val produced = mutableListOf<ByteArray>()
        val reader = ScannedPageReader(context) { raw ->
            if (raw[0] == 2.toByte()) null else ByteArray(8) { 7 }.also { produced += it }
        }

        assertEquals(ScanResult.Failed, reader.read(files.map(Uri::fromFile)))
        assertTrue(produced.single().all { it == 0.toByte() })
    }

    @Test
    fun `si un archivo posterior no se puede abrir las ya leidas quedan en cero`() = runTest {
        val first = scannedFile("page1.jpg", byteArrayOf(1))
        val produced = mutableListOf<ByteArray>()
        val reader = ScannedPageReader(context) { ByteArray(8) { 7 }.also { produced += it } }

        assertEquals(ScanResult.Failed, reader.read(listOf(Uri.fromFile(first), Uri.fromFile(File(context.cacheDir, "no-existe.jpg")))))
        assertTrue(produced.single().all { it == 0.toByte() })
    }

    @Test
    fun `limpiar la cache del escaner borra su carpeta y nada mas`() {
        val leftover = File(File(context.cacheDir, ScannedPageReader.SCANNER_CACHE_DIR).apply { mkdirs() }, "123.jpg").apply { writeBytes(byteArrayOf(1)) }
        val other = File(context.cacheDir, "otra.tmp").apply { writeBytes(byteArrayOf(2)) }

        ScannedPageReader.clearScannerCache(context.cacheDir)

        assertFalse(leftover.exists() || leftover.parentFile!!.exists())
        assertTrue(other.exists())
        other.delete()
    }

    @Test
    fun `al arrancar la app borra las paginas que el escaner dejo si el proceso murio`() {
        val app = ApplicationProvider.getApplicationContext<com.solventa.app.SolventaApplication>()
        val leftover = File(File(app.cacheDir, ScannedPageReader.SCANNER_CACHE_DIR).apply { mkdirs() }, "456.jpg").apply { writeBytes(byteArrayOf(1)) }

        app.clearLeftoverScans()

        assertFalse(leftover.exists())
    }

    @Test
    fun `un archivo que no existe falla sin romper la app`() = runTest {
        val missing = File(context.cacheDir, "no-existe.jpg")

        assertEquals(ScanResult.Failed, ScannedPageReader(context) { it }.read(listOf(Uri.fromFile(missing))))
    }

    @Test
    fun `al cancelar se borran las paginas y nunca un archivo fuera de la app`() = runTest {
        val own = scannedFile("page1.jpg", byteArrayOf(1))
        val outside = File.createTempFile("ajeno", ".jpg").apply { writeBytes(byteArrayOf(9)) }

        ScannedPageReader(context).discard(listOf(Uri.fromFile(own), Uri.fromFile(outside)))

        assertFalse(own.exists())
        assertTrue(outside.exists())
        outside.delete()
    }
}
