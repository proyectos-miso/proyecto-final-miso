package com.solventa.app.data.settings

import android.content.Context
import com.solventa.app.data.policies.PassphraseVault
import com.solventa.app.domain.policies.NotificationPreferences
import com.solventa.app.domain.policies.PushPreferences

/**
 * Preferencias de avisos por cuenta (FE-MOV-04). No son datos sensibles: solo tres interruptores bajo el id de la
 * cuenta, sin el correo. Se borran con los demás datos de la cuenta.
 */
class NotificationPreferenceStore(context: Context) : NotificationPreferences {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun read(accountEmail: String): PushPreferences {
        val id = PassphraseVault.accountId(accountEmail)
        return PushPreferences(
            claims = prefs.getBoolean("$id.claims", true),
            renewals = prefs.getBoolean("$id.renewals", true),
            payments = prefs.getBoolean("$id.payments", true),
        )
    }

    @Synchronized
    override fun pending(accountEmail: String): Boolean = prefs.getBoolean("${PassphraseVault.accountId(accountEmail)}.pending", false)

    @Synchronized
    override fun save(accountEmail: String, preferences: PushPreferences, pending: Boolean) {
        val id = PassphraseVault.accountId(accountEmail)
        prefs.edit()
            .putBoolean("$id.claims", preferences.claims)
            .putBoolean("$id.renewals", preferences.renewals)
            .putBoolean("$id.payments", preferences.payments)
            .putBoolean("$id.pending", pending)
            .apply()
    }

    @Synchronized
    override fun replaceIf(accountEmail: String, expected: PushPreferences, expectedPending: Boolean, preferences: PushPreferences): Boolean {
        if (read(accountEmail) != expected || pending(accountEmail) != expectedPending) return false
        save(accountEmail, preferences, pending = false)
        return true
    }

    @Synchronized
    override fun delete(accountEmail: String) {
        val id = PassphraseVault.accountId(accountEmail)
        prefs.edit().remove("$id.claims").remove("$id.renewals").remove("$id.payments").remove("$id.pending").apply()
    }

    private companion object {
        const val PREFS_NAME = "solventa_notices"
    }
}
