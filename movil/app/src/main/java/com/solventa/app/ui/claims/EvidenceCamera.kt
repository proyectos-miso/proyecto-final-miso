package com.solventa.app.ui.claims

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.solventa.app.data.claims.EvidenceEncoder
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Cámara de la evidencia: solo la de la app, sin galería ni apps de cámara de terceros (AC-02, CTL-01). */
interface EvidenceCamera {
    @Composable
    fun Preview(modifier: Modifier, onUnavailable: () -> Unit)

    /** La foto en JPEG de a lo sumo 2 MB y sin EXIF, o null si la cámara falló. Nunca toca el disco (CTL-05). */
    suspend fun capture(): ByteArray?
}

/** Cámara trasera para la evidencia y el documento; frontal para la selfie del registro (mejora FE-MOV-01). */
enum class CameraLens { BACK, FRONT }

/**
 * CameraX con la cámara elegida. `ImageCapture.takePicture` con callback entrega la imagen en memoria; se evita
 * `OutputFileOptions`, que escribiría un JPEG en claro al disco (CTL-05).
 */
class CameraXEvidenceCamera(
    private val context: Context,
    private val encoder: EvidenceEncoder = EvidenceEncoder(),
    lens: CameraLens = CameraLens.BACK,
) : EvidenceCamera {

    internal val selector: CameraSelector = selectorFor(lens)

    private val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    @Suppress("DEPRECATION")
    @Composable
    override fun Preview(modifier: Modifier, onUnavailable: () -> Unit) {
        val lifecycleOwner = LocalLifecycleOwner.current
        // Con `this` como llave, otra cámara en el mismo lugar de la composición (documento y selfie) crea su propia
        // vista y vuelve a enlazar CameraX; sin ella se reusaría la vista y el enlace de la cámara anterior.
        val previewView = remember(this) { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
        DisposableEffect(lifecycleOwner, this) {
            val future = ProcessCameraProvider.getInstance(context)
            var provider: ProcessCameraProvider? = null
            val preview = Preview.Builder().build()
            future.addListener({
                try {
                    val cameras = future.get()
                    provider = cameras
                    // Un teléfono sin cámara frontal no puede tomar la selfie: se avisa en lugar de reintentar.
                    if (!cameras.hasCamera(selector)) {
                        onUnavailable()
                    } else {
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        cameras.unbindAll()
                        cameras.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                    }
                } catch (e: Exception) {
                    onUnavailable()
                }
            }, context.mainExecutor)
            onDispose { provider?.unbindAll() }
        }
        AndroidView(factory = { previewView }, modifier = modifier)
    }

    override suspend fun capture(): ByteArray? = suspendCancellableCoroutine { continuation ->
        imageCapture.takePicture(
            worker,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val jpeg = try {
                        val bitmap = image.toBitmap()
                        val rotation = image.imageInfo.rotationDegrees
                        image.close()
                        encoder.encode(bitmap, rotation).also { bitmap.recycle() }
                    } catch (e: RuntimeException) {
                        image.close()
                        null
                    }
                    if (continuation.isActive) continuation.resume(jpeg)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            },
        )
    }
}

internal fun selectorFor(lens: CameraLens): CameraSelector = when (lens) {
    CameraLens.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
    CameraLens.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
}
