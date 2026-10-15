package com.viajerentable.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDecisionTest {
    private val defaultCosts = DriverCosts()

    private fun offer(fare: Double, pickupKm: Double = 1.0, pickupMinutes: Double = 5.0,
                      tripKm: Double = 4.0, tripMinutes: Double = 15.0): ParsedOffer {
        return ParsedOffer(fare, pickupKm, pickupMinutes, tripKm, tripMinutes)
    }

    private fun calculate(offer: ParsedOffer, costs: DriverCosts = defaultCosts): ProfitResult {
        return Profitability.calculate(
            Offer(
                offer.fare,
                offer.pickupKm!!,
                offer.pickupMinutes!!,
                offer.tripKm!!,
                offer.tripMinutes!!
            ),
            costs
        )
    }

    @Test
    fun profitableOfferWouldBeAccepted() {
        val p = offer(5000.0)
        val result = DemoDecision.evaluate(p, calculate(p), defaultCosts)
        assertEquals(Decision.ACEPTARIA, result.decision)
        assertEquals(listOf("Cumple neto/km y neto/hora"), result.reasons)
    }

    @Test
    fun lowNetPerKmWouldBeRejectedWithReason() {
        val costs = DriverCosts(kmPerLiter = 1000000.0, fuelPricePerLiter = 0.0,
            maintenancePerKm = 0.0, minimumNetPerHour = 0.0, minimumNetPerKm = 500.0)
        val p = offer(1000.0, 1.0, 5.0, 4.0, 15.0)
        val result = DemoDecision.evaluate(p, calculate(p, costs), costs)
        assertEquals(Decision.RECHAZARIA, result.decision)
        assertTrue(result.reasons.any { it.startsWith("Neto/km") })
    }

    @Test
    fun lowNetPerHourWouldBeRejectedWithReason() {
        val costs = DriverCosts(kmPerLiter = 1000000.0, fuelPricePerLiter = 0.0,
            maintenancePerKm = 0.0, minimumNetPerHour = 10000.0, minimumNetPerKm = 0.0)
        val p = offer(1000.0, 1.0, 5.0, 4.0, 15.0)
        val result = DemoDecision.evaluate(p, calculate(p, costs), costs)
        assertEquals(Decision.RECHAZARIA, result.decision)
        assertTrue(result.reasons.any { it.startsWith("Neto/hora") })
    }

    @Test
    fun negativeNetWouldBeRejected() {
        val costs = DriverCosts(kmPerLiter = 1000000.0, fuelPricePerLiter = 0.0,
            maintenancePerKm = 100.0, minimumNetPerHour = 0.0, minimumNetPerKm = 0.0)
        val p = offer(100.0)
        val result = DemoDecision.evaluate(p, calculate(p, costs), costs)
        assertEquals(Decision.RECHAZARIA, result.decision)
        assertTrue(result.reasons.contains("Neto negativo"))
    }

    @Test
    fun exactMinimumThresholdsWouldBeAccepted() {
        val costs = DriverCosts(kmPerLiter = 1000000.0, fuelPricePerLiter = 0.0,
            maintenancePerKm = 0.0, minimumNetPerHour = 2500.0, minimumNetPerKm = 500.0)
        val p = offer(1000.0, 1.0, 12.0, 1.0, 12.0)
        val result = DemoDecision.evaluate(p, calculate(p, costs), costs)
        assertEquals(Decision.ACEPTARIA, result.decision)
    }

    @Test
    fun compactOfferHasNoData() {
        val p = ParsedOffer(3200.0)
        val result = DemoDecision.evaluate(p, null, defaultCosts)
        assertEquals(Decision.SIN_DATOS, result.decision)
        assertEquals(listOf("Sin datos de recorrido"), result.reasons)
    }

    @Test
    fun pickupAboveMaximumRejectsEvenWhenProfitabilityPasses() {
        val costs = DriverCosts(
            kmPerLiter = 1_000_000.0,
            fuelPricePerLiter = 0.0,
            maintenancePerKm = 0.0,
            minimumNetPerHour = 0.0,
            minimumNetPerKm = 0.0,
            maxPickupKm = 2.0
        )
        val p = offer(5000.0, pickupKm = 2.5)
        val result = DemoDecision.evaluate(p, calculate(p, costs), costs)

        assertEquals(Decision.RECHAZARIA, result.decision)
        assertTrue(result.reasons.contains("Recogida 2.50 km > máximo 2.00 km"))
    }

    @Test
    fun pickupAtMaximumDoesNotTriggerPickupRejection() {
        val costs = DriverCosts(
            kmPerLiter = 1_000_000.0,
            fuelPricePerLiter = 0.0,
            maintenancePerKm = 0.0,
            minimumNetPerHour = 0.0,
            minimumNetPerKm = 0.0,
            maxPickupKm = 2.0
        )
        val p = offer(5000.0, pickupKm = 2.0)
        val result = DemoDecision.evaluate(p, calculate(p, costs), costs)

        assertEquals(Decision.ACEPTARIA, result.decision)
    }

    @Test
    fun compactOfferRemainsWithoutDataEvenWhenPickupLimitExists() {
        val p = ParsedOffer(3200.0)
        val result = DemoDecision.evaluate(p, null, DriverCosts(maxPickupKm = 2.0))

        assertEquals(Decision.SIN_DATOS, result.decision)
    }

    @Test
    fun multipleFailedCriteriaAreAllReported() {
        val costs = DriverCosts(kmPerLiter = 1000000.0, fuelPricePerLiter = 0.0,
            maintenancePerKm = 100.0, minimumNetPerHour = 50000.0, minimumNetPerKm = 1000.0)
        val p = offer(100.0)
        val result = DemoDecision.evaluate(p, calculate(p, costs), costs)
        assertEquals(Decision.RECHAZARIA, result.decision)
        assertTrue(result.reasons.contains("Neto negativo"))
        assertTrue(result.reasons.any { it.startsWith("Neto/km") })
        assertTrue(result.reasons.any { it.startsWith("Neto/hora") })
    }
}
