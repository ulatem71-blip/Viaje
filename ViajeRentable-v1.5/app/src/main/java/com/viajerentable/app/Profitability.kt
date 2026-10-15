package com.viajerentable.app

data class Offer(
    val fare: Double,
    val pickupKm: Double,
    val pickupMinutes: Double,
    val tripKm: Double,
    val tripMinutes: Double
)

data class DriverCosts(
    val kmPerLiter: Double = 18.0,
    val fuelPricePerLiter: Double = 720.0,
    val maintenancePerKm: Double = 25.0,
    val minimumNetPerHour: Double = 2500.0,
    val minimumNetPerKm: Double = 250.0,
    val acEnabled: Boolean = false,
    val acExtraPercent: Double = 10.0,
    val maxPickupKm: Double = 2.0
)

data class ProfitResult(
    val totalKm: Double,
    val totalMinutes: Double,
    val fuelCost: Double,
    val maintenanceCost: Double,
    val net: Double,
    val netPerKm: Double,
    val netPerHour: Double,
    val recommended: Boolean,
    val fuelCostPerKm: Double = 0.0
)

object Profitability {
    fun calculate(offer: Offer, costs: DriverCosts): ProfitResult {
        require(offer.fare >= 0 && offer.pickupKm >= 0 && offer.tripKm >= 0)
        require(costs.kmPerLiter > 0 && costs.fuelPricePerLiter >= 0 && costs.maintenancePerKm >= 0)
        val km = offer.pickupKm + offer.tripKm
        val minutes = offer.pickupMinutes + offer.tripMinutes
        val acMultiplier = if (costs.acEnabled) 1.0 + costs.acExtraPercent / 100.0 else 1.0
        val fuelCostPerKm = costs.fuelPricePerLiter / costs.kmPerLiter * acMultiplier
        val fuel = km * fuelCostPerKm
        val maintenance = km * costs.maintenancePerKm
        val net = offer.fare - fuel - maintenance
        val perKm = if (km > 0) net / km else 0.0
        val perHour = if (minutes > 0) net / (minutes / 60.0) else 0.0
        return ProfitResult(
            totalKm = km,
            totalMinutes = minutes,
            fuelCost = fuel,
            maintenanceCost = maintenance,
            net = net,
            netPerKm = perKm,
            netPerHour = perHour,
            recommended = net >= 0 && perKm >= costs.minimumNetPerKm && perHour >= costs.minimumNetPerHour,
            fuelCostPerKm = fuelCostPerKm
        )
    }
}
