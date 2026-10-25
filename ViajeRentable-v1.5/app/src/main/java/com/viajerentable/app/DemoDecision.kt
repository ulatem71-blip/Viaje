package com.viajerentable.app

import android.content.Context
import java.util.Locale

enum class Decision {
    ACEPTARIA,
    RECHAZARIA,
    SIN_DATOS
}

data class DemoResult(
    val decision: Decision,
    val reasons: List<String>
)

object DemoDecision {
    fun evaluate(
        offer: ParsedOffer,
        result: ProfitResult?,
        costs: DriverCosts,
        zoneRiskConfig: ZoneRiskConfig = ZoneRiskConfig()
    ): DemoResult {
        val riskyZone = ZoneRiskStore.match(offer, zoneRiskConfig)
        if (riskyZone != null) {
            return DemoResult(
                Decision.RECHAZARIA,
                listOf("Zona de riesgo: $riskyZone")
            )
        }

        if (!offer.hasRouteDetails || result == null) {
            return DemoResult(Decision.SIN_DATOS, listOf("Sin datos de recorrido"))
        }

        val reasons = mutableListOf<String>()
        if (offer.pickupKm != null && offer.pickupKm > costs.maxPickupKm) {
            reasons.add(
                "Recogida ${format(offer.pickupKm)} km > máximo ${format(costs.maxPickupKm)} km"
            )
            return DemoResult(Decision.RECHAZARIA, reasons)
        }

        if (result.recommended) {
            return DemoResult(Decision.ACEPTARIA, listOf("Cumple neto/km y neto/hora"))
        }

        if (result.net < 0.0) {
            reasons.add("Neto negativo")
        }
        if (result.netPerKm < costs.minimumNetPerKm) {
            reasons.add(
                "Neto/km ${format(result.netPerKm)} < mínimo ${format(costs.minimumNetPerKm)}"
            )
        }
        if (result.netPerHour < costs.minimumNetPerHour) {
            reasons.add(
                "Neto/hora ${format(result.netPerHour)} < mínimo ${format(costs.minimumNetPerHour)}"
            )
        }
        return DemoResult(Decision.RECHAZARIA, reasons)
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
}

object DemoModeStore {
    private const val PREFS = "demo_mode"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
