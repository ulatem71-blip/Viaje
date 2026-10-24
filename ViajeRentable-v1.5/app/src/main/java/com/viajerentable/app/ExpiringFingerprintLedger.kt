package com.viajerentable.app

/** In-memory policy for timestamped acceptance fingerprints, independently testable. */
internal class ExpiringFingerprintLedger(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = MAX_ENTRIES
) {
    private val entries = linkedMapOf<String, Long>()

    fun contains(fingerprint: String, nowMs: Long): Boolean {
        removeExpired(nowMs)
        return entries.containsKey(fingerprint)
    }

    fun add(fingerprint: String, nowMs: Long) {
        removeExpired(nowMs)
        entries.remove(fingerprint)
        entries[fingerprint] = nowMs
        while (entries.size > maxEntries) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }

    fun storedEntries(): Set<String> {
        return entries.map { (fingerprint, timestamp) ->
            "$fingerprint|$timestamp"
        }.toSet()
    }

    private fun removeExpired(nowMs: Long) {
        entries.entries.removeAll { nowMs - it.value >= ttlMs || nowMs < it.value }
    }

    companion object {
        const val DEFAULT_TTL_MS = 10 * 60 * 1000L
        const val MAX_ENTRIES = 50

        fun fromStored(
            stored: Set<String>,
            nowMs: Long,
            ttlMs: Long = DEFAULT_TTL_MS,
            maxEntries: Int = MAX_ENTRIES
        ): ExpiringFingerprintLedger {
            val ledger = ExpiringFingerprintLedger(ttlMs, maxEntries)
            stored.forEach { value ->
                val separator = value.lastIndexOf('|')
                if (separator <= 0 || separator == value.lastIndex) {
                    return@forEach
                }
                val fingerprint = value.substring(0, separator)
                val timestamp = value.substring(separator + 1).toLongOrNull() ?: return@forEach
                if (nowMs >= timestamp && nowMs - timestamp < ttlMs) {
                    ledger.entries[fingerprint] = timestamp
                }
            }
            while (ledger.entries.size > maxEntries) {
                val oldest = ledger.entries.keys.firstOrNull() ?: break
                ledger.entries.remove(oldest)
            }
            return ledger
        }
    }
}
