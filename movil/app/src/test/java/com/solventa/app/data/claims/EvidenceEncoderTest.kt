package com.solventa.app.data.claims

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** De la imagen de la cámara al JPEG que se guarda (HU-MOV-08: TC-07, TC-09). */
@RunWith(RobolectricTestRunner::class)
class EvidenceEncoderTest {

    private val sizes = mutableListOf<Pair<Int, Int>>()
    private val qualities = mutableListOf<Int>()

    private fun encoder(bytesFor: (Int) -> Int) = EvidenceEncoder(maxBytes = 1_000) { bitmap, quality ->
        sizes += bitmap.width to bitmap.height
        qualities += quality
        ByteArray(bytesFor(quality))
    }

    @Test
    fun `una foto grande queda con el lado mayor en 1920 px`() {
        encoder { 10 }.encode(Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888), 0)

        assertEquals(1920 to 1440, sizes.single())
    }

    @Test
    fun `una foto pequeña no se agranda`() {
        encoder { 10 }.encode(Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888), 0)

        assertEquals(800 to 600, sizes.single())
    }

    @Test
    fun `gira la foto según el sensor`() {
        encoder { 10 }.encode(Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888), 90)

        assertEquals(1440 to 1920, sizes.single())
    }

    @Test
    fun `TC-09 baja la calidad hasta que cabe en el límite`() {
        val bytes = encoder { quality -> if (quality > 65) 2_000 else 900 }.encode(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888), 0)

        assertEquals(listOf(85, 75, 65), qualities)
        assertTrue(bytes!!.size <= 1_000)
    }

    @Test
    fun `TC-09 si ni con la calidad más baja cabe no hay foto`() {
        assertNull(encoder { 5_000 }.encode(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888), 0))
        assertEquals(listOf(85, 75, 65, 55, 45), qualities)
    }
}
