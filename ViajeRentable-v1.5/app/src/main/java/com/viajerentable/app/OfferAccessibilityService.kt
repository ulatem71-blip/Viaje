package com.viajerentable.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class OfferAccessibilityService : AccessibilityService() {
    private var lastLogged = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Remove previous diagnostic entries, which may contain screen text.
        LogStore.clear(this)
        LogStore.add(this, "SERVICIO CONECTADO; registro seguro activado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != "com.ubercab.driver") return

        val now = System.currentTimeMillis()
        if (now - lastLogged < 1000) return
        lastLogged = now

        val root = rootInActiveWindow
        if (root == null) {
            LogStore.add(this, "UBER DETECTADO; ventana activa no disponible")
            return
        }

        val stats = TextStats()
        try {
            inspectTree(root, stats, 0)
        } finally {
            root.recycle()
        }

        // Never store the actual text, labels, addresses, or trip details.
        LogStore.add(
            this,
            "UBER DETECTADO; evento=${event.eventType}; nodos=${stats.nodes}; " +
                "nodosConTexto=${stats.nodesWithText}; textoAccesible=${stats.nodesWithText > 0}"
        )
    }

    private fun inspectTree(
        node: AccessibilityNodeInfo,
        stats: TextStats,
        depth: Int
    ) {
        if (depth > 18) return
        stats.nodes++
        val hasText = !node.text.isNullOrBlank() ||
            !node.contentDescription.isNullOrBlank()
        if (hasText) stats.nodesWithText++

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                inspectTree(child, stats, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    override fun onInterrupt() {
        LogStore.add(this, "SERVICIO INTERRUMPIDO")
    }

    override fun onDestroy() {
        LogStore.add(this, "SERVICIO DESCONECTADO")
        super.onDestroy()
    }

    private class TextStats {
        var nodes: Int = 0
        var nodesWithText: Int = 0
    }
}
