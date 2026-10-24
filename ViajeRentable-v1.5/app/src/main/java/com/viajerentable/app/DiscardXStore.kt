package com.viajerentable.app

import android.content.Context

object DiscardXStore {
    private const val PREFS = "discard_x"
    private const val SUGGESTIONS_ENABLED = "suggestions_enabled"
    private const val ASSIGNED_ENABLED = "assigned_enabled"
    private const val LAST_DISCARD_AT = "last_discard_at"
    private const val CONSECUTIVE_WITHOUT_ACCEPTANCE = "consecutive_without_acceptance"
    private const val CONSECUTIVE_LIMIT = "consecutive_limit"
    private const val LAST_SEEN_VERSION = "last_seen_version"
    private const val ATTEMPTED_FINGERPRINTS = "attempted_fingerprints"
    private const val TRIAL_FINGERPRINTS = "trial_fingerprints"
    const val MIN_DISCARD_INTERVAL_MS = 2000L
    const val DEFAULT_CONSECUTIVE_LIMIT = 15

    fun suggestionsEnabled(context: Context): Boolean {
        return preferences(context).getBoolean(SUGGESTIONS_ENABLED, false)
    }

    fun assignedEnabled(context: Context): Boolean {
        return preferences(context).getBoolean(ASSIGNED_ENABLED, false)
    }

    fun setSuggestionsEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(SUGGESTIONS_ENABLED, enabled).apply()
    }

    fun setAssignedEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(ASSIGNED_ENABLED, enabled).apply()
    }

    fun disableAll(context: Context) {
        preferences(context).edit().putBoolean(SUGGESTIONS_ENABLED, false)
            .putBoolean(ASSIGNED_ENABLED, false).apply()
    }

    fun enforceOffForVersionChange(context: Context, currentVersion: String) {
        val prefs = preferences(context)
        val previousVersion = prefs.getString(LAST_SEEN_VERSION, null)
        if (previousVersion != currentVersion) {
            prefs.edit().putBoolean(SUGGESTIONS_ENABLED, false)
                .putBoolean(ASSIGNED_ENABLED, false)
                .putString(LAST_SEEN_VERSION, currentVersion).apply()
        }
    }

    fun mayDiscardNow(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = preferences(context).getLong(LAST_DISCARD_AT, 0L)
        return last == 0L || nowMs - last >= MIN_DISCARD_INTERVAL_MS
    }

    fun markDiscardAttempt(context: Context, nowMs: Long = System.currentTimeMillis()) {
        preferences(context).edit().putLong(LAST_DISCARD_AT, nowMs).apply()
    }

    fun wasAttempted(context: Context, fingerprint: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val prefs = preferences(context)
        val ledger = ExpiringFingerprintLedger.fromStored(
            prefs.getStringSet(ATTEMPTED_FINGERPRINTS, emptySet())?.toSet() ?: emptySet(),
            nowMs
        )
        prefs.edit().putStringSet(ATTEMPTED_FINGERPRINTS, ledger.storedEntries()).apply()
        return ledger.contains(fingerprint, nowMs)
    }

    fun markAttempted(context: Context, fingerprint: String, nowMs: Long = System.currentTimeMillis()) {
        val prefs = preferences(context)
        val ledger = ExpiringFingerprintLedger.fromStored(
            prefs.getStringSet(ATTEMPTED_FINGERPRINTS, emptySet())?.toSet() ?: emptySet(),
            nowMs
        )
        ledger.add(fingerprint, nowMs)
        prefs.edit().putStringSet(ATTEMPTED_FINGERPRINTS, ledger.storedEntries()).apply()
    }

    fun shouldLogTrial(context: Context, fingerprint: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val prefs = preferences(context)
        val ledger = ExpiringFingerprintLedger.fromStored(
            prefs.getStringSet(TRIAL_FINGERPRINTS, emptySet())?.toSet() ?: emptySet(),
            nowMs
        )
        val exists = ledger.contains(fingerprint, nowMs)
        if (!exists) {
            ledger.add(fingerprint, nowMs)
        }
        prefs.edit().putStringSet(TRIAL_FINGERPRINTS, ledger.storedEntries()).apply()
        return !exists
    }

    fun consecutiveWithoutAcceptance(context: Context): Int {
        return preferences(context).getInt(CONSECUTIVE_WITHOUT_ACCEPTANCE, 0)
    }

    fun consecutiveLimit(context: Context): Int {
        return preferences(context).getInt(CONSECUTIVE_LIMIT, DEFAULT_CONSECUTIVE_LIMIT).coerceIn(1, 100)
    }

    fun setConsecutiveLimit(context: Context, limit: Int) {
        preferences(context).edit().putInt(CONSECUTIVE_LIMIT, limit.coerceIn(1, 100)).apply()
    }

    fun recordDiscard(context: Context): ConsecutiveDiscardState {
        val next = consecutiveWithoutAcceptance(context) + 1
        preferences(context).edit().putInt(CONSECUTIVE_WITHOUT_ACCEPTANCE, next).apply()
        return ConsecutiveDiscardState(next, consecutiveLimit(context))
    }

    fun recordAcceptance(context: Context) {
        preferences(context).edit().putInt(CONSECUTIVE_WITHOUT_ACCEPTANCE, 0).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
