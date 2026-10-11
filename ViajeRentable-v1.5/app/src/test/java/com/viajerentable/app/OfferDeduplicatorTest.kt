package com.viajerentable.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferDeduplicatorTest {
    private fun compact(fare: Double): ParsedOffer {
        return ParsedOffer(fare)
    }

    private fun detailed(
        fare: Double,
        pickupKm: Double = 2.0,
        pickupMinutes: Double = 6.0,
        tripKm: Double = 0.9,
        tripMinutes: Double = 3.0
    ): ParsedOffer {
        return ParsedOffer(fare, pickupKm, pickupMinutes, tripKm, tripMinutes)
    }

    @Test
    fun sameDetailedOfferTwiceWithinFiveSecondsRecordsOnlyFirst() {
        val deduplicator = OfferDeduplicator()
        val offer = detailed(834.75)

        assertTrue(deduplicator.shouldRecord(offer, 1000L))
        assertFalse(deduplicator.shouldRecord(offer, 6000L))
    }

    @Test
    fun compactDetailedAlternationRecordsOnlyCompactAndDetailed() {
        val deduplicator = OfferDeduplicator()
        val compactOffer = compact(834.75)
        val detailedOffer = detailed(834.75)

        assertTrue(deduplicator.shouldRecord(compactOffer, 1000L))
        assertTrue(deduplicator.shouldRecord(detailedOffer, 2000L))
        assertFalse(deduplicator.shouldRecord(compactOffer, 3000L))
        assertFalse(deduplicator.shouldRecord(detailedOffer, 4000L))
    }

    @Test
    fun sameFareWithDifferentRouteRecordsBothDetailedOffers() {
        val deduplicator = OfferDeduplicator()
        val firstRoute = detailed(834.75, pickupKm = 2.0)
        val secondRoute = detailed(834.75, pickupKm = 1.1)

        assertTrue(deduplicator.shouldRecord(firstRoute, 1000L))
        assertTrue(deduplicator.shouldRecord(secondRoute, 2000L))
    }

    @Test
    fun sameOfferIsRecordableAgainAfterWindowExpires() {
        val deduplicator = OfferDeduplicator()
        val offer = detailed(834.75)

        assertTrue(deduplicator.shouldRecord(offer, 1000L))
        assertTrue(deduplicator.shouldRecord(offer, 121001L))
    }

    @Test
    fun compactOfferIsSuppressedWhenSameFareHasRecentDetailedOffer() {
        val deduplicator = OfferDeduplicator()
        val offer = detailed(834.75)

        assertTrue(deduplicator.shouldRecord(offer, 1000L))
        assertFalse(deduplicator.shouldRecord(compact(834.75), 2000L))
    }

    @Test
    fun injectedClockIsUsedWhenTimestampIsOmitted() {
        var nowMs = 1000L
        val deduplicator = OfferDeduplicator(clock = { nowMs })
        val offer = detailed(834.75)

        assertTrue(deduplicator.shouldRecord(offer))
        assertFalse(deduplicator.shouldRecord(offer))
        nowMs += OfferDeduplicator.DEFAULT_WINDOW_MS + 1L
        assertTrue(deduplicator.shouldRecord(offer))
    }
}
