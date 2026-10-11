package com.viajerentable.app

import android.content.Context

object LogStore {
    private const val PREFS = "diagnostic_log"
    private const val KEY = "entries"
    private const val LAST_DIAGNOSTIC_KEY = "last_diagnostic_at"
    private const val LIMIT = 4000
    private const val DIAGNOSTIC_INTERVAL_MS = 30000L

    @Synchronized
    fun add(c: Context, line: String) {
        val preferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val isDiagnostic = line.startsWith("DIAGNOSTICO")
        val now = System.currentTimeMillis()
        if (isDiagnostic) {
            val lastDiagnosticAt = preferences.getLong(LAST_DIAGNOSTIC_KEY, 0L)
            if (lastDiagnosticAt > 0L && now - lastDiagnosticAt < DIAGNOSTIC_INTERVAL_MS) {
                return
            }
            preferences.edit().putLong(LAST_DIAGNOSTIC_KEY, now).apply()
        }

        val rows = (preferences.getString(KEY, "") ?: "")
            .lines()
            .filter { it.isNotBlank() }
            .toMutableList()
        rows.addAll(line.lines().filter { it.isNotBlank() })

        while (rows.joinToString("\n").length > LIMIT && rows.isNotEmpty()) {
            val oldestDiagnostic = rows.indexOfFirst { it.startsWith("DIAGNOSTICO") }
            if (oldestDiagnostic >= 0) {
                rows.removeAt(oldestDiagnostic)
            } else {
                rows.removeAt(0)
            }
        }
        preferences.edit().putString(KEY, rows.joinToString("\n")).apply()
    }

    fun read(c: Context): String {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "Sin registros todavía.") ?: ""
    }

    fun clear(c: Context) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .remove(LAST_DIAGNOSTIC_KEY)
            .apply()
    }
}
