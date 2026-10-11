package com.viajerentable.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Private on-device records; no data is uploaded. */
object AcceptedTripStore {
    private const val PREFS = "accepted_trips"
    private const val KEY_RECORDS = "records"
    private const val KEY_SCREEN = "latest_uber_screen"
    private const val MIN_WRITE_INTERVAL_MS = 2000L

    private var lastWrite = 0L
    private var lastText = ""

    @Synchronized
    fun updateLatestScreen(c: Context, text: String) {
        if (text.isBlank() || text == lastText) return
        val now = System.currentTimeMillis()
        if (now - lastWrite < MIN_WRITE_INTERVAL_MS) return
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SCREEN, text.take(7000)).apply()
        lastWrite = now
        lastText = text
    }

    fun latestScreen(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SCREEN, "") ?: ""

    private fun clean(s: String): String = s.replace("\r", " ").replace("\n", " ↵ ").trim()

    fun add(c: Context, reason: String, screenText: String): Boolean {
        if (screenText.isBlank()) return false
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val record = "VIAJE ACEPTADO [$stamp]\n" +
            "Motivo: ${clean(reason.ifBlank { "No indicado" })}\n" +
            "Datos visibles de Uber: ${clean(screenText).take(5000)}"
        val old = prefs.getString(KEY_RECORDS, "") ?: ""
        prefs.edit().putString(KEY_RECORDS, (record + "\n---\n" + old).take(30000)).apply()
        return true
    }

    fun read(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RECORDS, "")
            ?.takeIf { it.isNotBlank() } ?: "Todavía no has guardado viajes aceptados."

    fun clear(c: Context) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_RECORDS).apply()
    }
}
