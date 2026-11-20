package com.solventa.app.data.claims

import android.graphics.Bitmap
import android.graphics.Matrix
import com.solventa.app.domain.claims.ClaimRules
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * De la imagen de la cámara a la foto que se guarda: girada según el sensor, con el lado mayor de a lo sumo 1920 px
 * y en JPEG de a lo sumo 2 MB (AC-14). `Bitmap.compress` no escribe EXIF, así que la foto no repite la ubicación
 * ni lleva el modelo del teléfono (CTL-05). Todo pasa en memoria.
 */
class EvidenceEncoder(
    private val maxBytes: Int = ClaimRules.MAX_PHOTO_BYTES,
    private val maxSide: Int = MAX_SIDE,
    private val compress: (Bitmap, Int) -> ByteArray = ::jpeg,
) {

    /** null si ni con la calidad más baja cabe en el límite. */
    fun encode(image: Bitmap, rotationDegrees: Int): ByteArray? {
        val scaled = scale(image)
        val prepared = rotate(scaled, rotationDegrees)
        if (scaled !== image && scaled !== prepared) scaled.recycle()
        try {
            var quality = START_QUALITY
            while (quality >= MIN_QUALITY) {
                val bytes = compress(prepared, quality)
                if (bytes.size <= maxBytes) return bytes
                quality -= QUALITY_STEP
            }
            return null
        } finally {
            if (prepared !== image) prepared.recycle()
        }
    }

    private fun scale(image: Bitmap): Bitmap {
        val longest = max(image.width, image.height)
        if (longest <= maxSide) return image
        val factor = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(image, (image.width * factor).roundToInt(), (image.height * factor).roundToInt(), true)
    }

    private fun rotate(image: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return image
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true)
    }

    companion object {
        const val MAX_SIDE = 1920
        private const val START_QUALITY = 85
        private const val MIN_QUALITY = 45
        private const val QUALITY_STEP = 10

        private fun jpeg(image: Bitmap, quality: Int): ByteArray =
            ByteArrayOutputStream().use { out ->
                image.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
    }
}
