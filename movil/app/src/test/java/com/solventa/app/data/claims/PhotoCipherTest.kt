package com.solventa.app.data.claims

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Cifrado de cada foto con AES-256-GCM y su huella (HU-MOV-08: TC-07, CRY-03, CRY-04). */
class PhotoCipherTest {

    private val cipher = PhotoCipher()
    private val photo = ByteArray(2_048) { (it % 256).toByte() }

    @Test
    fun `cifra y descifra la misma foto`() {
        val sealed = cipher.seal(photo)

        assertEquals(32, sealed.key.size)
        assertEquals(12, sealed.nonce.size)
        assertArrayEquals(photo, cipher.open(sealed.key, sealed.nonce, sealed.ciphertext))
    }

    @Test
    fun `cada foto tiene su propia llave y su propio nonce`() {
        val first = cipher.seal(photo)
        val second = cipher.seal(photo)

        assertFalse(first.key.contentEquals(second.key))
        assertFalse(first.nonce.contentEquals(second.nonce))
        assertFalse(first.ciphertext.contentEquals(second.ciphertext))
    }

    @Test
    fun `un byte cambiado, otra llave u otro nonce no descifran`() {
        val sealed = cipher.seal(photo)
        val tampered = sealed.ciphertext.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        val other = cipher.seal(photo)

        assertNull(cipher.open(sealed.key, sealed.nonce, tampered))
        assertNull(cipher.open(other.key, sealed.nonce, sealed.ciphertext))
        assertNull(cipher.open(sealed.key, other.nonce, sealed.ciphertext))
        assertNull(cipher.open(ByteArray(16), sealed.nonce, sealed.ciphertext))
    }

    @Test
    fun `la huella es el SHA-256 en hexadecimal`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", PhotoCipher.sha256("abc".toByteArray()))
    }
}
