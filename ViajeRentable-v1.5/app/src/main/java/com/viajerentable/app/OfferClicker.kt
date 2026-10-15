package com.viajerentable.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.accessibility.AccessibilityNodeInfo

data class AcceptanceGateInput(
    val enabled: Boolean,
    val target: CardTarget?,
    val decision: Decision,
    val parsedOffer: ParsedOffer?,
    val maxPickupKm: Double,
    val ambiguous: Boolean,
    val fareMatches: Boolean
)

data class AcceptanceGateResult(
    val allowed: Boolean,
    val reason: String
)

object AcceptanceDecisionGate {
    fun evaluate(input: AcceptanceGateInput): AcceptanceGateResult {
        if (!input.enabled) return AcceptanceGateResult(false, "interruptor apagado")
        val target = input.target ?: return AcceptanceGateResult(false, "botón no localizado")
        if (target.buttonType != "Aceptar") return AcceptanceGateResult(false, "tipo de botón no permitido")
        if (input.decision != Decision.ACEPTARIA) return AcceptanceGateResult(false, "decisión no ACEPTARIA")
        val parsed = input.parsedOffer
            ?: return AcceptanceGateResult(false, "oferta no parseable")
        if (!parsed.hasRouteDetails) return AcceptanceGateResult(false, "ruta incompleta")
        if (parsed.pickupKm!! > input.maxPickupKm) return AcceptanceGateResult(false, "recogida supera el máximo")
        if (input.ambiguous) return AcceptanceGateResult(false, "tarjeta ambigua")
        if (!input.fareMatches || kotlin.math.abs(target.fare - parsed.fare) >= 0.01) {
            return AcceptanceGateResult(false, "tarifa discrepante")
        }
        if (!target.isEnabled) return AcceptanceGateResult(false, "botón deshabilitado")
        if (!target.isVisibleToUser) return AcceptanceGateResult(false, "botón no visible")
        return AcceptanceGateResult(true, "condiciones cumplidas")
    }
}

enum class AutoAcceptanceShutdownTrigger {
    SERVICE_RESTART,
    UBER_LEFT_FOREGROUND,
    CLICK_FAILURE,
    TARIFF_DISCREPANCY
}

object AutoAcceptanceShutdownPolicy {
    fun shouldDisable(
        enabled: Boolean,
        trigger: AutoAcceptanceShutdownTrigger,
        safetyState: AutoAcceptanceSafetyState
    ): Boolean {
        if (!enabled) return false
        return when (trigger) {
            AutoAcceptanceShutdownTrigger.SERVICE_RESTART,
            AutoAcceptanceShutdownTrigger.UBER_LEFT_FOREGROUND -> true
            AutoAcceptanceShutdownTrigger.CLICK_FAILURE,
            AutoAcceptanceShutdownTrigger.TARIFF_DISCREPANCY -> safetyState.mustDisable()
        }
    }
}

data class AutoAcceptanceSafetyState(
    val consecutiveClickFailures: Int = 0,
    val consecutiveDiscrepancies: Int = 0
) {
    fun afterClick(success: Boolean): AutoAcceptanceSafetyState {
        return copy(consecutiveClickFailures = if (success) 0 else consecutiveClickFailures + 1)
    }

    fun afterDiscrepancy(): AutoAcceptanceSafetyState {
        return copy(consecutiveDiscrepancies = consecutiveDiscrepancies + 1)
    }

    fun mustDisable(): Boolean {
        return consecutiveClickFailures >= 2 || consecutiveDiscrepancies >= 3
    }
}

object AutoAcceptanceStore {
    private const val PREFS = "real_acceptance"
    private const val ENABLED = "enabled"
    private const val MAX_PICKUP_KM = "max_pickup_km"
    private const val LAST_CLICK_AT = "last_click_at"
    private const val ACCEPTED_FINGERPRINTS = "accepted_fingerprints"
    private const val FAILURE_COUNT = "consecutive_failures"
    private const val DISCREPANCY_COUNT = "consecutive_discrepancies"
    const val MIN_CLICK_INTERVAL_MS = 5000L

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(ENABLED, enabled).apply()
    }

    fun maxPickupKm(context: Context): Double {
        return prefs(context).getString(MAX_PICKUP_KM, "2.0")?.toDoubleOrNull()
            ?.takeIf { it >= 0.0 } ?: 2.0
    }

    fun setMaxPickupKm(context: Context, value: Double) {
        if (value >= 0.0 && value.isFinite()) {
            prefs(context).edit().putString(MAX_PICKUP_KM, value.toString()).apply()
        }
    }

    fun mayClickNow(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = prefs(context).getLong(LAST_CLICK_AT, 0L)
        return last == 0L || nowMs - last >= MIN_CLICK_INTERVAL_MS
    }

    fun markClickAttempt(context: Context, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(LAST_CLICK_AT, nowMs).apply()
    }

    fun wasAccepted(context: Context, fingerprint: String): Boolean {
        return acceptedFingerprints(context).contains(fingerprint)
    }

    fun markAccepted(context: Context, fingerprint: String) {
        val all = acceptedFingerprints(context).toMutableSet()
        all.add(fingerprint)
        prefs(context).edit().putStringSet(ACCEPTED_FINGERPRINTS, all).apply()
    }

    fun safetyState(context: Context): AutoAcceptanceSafetyState {
        val p = prefs(context)
        return AutoAcceptanceSafetyState(
            p.getInt(FAILURE_COUNT, 0),
            p.getInt(DISCREPANCY_COUNT, 0)
        )
    }

    fun recordClickResult(context: Context, success: Boolean): AutoAcceptanceSafetyState {
        val next = safetyState(context).afterClick(success)
        prefs(context).edit().putInt(FAILURE_COUNT, next.consecutiveClickFailures).apply()
        return next
    }

    fun recordDiscrepancy(context: Context): AutoAcceptanceSafetyState {
        val next = safetyState(context).afterDiscrepancy()
        prefs(context).edit().putInt(DISCREPANCY_COUNT, next.consecutiveDiscrepancies).apply()
        return next
    }

    fun resetSafetyCounters(context: Context) {
        prefs(context).edit().putInt(FAILURE_COUNT, 0).putInt(DISCREPANCY_COUNT, 0).apply()
    }

    private fun acceptedFingerprints(context: Context): Set<String> {
        return prefs(context).getStringSet(ACCEPTED_FINGERPRINTS, emptySet())?.toSet() ?: emptySet()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The only production class allowed to invoke AccessibilityNodeInfo click actions. */
data class OfferClickResult(
    val actionInvoked: Boolean,
    val succeeded: Boolean
)

object OfferClicker {
    fun click(target: CardTarget, freshWindowRoot: AccessibilityNodeInfo): OfferClickResult {
        if (target.buttonType != "Aceptar") return OfferClickResult(false, false)
        val button = findExactAcceptButton(freshWindowRoot, target.buttonBoundsInScreen)
            ?: return OfferClickResult(false, false)
        return try {
            if (!button.isEnabled || !button.isVisibleToUser) {
                OfferClickResult(false, false)
            } else {
                OfferClickResult(
                    actionInvoked = true,
                    succeeded = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                )
            }
        } finally {
            button.recycle()
        }
    }

    fun fingerprint(offer: ParsedOffer): String {
        return OfferDeduplicator.fingerprint(offer)
    }

    fun playClickSound() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 180)
        tone.release()
    }

    fun playSafetyWarning() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
        tone.release()
    }

    private fun findExactAcceptButton(
        root: AccessibilityNodeInfo,
        bounds: ScreenBounds
    ): AccessibilityNodeInfo? {
        if (root.className?.toString()?.substringAfterLast('.') == "Button" &&
            root.text?.toString()?.trim() == "Aceptar"
        ) {
            val actual = android.graphics.Rect()
            root.getBoundsInScreen(actual)
            if (actual.left == bounds.left && actual.top == bounds.top &&
                actual.right == bounds.right && actual.bottom == bounds.bottom
            ) {
                return AccessibilityNodeInfo.obtain(root)
            }
        }

        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val match = try {
                findExactAcceptButton(child, bounds)
            } finally {
                child.recycle()
            }
            if (match != null) return match
        }
        return null
    }

    private fun format(value: Double): String {
        return String.format(Locale.US, "%.3f", value)
    }
}
