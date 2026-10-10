package com.viajerentable.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads text exposed by Uber Driver's accessibility tree.
 * It never clicks controls or accepts/declines trips. Candidate offer text is
 * stored only in this app's private local log.
 */
class OfferAccessibilityService : AccessibilityService() {
    private var lastDiagnosticAt = 0L
    private var lastTreeFingerprint = ""
    private var lastOfferFingerprint = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogStore.add(this, "SERVICIO CONECTADO; captura local de texto candidato habilitada")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != UBER_DRIVER_PACKAGE) return

        val root = rootInActiveWindow
        if (root == null) {
            addDiagnostic("UBER DETECTADO; ventana activa no disponible")
            return
        }

        try {
            val texts = mutableListOf<String>()
            val counter = Counter()
            collectText(root, texts, counter, 0)
            val normalized = texts.map { it.replace(Regex("\\s+"), " ").trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val treeText = normalized.joinToString(" | ")
            val fingerprint = treeText.hashCode().toString()

            if (treeText.isNotBlank() && fingerprint != lastTreeFingerprint) {
                lastTreeFingerprint = fingerprint
                if (isOfferLike(treeText) && fingerprint != lastOfferFingerprint) {
                    lastOfferFingerprint = fingerprint
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date())
                    LogStore.add(this, "OFERTA CANDIDATA [$timestamp]\n$treeText")
                } else if (!isOfferLike(treeText)) {
                    addDiagnostic("UBER DETECTADO; texto cambió, sin señales suficientes de oferta")
                }
            }

            val now = System.currentTimeMillis()
            if (now - lastDiagnosticAt >= DIAGNOSTIC_INTERVAL_MS) {
                lastDiagnosticAt = now
                addDiagnostic(
                    "UBER DETECTADO; evento=${event.eventType}; nodos=${counter.nodes}; " +
                        "nodosConTexto=${normalized.size}; textoAccesible=${normalized.isNotEmpty()}; " +
                        "ofertaProbable=${isOfferLike(treeText)}"
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

    private fun isOfferLike(text: String): Boolean {
        val value = text.lowercase(Locale.ROOT)
        val hasCurrency = value.contains("crc") || value.contains("₡") ||
            value.contains("colones") || Regex("""\$\s*\d""").containsMatchIn(value)
        val hasTripSignal = listOf(
            " km", "kilómetro", "kilometro", "min.", " minutos", " minuto",
            "recogida", "destino", "ganancia", "oferta", "por completar",
            "por adelantado", "uberx", "uber comfort"
        ).any { value.contains(it) }
        return hasCurrency && hasTripSignal
    }

    private fun addDiagnostic(message: String) = LogStore.add(this, message)

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
        private const val DIAGNOSTIC_INTERVAL_MS = 5000L
    }
}
