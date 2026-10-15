package com.viajerentable.app

/**
 * Suppresses repeated snapshots of the same offer while allowing a detailed route to enrich
 * a previously recorded compact offer with the same fare.
 */
class OfferDeduplicator(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class DetailedFingerprint(
        val fare: Double,
        val pickupKm: Double,
        val pickupMinutes: Double,
        val tripKm: Double,
        val tripMinutes: Double
    )

    private data class RecentOffer(
        val fare: Double,
        val detailedFingerprint: DetailedFingerprint?,
        val recordedAtMs: Long
    )

    private val recentOffers = mutableListOf<RecentOffer>()

    @Synchronized
    fun shouldRecord(offer: ParsedOffer, nowMs: Long = clock()): Boolean {
        recentOffers.removeAll { nowMs - it.recordedAtMs > windowMs }

        if (offer.hasRouteDetails) {
            val fingerprint = DetailedFingerprint(
                offer.fare,
                offer.pickupKm!!,
                offer.pickupMinutes!!,
                offer.tripKm!!,
                offer.tripMinutes!!
            )
            val alreadyRecorded = recentOffers.any {
                it.detailedFingerprint == fingerprint
            }
            if (alreadyRecorded) {
                return false
            }

            recentOffers.add(RecentOffer(offer.fare, fingerprint, nowMs))
            return true
        }

        val sameFareAlreadyRecorded = recentOffers.any { it.fare == offer.fare }
        if (sameFareAlreadyRecorded) {
            return false
        }

        recentOffers.add(RecentOffer(offer.fare, null, nowMs))
        return true
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 120000L

        fun fingerprint(offer: ParsedOffer): String {
            if (!offer.hasRouteDetails) {
                return "compact:${String.format(java.util.Locale.US, "%.3f", offer.fare)}"
            }
            return listOf(
                offer.fare,
                offer.pickupKm!!,
                offer.pickupMinutes!!,
                offer.tripKm!!,
                offer.tripMinutes!!
            ).joinToString("|") { String.format(java.util.Locale.US, "%.3f", it) }
        }
    }
}
