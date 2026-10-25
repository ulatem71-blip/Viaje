package com.viajerentable.app

import android.content.Context
import java.text.Normalizer
import java.util.Locale

data class ZoneRiskConfig(
    val names: List<String> = emptyList(),
    val applyToDestination: Boolean = true,
    val applyToPickup: Boolean = false
)

object ZoneRiskStore {
    private const val PREFS = "zone_risk_store"
    private const val KEY_NAMES = "names"
    private const val KEY_DESTINATION = "apply_destination"
    private const val KEY_PICKUP = "apply_pickup"
    const val MAX_NAMES = 100

    fun read(context: Context): ZoneRiskConfig {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val names = preferences.getString(KEY_NAMES, null)
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        return ZoneRiskConfig(
            names = names.distinctBy { normalize(it) }.take(MAX_NAMES),
            applyToDestination = preferences.getBoolean(KEY_DESTINATION, true),
            applyToPickup = preferences.getBoolean(KEY_PICKUP, false)
        )
    }

    fun add(context: Context, name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) {
            return false
        }
        val current = read(context).names
        if (current.any { normalize(it) == normalize(clean) } || current.size >= MAX_NAMES) {
            return false
        }
        saveNames(context, current + clean)
        return true
    }

    fun remove(context: Context, name: String) {
        val remaining = read(context).names.filterNot {
            normalize(it) == normalize(name)
        }
        saveNames(context, remaining)
    }

    fun setApplyToDestination(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DESTINATION, enabled)
            .apply()
    }

    fun setApplyToPickup(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PICKUP, enabled)
            .apply()
    }

    private fun saveNames(context: Context, names: List<String>) {
        val distinct = names.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { normalize(it) }
            .take(MAX_NAMES)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAMES, distinct.joinToString("\n"))
            .apply()
    }

    fun normalize(value: String): String {
        val noAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    fun match(offer: ParsedOffer, config: ZoneRiskConfig): String? {
        if (config.names.isEmpty()) {
            return null
        }
        val destination = normalize(offer.destinationText)
        if (config.applyToDestination && destination.isNotEmpty()) {
            config.names.firstOrNull { name ->
                val normalizedName = normalize(name)
                normalizedName.isNotEmpty() && destination.contains(normalizedName)
            }?.let { return it }
        }
        val pickup = normalize(offer.pickupText)
        if (config.applyToPickup && pickup.isNotEmpty()) {
            config.names.firstOrNull { name ->
                val normalizedName = normalize(name)
                normalizedName.isNotEmpty() && pickup.contains(normalizedName)
            }?.let { return it }
        }
        return null
    }
}
