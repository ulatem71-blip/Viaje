package com.viajerentable.app

data class ParsedOffer(
    val fare: Double,
    val pickupKm: Double? = null,
    val pickupMinutes: Double? = null,
    val tripKm: Double? = null,
    val tripMinutes: Double? = null,
    val pickupPlace: String? = null,
    val destinationPlace: String? = null
) {
    val hasRouteDetails: Boolean
        get() = pickupKm != null && pickupMinutes != null && tripKm != null && tripMinutes != null
}

object OfferParser {
    private val fareRegex = Regex("""(?:CRC\s*|₡\s*|\$\s*)([0-9][0-9,.]*)""", RegexOption.IGNORE_CASE)
    private val legRegex = Regex("""([0-9]{1,3})\s*(?:min(?:uto)?s?|mins?)\b[^|]{0,120}?([0-9]+(?:[.,][0-9]+)?)\s*km""", RegexOption.IGNORE_CASE)

    fun containsFare(rawText: String): Boolean = fareRegex.containsMatchIn(rawText)

    fun containsRouteLeg(rawText: String): Boolean = legRegex.findAll(rawText).count() >= 2

    fun parse(rawText: String): ParsedOffer? {
        val segments = rawText.split("|").map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
        val fareMatch = fareRegex.find(segments.joinToString(" | ")) ?: return null
        val fare = parseNumber(fareMatch.groupValues[1]) ?: return null
        if (fare <= 0) return null

        val legs = legRegex.findAll(segments.joinToString(" | ")).toList()
        // Compact Uber cards show a fare but no route yet. Keep them as fare-only
        // records rather than discarding them or borrowing route data from another card.
        if (legs.size < 2) return ParsedOffer(fare = fare)

        val pickupMatch = legs[0]
        val tripMatch = legs[1]
        val pickupMinutes = pickupMatch.groupValues[1].toDoubleOrNull() ?: return ParsedOffer(fare)
        val pickupKm = parseNumber(pickupMatch.groupValues[2]) ?: return ParsedOffer(fare)
        val tripMinutes = tripMatch.groupValues[1].toDoubleOrNull() ?: return ParsedOffer(fare)
        val tripKm = parseNumber(tripMatch.groupValues[2]) ?: return ParsedOffer(fare)
        if (pickupMinutes < 0 || pickupKm < 0 || tripMinutes <= 0 || tripKm <= 0) return ParsedOffer(fare)

        val pickupIndex = segments.indexOfFirst { legRegex.containsMatchIn(it) }
        val tripIndex = segments.indexOfLast { legRegex.containsMatchIn(it) }
        val pickupPlace = findPlace(segments, pickupIndex + 1, tripIndex)
        val destinationPlace = findPlace(segments, tripIndex + 1, segments.size)
        return ParsedOffer(fare, pickupKm, pickupMinutes, tripKm, tripMinutes, pickupPlace, destinationPlace)
    }

    private fun findPlace(parts: List<String>, start: Int, end: Int): String? {
        for (i in start until end.coerceAtMost(parts.size)) {
            val s = parts[i].trim()
            if (s.length !in 3..100) continue
            if (fareRegex.containsMatchIn(s) || legRegex.containsMatchIn(s)) continue
            if (s.contains("aceptar", true) || s.contains("rechazar", true) || s.contains("interesa", true)) continue
            if (s.equals("UberX", true) || s.equals("Exclusivo", true) || s.equals("Viaje", true)) continue
            if (s.matches(Regex("[0-9.,() ]+"))) continue
            return s
        }
        return null
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
