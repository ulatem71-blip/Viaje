package com.viajerentable.app

import android.content.Context

object LogStore {
    private const val PREFS = "diagnostic_log"
    private const val AUDIT_PREFS = "acceptance_audit"
    private const val AUDIT_KEY = "entries"
    private const val AUDIT_LIMIT = 1000
    private const val DISCARD_AUDIT_PREFS = "discard_x_audit"
    private const val DISCARD_AUDIT_KEY = "entries"
    private const val KEY = "entries"
    private const val LAST_DIAGNOSTIC_KEY = "last_diagnostic_at"
    private const val LAST_MULTI_CARD_KEY = "last_multi_card_at"
    private const val LAST_MULTI_TARIFFS_KEY = "last_multi_card_tariffs"
    private const val LIMIT = 4000
    private const val DIAGNOSTIC_INTERVAL_MS = 30000L
    private const val MULTI_CARD_INTERVAL_MS = 60000L
    private const val MAX_MULTI_CARD_LINES = 5

    @Synchronized
    fun add(c: Context, line: String) {
        val preferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val isDiagnostic = line.startsWith("DIAGNOSTICO")
        val isMultiCardDiagnostic = line.startsWith("VARIAS TARJETAS")

        if (isDiagnostic) {
            val lastDiagnosticAt = preferences.getLong(LAST_DIAGNOSTIC_KEY, 0L)
            if (lastDiagnosticAt > 0L && now - lastDiagnosticAt < DIAGNOSTIC_INTERVAL_MS) {
                return
            }
            preferences.edit().putLong(LAST_DIAGNOSTIC_KEY, now).apply()
        }

        if (isMultiCardDiagnostic) {
            val tariffs = extractTariffs(line)
            val lastTariffs = preferences.getString(LAST_MULTI_TARIFFS_KEY, null)
            if (tariffs != null && tariffs == lastTariffs) {
                return
            }
            val lastMultiCardAt = preferences.getLong(LAST_MULTI_CARD_KEY, 0L)
            if (lastMultiCardAt > 0L && now - lastMultiCardAt < MULTI_CARD_INTERVAL_MS) {
                return
            }
            preferences.edit()
                .putLong(LAST_MULTI_CARD_KEY, now)
                .putString(LAST_MULTI_TARIFFS_KEY, tariffs)
                .apply()
        }

        val rows = (preferences.getString(KEY, "") ?: "")
            .lines()
            .filter { it.isNotBlank() }
            .toMutableList()
        rows.addAll(line.lines().filter { it.isNotBlank() })

        while (rows.count { it.startsWith("VARIAS TARJETAS") } > MAX_MULTI_CARD_LINES) {
            val oldestMultiCard = rows.indexOfFirst { it.startsWith("VARIAS TARJETAS") }
            if (oldestMultiCard >= 0) {
                rows.removeAt(oldestMultiCard)
            }
        }

        while (rows.joinToString("\n").length > LIMIT && rows.isNotEmpty()) {
            val multiCardCount = rows.count { it.startsWith("VARIAS TARJETAS") }
            val oldestDiagnostic = rows.indexOfFirst { it.startsWith("DIAGNOSTICO") }
            if (oldestDiagnostic >= 0) {
                rows.removeAt(oldestDiagnostic)
            } else {
                val oldestNonMultiCard = rows.indexOfFirst { !it.startsWith("VARIAS TARJETAS") }
                if (oldestNonMultiCard >= 0) {
                    rows.removeAt(oldestNonMultiCard)
                } else {
                    break
                }
            }
        }
        preferences.edit().putString(KEY, rows.joinToString("\n")).apply()
    }

    private fun extractTariffs(line: String): String? {
        val match = Regex("""(?:^|;\s*)tarifas=([^;]*)(?:;|$)""").find(line)
        return match?.groupValues?.get(1)?.trim()
    }

    fun read(c: Context): String {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "Sin registros todavía.") ?: ""
    }

    @Synchronized
    fun addAudit(c: Context, line: String) {
        val preferences = c.getSharedPreferences(AUDIT_PREFS, Context.MODE_PRIVATE)
        val rows = (preferences.getString(AUDIT_KEY, "") ?: "")
            .lines()
            .filter { it.isNotBlank() }
            .toMutableList()
        rows.add(line)
        while (rows.size > AUDIT_LIMIT) {
            rows.removeAt(0)
        }
        preferences.edit().putString(AUDIT_KEY, rows.joinToString("\n")).apply()
    }

    fun readAudit(c: Context): String {
        return c.getSharedPreferences(AUDIT_PREFS, Context.MODE_PRIVATE)
            .getString(AUDIT_KEY, "Sin aceptaciones reales registradas.") ?: ""
    }

    fun csvAudit(c: Context): String {
        val rows = (c.getSharedPreferences(AUDIT_PREFS, Context.MODE_PRIVATE)
            .getString(AUDIT_KEY, "") ?: "").lines().filter { it.isNotBlank() }
        val header = "fecha,tarifaCRC,ruta,recogidaKm,netoPorKm,netoPorHora,boundsBoton,resultado"
        val data = rows.map { line ->
            line.split("|", limit = 8).joinToString(",") { cell ->
                "\"" + cell.replace("\"", "\"\"") + "\""
            }
        }
        return (listOf(header) + data).joinToString("\r\n")
    }

    @Synchronized
    fun addDiscardAudit(c: Context, line: String) {
        val preferences = c.getSharedPreferences(DISCARD_AUDIT_PREFS, Context.MODE_PRIVATE)
        val rows = (preferences.getString(DISCARD_AUDIT_KEY, "") ?: "")
            .lines()
            .filter { it.isNotBlank() }
            .toMutableList()
        rows.add(line)
        while (rows.size > AUDIT_LIMIT) {
            rows.removeAt(0)
        }
        preferences.edit().putString(DISCARD_AUDIT_KEY, rows.joinToString("\n")).apply()
    }

    fun readDiscardAudit(c: Context): String {
        return c.getSharedPreferences(DISCARD_AUDIT_PREFS, Context.MODE_PRIVATE)
            .getString(DISCARD_AUDIT_KEY, "Sin descartes reales registrados.")
            ?: ""
    }

    fun csvDiscardAudit(c: Context): String {
        val rows = (c.getSharedPreferences(DISCARD_AUDIT_PREFS, Context.MODE_PRIVATE)
            .getString(DISCARD_AUDIT_KEY, "") ?: "").lines().filter { it.isNotBlank() }
        val header = "fecha,tarifaCRC,ruta,motivoRechazo,tipoTarjeta,boundsX,resultado"
        val data = rows.map { line ->
            line.split("|", limit = 7).joinToString(",") { cell ->
                "\"" + cell.replace("\"", "\"\"") + "\""
            }
        }
        return (listOf(header) + data).joinToString("\r\n")
    }

    fun clear(c: Context) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .remove(LAST_DIAGNOSTIC_KEY)
            .remove(LAST_MULTI_CARD_KEY)
            .remove(LAST_MULTI_TARIFFS_KEY)
            .apply()
    }
}
