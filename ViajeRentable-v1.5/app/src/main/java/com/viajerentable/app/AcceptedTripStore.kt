package com.viajerentable.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AcceptedTripStore {
    private const val PREFS = "accepted_trips"
    private const val KEY_RECORDS = "records"
    private const val KEY_SCREEN = "latest_uber_screen"
    private var lastWrite = 0L
    private var lastText = ""

    @Synchronized
    fun updateLatestScreen(context: Context, text: String) {
        if (text.isBlank() || text == lastText) return
        val now = System.currentTimeMillis()
        if (now - lastWrite < 2000L) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SCREEN, text.take(7000)).apply()
        lastWrite = now
        lastText = text
    }

    fun latestScreen(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SCREEN, "") ?: ""

    fun add(context: Context, reason: String, screenText: String): Boolean {
        if (screenText.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        fun clean(value: String): String =
            value.replace("\r", " ").replace("\n", " ↵ ").trim()

        val record = "VIAJE ACEPTADO [$stamp]\n" +
            "Motivo: ${clean(reason.ifBlank { "No indicado" })}\n" +
            "Datos visibles de Uber: ${clean(screenText).take(5000)}"
        val old = prefs.getString(KEY_RECORDS, "") ?: ""
        prefs.edit().putString(KEY_RECORDS, (record + "\n---\n" + old).take(30000)).apply()
        return true
    }

    fun read(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECORDS, "")?.takeIf { it.isNotBlank() }
            ?: "Todavía no has guardado viajes aceptados."

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_RECORDS).apply()
    }
}