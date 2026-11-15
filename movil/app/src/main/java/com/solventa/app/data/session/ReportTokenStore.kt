package com.solventa.app.data.session

import android.content.Context
import com.solventa.app.data.policies.PassphraseVault
import com.solventa.app.data.policies.Unwrapped
import com.solventa.app.data.policies.WalletKeys
import com.solventa.app.data.policies.WrappedSecret
import java.util.Base64

/**
 * Último token de reporte de cada cuenta (HU-MOV-09, SR-02). Solo sirve para `POST insured/claims` y permite enviar
 * la cola con la app cerrada, sin guardar el token de acceso ni el de renovación (HU-MOV-07). Se lee por el id de
 * la cuenta porque el envío corre sin sesión ni correo. Ninguna operación lanza.
 */
interface ReportTokens {
    fun save(accountEmail: String, token: String)

    fun read(accountId: String): String?

    fun delete(accountEmail: String)

    object None : ReportTokens {
        override fun save(accountEmail: String, token: String) = Unit
        override fun read(accountId: String): String? = null
        override fun delete(accountEmail: String) = Unit
    }
}

/** Cifrado con una llave AES-GCM del Keystore por cuenta, sin huella ni desbloqueo (CTL-05). */
class ReportTokenStore(context: Context, private val keys: WalletKeys) : ReportTokens {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun save(accountEmail: String, token: String) {
        val accountId = PassphraseVault.accountId(accountEmail)
        val plain = token.toByteArray(Charsets.UTF_8)
        val wrapped = try {
            keys.wrap(alias(accountId), plain)
        } finally {
            plain.fill(0)
        }
        // Sin llave se borra el anterior: ya no sirve, porque el servidor lo ata al refresh que se acaba de rotar.
        if (wrapped == null) return remove(accountId)
        prefs.edit()
            .putString("$accountId.iv", encode(wrapped.iv))
            .putString("$accountId.ct", encode(wrapped.ciphertext))
            .commit()
    }

    override fun read(accountId: String): String? {
        val iv = prefs.getString("$accountId.iv", null) ?: return null
        val ciphertext = prefs.getString("$accountId.ct", null) ?: return null
        val wrapped = try {
            WrappedSecret(decode(iv), decode(ciphertext))
        } catch (e: IllegalArgumentException) {
            remove(accountId)
            return null
        }
        return when (val opened = keys.unwrap(alias(accountId), wrapped)) {
            is Unwrapped.Secret -> try {
                String(opened.bytes, Charsets.UTF_8)
            } finally {
                opened.bytes.fill(0)
            }
            Unwrapped.Invalid -> {
                remove(accountId)
                null
            }
            Unwrapped.Unavailable -> null
        }
    }

    override fun delete(accountEmail: String) = remove(PassphraseVault.accountId(accountEmail))

    private fun remove(accountId: String) {
        prefs.edit().remove("$accountId.iv").remove("$accountId.ct").commit()
        keys.delete(alias(accountId))
    }

    private fun alias(accountId: String) = "$ALIAS_PREFIX$accountId"
    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(value: String) = Base64.getDecoder().decode(value)

    private companion object {
        const val PREFS = "solventa_report_tokens"
        const val ALIAS_PREFIX = "solventa_report_"
    }
}
