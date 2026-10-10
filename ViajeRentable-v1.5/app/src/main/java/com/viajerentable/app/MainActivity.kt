package com.viajerentable.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        layout.addView(TextView(this).apply {
            text = "Viaje Rentable 1.6\nCaptura de ofertas por accesibilidad"
            textSize = 23f
        })
        layout.addView(TextView(this).apply {
            text = "Lee texto que Uber Driver expone a accesibilidad y guarda localmente cambios que parecen ofertas. No pulsa botones ni acepta/rechaza viajes. El registro puede contener detalles visibles de una oferta; se conserva solo en este dispositivo y puedes borrarlo aquí."
            textSize = 16f
            setPadding(0, pad, 0, pad)
        })
        layout.addView(Button(this).apply {
            text = "Abrir ajustes de accesibilidad"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        layout.addView(Button(this).apply {
            text = "Actualizar registro"
            setOnClickListener { refreshLog() }
        })
        layout.addView(Button(this).apply {
            text = "Borrar registro"
            setOnClickListener { LogStore.clear(this@MainActivity); refreshLog() }
        })
        logView = TextView(this).apply { textSize = 13f; setTextIsSelectable(true) }
        val scroll = ScrollView(this)
        scroll.addView(logView)
        layout.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        refreshLog()
    }

    override fun onResume() {
        super.onResume()
        if (::logView.isInitialized) refreshLog()
    }

    private fun refreshLog() {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id?.contains(packageName) == true }
        logView.text = "Servicio habilitado: $enabled\n\n" + LogStore.read(this)
    }
}
