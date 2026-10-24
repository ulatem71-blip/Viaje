package com.viajerentable.app

/** Limits repeated trial log entries by fare, button kind and simulated decision. */
class TrialLogDeduplicator(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class TrialKey(
        val fare: String,
        val buttonType: String,
        val decision: String
    )

    private data class RecentTrial(
        val key: TrialKey,
        val recordedAtMs: Long
    )

    private val recentTrials = mutableListOf<RecentTrial>()

    @Synchronized
    fun shouldRecord(
        fare: String,
        buttonType: String,
        decision: String,
        nowMs: Long = clock()
    ): Boolean {
        recentTrials.removeAll { nowMs - it.recordedAtMs > windowMs }
        val key = TrialKey(fare, buttonType, decision)
        if (recentTrials.any { it.key == key }) {
            return false
        }
        recentTrials.add(RecentTrial(key, nowMs))
        return true
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 15000L
    }
}
