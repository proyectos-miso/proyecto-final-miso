package com.solventa.app.data.policies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.KeyStoreSpi
import java.security.ProviderException
import java.security.cert.Certificate
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Qué falla del Keystore descarta la copia y cuál solo la deja no disponible (HU-MOV-05: TC-04, TC-05).
@RunWith(RobolectricTestRunner::class)
class KeystoreWalletKeysTest {

    private val key = SecretKeySpec(ByteArray(32) { 7 }, "AES")
    private val nonce = ByteArray(12) { 3 }
    private val wrapped = WrappedSecret(nonce, encrypt(byteArrayOf(1, 2, 3)))

    @Test
    fun `con la llave presente desenvuelve el secreto`() {
        val unwrapped = keys(FakeSpi(key)).unwrap(ALIAS, wrapped)

        assertTrue((unwrapped as Unwrapped.Secret).bytes.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `sin el alias o con el secreto alterado es invalida`() {
        assertEquals(Unwrapped.Invalid, keys(FakeSpi(null)).unwrap(ALIAS, wrapped))
        val tampered = WrappedSecret(nonce, wrapped.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
        assertEquals(Unwrapped.Invalid, keys(FakeSpi(key)).unwrap(ALIAS, tampered))
    }

    @Test
    fun `un Keystore que falla al cargar, buscar o borrar no lanza y deja la llave no disponible`() {
        val broken = KeystoreWalletKeys(isDeviceLocked = { false }, loadKeyStore = { throw IOException("Keystore ocupado") })
        assertEquals(Unwrapped.Unavailable, broken.unwrap(ALIAS, wrapped))
        assertNull(broken.wrap(ALIAS, byteArrayOf(1)))
        broken.delete(ALIAS)

        listOf(ProviderException("pasajera"), KeyStoreException("pasajera")).forEach { failure ->
            val failing = keys(FakeSpi(key, failure))
            assertEquals(Unwrapped.Unavailable, failing.unwrap(ALIAS, wrapped))
            failing.delete(ALIAS)
        }
    }

    @Test
    fun `con el telefono bloqueado antes o despues del fallo la llave no esta disponible`() {
        val tampered = WrappedSecret(nonce, ByteArray(wrapped.ciphertext.size))
        var calls = 0
        val lockedAfterFailure = KeystoreWalletKeys(isDeviceLocked = { calls++ > 0 }, loadKeyStore = { store(FakeSpi(key)) })

        assertEquals(Unwrapped.Unavailable, keys(FakeSpi(key), locked = true).unwrap(ALIAS, wrapped))
        assertNull(keys(FakeSpi(key), locked = true).wrap(ALIAS, byteArrayOf(1)))
        assertEquals(Unwrapped.Unavailable, lockedAfterFailure.unwrap(ALIAS, tampered))
        assertEquals(Unwrapped.Invalid, keys(FakeSpi(null)).unwrap(ALIAS, tampered))
    }

    private fun keys(spi: FakeSpi, locked: Boolean = false) =
        KeystoreWalletKeys(isDeviceLocked = { locked }, loadKeyStore = { store(spi) })

    private fun store(spi: KeyStoreSpi): KeyStore = object : KeyStore(spi, null, "fake") {}.apply { load(null) }

    private fun encrypt(plaintext: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce)) }.doFinal(plaintext)

    private class FakeSpi(private val key: Key?, private val failure: Exception? = null) : KeyStoreSpi() {
        override fun engineGetKey(alias: String, password: CharArray?): Key? {
            failure?.let { throw it }
            return key
        }

        override fun engineContainsAlias(alias: String): Boolean {
            failure?.let { throw it }
            return key != null
        }

        override fun engineDeleteEntry(alias: String) {
            failure?.let { throw it }
        }

        override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
        override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
        override fun engineGetCertificate(alias: String): Certificate? = null
        override fun engineGetCreationDate(alias: String): Date? = null
        override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<Certificate>?) = Unit
        override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<Certificate>?) = Unit
        override fun engineSetCertificateEntry(alias: String, cert: Certificate) = Unit
        override fun engineAliases(): Enumeration<String> = Collections.emptyEnumeration()
        override fun engineSize(): Int = 0
        override fun engineIsKeyEntry(alias: String): Boolean = key != null
        override fun engineIsCertificateEntry(alias: String): Boolean = false
        override fun engineGetCertificateAlias(cert: Certificate): String? = null
        override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
    }

    private companion object {
        const val ALIAS = "solventa_wallet_prueba"
    }
}
