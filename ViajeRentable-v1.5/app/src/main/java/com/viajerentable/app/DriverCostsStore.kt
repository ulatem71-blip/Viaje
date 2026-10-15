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
            minimumNetPerKm = value("minimumNetPerKm", 250.0)
        )
    }

    fun maxPickupKm(context: Context): Double {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = preferences.getString("maxPickupKm", "2.0")?.toDoubleOrNull() ?: 2.0
        return value.takeIf { it >= 0.0 && it.isFinite() } ?: 2.0
    }

    fun saveMaxPickupKm(context: Context, value: Double) {
        if (value < 0.0 || !value.isFinite()) {
            return
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("maxPickupKm", value.toString())
            .apply()
    }

    fun save(context: Context, costs: DriverCosts) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("kmPerLiter", costs.kmPerLiter.toString())
            .putString("fuelPricePerLiter", costs.fuelPricePerLiter.toString())
            .putString("maintenancePerKm", costs.maintenancePerKm.toString())
            .putString("minimumNetPerHour", costs.minimumNetPerHour.toString())
            .putString("minimumNetPerKm", costs.minimumNetPerKm.toString())
            .apply()
    }
}
