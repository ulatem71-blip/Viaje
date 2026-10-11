package com.viajerentable.app

import java.util.Locale

data class ParsedOffer(
    val fare: Double, val pickupKm: Double, val pickupMinutes: Double,
    val tripKm: Double, val tripMinutes: Double
)

object OfferParser {
    fun parse(rawText: String): ParsedOffer? {
        val text = rawText.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return null
        val fareMatch = Regex("""(?:CRC\\s*|₡\\s*|\\$\\s*)([0-9][0-9,.]*)""",
            RegexOption.IGNORE_CASE).find(text) ?: return null
        val fare = parseNumber(fareMatch.groupValues[1]) ?: return null
        val legRegex = Regex("""([0-9]{1,3})\\s*(?:min(?:uto)?s?|mins?)\\b[^|]{0,120}?([0-9]+(?:[.,][0-9]+)?)\\s*km""",
            RegexOption.IGNORE_CASE)
        val legs = legRegex.findAll(text).mapNotNull {
            val minutes = it.groupValues[1].toDoubleOrNull()
            val km = parseNumber(it.groupValues[2])
            if (minutes != null && km != null) Pair(minutes, km) else null
        }.toList()
        if (legs.size < 2) return null
        val pickup = legs[0]
        val trip = legs[1]
        if (fare <= 0 || pickup.first < 0 || pickup.second < 0 || trip.first <= 0 || trip.second <= 0) return null
        return ParsedOffer(fare, pickup.second, pickup.first, trip.second, trip.first)
    }

    private fun parseNumber(input: String): Double? {
        val value = input.trim()
        val normalized = if (value.contains(',') && value.contains('.')) {
            if (value.lastIndexOf('.') > value.lastIndexOf(',')) value.replace(",", "")
            else value.replace(".", "").replace(",", ".")
        } else if (value.contains(',')) {
            val tail = value.substringAfterLast(',')
            if (tail.length in 1..2) value.replace(",", ".") else value.replace(",", "")
        } else value
        return normalized.toDoubleOrNull()
    }
}
