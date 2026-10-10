package com.viajerentable.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfitabilityTest {
    @Test fun calculatesOfferAndRejectsLowHourlyReturn() {
        val result = Profitability.calculate(
            Offer(3200.0, 1.8, 5.0, 6.3, 60.0),
            DriverCosts(kmPerLiter = 18.0, fuelPricePerLiter = 720.0, maintenancePerKm = 25.0)
        )
        assertEquals(8.1, result.totalKm, 0.001)
        assertEquals(324.0, result.fuelCost, 0.001)
        assertEquals(202.5, result.maintenanceCost, 0.001)
        assertEquals(2673.5, result.net, 0.001)
        assertFalse(result.recommended)
    }
}
