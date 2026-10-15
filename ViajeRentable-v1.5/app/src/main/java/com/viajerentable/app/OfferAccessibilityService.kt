package com.viajerentable.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads text exposed by Uber Driver accessibility, including visible interactive windows. */
class OfferAccessibilityService : AccessibilityService() {
    private var lastDiagnosticAt = 0L
    private var lastTreeDumpAt = 0L
    private val offerDeduplicator = OfferDeduplicator()
    private var hadOfferLocatedWhileEnabled = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (AutoAcceptanceShutdownPolicy.shouldDisable(AutoAcceptanceStore.isEnabled(this), AutoAcceptanceShutdownTrigger.SERVICE_RESTART, AutoAcceptanceStore.safetyState(this))) {
            disableAutoAcceptance("servicio de accesibilidad reiniciado")
        }
        LogStore.add(this, "SERVICIO CONECTADO; captura local activada")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val eventPackage = event.packageName?.toString()
        if (eventPackage != UBER_DRIVER_PACKAGE) {
            if (AutoAcceptanceStore.isEnabled(this) && hadOfferLocatedWhileEnabled) {
                val active = try {
                    rootInActiveWindow
                } catch (_: Exception) {
                    null
                }
                val activePackage = try {
                    active?.packageName?.toString()
                } finally {
                    active?.recycle()
                }
                if (activePackage != UBER_DRIVER_PACKAGE && AutoAcceptanceShutdownPolicy.shouldDisable(true, AutoAcceptanceShutdownTrigger.UBER_LEFT_FOREGROUND, AutoAcceptanceStore.safetyState(this))) {
                    disableAutoAcceptance("Uber Driver dejó de estar en primer plano")
                }
            }
            return
        }
        try {
            val texts = mutableListOf<String>()
            val counter = Counter()
            val source = event.source
            if (source != null) {
                try {
                    collectText(source, texts, counter, 0)
                } finally {
                    source.recycle()
                }
            }

            val windowsSnapshot = try {
                windows
            } catch (_: Exception) {
                emptyList()
            }
            for (window in windowsSnapshot) {
                if (counter.nodes >= MAX_NODES) break
                val windowRoot = try {
                    window.root
                } catch (_: Exception) {
                    null
                } ?: continue
                try {
                    if (windowRoot.packageName?.toString() == UBER_DRIVER_PACKAGE) {
                        collectText(windowRoot, texts, counter, 0)
                    }
                } finally {
                    windowRoot.recycle()
                }
            }

            if (texts.isEmpty()) {
                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        if (root.packageName?.toString() == UBER_DRIVER_PACKAGE) {
                            collectText(root, texts, counter, 0)
                        }
                    } finally {
                        root.recycle()
                    }
                }
            }

            val normalized = texts.map { it.replace(Regex("\\s+"), " ").trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val combined = normalized.joinToString(" | ")
            AcceptedTripStore.updateLatestScreen(this, combined)

            val hasOfferAction = combined.contains("Aceptar", ignoreCase = true) ||
                combined.contains("Me interesa", ignoreCase = true)
            val treeDumpTime = System.currentTimeMillis()
            if (ScreenTreeDumpStore.isEnabled(this) &&
                hasOfferAction &&
                treeDumpTime - lastTreeDumpAt >= TREE_DUMP_INTERVAL_MS
            ) {
                val treeDump = captureWindowTree(windowsSnapshot)
                if (treeDump.isNotBlank()) {
                    ScreenTreeDumpStore.saveLastDump(this, treeDump)
                    lastTreeDumpAt = treeDumpTime
                    LogStore.add(this, "VOLCADO DE ÁRBOL guardado localmente (" + treeDump.length + " caracteres)")
                }
            }

            val parsedOffers = OfferParser.parseAll(combined)
            val now = System.currentTimeMillis()
            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
            ).format(Date(now))

            for (parsed in parsedOffers) {
                if (!offerDeduplicator.shouldRecord(parsed, now)) {
                    continue
                }

                if (parsed.hasRouteDetails) {
                    val costs = DriverCostsStore.read(this)
                    val result = Profitability.calculate(
                        Offer(
                            parsed.fare,
                            parsed.pickupKm!!,
                            parsed.pickupMinutes!!,
                            parsed.tripKm!!,
                            parsed.tripMinutes!!
                        ),
                        costs
                    )
                    val demo = if (DemoModeStore.isEnabled(this)) {
                        DemoDecision.evaluate(parsed, result, costs)
                    } else {
                        null
                    }
                    OffersStore.add(this, parsed, result, demo)
                    LogStore.add(
                        this,
                        "OFERTA DETALLADA [$timestamp]; tarifaCRC=${fmt(parsed.fare)}; " +
                            "totalKm=${fmt(result.totalKm)}; totalMin=${fmt(result.totalMinutes)}; " +
                            "netoCRC=${fmt(result.net)}; netoPorKmCRC=${fmt(result.netPerKm)}; " +
                            "netoPorHoraCRC=${fmt(result.netPerHour)}; recomendado=${result.recommended}"
                    )
                    if (demo != null) {
                        val wording = when (demo.decision) {
                            Decision.ACEPTARIA -> "HABRÍA ACEPTADO"
                            Decision.RECHAZARIA -> "HABRÍA RECHAZADO"
                            Decision.SIN_DATOS -> "SIN DATOS"
                        }
                        LogStore.add(
                            this,
                            "DEMO: $wording [$timestamp]; motivos=${demo.reasons.joinToString("; ")}"
                        )
                    }
                } else {
                    OffersStore.add(this, parsed, null)
                    LogStore.add(
                        this,
                        "OFERTA COMPACTA [$timestamp]; tarifaCRC=${fmt(parsed.fare)}; " +
                            "recorrido=no disponible; rentabilidad=pendiente"
                    )
                }
            }

            runButtonLocationTrial(windowsSnapshot, timestamp)

            if (parsedOffers.size >= 2) {
                val tariffs = parsedOffers.map { fmt(it.fare) }.sorted().joinToString(",")
                val preview = combined.take(MULTI_CARD_TEXT_LIMIT)
                LogStore.add(
                    this,
                    "VARIAS TARJETAS [$timestamp]; n=${parsedOffers.size}; " +
                        "tarifas=$tariffs; texto=$preview"
                )
            }

            if (now - lastDiagnosticAt >= DIAGNOSTIC_INTERVAL_MS) {
                lastDiagnosticAt = now
                val hasFare = OfferParser.containsFare(combined)
                val hasRoute = OfferParser.containsRouteLeg(combined)
                addDiagnostic(
                    "DIAGNOSTICO; evento=${event.eventType}; ventanas=${windowsSnapshot.size}; " +
                        "nodos=${counter.nodes}; nodosConTexto=${normalized.size}; " +
                        "textoAccesible=${normalized.isNotEmpty()}; contieneTarifa=$hasFare; " +
                        "contieneTramo=$hasRoute; ofertasDetectadas=${parsedOffers.size}; " +
                        "tipoOferta=${parsedOffers.joinToString(",") { if (it.hasRouteDetails) "detallada" else "compacta" }}"
                )
            }
        } catch (e: Exception) {
            addDiagnostic("ERROR DE LECTURA: ${e.javaClass.simpleName}")
        }
    }


    private fun runButtonLocationTrial(
        windowList: List<android.view.accessibility.AccessibilityWindowInfo>,
        timestamp: String
    ) {
        for (window in windowList) {
            val root = try {
                window.root
            } catch (_: Exception) {
                null
            } ?: continue

            try {
                if (root.packageName?.toString() != UBER_DRIVER_PACKAGE) {
                    continue
                }
                val result = OfferButtonLocator.locate(root)
                if (AutoAcceptanceStore.isEnabled(this) && result.cards.isNotEmpty()) {
                    hadOfferLocatedWhileEnabled = true
                }
                for (ambiguous in result.ambiguous) {
                    OfferButtonTrialStore.incrementAmbiguous(this)
                    val parsed = OfferParser.parseAll(
                        ambiguous.cardText + " | " + ambiguous.buttonType
                    ).firstOrNull()
                    val decision = parsed?.let { evaluateTrialDecision(it) }
                    if (decision?.decision == Decision.ACEPTARIA) {
                        OfferButtonTrialStore.incrementAcceptedWithoutButton(this)
                        LogStore.add(
                            this,
                            "ENSAYO: ACEPTARIA PERO BOTÓN NO LOCALIZADO (${ambiguous.reason})"
                        )
                    } else {
                        val decisionName = decision?.decision?.name ?: Decision.SIN_DATOS.name
                        LogStore.add(
                            this,
                            "ENSAYO: NO PULSARÍA ($decisionName) tipo=${ambiguous.buttonType} X=no"
                        )
                    }
                }

                for (target in result.cards) {
                    val parsed = OfferParser.parse(target.cardText)
                    if (parsed == null) {
                        LogStore.add(
                            this,
                            "ENSAYO: ACEPTARIA PERO BOTÓN NO LOCALIZADO (tarjeta no parseable)"
                        )
                        OfferButtonTrialStore.incrementAcceptedWithoutButton(this)
                        continue
                    }

                    val decision = evaluateTrialDecision(parsed)
                    val storedOffers = OffersStore.read(this)
                    val routeMatch = storedOffers.firstOrNull {
                        sameRoute(it.parsed, parsed)
                    }
                    val exactFareMatch = storedOffers.any {
                        kotlin.math.abs(it.parsed.fare - target.fare) < 0.01
                    }
                    if (routeMatch != null &&
                        kotlin.math.abs(routeMatch.parsed.fare - target.fare) >= 0.01
                    ) {
                        recordTrialDiscrepancy(
                            "ENSAYO: DISCREPANCIA tarifa ${fmt(target.fare)} vs ${fmt(routeMatch.parsed.fare)}"
                        )
                    } else if (routeMatch == null && !exactFareMatch) {
                        val storedFare = routeMatch?.parsed?.fare ?: storedOffers.firstOrNull()?.parsed?.fare
                        val registered = storedFare?.let { fmt(it) } ?: "no registrada"
                        recordTrialDiscrepancy(
                            "ENSAYO: DISCREPANCIA tarifa ${fmt(target.fare)} vs $registered"
                        )
                    }

                    if (decision.decision == Decision.ACEPTARIA) {
                        attemptRealAcceptance(
                            target = target,
                            parsed = parsed,
                            decision = decision,
                            fareMatches = routeMatch != null &&
                                kotlin.math.abs(routeMatch.parsed.fare - target.fare) < 0.01
                        )
                    }

                    val xBounds = target.xBoundsInScreen?.toShortString() ?: "no"
                    when (decision.decision) {
                        Decision.ACEPTARIA -> {
                            if (target.buttonType == "Aceptar") {
                                OfferButtonTrialStore.incrementAcceptedWithButton(this)
                                LogStore.add(
                                    this,
                                    "ENSAYO: HABRÍA PULSADO Aceptar tarifa=${fmt(target.fare)} " +
                                        "botón=${target.buttonBoundsInScreen.toShortString()} X=$xBounds " +
                                        "habilitado=${target.isEnabled} visible=${target.isVisibleToUser}"
                                )
                            } else {
                                LogStore.add(
                                    this,
                                    "ENSAYO: HABRÍA PULSADO Me interesa tarifa=${fmt(target.fare)} " +
                                        "botón=${target.buttonBoundsInScreen.toShortString()} X=$xBounds " +
                                        "habilitado=${target.isEnabled} visible=${target.isVisibleToUser}"
                                )
                            }
                        }
                        Decision.RECHAZARIA, Decision.SIN_DATOS -> {
                            LogStore.add(
                                this,
                                "ENSAYO: NO PULSARÍA (${decision.decision}) tipo=${target.buttonType} X=$xBounds"
                            )
                        }
                    }
                }
            } finally {
                root.recycle()
            }
        }
    }

    private fun attemptRealAcceptance(
        target: CardTarget,
        parsed: ParsedOffer,
        decision: DemoResult,
        fareMatches: Boolean
    ) {
        if (!AutoAcceptanceStore.isEnabled(this)) return
        if (target.buttonType != "Aceptar") return
        val fingerprint = OfferClicker.fingerprint(parsed)
        if (AutoAcceptanceStore.wasAccepted(this, fingerprint)) {
            LogStore.add(this, "ACEPTACIÓN OMITIDA: oferta ya intentada fingerprint=$fingerprint")
            return
        }
        val gate = AcceptanceDecisionGate.evaluate(
            AcceptanceGateInput(
                enabled = AutoAcceptanceStore.isEnabled(this),
                target = target,
                decision = decision.decision,
                parsedOffer = parsed,
                maxPickupKm = DriverCostsStore.maxPickupKm(this),
                ambiguous = false,
                fareMatches = fareMatches
            )
        )
        if (!gate.allowed) {
            LogStore.add(this, "ACEPTACIÓN OMITIDA: ${gate.reason}")
            return
        }
        if (!AutoAcceptanceStore.mayClickNow(this)) {
            LogStore.add(this, "ACEPTACIÓN OMITIDA: pausa mínima de 5 segundos")
            return
        }

        val freshRoot = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        }
        if (freshRoot == null) {
            LogStore.add(this, "ACEPTACIÓN CANCELADA: la tarjeta cambió")
            return
        }

        try {
            if (freshRoot.packageName?.toString() != UBER_DRIVER_PACKAGE) {
                LogStore.add(this, "ACEPTACIÓN CANCELADA: la tarjeta cambió")
                return
            }
            val freshResult = OfferButtonLocator.locate(freshRoot)
            val freshTarget = freshResult.cards.singleOrNull { candidate ->
                candidate.buttonType == "Aceptar" &&
                    kotlin.math.abs(candidate.fare - target.fare) < 0.01 &&
                    candidate.buttonBoundsInScreen == target.buttonBoundsInScreen &&
                    sameRoute(OfferParser.parse(candidate.cardText) ?: return@singleOrNull false, parsed)
            }
            if (freshTarget == null || freshResult.ambiguous.any {
                    it.boundsInScreen == target.buttonBoundsInScreen
                }
            ) {
                LogStore.add(this, "ACEPTACIÓN CANCELADA: la tarjeta cambió")
                return
            }

            val freshParsed = OfferParser.parse(freshTarget.cardText)
            val freshDecision = freshParsed?.let { evaluateTrialDecision(it) }
            val freshGate = AcceptanceDecisionGate.evaluate(
                AcceptanceGateInput(
                    enabled = AutoAcceptanceStore.isEnabled(this),
                    target = freshTarget,
                    decision = freshDecision?.decision ?: Decision.SIN_DATOS,
                    parsedOffer = freshParsed,
                    maxPickupKm = DriverCostsStore.maxPickupKm(this),
                    ambiguous = false,
                    fareMatches = freshParsed != null &&
                        kotlin.math.abs(freshTarget.fare - freshParsed.fare) < 0.01 &&
                        sameRoute(freshParsed, parsed)
                )
            )
            if (!freshGate.allowed || !AutoAcceptanceStore.isEnabled(this)) {
                LogStore.add(this, "ACEPTACIÓN CANCELADA: la tarjeta cambió")
                return
            }
            if (!AutoAcceptanceStore.mayClickNow(this)) {
                LogStore.add(this, "ACEPTACIÓN OMITIDA: pausa mínima de 5 segundos")
                return
            }

            AutoAcceptanceStore.markAccepted(this, fingerprint)
            AutoAcceptanceStore.markClickAttempt(this)
            val clickResult = OfferClicker.click(freshTarget, freshRoot)
            if (!clickResult.actionInvoked) {
                LogStore.add(this, "ACEPTACIÓN CANCELADA: la tarjeta cambió")
                return
            }
            val clicked = clickResult.succeeded
            val safety = AutoAcceptanceStore.recordClickResult(this, clicked)
            val profitability = Profitability.calculate(
                Offer(
                    parsed.fare,
                    parsed.pickupKm!!,
                    parsed.pickupMinutes!!,
                    parsed.tripKm!!,
                    parsed.tripMinutes!!
                ),
                DriverCostsStore.read(this)
            )
            val route = "origen=${parsed.pickupPlace.orEmpty()}; destino=${parsed.destinationPlace.orEmpty()}; " +
                "recogida ${fmt(parsed.pickupKm!!)} km/${fmt(parsed.pickupMinutes!!)} min; " +
                "viaje ${fmt(parsed.tripKm!!)} km/${fmt(parsed.tripMinutes!!)} min"
            val resultText = if (clicked) "resultado=true" else "resultado=false"
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            LogStore.addAudit(
                this,
                listOf(
                    timestamp,
                    fmt(parsed.fare),
                    route,
                    fmt(parsed.pickupKm!!),
                    fmt(profitability.netPerKm),
                    fmt(profitability.netPerHour),
                    freshTarget.buttonBoundsInScreen.toShortString(),
                    resultText
                ).joinToString("|")
            )
            LogStore.add(this, "ACEPTACIÓN REAL: tarifa=${fmt(parsed.fare)}; $route; $resultText")
            if (clicked) {
                OfferClicker.playClickSound()
            } else {
                LogStore.add(this, "ACEPTACIÓN FALLIDA: la acción de botón devolvió false")
            }
            if (AutoAcceptanceShutdownPolicy.shouldDisable(AutoAcceptanceStore.isEnabled(this), AutoAcceptanceShutdownTrigger.CLICK_FAILURE, safety)) {
                disableAutoAcceptance("dos fallos consecutivos al solicitar el botón")
            }
        } finally {
            freshRoot.recycle()
        }
    }

    private fun recordTrialDiscrepancy(message: String) {
        OfferButtonTrialStore.incrementDiscrepancies(this)
        LogStore.add(this, message)
        if (AutoAcceptanceStore.isEnabled(this)) {
            val safety = AutoAcceptanceStore.recordDiscrepancy(this)
            if (AutoAcceptanceShutdownPolicy.shouldDisable(AutoAcceptanceStore.isEnabled(this), AutoAcceptanceShutdownTrigger.TARIFF_DISCREPANCY, safety)) {
                disableAutoAcceptance("tres discrepancias de tarifa en el ensayo")
            }
        }
    }

    private fun disableAutoAcceptance(reason: String) {
        val wasEnabled = AutoAcceptanceStore.isEnabled(this)
        AutoAcceptanceStore.setEnabled(this, false)
        hadOfferLocatedWhileEnabled = false
        val message = "ACEPTACIÓN AUTOMÁTICA APAGADA: $reason"
        LogStore.add(this, message)
        LogStore.addAudit(
            this,
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()) +
                "| | | | | | |apagado automático: $reason"
        )
        if (wasEnabled) {
            OfferClicker.playSafetyWarning()
            sendBroadcast(Intent(ACTION_AUTO_ACCEPTANCE_DISABLED).setPackage(packageName))
        }
    }

    private fun sameRoute(first: ParsedOffer, second: ParsedOffer): Boolean {
        if (!first.hasRouteDetails || !second.hasRouteDetails) {
            return false
        }
        return kotlin.math.abs(first.pickupKm!! - second.pickupKm!!) < 0.01 &&
            kotlin.math.abs(first.pickupMinutes!! - second.pickupMinutes!!) < 0.01 &&
            kotlin.math.abs(first.tripKm!! - second.tripKm!!) < 0.01 &&
            kotlin.math.abs(first.tripMinutes!! - second.tripMinutes!!) < 0.01
    }

    private fun evaluateTrialDecision(offer: ParsedOffer): DemoResult {
        if (!offer.hasRouteDetails) {
            return DemoResult(Decision.SIN_DATOS, listOf("Sin datos de recorrido"))
        }
        val costs = DriverCostsStore.read(this)
        val result = Profitability.calculate(
            Offer(
                offer.fare,
                offer.pickupKm!!,
                offer.pickupMinutes!!,
                offer.tripKm!!,
                offer.tripMinutes!!
            ),
            costs
        )
        return DemoDecision.evaluate(offer, result, costs)
    }

    private fun captureWindowTree(windowList: List<android.view.accessibility.AccessibilityWindowInfo>): String {
        val output = StringBuilder()
        for (window in windowList) {
            val root = try {
                window.root
            } catch (_: Exception) {
                null
            } ?: continue

            try {
                if (root.packageName?.toString() == UBER_DRIVER_PACKAGE) {
                    appendNodeTree(root, output, 0)
                    if (output.isNotEmpty()) {
                        break
                    }
                }
            } finally {
                root.recycle()
            }
        }
        return output.toString().take(ScreenTreeDumpStore.MAX_DUMP_CHARS)
    }

    private fun appendNodeTree(
        node: AccessibilityNodeInfo,
        output: StringBuilder,
        depth: Int
    ) {
        if (depth > MAX_TREE_DEPTH || output.length >= ScreenTreeDumpStore.MAX_DUMP_CHARS) {
            return
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val indent = "  ".repeat(depth)
        val line = "$indent className=${cleanField(node.className?.toString())}" +
            " viewIdResourceName=${cleanField(node.viewIdResourceName)}" +
            " text=${cleanField(node.text?.toString())}" +
            " contentDescription=${cleanField(node.contentDescription?.toString())}" +
            " isClickable=${node.isClickable}" +
            " isEnabled=${node.isEnabled}" +
            " isVisibleToUser=${node.isVisibleToUser}" +
            " boundsInScreen=$bounds\n"
        output.append(line.take(ScreenTreeDumpStore.MAX_DUMP_CHARS - output.length))

        for (index in 0 until node.childCount) {
            if (output.length >= ScreenTreeDumpStore.MAX_DUMP_CHARS) {
                break
            }
            val child = node.getChild(index) ?: continue
            try {
                appendNodeTree(child, output, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    private fun cleanField(value: String?): String {
        return value.orEmpty()
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace(" ", "␠")
    }
    private fun fmt(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }

    private fun collectText(
        node: AccessibilityNodeInfo,
        output: MutableList<String>,
        counter: Counter,
        depth: Int
    ) {
        if (depth > MAX_TREE_DEPTH || counter.nodes >= MAX_NODES) return
        counter.nodes++
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        for (i in 0 until node.childCount) {
            if (counter.nodes >= MAX_NODES) break
            val child = node.getChild(i) ?: continue
            try {
                collectText(child, output, counter, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    private fun addDiagnostic(message: String) {
        LogStore.add(this, message)
    }

    override fun onInterrupt() {
        LogStore.add(this, "SERVICIO INTERRUMPIDO")
    }

    override fun onDestroy() {
        LogStore.add(this, "SERVICIO DESCONECTADO")
        super.onDestroy()
    }

    private class Counter(var nodes: Int = 0)

    companion object {
        const val ACTION_AUTO_ACCEPTANCE_DISABLED = "com.viajerentable.app.AUTO_ACCEPTANCE_DISABLED"
        private const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
        private const val MAX_TREE_DEPTH = 18
        private const val MAX_NODES = 1000
        private const val DIAGNOSTIC_INTERVAL_MS = 30000L
        private const val TREE_DUMP_INTERVAL_MS = 3000L
        private const val MULTI_CARD_TEXT_LIMIT = 700
    }
}
