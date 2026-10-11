package com.viajerentable.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads Uber Driver accessibility text; stores only numeric fields and derived calculations. */
class OfferAccessibilityService : AccessibilityService() {
    private var lastDiagnosticAt = 0L
    private var lastOfferFingerprint = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogStore.add(this, "SERVICIO CONECTADO; captura estructurada local activada")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != UBER_DRIVER_PACKAGE) return
        val root = rootInActiveWindow
        if (root == null) {
            addDiagnostic("Uber detectado; ventana activa no disponible")
            return
        }
        try {
            val texts = mutableListOf<String>()
            val counter = Counter()
            collectText(root, texts, counter, 0)
            val normalized = texts.map { it.replace(Regex("\\s+"), " ").trim() }
                .filter { it.isNotEmpty() }.distinct()
            val parsed = OfferParser.parse(normalized.joinToString(" | "))
            val now = System.currentTimeMillis()
            if (parsed != null) {
                val fingerprint = listOf(parsed.fare, parsed.pickupKm, parsed.pickupMinutes, parsed.tripKm, parsed.tripMinutes).joinToString("|")
                if (fingerprint != lastOfferFingerprint) {
                    lastOfferFingerprint = fingerprint
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val result = Profitability.calculate(
                        Offer(parsed.fare, parsed.pickupKm, parsed.pickupMinutes, parsed.tripKm, parsed.tripMinutes),
                        DriverCosts()
                    )
                    LogStore.add(this,
                        "OFERTA ESTRUCTURADA [$timestamp]\n" +
                        "tarifaCRC=${fmt(parsed.fare)}; recogidaKm=${fmt(parsed.pickupKm)}; recogidaMin=${fmt(parsed.pickupMinutes)}; " +
                        "viajeKm=${fmt(parsed.tripKm)}; viajeMin=${fmt(parsed.tripMinutes)}\n" +
                        "recogidaLugar=${parsed.pickupPlace ?: "no detectado"}; destino=${parsed.destinationPlace ?: "no detectado"}\n" +
                        "totalKm=${fmt(result.totalKm)}; totalMin=${fmt(result.totalMinutes)}; combustibleCRC=${fmt(result.fuelCost)}; " +
                        "mantenimientoCRC=${fmt(result.maintenanceCost)}; netoCRC=${fmt(result.net)}; netoPorKmCRC=${fmt(result.netPerKm)}; " +
                        "netoPorHoraCRC=${fmt(result.netPerHour)}; recomendado=${result.recommended}"
                    )
                }
            }
            if (now - lastDiagnosticAt >= DIAGNOSTIC_INTERVAL_MS) {
                lastDiagnosticAt = now
                addDiagnostic("DIAGNOSTICO; evento=${event.eventType}; nodos=${counter.nodes}; nodosConTexto=${normalized.size}; " +
                    "textoAccesible=${normalized.isNotEmpty()}; ofertaEstructurada=${parsed != null}")
            }
        } catch (e: Exception) {
            addDiagnostic("ERROR DE LECTURA: ${e.javaClass.simpleName}")
        } finally {
            root.recycle()
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
        private const val MAX_NODES = 500
        private const val DIAGNOSTIC_INTERVAL_MS = 10000L
    }
}
