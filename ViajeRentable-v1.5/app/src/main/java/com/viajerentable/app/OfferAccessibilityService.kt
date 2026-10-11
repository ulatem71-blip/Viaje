package com.viajerentable.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads text exposed by Uber Driver accessibility, including visible interactive windows. */
class OfferAccessibilityService : AccessibilityService() {
    private var lastDiagnosticAt = 0L
    private var lastOfferFingerprint = ""

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

            // The event source can point directly at the Uber card even when split-screen
            // makes rootInActiveWindow point at another app.
            val source = event.source
            if (source != null) {
                try { collectText(source, texts, counter, 0) } finally { source.recycle() }
            }

            // Read all interactive windows exposed by Accessibility, not just the active root.
            val windowsSnapshot = try { windows } catch (_: Exception) { emptyList() }
            for (window in windowsSnapshot) {
                if (counter.nodes >= MAX_NODES) break
                val windowRoot = try { window.root } catch (_: Exception) { null } ?: continue
                try {
                    if (windowRoot.packageName?.toString() == UBER_DRIVER_PACKAGE) collectText(windowRoot, texts, counter, 0)
                } finally { windowRoot.recycle() }
            }

            if (texts.isEmpty()) {
                val root = rootInActiveWindow
                if (root != null) {
                    try { if (root.packageName?.toString() == UBER_DRIVER_PACKAGE) collectText(root, texts, counter, 0) } finally { root.recycle() }
                }
            }

            val normalized = texts.map { it.replace(Regex("\\s+"), " ").trim() }
                .filter { it.isNotEmpty() }.distinct()
            val combined = normalized.joinToString(" | ")
            // Keep the latest text exposed by Uber locally so the driver can review vehicle/trip details
            // and manually save an accepted trip with their own reason in the app UI.
            AcceptedTripStore.updateLatestScreen(this, combined)
            val parsed = OfferParser.parse(combined)
            val now = System.currentTimeMillis()

            if (parsed != null) {
                val kind = if (parsed.hasRouteDetails) "DETALLADA" else "COMPACTA"
                val fingerprint = listOf(kind, parsed.fare, parsed.pickupKm, parsed.pickupMinutes, parsed.tripKm, parsed.tripMinutes).joinToString("|")
                if (fingerprint != lastOfferFingerprint) {
                    lastOfferFingerprint = fingerprint
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    if (parsed.hasRouteDetails) {
                        val result = Profitability.calculate(
                            Offer(parsed.fare, parsed.pickupKm!!, parsed.pickupMinutes!!, parsed.tripKm!!, parsed.tripMinutes!!),
                            DriverCostsStore.read(this)
                        )
                        LogStore.add(this,
                            "OFERTA DETALLADA [$timestamp]\n" +
                            "tarifaCRC=${fmt(parsed.fare)}; recogidaKm=${fmt(parsed.pickupKm)}; recogidaMin=${fmt(parsed.pickupMinutes)}; " +
                            "viajeKm=${fmt(parsed.tripKm)}; viajeMin=${fmt(parsed.tripMinutes)}\n" +
                            "recogidaLugar=${parsed.pickupPlace ?: "no detectado"}; destino=${parsed.destinationPlace ?: "no detectado"}\n" +
                            "totalKm=${fmt(result.totalKm)}; totalMin=${fmt(result.totalMinutes)}; combustibleCRC=${fmt(result.fuelCost)}; " +
                            "mantenimientoCRC=${fmt(result.maintenanceCost)}; netoCRC=${fmt(result.net)}; netoPorKmCRC=${fmt(result.netPerKm)}; " +
                            "netoPorHoraCRC=${fmt(result.netPerHour)}; recomendado=${result.recommended}"
                        )
                    } else {
                        OffersStore.add(this, parsed, null)
                        LogStore.add(this, "OFERTA COMPACTA [$timestamp]; tarifaCRC=${fmt(parsed.fare)}; recorrido=no disponible; rentabilidad=pendiente")
                    }
                    if (parsed.hasRouteDetails) OffersStore.add(this, parsed, Profitability.calculate(
                        Offer(parsed.fare, parsed.pickupKm!!, parsed.pickupMinutes!!, parsed.tripKm!!, parsed.tripMinutes!!),
                        DriverCostsStore.read(this)
                    ))
                }
            }

            if (now - lastDiagnosticAt >= DIAGNOSTIC_INTERVAL_MS) {
                lastDiagnosticAt = now
                val hasFare = OfferParser.containsFare(combined)
                val hasRoute = OfferParser.containsRouteLeg(combined)
                addDiagnostic("DIAGNOSTICO; evento=${event.eventType}; ventanas=${windowsSnapshot.size}; nodos=${counter.nodes}; " +
                    "nodosConTexto=${normalized.size}; textoAccesible=${normalized.isNotEmpty()}; contieneTarifa=$hasFare; " +
                    "contieneTramo=$hasRoute; ofertaDetectada=${parsed != null}; tipoOferta=${parsed?.let { if (it.hasRouteDetails) "detallada" else "compacta" } ?: "ninguna"}")
            }
        } catch (e: Exception) {
            addDiagnostic("ERROR DE LECTURA: ${e.javaClass.simpleName}")
        }
    }

    private fun fmt(value: Double) = String.format(Locale.US, "%.2f", value)
    private fun collectText(node: AccessibilityNodeInfo, output: MutableList<String>, counter: Counter, depth: Int) {
        if (depth > MAX_TREE_DEPTH || counter.nodes >= MAX_NODES) return
        counter.nodes++
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        for (i in 0 until node.childCount) {
            if (counter.nodes >= MAX_NODES) break
            val child = node.getChild(i) ?: continue
            try { collectText(child, output, counter, depth + 1) } finally { child.recycle() }
        }
    }
    private fun addDiagnostic(message: String) = LogStore.add(this, message)
    override fun onInterrupt() { LogStore.add(this, "SERVICIO INTERRUMPIDO") }
    override fun onDestroy() { LogStore.add(this, "SERVICIO DESCONECTADO"); super.onDestroy() }
    private class Counter(var nodes: Int = 0)
    companion object {
        private const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
        private const val MAX_TREE_DEPTH = 18
        private const val MAX_NODES = 1000
        private const val DIAGNOSTIC_INTERVAL_MS = 10000L
    }
}
