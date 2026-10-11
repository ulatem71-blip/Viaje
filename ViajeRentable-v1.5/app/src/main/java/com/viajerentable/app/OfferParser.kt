package com.viajerentable.app

data class ParsedOffer(
    val fare: Double,
    val pickupKm: Double,
    val pickupMinutes: Double,
    val tripKm: Double,
    val tripMinutes: Double,
    val pickupPlace: String?,
    val destinationPlace: String?
)

object OfferParser {
    private val fareRegex = Regex("""(?:CRC\s*|₡\s*|\$\s*)([0-9][0-9,.]*)""", RegexOption.IGNORE_CASE)
    private val legRegex = Regex("""([0-9]{1,3})\s*(?:min(?:uto)?s?|mins?)\b[^|]{0,120}?([0-9]+(?:[.,][0-9]+)?)\s*km""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): ParsedOffer? {
        val segments = rawText.split("|").map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
        val text = segments.joinToString(" | ")
        val fareMatch = fareRegex.find(text) ?: return null
        val fare = parseNumber(fareMatch.groupValues[1]) ?: return null
        val legs = legRegex.findAll(text).toList()
        if (legs.size < 2) return null
        val pickupMatch = legs[0]
        val tripMatch = legs[1]
        val pickupMinutes = pickupMatch.groupValues[1].toDoubleOrNull() ?: return null
        val pickupKm = parseNumber(pickupMatch.groupValues[2]) ?: return null
        val tripMinutes = tripMatch.groupValues[1].toDoubleOrNull() ?: return null
        val tripKm = parseNumber(tripMatch.groupValues[2]) ?: return null
        if (fare <= 0 || pickupMinutes < 0 || pickupKm < 0 || tripMinutes <= 0 || tripKm <= 0) return null

        // Place labels are usually the text segments between pickup leg and trip leg,
        // then between trip leg and action buttons. Only retain short visible labels.
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
            if (s.contains("aceptar", true) || s.contains("rechazar", true)) continue
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
