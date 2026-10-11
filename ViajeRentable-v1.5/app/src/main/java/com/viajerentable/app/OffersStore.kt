package com.viajerentable.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordedOffer(
    val timestamp: String,
    val parsed: ParsedOffer,
    val result: ProfitResult?,
    val decision: Decision? = null,
    val reasons: List<String> = emptyList()
)

/** Private on-device log of detected offers (max 200 entries, oldest dropped whole). */
object OffersStore {
    private const val PREFS = "offers_log"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 200

    private fun load(c: Context): JSONArray = try {
        JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]")
    } catch (_: Exception) {
        JSONArray()
    }

    @Synchronized
    fun add(c: Context, p: ParsedOffer, r: ProfitResult?, demo: DemoResult? = null) {
        val old = load(c)
        val out = JSONArray()
        for (i in (old.length() - (MAX_ENTRIES - 1)).coerceAtLeast(0) until old.length()) {
            out.put(old.getJSONObject(i))
        }
        val entry = JSONObject()
            .put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            .put("fare", p.fare)
            .put("pickupKm", p.pickupKm ?: JSONObject.NULL)
            .put("pickupMin", p.pickupMinutes ?: JSONObject.NULL)
            .put("tripKm", p.tripKm ?: JSONObject.NULL)
            .put("tripMin", p.tripMinutes ?: JSONObject.NULL)
            .put("net", r?.net ?: JSONObject.NULL)
            .put("perKm", r?.netPerKm ?: JSONObject.NULL)
            .put("perHour", r?.netPerHour ?: JSONObject.NULL)
            .put("recommended", r?.recommended ?: false)
            .put("decision", demo?.decision?.name ?: JSONObject.NULL)
            .put("reasons", JSONArray(demo?.reasons ?: emptyList<String>()))
        out.put(entry)
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, out.toString()).apply()
    }

    private fun optDouble(o: JSONObject, k: String): Double? = if (o.isNull(k)) null else o.getDouble(k)

    /** Newest first. */
    fun read(c: Context): List<RecordedOffer> {
        val a = load(c)
        val list = mutableListOf<RecordedOffer>()
        for (i in 0 until a.length()) {
            try {
                val o = a.getJSONObject(i)
                val p = ParsedOffer(
                    o.getDouble("fare"),
                    optDouble(o, "pickupKm"),
                    optDouble(o, "pickupMin"),
                    optDouble(o, "tripKm"),
                    optDouble(o, "tripMin")
                )
                val r = if (o.isNull("net")) null else ProfitResult(
                    (p.pickupKm ?: 0.0) + (p.tripKm ?: 0.0),
                    (p.pickupMinutes ?: 0.0) + (p.tripMinutes ?: 0.0),
                    0.0,
                    0.0,
                    o.getDouble("net"),
                    o.getDouble("perKm"),
                    o.getDouble("perHour"),
                    o.optBoolean("recommended")
                )
                val decision = if (o.has("decision") && !o.isNull("decision")) {
                    runCatching { Decision.valueOf(o.getString("decision")) }.getOrNull()
                } else {
                    null
                }
                val reasonsArray = o.optJSONArray("reasons")
                val reasons = mutableListOf<String>()
                if (reasonsArray != null) {
                    for (j in 0 until reasonsArray.length()) {
                        reasonsArray.optString(j).takeIf { it.isNotBlank() }?.let(reasons::add)
                    }
                }
                list.add(RecordedOffer(o.getString("date"), p, r, decision, reasons))
            } catch (_: Exception) {
                // Skip a corrupt entry instead of losing the whole log.
            }
        }
        return list.reversed()
    }

    private fun num(v: Double?): String = v?.let { String.format(Locale.US, "%.2f", it) } ?: ""

    private fun csvCell(value: String): String {
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    fun csv(c: Context): String {
        val rows = mutableListOf("fecha,tarifaCRC,recogidaKm,recogidaMin,viajeKm,viajeMin,netoCRC,netoPorKm,netoPorHora,recomendado,decisionDemo,motivos")
        for (x in read(c).reversed()) {
            rows.add(
                listOf(
                    x.timestamp,
                    num(x.parsed.fare),
                    num(x.parsed.pickupKm),
                    num(x.parsed.pickupMinutes),
                    num(x.parsed.tripKm),
                    num(x.parsed.tripMinutes),
                    num(x.result?.net),
                    num(x.result?.netPerKm),
                    num(x.result?.netPerHour),
                    (x.result?.recommended ?: false).toString(),
                    x.decision?.name ?: "",
                    x.reasons.joinToString("; ")
                ).joinToString(",") { csvCell(it) }
            )
        }
        return rows.joinToString("\r\n")
    }
}
