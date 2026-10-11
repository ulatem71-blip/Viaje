package com.viajerentable.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordedOffer(val timestamp: String, val parsed: ParsedOffer, val result: ProfitResult?)

object OffersStore {
    private const val PREFS = "offers_log"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 200

    @Synchronized
    fun add(context: Context, parsed: ParsedOffer, result: ProfitResult?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = try {
            JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        val out = JSONArray()
        val start = (old.length() - MAX_ENTRIES + 1).coerceAtLeast(0)
        for (i in start until old.length()) out.put(old.getJSONObject(i))

        val entry = JSONObject()
            .put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            .put("fare", parsed.fare)
            .put("pickupKm", parsed.pickupKm ?: JSONObject.NULL)
            .put("pickupMin", parsed.pickupMinutes ?: JSONObject.NULL)
            .put("tripKm", parsed.tripKm ?: JSONObject.NULL)
            .put("tripMin", parsed.tripMinutes ?: JSONObject.NULL)
            .put("net", result?.net ?: JSONObject.NULL)
            .put("perKm", result?.netPerKm ?: JSONObject.NULL)
            .put("perHour", result?.netPerHour ?: JSONObject.NULL)
            .put("recommended", result?.recommended ?: false)
        out.put(entry)
        prefs.edit().putString(KEY, out.toString()).apply()
    }

    fun read(context: Context): List<RecordedOffer> {
        val entries = try {
            JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        return (0 until entries.length()).mapNotNull { index ->
            try {
                val item = entries.getJSONObject(index)
                fun optionalDouble(key: String): Double? =
                    if (item.isNull(key)) null else item.getDouble(key)

                val parsed = ParsedOffer(
                    fare = item.getDouble("fare"),
                    pickupKm = optionalDouble("pickupKm"),
                    pickupMinutes = optionalDouble("pickupMin"),
                    tripKm = optionalDouble("tripKm"),
                    tripMinutes = optionalDouble("tripMin")
                )
                val result = if (item.isNull("net")) null else ProfitResult(
                    totalKm = (parsed.pickupKm ?: 0.0) + (parsed.tripKm ?: 0.0),
                    totalMinutes = (parsed.pickupMinutes ?: 0.0) + (parsed.tripMinutes ?: 0.0),
                    fuelCost = 0.0,
                    maintenanceCost = 0.0,
                    net = item.getDouble("net"),
                    netPerKm = item.getDouble("perKm"),
                    netPerHour = item.getDouble("perHour"),
                    recommended = item.optBoolean("recommended")
                )
                RecordedOffer(item.getString("date"), parsed, result)
            } catch (_: Exception) {
                null
            }
        }.reversed()
    }

    fun csv(context: Context): String {
        val rows = mutableListOf("fecha,tarifaCRC,recogidaKm,recogidaMin,viajeKm,viajeMin,netoCRC,netoPorKm,netoPorHora,recomendado")
        read(context).reversed().forEach { offer ->
            fun number(value: Double?): String = value?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            rows += listOf(
                offer.timestamp, number(offer.parsed.fare), number(offer.parsed.pickupKm),
                number(offer.parsed.pickupMinutes), number(offer.parsed.tripKm),
                number(offer.parsed.tripMinutes), number(offer.result?.net),
                number(offer.result?.netPerKm), number(offer.result?.netPerHour),
                offer.result?.recommended?.toString() ?: "false"
            ).joinToString(",") { csvCell(it) }
        }
        return rows.joinToString("\r\n")
    }

    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
}