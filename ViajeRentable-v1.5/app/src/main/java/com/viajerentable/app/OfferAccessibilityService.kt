package com.viajerentable.app

import android.accessibilityservice.AccessibilityService
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
    private val trialLogDeduplicator = TrialLogDeduplicator()
    private var hadOfferLocatedWhileEnabled = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (AutoAcceptanceShutdownPolicy.shouldDisable(
                AutoAcceptanceStore.isEnabled(this),
                AutoAcceptanceShutdownTrigger.SERVICE_RESTART,
                AutoAcceptanceStore.safetyState(this)
            )
        ) {
            disableAutoAcceptance("servicio de accesibilidad reiniciado")
        }
        if (SystemPowerStore.isOn(this)) {
            LogStore.add(this, "SERVICIO CONECTADO; captura local activada")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!SystemPowerPolicy.shouldProcess(SystemPowerStore.isOn(this))) {
            return
        }
        event ?: return
        if (event.packageName?.toString() != UBER_DRIVER_PACKAGE) {
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
                if (activePackage != UBER_DRIVER_PACKAGE &&
                    AutoAcceptanceShutdownPolicy.shouldDisable(
                        true,
                        AutoAcceptanceShutdownTrigger.UBER_LEFT_FOREGROUND,
                        AutoAcceptanceStore.safetyState(this)
                    )
                ) {
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
                        DemoDecision.evaluate(parsed, result, costs, ZoneRiskStore.read(this))
                    } else {
                        null
                    }
                    OffersStore.add(this, parsed, result, demo, costs.acEnabled)
                    LogStore.add(
                        this,
                        "OFERTA DETALLADA [$timestamp]; tarifaCRC=${fmt(parsed.fare)}; " +
                            "totalKm=${fmt(result.totalKm)}; totalMin=${fmt(result.totalMinutes)}; " +
                            "netoCRC=${fmt(result.net)}; netoPorKmCRC=${fmt(result.netPerKm)}; " +
                            "netoPorHoraCRC=${fmt(result.netPerHour)}; A/C=${if (costs.acEnabled) "sí" else "no"}; recomendado=${result.recommended}"
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
            runAutomaticAcceptance(windowsSnapshot, timestamp)

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
                for (ambiguous in result.ambiguous) {
                    OfferButtonTrialStore.incrementAmbiguous(this)
                    val parsed = OfferParser.parseAll(
                        ambiguous.cardText + " | " + ambiguous.buttonType
                    ).firstOrNull()
                    val decision = parsed?.let { evaluateTrialDecision(it) }
                    val decisionName = decision?.decision?.name ?: Decision.SIN_DATOS.name
                    val fareKey = parsed?.fare?.let { fmt(it) }
                        ?: ambiguous.fareValues.firstOrNull()
                        ?: "sin tarifa"
                    if (!trialLogDeduplicator.shouldRecord(
                            fareKey,
                            ambiguous.buttonType,
                            decisionName
                        )
                    ) {
                        continue
                    }
                    if (decision?.decision == Decision.ACEPTARIA) {
                        recordAcceptanceDiscrepancy("tarjeta ambigua")
                        OfferButtonTrialStore.incrementAcceptedWithoutButton(this)
                        LogStore.add(
                            this,
                            "ENSAYO: ACEPTARIA PERO BOTÓN NO LOCALIZADO (${ambiguous.reason})"
                        )
                        val detail = "ENSAYO: AMBIGUA detalle tarifaEncontrada=" +
                            ambiguous.fareValues.joinToString(",") +
                            "; className=${ambiguous.ancestorClassName}; bounds=${ambiguous.boundsInScreen.toShortString()}" +
                            "; botonesAceptar=${ambiguous.acceptButtonCount}" +
                            "; botonesMeInteresa=${ambiguous.interestedButtonCount}" +
                            "; textosTarifa=[${ambiguous.fareNodeDetails.joinToString(" || ")}]"
                        LogStore.add(this, detail)
                        AmbiguousCaseDumpStore.saveLastDump(this, captureSingleWindowTree(root))
                    } else {
                        LogStore.add(
                            this,
                            "ENSAYO: NO PULSARÍA ($decisionName) tipo=${ambiguous.buttonType} X=no"
                        )
                    }
                }

                for (target in result.cards) {
                    val parsed = OfferParser.parse(target.cardText)
                    if (parsed == null) {
                        if (!trialLogDeduplicator.shouldRecord(
                                fmt(target.fare),
                                target.buttonType,
                                Decision.SIN_DATOS.name
                            )
                        ) {
                            continue
                        }
                        LogStore.add(
                            this,
                            "ENSAYO: ACEPTARIA PERO BOTÓN NO LOCALIZADO (tarjeta no parseable)"
                        )
                        OfferButtonTrialStore.incrementAcceptedWithoutButton(this)
                        continue
                    }

                    val decision = evaluateTrialDecision(parsed)
                    if (!trialLogDeduplicator.shouldRecord(
                            fmt(target.fare),
                            target.buttonType,
                            decision.decision.name
                        )
                    ) {
                        continue
                    }
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
                        OfferButtonTrialStore.incrementDiscrepancies(this)
                        recordAcceptanceDiscrepancy("discrepancia de tarifa")
                        LogStore.add(
                            this,
                            "ENSAYO: DISCREPANCIA tarifa ${fmt(target.fare)} vs ${fmt(routeMatch.parsed.fare)}"
                        )
                    } else if (routeMatch == null && !exactFareMatch) {
                        OfferButtonTrialStore.incrementDiscrepancies(this)
                        val storedFare = storedOffers.firstOrNull()?.parsed?.fare
                        val registered = storedFare?.let { fmt(it) } ?: "no registrada"
                        LogStore.add(
                            this,
                            "ENSAYO: DISCREPANCIA tarifa ${fmt(target.fare)} vs $registered"
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

    private fun runAutomaticAcceptance(
        windowList: List<android.view.accessibility.AccessibilityWindowInfo>,
        timestamp: String
    ) {
        if (!SystemPowerStore.isOn(this) || !AutoAcceptanceStore.isEnabled(this)) {
            return
        }

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
                val located = OfferButtonLocator.locate(root)
                if (located.cards.isNotEmpty()) {
                    hadOfferLocatedWhileEnabled = true
                }
                for (target in located.cards) {
                    val parsed = OfferParser.parse(target.cardText) ?: continue
                    val costs = DriverCostsStore.read(this)
                    if (!parsed.hasRouteDetails) {
                        continue
                    }
                    val profitability = Profitability.calculate(
                        Offer(
                            parsed.fare,
                            parsed.pickupKm!!,
                            parsed.pickupMinutes!!,
                            parsed.tripKm!!,
                            parsed.tripMinutes!!
                        ),
                        costs
                    )
                    val decision = DemoDecision.evaluate(parsed, profitability, costs, ZoneRiskStore.read(this)).decision
                    val gate = AcceptanceDecisionGate.evaluate(
                        AcceptanceGateInput(
                            enabled = SystemPowerStore.isOn(this) &&
                                AutoAcceptanceStore.isEnabled(this),
                            target = target,
                            decision = decision,
                            parsedOffer = parsed,
                            maxPickupKm = costs.maxPickupKm,
                            ambiguous = located.ambiguous.any {
                                it.boundsInScreen == target.buttonBoundsInScreen
                            },
                            fareMatches = kotlin.math.abs(target.fare - parsed.fare) < 0.01
                        )
                    )
                    if (!gate.allowed) {
                        continue
                    }

                    val fingerprint = OfferClicker.fingerprint(parsed)
                    if (AutoAcceptanceStore.wasAttempted(this, fingerprint)) {
                        continue
                    }
                    val now = System.currentTimeMillis()
                    if (!AutoAcceptanceStore.mayClickNow(this, now)) {
                        continue
                    }

                    val freshRoot = try {
                        window.root
                    } catch (_: Exception) {
                        null
                    }
                    if (freshRoot == null) {
                        LogStore.add(this, "ACEPTACIÓN CANCELADA: no se pudo releer la ventana")
                        continue
                    }
                    val clickResult = try {
                        val freshTarget = revalidateTargetForClick(freshRoot, target, parsed)
                        if (freshTarget == null) {
                            null
                        } else {
                            AutoAcceptanceStore.markAttempted(this, fingerprint, now)
                            AutoAcceptanceStore.markClickAttempt(this, now)
                            OfferClicker.click(freshTarget, freshRoot)
                        }
                    } finally {
                        freshRoot.recycle()
                    }
                    if (clickResult == null) {
                        LogStore.add(this, "ACEPTACIÓN CANCELADA: la tarjeta cambió antes del clic")
                        continue
                    }
                    val safetyState = AutoAcceptanceStore.recordClickResult(this, clickResult.succeeded)
                    val route = "recogida=${fmt(parsed.pickupKm!!)}km/${fmt(parsed.pickupMinutes!!)}min; viaje=${fmt(parsed.tripKm!!)}km/${fmt(parsed.tripMinutes!!)}min"
                    val auditLine = listOf(
                        timestamp,
                        fmt(parsed.fare),
                        route,
                        fmt(parsed.pickupKm!!),
                        fmt(profitability.netPerKm),
                        fmt(profitability.netPerHour),
                        target.buttonBoundsInScreen.toShortString(),
                        if (clickResult.succeeded) "CLICK_OK" else if (clickResult.actionInvoked) "CLICK_FALLO" else "BOTON_NO_ENCONTRADO"
                    ).joinToString("|")
                    LogStore.addAudit(this, auditLine)
                    LogStore.add(
                        this,
                        "ACEPTACIÓN REAL: tarifa=${fmt(parsed.fare)}; resultado=${if (clickResult.succeeded) "CLICK_OK" else "CLICK_FALLO"}; motivo=${gate.reason}"
                    )
                    if (clickResult.succeeded) {
                        OfferClicker.playClickSound()
                    } else {
                        OfferClicker.playSafetyWarning()
                    }
                    if (AutoAcceptanceShutdownPolicy.shouldDisable(
                            AutoAcceptanceStore.isEnabled(this),
                            AutoAcceptanceShutdownTrigger.CLICK_FAILURE,
                            safetyState
                        )
                    ) {
                        disableAutoAcceptance("dos fallos consecutivos de clic")
                    }
                    if (!SystemPowerStore.isOn(this)) {
                        AutoAcceptanceStore.setEnabled(this, false)
                    }
                }
            } finally {
                root.recycle()
            }
        }
    }

    /** Re-reads the window right before clicking and requires the same card, fare, route and gate. */
    private fun revalidateTargetForClick(
        freshRoot: AccessibilityNodeInfo,
        original: CardTarget,
        originalParsed: ParsedOffer
    ): CardTarget? {
        if (freshRoot.packageName?.toString() != UBER_DRIVER_PACKAGE) {
            return null
        }
        val fresh = OfferButtonLocator.locate(freshRoot)
        val freshTarget = fresh.cards.filter {
            it.buttonType == "Aceptar" &&
                it.buttonBoundsInScreen == original.buttonBoundsInScreen
        }.singleOrNull() ?: return null
        val freshParsed = OfferParser.parse(freshTarget.cardText) ?: return null
        if (!sameRoute(freshParsed, originalParsed)) {
            return null
        }
        if (kotlin.math.abs(freshParsed.fare - originalParsed.fare) >= 0.01) {
            return null
        }
        val costs = DriverCostsStore.read(this)
        val decision = evaluateTrialDecision(freshParsed).decision
        val gate = AcceptanceDecisionGate.evaluate(
            AcceptanceGateInput(
                enabled = SystemPowerStore.isOn(this) && AutoAcceptanceStore.isEnabled(this),
                target = freshTarget,
                decision = decision,
                parsedOffer = freshParsed,
                maxPickupKm = costs.maxPickupKm,
                ambiguous = false,
                fareMatches = kotlin.math.abs(freshTarget.fare - freshParsed.fare) < 0.01
            )
        )
        return if (gate.allowed) freshTarget else null
    }

    private fun recordAcceptanceDiscrepancy(reason: String) {
        if (!AutoAcceptanceStore.isEnabled(this)) {
            return
        }
        val state = AutoAcceptanceStore.recordDiscrepancy(this)
        if (AutoAcceptanceShutdownPolicy.shouldDisable(
                true,
                AutoAcceptanceShutdownTrigger.TARIFF_DISCREPANCY,
                state
            )
        ) {
            disableAutoAcceptance("tres discrepancias: $reason")
        }
    }

    private fun disableAutoAcceptance(reason: String) {
        AutoAcceptanceStore.setEnabled(this, false)
        LogStore.add(this, "ACEPTACIÓN AUTOMÁTICA APAGADA: $reason")
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
        return DemoDecision.evaluate(offer, result, costs, ZoneRiskStore.read(this))
    }

    private fun captureWindowTree(
        windowList: List<android.view.accessibility.AccessibilityWindowInfo>
    ): String {
        val output = StringBuilder()
        var uberWindowNumber = 0
        for (window in windowList) {
            if (output.length >= ScreenTreeDumpStore.MAX_DUMP_CHARS) {
                break
            }
            val root = try {
                window.root
            } catch (_: Exception) {
                null
            } ?: continue

            try {
                if (root.packageName?.toString() == UBER_DRIVER_PACKAGE) {
                    uberWindowNumber += 1
                    val bounds = Rect()
                    root.getBoundsInScreen(bounds)
                    val nodeCount = countTreeNodes(root)
                    output.append(
                        "VENTANA $uberWindowNumber: nodos=$nodeCount, bounds=$bounds\n"
                    )
                    appendNodeTree(root, output, 0)
                }
            } finally {
                root.recycle()
            }
        }
        return output.toString().take(ScreenTreeDumpStore.MAX_DUMP_CHARS)
    }

    private fun captureSingleWindowTree(root: AccessibilityNodeInfo): String {
        val output = StringBuilder()
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        output.append("VENTANA AMBIGUA: nodos=${countTreeNodes(root)}, bounds=$bounds\n")
        appendNodeTree(root, output, 0)
        return output.toString().take(AmbiguousCaseDumpStore.MAX_DUMP_CHARS)
    }

    private fun countTreeNodes(root: AccessibilityNodeInfo): Int {
        var count = 1
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            try {
                count += countTreeNodes(child)
            } finally {
                child.recycle()
            }
        }
        return count
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
        if (SystemPowerStore.isOn(this)) {
            LogStore.add(this, "SERVICIO INTERRUMPIDO")
        }
    }

    override fun onDestroy() {
        if (SystemPowerStore.isOn(this)) {
            LogStore.add(this, "SERVICIO DESCONECTADO")
        }
        super.onDestroy()
    }

    private class Counter(var nodes: Int = 0)

    companion object {
        private const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
        private const val MAX_TREE_DEPTH = 18
        private const val MAX_NODES = 1000
        private const val DIAGNOSTIC_INTERVAL_MS = 30000L
        private const val TREE_DUMP_INTERVAL_MS = 3000L
        private const val MULTI_CARD_TEXT_LIMIT = 700
    }
}
