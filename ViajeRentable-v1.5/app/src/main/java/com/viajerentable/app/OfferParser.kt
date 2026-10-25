package com.viajerentable.app

data class ParsedOffer(
    val fare: Double,
    val pickupKm: Double? = null,
    val pickupMinutes: Double? = null,
    val tripKm: Double? = null,
    val tripMinutes: Double? = null,
    val pickupPlace: String? = null,
    val destinationPlace: String? = null,
    val destinationText: String = "",
    val pickupText: String = ""
) {
    val hasRouteDetails: Boolean
        get() = pickupKm != null &&
            pickupMinutes != null &&
            tripKm != null &&
            tripMinutes != null
}

object OfferParser {
    private val fareRegex = Regex(
        """(?:CRC\s*|₡\s*|\$\s*)([0-9]+(?:[,.][0-9]+)*)""",
        RegexOption.IGNORE_CASE
    )
    private val legRegex = Regex(
        """([^|()]{0,80}?)\(([0-9]+(?:[.,][0-9]+)?)\s*(km|m)\)""",
        RegexOption.IGNORE_CASE
    )
    private val hourRegex = Regex(
        """([0-9]{1,3})\s*(?:h|horas?)""",
        RegexOption.IGNORE_CASE
    )
    private val minuteRegex = Regex(
        """([0-9]{1,3})\s*(?:min(?:utos?)?|mins?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val whitespaceRegex = Regex("""\s+""")
    private val buttonRegex = Regex("""(?:Aceptar|Me\s+interesa)""", RegexOption.IGNORE_CASE)

    fun containsFare(rawText: String): Boolean {
        return fareRegex.containsMatchIn(rawText)
    }

    fun containsRouteLeg(rawText: String): Boolean {
        return legRegex.findAll(rawText).count() >= 2
    }

    fun parse(rawText: String): ParsedOffer? {
        return parseAll(rawText).firstOrNull()
    }

    fun parseAll(rawText: String): List<ParsedOffer> {
        val normalized = rawText.replace(whitespaceRegex, " ").trim()
        if (normalized.isEmpty()) {
            return emptyList()
        }

        val segments = normalized.split(Regex("""\s*\|\s*"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val offers = mutableListOf<ParsedOffer>()
        val currentCard = mutableListOf<String>()

        for (segment in segments) {
            currentCard.add(segment)
            if (buttonRegex.matches(segment)) {
                parseCard(currentCard)?.let(offers::add)
                currentCard.clear()
            }
        }
        return offers
    }

    private fun parseCard(segments: List<String>): ParsedOffer? {
        val text = segments.joinToString(" | ")
        val fareMatch = fareRegex.find(text) ?: return null
        val fare = parseNumber(fareMatch.groupValues[1]) ?: return null
        if (fare < 300.0 || fare > 200000.0) {
            return null
        }

        val legs = legRegex.findAll(text).toList()
        if (legs.size < 2) {
            return ParsedOffer(fare)
        }

        val pickupLeg = legs[0]
        val tripLeg = legs[1]
        val pickupMinutes = parseDuration(pickupLeg.groupValues[1]) ?: return ParsedOffer(fare)
        val pickupDistance = parseNumber(pickupLeg.groupValues[2]) ?: return ParsedOffer(fare)
        val pickupKm = if (pickupLeg.groupValues[3].equals("m", true)) {
            pickupDistance / 1000.0
        } else {
            pickupDistance
        }

        val tripMinutes = parseDuration(tripLeg.groupValues[1]) ?: return ParsedOffer(fare)
        val tripDistance = parseNumber(tripLeg.groupValues[2]) ?: return ParsedOffer(fare)
        val tripKm = if (tripLeg.groupValues[3].equals("m", true)) {
            tripDistance / 1000.0
        } else {
            tripDistance
        }

        if (pickupMinutes < 0 || pickupKm < 0 || tripMinutes <= 0 || tripKm <= 0) {
            return ParsedOffer(fare)
        }

        val pickupIndex = segments.indexOfFirst { legRegex.containsMatchIn(it) }
        val tripIndex = segments.indexOfLast { legRegex.containsMatchIn(it) }
        val pickupPlace = findPlace(segments, pickupIndex + 1, tripIndex)
        val destinationPlace = findPlace(segments, tripIndex + 1, segments.size - 1)

        return ParsedOffer(
            fare = fare,
            pickupKm = pickupKm,
            pickupMinutes = pickupMinutes,
            tripKm = tripKm,
            tripMinutes = tripMinutes,
            pickupPlace = pickupPlace,
            destinationPlace = destinationPlace,
            destinationText = destinationPlace.orEmpty(),
            pickupText = pickupPlace.orEmpty()
        )
    }

    private fun parseDuration(text: String): Double? {
        val hours = hourRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val minutes = minuteRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return if (hours == 0.0 && minutes == 0.0) {
            null
        } else {
            hours * 60.0 + minutes
        }
    }

    private fun findPlace(segments: List<String>, start: Int, end: Int): String? {
        for (index in start until end.coerceAtMost(segments.size)) {
            val segment = segments[index].trim()
            if (segment.length !in 3..100) {
                continue
            }
            if (fareRegex.containsMatchIn(segment) || legRegex.containsMatchIn(segment)) {
                continue
            }
            if (segment.contains("aceptar", true) ||
                segment.contains("rechazar", true) ||
                segment.contains("interesa", true)
            ) {
                continue
            }
            if (segment.equals("UberX", true) ||
                segment.equals("Exclusivo", true) ||
                segment.equals("Viaje", true) ||
                segment.matches(Regex("[0-9.,() ]+"))
            ) {
                continue
            }
            return segment
        }
        return null
    }

    internal fun parseNumber(input: String): Double? {
        val value = input.trim().trimEnd('.', ',')
        val normalized = if (value.contains(',') && value.contains('.')) {
            if (value.lastIndexOf('.') > value.lastIndexOf(',')) {
                value.replace(",", "")
            } else {
                value.replace(".", "").replace(",", ".")
            }
        } else if (value.contains(',')) {
            if (value.substringAfterLast(',').length in 1..2) {
                value.replace(",", ".")
            } else {
                value.replace(",", "")
            }
        } else if (value.contains('.')) {
            if (value.substringAfterLast('.').length == 3 &&
                value.substringBeforeLast('.').isNotEmpty()
            ) {
                value.replace(".", "")
            } else {
                value
            }
        } else {
            value
        }
        return normalized.toDoubleOrNull()
    }
}
