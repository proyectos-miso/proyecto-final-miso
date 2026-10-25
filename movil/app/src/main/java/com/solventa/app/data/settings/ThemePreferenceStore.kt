package com.solventa.app.data.settings

import android.content.Context

/** Tema elegido en el teléfono; null mientras el asegurado no haya elegido y se siga el del sistema. */
class ThemePreferenceStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun darkTheme(): Boolean? = if (prefs.contains(KEY_DARK)) prefs.getBoolean(KEY_DARK, false) else null

    fun saveDarkTheme(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK, dark).apply()
    }

    private companion object {
        const val PREFS_NAME = "solventa_ui"
        const val KEY_DARK = "dark_theme"
    }
}
