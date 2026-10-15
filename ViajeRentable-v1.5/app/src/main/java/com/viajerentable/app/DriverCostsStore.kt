package com.viajerentable.app

import android.content.Context

object DriverCostsStore {
    private const val PREFS = "driver_costs"

    fun read(context: Context): DriverCosts {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun value(key: String, default: Double, minimum: Double = 0.0): Double {
            val parsed = preferences.getString(key, default.toString())?.toDoubleOrNull() ?: default
            return parsed.takeIf { it >= minimum } ?: default
        }

        return DriverCosts(
            kmPerLiter = value("kmPerLiter", 18.0, 0.000001),
            fuelPricePerLiter = value("fuelPricePerLiter", 720.0),
            maintenancePerKm = value("maintenancePerKm", 25.0),
            minimumNetPerHour = value("minimumNetPerHour", 2500.0),
            minimumNetPerKm = value("minimumNetPerKm", 250.0),
            acEnabled = preferences.getBoolean("acEnabled", false),
            acExtraPercent = value("acExtraPercent", 10.0).coerceIn(0.0, 50.0),
            maxPickupKm = value("maxPickupKm", 2.0).coerceIn(0.1, 20.0)
        )
    }

    fun save(context: Context, costs: DriverCosts) {
        require(costs.kmPerLiter > 0.0)
        require(costs.fuelPricePerLiter >= 0.0)
        require(costs.maintenancePerKm >= 0.0)
        require(costs.minimumNetPerHour >= 0.0)
        require(costs.minimumNetPerKm >= 0.0)
        require(costs.acExtraPercent in 0.0..50.0)
        require(costs.maxPickupKm in 0.1..20.0)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("kmPerLiter", costs.kmPerLiter.toString())
            .putString("fuelPricePerLiter", costs.fuelPricePerLiter.toString())
            .putString("maintenancePerKm", costs.maintenancePerKm.toString())
            .putString("minimumNetPerHour", costs.minimumNetPerHour.toString())
            .putString("minimumNetPerKm", costs.minimumNetPerKm.toString())
            .putBoolean("acEnabled", costs.acEnabled)
            .putString("acExtraPercent", costs.acExtraPercent.toString())
            .putString("maxPickupKm", costs.maxPickupKm.toString())
            .apply()
    }
}
