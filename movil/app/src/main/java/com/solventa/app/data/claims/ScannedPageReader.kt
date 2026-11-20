package com.solventa.app.data.claims

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.solventa.app.domain.claims.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Pasa a memoria las páginas que ML Kit dejó en la caché de la app y borra cada archivo, lea o no (mejora
 * FE-MOV-05, CTL-06). Cada página se vuelve a codificar con [EvidenceEncoder]: JPEG sin EXIF de a lo sumo 2 MB.
 * Si una página falla, las ya leídas se ponen en cero antes de soltarlas.
 */
class ScannedPageReader(
    private val context: Context,
    private val encode: (ByteArray) -> ByteArray? = ::reencode,
) {

    suspend fun read(uris: List<Uri>): ScanResult = withContext(Dispatchers.IO) {
        val pages = mutableListOf<ByteArray>()
        var complete = false
        try {
            for (uri in uris) {
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext ScanResult.Failed
                try {
                    pages += encode(raw) ?: return@withContext ScanResult.Failed
                } finally {
                    raw.fill(0)
                }
            }
            complete = true
            ScanResult.Pages(pages)
        } catch (e: IOException) {
            ScanResult.Failed
        } catch (e: SecurityException) {
            ScanResult.Failed
        } finally {
            if (!complete) pages.forEach { it.fill(0) }
            uris.forEach(::delete)
        }
    }

    suspend fun discard(uris: List<Uri>) = withContext(Dispatchers.IO) { uris.forEach(::delete) }

    fun clearCache() = clearScannerCache(context.cacheDir)

    // Solo archivos de la caché o del almacenamiento interno de la app: nunca borra fuera de ella.
    private fun delete(uri: Uri) {
        val path = uri.path ?: return
        if (uri.scheme != "file") return
        val file = File(path).canonicalFile
        val own = listOf(context.cacheDir, context.filesDir, context.noBackupFilesDir).map { it.canonicalPath + File.separator }
        if (own.any { file.path.startsWith(it) }) file.delete()
    }

    companion object {
        /** Carpeta donde ML Kit Document Scanner 16.0.0 escribe las páginas, según su biblioteca cliente. */
        const val SCANNER_CACHE_DIR = "mlkit_docscan_ui_client"

        /** Borra lo que ML Kit haya dejado en su carpeta, por ejemplo si el proceso murió antes de leerlo. */
        fun clearScannerCache(cacheDir: File) {
            File(cacheDir, SCANNER_CACHE_DIR).deleteRecursively()
        }

        private fun reencode(raw: ByteArray): ByteArray? {
            val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
            return try {
                EvidenceEncoder().encode(bitmap, 0)
            } finally {
                bitmap.recycle()
            }
        }
    }
}
