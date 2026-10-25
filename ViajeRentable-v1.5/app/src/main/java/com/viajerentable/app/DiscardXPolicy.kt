package com.viajerentable.app

internal data class DiscardGateInput(
    val enabled: Boolean,
    val target: CardTarget?,
    val decision: Decision,
    val parsedOffer: ParsedOffer?,
    val ambiguous: Boolean,
    val fareMatches: Boolean
)

internal data class DiscardGateResult(
    val allowed: Boolean,
    val reason: String
)

internal object DiscardDecisionGate {
    fun evaluate(input: DiscardGateInput): DiscardGateResult {
        if (!input.enabled) return DiscardGateResult(false, "interruptor apagado")
        val target = input.target ?: return DiscardGateResult(false, "tarjeta no localizada")
        if (target.buttonType !in setOf("Aceptar", "Me interesa")) {
            return DiscardGateResult(false, "tipo de tarjeta no permitido")
        }
        if (input.decision != Decision.RECHAZARIA) {
            return DiscardGateResult(false, "decisión distinta de RECHAZARIA")
        }
        val parsed = input.parsedOffer
            ?: return DiscardGateResult(false, "oferta no parseable")
        if (!parsed.hasRouteDetails) {
            return DiscardGateResult(false, "ruta incompleta")
        }
        if (input.ambiguous) {
            return DiscardGateResult(false, "tarjeta ambigua")
        }
        if (!input.fareMatches || kotlin.math.abs(target.fare - parsed.fare) >= 0.01) {
            return DiscardGateResult(false, "tarifa discrepante")
        }
        if (!target.isEnabled || !target.isVisibleToUser) {
            return DiscardGateResult(false, "tarjeta no disponible")
        }
        if (target.xBoundsInScreen == null) {
            return DiscardGateResult(false, "X no localizada de forma única")
        }
        return DiscardGateResult(true, "condiciones cumplidas")
    }
}

internal data class XCandidate(
    val className: String,
    val text: String?,
    val contentDescription: String?,
    val clickable: Boolean,
    val enabled: Boolean,
    val visible: Boolean,
    val bounds: ScreenBounds
)

internal object DiscardXRecognition {
    fun selectSingle(cardBounds: ScreenBounds, candidates: List<XCandidate>): XCandidate? {
        return candidates.filter { isValid(cardBounds, it) }.singleOrNull()
    }

    fun isValid(cardBounds: ScreenBounds, candidate: XCandidate): Boolean {
        val bounds = candidate.bounds
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        val inCard = bounds.left >= cardBounds.left &&
            bounds.top >= cardBounds.top &&
            bounds.right <= cardBounds.right &&
            bounds.bottom <= cardBounds.bottom
        val rightOffset = cardBounds.right - bounds.right
        val topOffset = bounds.top - cardBounds.top
        return candidate.className.substringAfterLast('.').equals("Button", true) &&
            candidate.text.isNullOrBlank() &&
            candidate.contentDescription.isNullOrBlank() &&
            candidate.clickable && candidate.enabled && candidate.visible &&
            width in 100..170 && height in 100..170 && inCard &&
            rightOffset in 0 until 80 && topOffset in 0 until 120
    }
}

data class ConsecutiveDiscardState(
    val consecutiveWithoutAcceptance: Int = 0,
    val limit: Int = 15
) {
    fun afterDiscard(): ConsecutiveDiscardState {
        return copy(consecutiveWithoutAcceptance = consecutiveWithoutAcceptance + 1)
    }

    fun afterAcceptance(): ConsecutiveDiscardState {
        return copy(consecutiveWithoutAcceptance = 0)
    }

    fun mustDisable(): Boolean {
        return consecutiveWithoutAcceptance >= limit
    }
}
