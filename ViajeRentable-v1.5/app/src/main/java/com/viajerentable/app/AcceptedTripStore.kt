package com.viajerentable.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Private on-device records; no data is uploaded. */
object AcceptedTripStore {
    private const val PREFS = "accepted_trips"
    private const val KEY_RECORDS = "records"
    private const val KEY_LATEST_SCREEN = "latest_uber_screen"

    fun updateLatestScreen(context: Context, text: String) {
        if (text.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LATEST_SCREEN, text.take(7000)).apply()
    }

    fun latestScreen(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LATEST_SCREEN, "") ?: ""

    fun add(context: Context, reason: String, screenText: String): Boolean {
        if (screenText.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        fun clean(s: String) = s.replace("\\r", " ").replace("\\n", " ↵ ").trim()
        val record = "VIAJE ACEPTADO [$stamp]\\nMotivo: ${clean(reason.ifBlank { "No indicado" })}\\nDatos visibles de Uber: ${clean(screenText).take(5000)}"
        val old = prefs.getString(KEY_RECORDS, "") ?: ""
        prefs.edit().putString(KEY_RECORDS, (record + "\\n---\\n" + old).take(30000)).apply()
        return true
    }

    fun read(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECORDS, "")?.takeIf { it.isNotBlank() } ?: "Todavía no has guardado viajes aceptados."

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_RECORDS).apply()
    }
}