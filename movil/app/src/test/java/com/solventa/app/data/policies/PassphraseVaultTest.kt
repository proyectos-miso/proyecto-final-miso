package com.solventa.app.data.policies

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64

// Frase de la base guardada solo envuelta por la llave de su cuenta (HU-MOV-04: CRY-01, CRY-02; HU-MOV-05).
@RunWith(RobolectricTestRunner::class)
class PassphraseVaultTest {

    private class XorWalletKeys : WalletKeys {
        val aliases = mutableSetOf<String>()
        var canWrap = true
        var lastWrapped: ByteArray? = null

        override fun wrap(alias: String, plaintext: ByteArray): WrappedSecret? {
            lastWrapped = plaintext
            if (!canWrap) return null
            aliases += alias
            return WrappedSecret(ByteArray(12) { 1 }, plaintext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray() + ByteArray(16))
        }

        override fun unwrap(alias: String, wrapped: WrappedSecret): Unwrapped =
            if (alias in aliases) {
                Unwrapped.Secret(wrapped.ciphertext.dropLast(16).map { (it.toInt() xor 0x5A).toByte() }.toByteArray())
            } else {
                Unwrapped.Invalid
            }

        override fun delete(alias: String) {
            aliases -= alias
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("solventa_wallet", Context.MODE_PRIVATE)
    private lateinit var keys: XorWalletKeys
    private lateinit var vault: PassphraseVault

    @Before
    fun setUp() {
        keys = XorWalletKeys()
        vault = PassphraseVault(context, keys)
    }

    @Test
    fun `crea una frase aleatoria de 32 bytes por cuenta y solo guarda su version envuelta`() {
        val first = vault.create(ACCOUNT)!!
        val second = vault.create(OTHER_ACCOUNT)!!

        assertEquals(32, first.size)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(first, (vault.existing(ACCOUNT) as Unwrapped.Secret).bytes)
        assertFalse(prefs.all.values.joinToString().contains(Base64.getEncoder().encodeToString(first)))
    }

    @Test
    fun `borrar la cuenta elimina su frase, su llave y su marca sin tocar las demas`() {
        vault.create(ACCOUNT)
        vault.create(OTHER_ACCOUNT)
        vault.markDiscarded(ACCOUNT)

        vault.delete(ACCOUNT)

        assertNull(vault.existing(ACCOUNT))
        assertFalse(vault.wasDiscarded(ACCOUNT))
        assertTrue(vault.existing(OTHER_ACCOUNT) is Unwrapped.Secret)
        assertEquals(setOf("solventa_wallet_$OTHER_ACCOUNT"), keys.aliases)
    }

    @Test
    fun `sin la llave del Keystore la frase envuelta es invalida`() {
        vault.create(ACCOUNT)
        keys.aliases.clear()

        assertEquals(Unwrapped.Invalid, vault.existing(ACCOUNT))
    }

    // HU-MOV-05 TC-04: una frase guardada dañada es una llave perdida, sin lanzar.
    @Test
    fun `una frase guardada con Base64, IV o texto cifrado invalidos es invalida`() {
        // Con la llave presente, solo la validación del almacén puede responder Invalid.
        vault.create(ACCOUNT)
        val valid = Base64.getEncoder().encodeToString(ByteArray(16))
        listOf(
            "no-es-base64!" to valid,
            Base64.getEncoder().encodeToString(ByteArray(1)) to valid,
            Base64.getEncoder().encodeToString(ByteArray(12)) to Base64.getEncoder().encodeToString(ByteArray(8)),
        ).forEach { (iv, ciphertext) ->
            prefs.edit().putString("$ACCOUNT.iv", iv).putString("$ACCOUNT.ct", ciphertext).commit()

            assertEquals(Unwrapped.Invalid, vault.existing(ACCOUNT))
        }
    }

    // HU-MOV-05 TC-04: si la llave no se puede usar, la frase nueva no queda en memoria.
    @Test
    fun `si no se puede envolver, create devuelve null y deja la frase en ceros`() {
        keys.canWrap = false

        assertNull(vault.create(ACCOUNT))
        assertTrue(keys.lastWrapped!!.all { it == 0.toByte() })
        assertNull(vault.existing(ACCOUNT))
    }

    // HU-MOV-05 TC-07: la marca de copia descartada dura hasta que se borra.
    @Test
    fun `la marca de copia descartada sigue hasta borrarla`() {
        vault.markDiscarded(ACCOUNT)

        assertTrue(vault.wasDiscarded(ACCOUNT))
        assertTrue(vault.wasDiscarded(ACCOUNT))
        assertFalse(vault.wasDiscarded(OTHER_ACCOUNT))

        vault.clearDiscarded(ACCOUNT)
        assertFalse(vault.wasDiscarded(ACCOUNT))
    }

    @Test
    fun `el identificador de la cuenta no deja el correo y no distingue mayusculas`() {
        val id = PassphraseVault.accountId(" Asegurada@Example.com ")

        assertEquals(PassphraseVault.accountId("asegurada@example.com"), id)
        assertEquals(32, id.length)
        assertFalse(id.contains("asegurada"))
    }

    private companion object {
        val ACCOUNT = PassphraseVault.accountId("asegurada@example.com")
        val OTHER_ACCOUNT = PassphraseVault.accountId("otra.cuenta@example.com")
    }
}
