package com.viajerentable.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var screenView: TextView
    private lateinit var tripsView: TextView
    private lateinit var reasonInput: EditText
    private lateinit var diagnosticsView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val gap = (8 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        content.addView(TextView(this).apply {
            text = "Viaje Rentable 1.11"
            textSize = 25f
        })
        content.addView(TextView(this).apply {
            text = "Registro local de ofertas, viajes aceptados y datos visibles del vehículo."
            textSize = 15f
            setPadding(0, gap, 0, gap)
        })
        statusView = TextView(this).apply { textSize = 14f }
        content.addView(statusView)

        content.addView(Button(this).apply {
            text = "Abrir ajustes de accesibilidad"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        content.addView(Button(this).apply {
            text = "Actualizar datos de Uber"
            setOnClickListener { refreshViews() }
        })

        content.addView(sectionTitle("Última pantalla de Uber"))
        content.addView(TextView(this).apply {
            text = "Se muestra el texto que Uber expone a Accesibilidad; no es una captura de imagen."
            textSize = 12f
        })
        screenView = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(gap, gap, gap, gap)
        }
        content.addView(screenView)

        content.addView(sectionTitle("Guardar viaje aceptado"))
        content.addView(TextView(this).apply {
            text = "Cuando hayas aceptado un viaje y aparezcan los datos del carro en Uber, actualiza los datos y guarda el motivo."
            textSize = 13f
        })
        reasonInput = EditText(this).apply {
            hint = "Motivo de aceptación (por ejemplo: tarifa, zona, regreso)"
            minLines = 2
            gravity = android.view.Gravity.TOP
        }
        content.addView(reasonInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        content.addView(Button(this).apply {
            text = "Guardar como viaje aceptado"
            setOnClickListener {
                val snapshot = AcceptedTripStore.latestScreen(this@MainActivity)
                if (AcceptedTripStore.add(this@MainActivity, reasonInput.text.toString(), snapshot)) {
                    reasonInput.setText("")
                    refreshViews()
                    statusView.text = statusView.text.toString() + "\\nViaje guardado localmente."
                } else {
                    statusView.text = statusView.text.toString() + "\\nTodavía no hay texto de Uber para guardar. Abre Uber y actualiza."
                }
            }
        })

        content.addView(sectionTitle("Viajes aceptados guardados"))
        tripsView = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(gap, gap, gap, gap)
        }
        content.addView(tripsView)
        content.addView(Button(this).apply {
            text = "Borrar viajes aceptados"
            setOnClickListener { AcceptedTripStore.clear(this@MainActivity); refreshViews() }
        })

        content.addView(sectionTitle("Diagnóstico de captura"))
        diagnosticsView = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(gap, gap, gap, gap)
        }
        content.addView(diagnosticsView)
        content.addView(Button(this).apply {
            text = "Borrar diagnósticos"
            setOnClickListener { LogStore.clear(this@MainActivity); refreshViews() }
        })

        val scroll = ScrollView(this)
        scroll.addView(content)
        setContentView(scroll)
        refreshViews()
    }

    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized) refreshViews()
    }

    private fun sectionTitle(title: String) = TextView(this).apply {
        text = title
        textSize = 18f
        setPadding(0, 16, 0, 4)
    }

    private fun refreshViews() {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id?.contains(packageName) == true }
        statusView.text = "Servicio de accesibilidad habilitado: $enabled"
        screenView.text = AcceptedTripStore.latestScreen(this).ifBlank {
            "Todavía no hay texto capturado. Abre Uber Driver con una oferta o con los datos del vehículo visibles."
        }
        tripsView.text = AcceptedTripStore.read(this)
        diagnosticsView.text = LogStore.read(this)
    }
}
