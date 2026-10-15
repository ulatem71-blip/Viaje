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

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogStore.add(this, "SERVICIO CONECTADO; captura local activada")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != UBER_DRIVER_PACKAGE) return
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
                for (ambiguous in result.ambiguous) {
                    OfferButtonTrialStore.incrementAmbiguous(this)
                    val parsed = OfferParser.parseAll(
                        ambiguous.cardText + " | " + ambiguous.buttonType
                    ).firstOrNull()
                    val decision = parsed?.let { evaluateTrialDecision(it) }
                    if (ambiguous.buttonType == "Aceptar" && decision?.decision == Decision.ACEPTARIA) {
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
                    val storedMatch = OffersStore.read(this).firstOrNull {
                        kotlin.math.abs(it.parsed.fare - parsed.fare) < 0.01
                    }
                    if (storedMatch == null) {
                        OfferButtonTrialStore.incrementDiscrepancies(this)
                        LogStore.add(
                            this,
                            "ENSAYO: DISCREPANCIA tarifa ${fmt(parsed.fare)} vs no registrada"
                        )
                    } else if (kotlin.math.abs(storedMatch.parsed.fare - target.fare) >= 0.01) {
                        OfferButtonTrialStore.incrementDiscrepancies(this)
                        LogStore.add(
                            this,
                            "ENSAYO: DISCREPANCIA tarifa ${fmt(target.fare)} vs ${fmt(storedMatch.parsed.fare)}"
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
        private const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
        private const val MAX_TREE_DEPTH = 18
        private const val MAX_NODES = 1000
        private const val DIAGNOSTIC_INTERVAL_MS = 30000L
        private const val TREE_DUMP_INTERVAL_MS = 3000L
        private const val MULTI_CARD_TEXT_LIMIT = 700
    }
}
