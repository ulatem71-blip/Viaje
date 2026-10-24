package com.viajerentable.app

import android.content.Context

/**
 * Persists the master processing switch independently from Android's accessibility-service setting.
 * The accessibility service can stay connected while offer processing is paused.
 */
object SystemPowerStore {
    private const val PREFS = "system_power"
    private const val KEY_IS_ON = "is_on"
    private const val KEY_LAST_CHANGED_AT = "last_changed_at"

    fun isOn(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_ON, SystemPowerPolicy.defaultIsOn)
    }

    fun setOn(context: Context, isOn: Boolean) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = preferences.getBoolean(KEY_IS_ON, SystemPowerPolicy.defaultIsOn)
        if (previous != isOn) {
            preferences.edit()
                .putBoolean(KEY_IS_ON, isOn)
                .putLong(KEY_LAST_CHANGED_AT, System.currentTimeMillis())
                .apply()
        }
        if (!isOn) {
            AutoAcceptanceStore.setEnabled(context, false)
            DiscardXStore.disableAll(context)
        }
    }

    fun lastChangedAt(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHANGED_AT, 0L)
    }
}

/** Pure gate shared by the service and unit tests. */
internal object SystemPowerPolicy {
    const val defaultIsOn = true

    fun shouldProcess(isOn: Boolean): Boolean {
        return isOn
    }
}
