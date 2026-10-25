package com.viajerentable.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoneRiskStoreTest {
    @Test
    fun normalizationIgnoresAccentsCaseAndPunctuation() {
        assertEquals(
            "san jose centro",
            ZoneRiskStore.normalize("  SÁN   JOSÉ, CENTRO! ")
        )
    }

    @Test
    fun destinationMatchesByContainment() {
        val offer = ParsedOffer(
            fare = 2000.0,
            destinationText = "SAN RAFAEL - LA UNION"
        )
        val config = ZoneRiskConfig(names = listOf("SAN RAFAEL"))

        assertEquals("SAN RAFAEL", ZoneRiskStore.match(offer, config))
    }

    @Test
    fun destinationWithoutMatchIsNotRisky() {
        val offer = ParsedOffer(
            fare = 2000.0,
            destinationText = "SAN PEDRO - MONTES DE OCA"
        )
        val config = ZoneRiskConfig(names = listOf("SAN RAFAEL"))

        assertNull(ZoneRiskStore.match(offer, config))
    }

    @Test
    fun pickupZoneIsIgnoredWhenPickupSwitchIsOff() {
        val offer = ParsedOffer(fare = 2000.0, pickupText = "BARRIO CUBA")
        val config = ZoneRiskConfig(
            names = listOf("BARRIO CUBA"),
            applyToDestination = false,
            applyToPickup = false
        )

        assertNull(ZoneRiskStore.match(offer, config))
    }

    @Test
    fun pickupZoneIsUsedWhenPickupSwitchIsOn() {
        val offer = ParsedOffer(fare = 2000.0, pickupText = "BARRIO CUBA, SAN JOSE")
        val config = ZoneRiskConfig(
            names = listOf("BARRIO CUBA"),
            applyToDestination = false,
            applyToPickup = true
        )

        assertEquals("BARRIO CUBA", ZoneRiskStore.match(offer, config))
    }

    @Test
    fun compactOfferWithoutDestinationTextDoesNotMatch() {
        val offer = ParsedOffer(fare = 3200.0)
        val config = ZoneRiskConfig(names = listOf("SAN RAFAEL"))

        assertNull(ZoneRiskStore.match(offer, config))
    }
}
