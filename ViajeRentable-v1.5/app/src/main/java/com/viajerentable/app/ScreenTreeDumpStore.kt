package com.viajerentable.app

import android.content.Context

/** Stores the optional last accessibility tree locally on this device. */
object ScreenTreeDumpStore {
    private const val PREFS = "screen_tree_dump"
    private const val KEY_ENABLED = "capture_enabled"
    private const val KEY_LAST_DUMP = "last_dump"
    const val MAX_DUMP_CHARS = 20000

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun saveLastDump(context: Context, dump: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_DUMP, dump.take(MAX_DUMP_CHARS))
            .apply()
    }

    fun lastDump(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_DUMP, "")
            ?: ""
    }
}
