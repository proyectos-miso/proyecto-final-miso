package com.solventa.app.data.claims

import com.solventa.app.domain.claims.sha256Hex
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SealedPhoto(val key: ByteArray, val nonce: ByteArray, val ciphertext: ByteArray)

/**
 * Cifrado de cada foto con AES-256-GCM y una llave aleatoria propia, usada una sola vez (CRY-03). La llave y el
 * nonce se guardan en la fila de la foto, dentro de la base cifrada; una foto alterada o cambiada no descifra.
 */
class PhotoCipher(private val random: SecureRandom = SecureRandom()) {

    fun seal(plain: ByteArray): SealedPhoto {
        val key = ByteArray(KEY_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return SealedPhoto(key, nonce, cipher.doFinal(plain))
    }

    /** null si el archivo no corresponde a esa llave o fue alterado. */
    fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray? = try {
        if (key.size != KEY_BYTES || nonce.size != NONCE_BYTES) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.doFinal(ciphertext)
        }
    } catch (e: GeneralSecurityException) {
        null
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128

        /** Huella SHA-256 en hexadecimal de los bytes JPEG que se envían (CRY-04). */
        fun sha256(bytes: ByteArray): String = sha256Hex(bytes)
    }
}
