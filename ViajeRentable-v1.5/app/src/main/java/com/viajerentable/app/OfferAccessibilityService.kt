package com.viajerentable.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class OfferAccessibilityService : AccessibilityService() {
    private var lastLogged = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogStore.add(this, "SERVICIO CONECTADO")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != "com.ubercab.driver") return
        val now = System.currentTimeMillis()
        if (now - lastLogged < 1000) return
        lastLogged = now
        val root = rootInActiveWindow ?: return
        val lines = mutableListOf<String>()
        collectText(root, lines, 0)
        root.recycle()
        val content = lines.distinct().filter { it.isNotBlank() }.take(80)
        if (content.isNotEmpty()) {
            LogStore.add(this, "UBER DETECTADO: " + content.joinToString(" | "))
        } else {
            LogStore.add(this, "UBER DETECTADO: ventana sin texto legible")
        }
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 18) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, out, depth + 1)
            child.recycle()
        }
    }

    override fun onInterrupt() {
        LogStore.add(this, "SERVICIO INTERRUMPIDO")
    }

    override fun onDestroy() {
        LogStore.add(this, "SERVICIO DESCONECTADO")
        super.onDestroy()
    }
}
