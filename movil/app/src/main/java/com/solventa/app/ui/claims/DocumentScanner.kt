package com.solventa.app.ui.claims

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.solventa.app.data.claims.ScannedPageReader
import com.solventa.app.domain.claims.ScanResult
import kotlinx.coroutines.launch

/** Puerto del escáner de documentos (mejora FE-MOV-05); la UI del escaneo la presta el proveedor. */
interface DocumentScanner {
    /** false sin Google Play services: la pantalla ofrece la cámara trasera desde el principio. */
    fun available(): Boolean

    /** Lanzador que abre el escáner con un tope de páginas y entrega el resultado en [onResult]. */
    @Composable
    fun rememberLauncher(onResult: (ScanResult) -> Unit): (maxPages: Int) -> Unit
}

/**
 * ML Kit Document Scanner (`play-services-mlkit-document-scanner`, DEP-01): sin galería y en JPEG. ML Kit deja cada
 * página en un archivo de la caché de la app; [ScannedPageReader] la lee a memoria y borra el archivo enseguida.
 */
class MlKitDocumentScanner(
    private val context: Context,
    private val reader: ScannedPageReader = ScannedPageReader(context),
) : DocumentScanner {

    override fun available(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    @Composable
    override fun rememberLauncher(onResult: (ScanResult) -> Unit): (Int) -> Unit {
        val activity = LocalContext.current.findActivity()
        val scope = rememberCoroutineScope()
        val deliver by rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scan?.pages.orEmpty().map { it.imageUri }
            scope.launch {
                if (result.resultCode != Activity.RESULT_OK || uris.isEmpty()) {
                    reader.discard(uris)
                    deliver(ScanResult.Cancelled)
                } else {
                    deliver(reader.read(uris))
                }
            }
        }
        return remember(activity, launcher) {
            { maxPages ->
                if (activity == null) {
                    deliver(ScanResult.Unavailable)
                } else {
                    val options = GmsDocumentScannerOptions.Builder()
                        .setGalleryImportAllowed(false)
                        .setPageLimit(maxPages)
                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER)
                        .build()
                    GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                        .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                        .addOnFailureListener { deliver(ScanResult.Unavailable) }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
