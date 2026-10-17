package com.viajerentable.app

import android.content.Context

/** Stores only the most recent ambiguous-card window dump on device. */
object AmbiguousCaseDumpStore {
    private const val PREFS = "ambiguous_case_dump"
    private const val KEY_LAST_DUMP = "last_ambiguous_dump"
    const val MAX_DUMP_CHARS = 20000

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
