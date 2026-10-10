package com.viajerentable.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads only text exposed by Uber Driver's accessibility tree.
 * It never clicks controls, accepts/declines trips, or changes Uber's state.
 * Offer text is kept locally in the app's private preferences.
 */
class OfferAccessibilityService : AccessibilityService() {
    private var lastDiagnosticAt = 0L
    private var lastTreeFingerprint = ""
    private var lastOfferFingerprint = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogStore.add(this, "SERVICIO CONECTADO; lectura local de ofertas habilitada")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != UBER_DRIVER_PACKAGE) return

        val root = rootInActiveWindow
        if (root == null) {
            addDiagnostic("UBER DETECTADO; ventana activa no disponible")
            return
        }

        val texts = mutableListOf<String>()
        var nodeCount = 0
        var textNodeCount = 0
        try {
            collectText(root, texts, Counter(), 0)
            // Normalize duplicates and whitespace, but preserve readable text values.
            val normalized = texts.map { it.replace(Regex("\\s+"), " ").trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            nodeCount = countNodes(root, 0)
            textNodeCount = normalized.size
            val treeText = normalized.joinToString(" | ")
            val fingerprint = treeText.hashCode().toString()

            // The tree may emit many content-changed events for the same screen.
            // Log only when its accessible text actually changes.
            if (treeText.isNotBlank() && fingerprint != lastTreeFingerprint) {
                lastTreeFingerprint = fingerprint
                val likelyOffer = isOfferLike(treeText)
                if (likelyOffer) {
                    if (fingerprint != lastOfferFingerprint) {
                        lastOfferFingerprint = fingerprint
                        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date())
                        LogStore.add(this, "OFERTA/TEXTO CAMBIADO [$timestamp]\n$treeText")
                    }
                } else {
                    addDiagnostic("UBER DETECTADO; cambio de pantalla sin señal clara de oferta")
                }
            }

            val now = System.currentTimeMillis()
            if (now - lastDiagnosticAt >= 5000) {
                lastDiagnosticAt = now
                addDiagnostic(
                    "UBER DETECTADO; evento=${event.eventType}; nodos=$nodeCount; " +
                        "nodosConTexto=$textNodeCount; textoAccesible=${textNodeCount > 0}; " +
                        "ofertaProbable=${isOfferLike(normalized.joinToString(" "))}"
                )
            }
        } catch (e: Exception) {
            addDiagnostic("ERROR DE LECTURA: ${e.javaClass.simpleName}")
        } finally {
            root.recycle()
        }
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

    private fun countNodes(node: AccessibilityNodeInfo, depth: Int): Int {
        if (depth > MAX_TREE_DEPTH) return 0
        var count = 1
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                count += countNodes(child, depth + 1)
            } finally {
                child.recycle()
            }
            if (count >= MAX_NODES) return MAX_NODES
        }
        return count
    }

    private fun isOfferLike(text: String): Boolean {
        val value = text.lowercase(Locale.ROOT)
        val hasMoney = value.contains("crc") || value.contains("₡") ||
            Regex("""(?:\\$|\\b\d[\d.,]*\\s*(?:colones|crc)\\b)""").containsMatchIn(value)
        val hasTripSignal = listOf(
            "km", "kilómetro", "kilometro", "min", "minuto", "viaje",
            "recogida", "destino", "ganancia", "oferta", "por completar",
            "por adelantado", "uberx", "uber comfort"
        ).any { value.contains(it) }
        return hasMoney && hasTripSignal
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
        private const val MAX_NODES = 500
    }
}
