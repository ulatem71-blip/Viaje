package com.viajerentable.app

import android.content.Context

object LogStore {
    private const val PREFS = "diagnostic_log"
    private const val KEY = "entries"
    fun add(context: Context, line: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getString(KEY, "") ?: ""
        val next = (old + line + "\n").takeLast(12000)
        prefs.edit().putString(KEY, next).apply()
    }
    fun read(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "Sin registros todavía.") ?: ""
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
