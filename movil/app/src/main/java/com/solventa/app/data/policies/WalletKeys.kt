package com.solventa.app.data.policies

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class WrappedSecret(val iv: ByteArray, val ciphertext: ByteArray)

/** Resultado de desenvolver un secreto (HU-MOV-05). */
sealed interface Unwrapped {
    class Secret(val bytes: ByteArray) : Unwrapped

    /** La llave no existe o quedó invalidada, o el secreto envuelto está dañado: no volverá a servir. */
    data object Invalid : Unwrapped

    /** El teléfono está bloqueado o el Keystore falló de forma pasajera: puede servir más tarde. */
    data object Unavailable : Unwrapped
}

/** Ninguna operación lanza. */
interface WalletKeys {
    /** null si la llave no se puede usar ahora. */
    fun wrap(alias: String, plaintext: ByteArray): WrappedSecret?

    fun unwrap(alias: String, wrapped: WrappedSecret): Unwrapped

    fun delete(alias: String)
}

// Una llave AES-GCM por cuenta que no sale del Keystore y solo funciona con el teléfono desbloqueado (CRY-02).
// El bloqueo se consulta antes de usar la llave y después de un fallo (CTL-05); solo una invalidez real es
// Invalid, y cualquier otra falla es Unavailable para no borrar una copia que aún sirve (CTL-09).
class KeystoreWalletKeys(
    private val isDeviceLocked: () -> Boolean,
    loadKeyStore: () -> KeyStore = { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } },
) : WalletKeys {

    private val keyStore: KeyStore by lazy(loadKeyStore)

    override fun wrap(alias: String, plaintext: ByteArray): WrappedSecret? {
        if (isDeviceLocked()) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, existingKey(alias) ?: generateKey(alias))
            WrappedSecret(cipher.iv, cipher.doFinal(plaintext))
        } catch (e: Exception) {
            null
        }
    }

    override fun unwrap(alias: String, wrapped: WrappedSecret): Unwrapped {
        if (isDeviceLocked()) return Unwrapped.Unavailable
        return try {
            val key = existingKey(alias) ?: return Unwrapped.Invalid
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, wrapped.iv))
            Unwrapped.Secret(cipher.doFinal(wrapped.ciphertext))
        } catch (e: Exception) {
            if (!isDeviceLocked() && e.isPermanent()) Unwrapped.Invalid else Unwrapped.Unavailable
        }
    }

    override fun delete(alias: String) {
        try {
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        } catch (e: Exception) {
            // Una llave que no se pudo borrar queda sin frase ni copia que abrir.
        }
    }

    private fun existingKey(alias: String): SecretKey? = keyStore.getKey(alias, null) as SecretKey?

    private fun generateKey(alias: String): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUnlockedDeviceRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private fun Exception.isPermanent(): Boolean =
        this is KeyPermanentlyInvalidatedException || this is AEADBadTagException ||
            this is InvalidAlgorithmParameterException || this is IllegalBlockSizeException

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}

/** Frase aleatoria de la base de cada cuenta, guardada solo envuelta por su llave del Keystore (CRY-01). */
class PassphraseVault(context: Context, private val keys: WalletKeys) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    /** null sin frase guardada; Invalid si la guardada está dañada (HU-MOV-05). */
    fun existing(accountId: String): Unwrapped? {
        val iv = prefs.getString("$accountId.iv", null) ?: return null
        val ciphertext = prefs.getString("$accountId.ct", null) ?: return null
        val wrapped = try {
            WrappedSecret(decode(iv), decode(ciphertext))
        } catch (e: IllegalArgumentException) {
            return Unwrapped.Invalid
        }
        if (wrapped.iv.size != GCM_IV_BYTES || wrapped.ciphertext.size < GCM_TAG_BYTES) return Unwrapped.Invalid
        return keys.unwrap(alias(accountId), wrapped)
    }

    /** null si la llave no se pudo usar; en ese caso la frase nueva se borra de la memoria (CTL-07). */
    fun create(accountId: String): ByteArray? {
        val passphrase = ByteArray(PASSPHRASE_BYTES).also(random::nextBytes)
        val wrapped = keys.wrap(alias(accountId), passphrase) ?: run {
            passphrase.fill(0)
            return null
        }
        prefs.edit()
            .putString("$accountId.iv", encode(wrapped.iv))
            .putString("$accountId.ct", encode(wrapped.ciphertext))
            .commit()
        return passphrase
    }

    fun delete(accountId: String) {
        prefs.edit().remove("$accountId.iv").remove("$accountId.ct").remove("$accountId.discarded").commit()
        keys.delete(alias(accountId))
    }

    // Marca de que se descartó una copia que existía, para avisar hasta la siguiente sincronización (AC-08).
    fun markDiscarded(accountId: String) {
        prefs.edit().putBoolean("$accountId.discarded", true).commit()
    }

    fun wasDiscarded(accountId: String): Boolean = prefs.getBoolean("$accountId.discarded", false)

    fun clearDiscarded(accountId: String) {
        prefs.edit().remove("$accountId.discarded").commit()
    }

    private fun alias(accountId: String) = "solventa_wallet_$accountId"
    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(value: String) = Base64.getDecoder().decode(value)

    companion object {
        private const val PREFS_NAME = "solventa_wallet"
        private const val PASSPHRASE_BYTES = 32
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BYTES = 16

        /** Identificador de la cuenta para archivos y alias, sin dejar el correo en el teléfono. */
        fun accountId(email: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(32)
    }
}
