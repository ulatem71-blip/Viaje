package com.viajerentable.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads visible Uber Driver text, stores only parsed numeric offer fields, never clicks controls. */
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
            val treeText = normalized.joinToString(" | ")
            val parsed = parseOffer(treeText)
            val now = System.currentTimeMillis()

            if (parsed != null) {
                val fingerprint = listOf(parsed.fare, parsed.pickupKm, parsed.pickupMinutes,
                    parsed.tripKm, parsed.tripMinutes).joinToString("|")
                if (fingerprint != lastOfferFingerprint) {
                    lastOfferFingerprint = fingerprint
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val result = Profitability.calculate(
                        Offer(parsed.fare, parsed.pickupKm, parsed.pickupMinutes, parsed.tripKm, parsed.tripMinutes),
                        DriverCosts()
                    )
                    LogStore.add(this,
                        "OFERTA ESTRUCTURADA [$timestamp]\n" +
                        "tarifaCRC=${fmt(parsed.fare)}; recogidaKm=${fmt(parsed.pickupKm)}; " +
                        "recogidaMin=${fmt(parsed.pickupMinutes)}; viajeKm=${fmt(parsed.tripKm)}; " +
                        "viajeMin=${fmt(parsed.tripMinutes)}\n" +
                        "totalKm=${fmt(result.totalKm)}; totalMin=${fmt(result.totalMinutes)}; " +
                        "combustibleCRC=${fmt(result.fuelCost)}; mantenimientoCRC=${fmt(result.maintenanceCost)}; " +
                        "netoCRC=${fmt(result.net)}; netoPorKmCRC=${fmt(result.netPerKm)}; " +
                        "netoPorHoraCRC=${fmt(result.netPerHour)}; recomendado=${result.recommended}"
                    )
                }
            }

            if (now - lastDiagnosticAt >= DIAGNOSTIC_INTERVAL_MS) {
                lastDiagnosticAt = now
                addDiagnostic("DIAGNOSTICO; evento=${event.eventType}; nodos=${counter.nodes}; " +
                    "nodosConTexto=${normalized.size}; textoAccesible=${normalized.isNotEmpty()}; " +
                    "ofertaEstructurada=${parsed != null}")
            }
        } catch (e: Exception) {
            addDiagnostic("ERROR DE LECTURA: ${e.javaClass.simpleName}")
        } finally {
            root.recycle()
        }
    }

    private fun parseOffer(text: String): ParsedOffer? {
        val fareMatch = Regex("""(?:CRC\\s*|₡\\s*|\\$\\s*)([0-9][0-9,]*(?:\\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE).find(text) ?: return null
        val fare = fareMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val kmValues = Regex("""([0-9]+(?:[.,][0-9]+)?)\\s*km""", RegexOption.IGNORE_CASE)
            .findAll(text).mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }.toList()
        val minuteValues = Regex("""([0-9]{1,3})\\s*min(?:uto)?s?\\b""", RegexOption.IGNORE_CASE)
            .findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        if (kmValues.size < 2 || minuteValues.size < 2) return null
        val offer = ParsedOffer(fare, kmValues[0], minuteValues[0], kmValues[1], minuteValues[1])
        if (offer.fare <= 0 || offer.pickupKm < 0 || offer.tripKm <= 0 ||
            offer.pickupMinutes < 0 || offer.tripMinutes <= 0) return null
        return offer
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

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
    override fun onDestroy() {
        LogStore.add(this, "SERVICIO DESCONECTADO")
        super.onDestroy()
    }

    private data class ParsedOffer(val fare: Double, val pickupKm: Double, val pickupMinutes: Double,
        val tripKm: Double, val tripMinutes: Double)
    private class Counter(var nodes: Int = 0)

    companion object {
        private const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
        private const val MAX_TREE_DEPTH = 18
        private const val MAX_NODES = 500
        private const val DIAGNOSTIC_INTERVAL_MS = 10000L
    }
}
