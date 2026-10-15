package com.viajerentable.app

import android.content.Context

data class ButtonTrialCounters(
    val acceptedWithButton: Int = 0,
    val acceptedWithoutButton: Int = 0,
    val ambiguous: Int = 0,
    val discrepancies: Int = 0
)

object OfferButtonTrialStore {
    private const val PREFS = "offer_button_trial"
    private const val ACCEPTED_WITH_BUTTON = "accepted_with_button"
    private const val ACCEPTED_WITHOUT_BUTTON = "accepted_without_button"
    private const val AMBIGUOUS = "ambiguous"
    private const val DISCREPANCIES = "discrepancies"

    fun read(context: Context): ButtonTrialCounters {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ButtonTrialCounters(
            acceptedWithButton = prefs.getInt(ACCEPTED_WITH_BUTTON, 0),
            acceptedWithoutButton = prefs.getInt(ACCEPTED_WITHOUT_BUTTON, 0),
            ambiguous = prefs.getInt(AMBIGUOUS, 0),
            discrepancies = prefs.getInt(DISCREPANCIES, 0)
        )
    }

    fun incrementAcceptedWithButton(context: Context) {
        increment(context, ACCEPTED_WITH_BUTTON)
    }

    fun incrementAcceptedWithoutButton(context: Context) {
        increment(context, ACCEPTED_WITHOUT_BUTTON)
    }

    fun incrementAmbiguous(context: Context) {
        increment(context, AMBIGUOUS)
    }

    fun incrementDiscrepancies(context: Context) {
        increment(context, DISCREPANCIES)
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun increment(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }
}
