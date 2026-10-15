package com.viajerentable.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var systemPowerSwitch: Switch
    private lateinit var lastPowerChangeText: TextView
    private lateinit var pauseBanner: TextView
    private lateinit var latestOfferCard: LinearLayout
    private lateinit var offersPage: LinearLayout
    private lateinit var costsPage: LinearLayout
    private lateinit var diagnosticsPage: LinearLayout
    private lateinit var homePage: LinearLayout
    private lateinit var pageHost: FrameLayout
    private lateinit var tabs: LinearLayout

    private lateinit var kmPerLiterInput: EditText
    private lateinit var fuelPriceInput: EditText
    private lateinit var maintenanceInput: EditText
    private lateinit var minimumPerKmInput: EditText
    private lateinit var minimumPerHourInput: EditText
    private lateinit var maxPickupInput: EditText
    private lateinit var acPercentInput: EditText
    private lateinit var acSwitch: Switch
    private lateinit var demoSwitch: Switch
    private lateinit var treeDumpSwitch: Switch
    private lateinit var simulationInput: EditText
    private lateinit var simulationOutput: TextView
    private lateinit var liveDiagnosticText: TextView
    private lateinit var manualTripsText: TextView
    private lateinit var treeDumpText: TextView
    private lateinit var trialCountersText: TextView
    private lateinit var saveCostsButton: Button

    private val pageViews = mutableListOf<LinearLayout>()
    private val tabButtons = mutableListOf<LinearLayout>()
    private val pageNames = listOf("Inicio", "Ofertas", "Costos", "Diagnóstico")
    private var activePage = 0
    private var offerFilter = "Todas"
    private val palette: VisualTheme.Palette
        get() = VisualTheme.palette(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        loadCosts()
        showPage(0)
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            refreshAll()
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColor(android.R.attr.colorBackground))
        }

        val header = TextView(this).apply {
            text = "Viaje Rentable ${appVersion()}"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(themeColor(android.R.attr.textColorPrimary))
            setPadding(dp(20), dp(18), dp(20), dp(12))
        }
        root.addView(header)

        pauseBanner = TextView(this).apply {
            text = "EN PAUSA"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            minHeight = dp(28)
            background = roundedDrawable(palette.reject, 0)
            visibility = View.GONE
        }
        root.addView(pauseBanner, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(28)
        ))

        pageHost = FrameLayout(this)
        root.addView(pageHost, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        homePage = createPage()
        offersPage = createPage()
        costsPage = createPage()
        diagnosticsPage = createPage()
        pageViews.addAll(listOf(homePage, offersPage, costsPage, diagnosticsPage))
        for (page in pageViews) {
            pageHost.addView(page, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            page.visibility = View.GONE
        }

        buildHomePage()
        buildOffersPage()
        buildCostsPage()
        buildDiagnosticsPage()
        buildBottomNavigation(root)
        setContentView(root)
    }

    private fun createPage(): LinearLayout {
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(28))
        }
        scroll.addView(content)
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        wrapper.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        return wrapper
    }

    private fun pageContent(page: LinearLayout): LinearLayout {
        val scroll = page.getChildAt(0) as ScrollView
        return scroll.getChildAt(0) as LinearLayout
    }

    private fun buildHomePage() {
        val content = pageContent(homePage)
        val powerCard = card().apply {
            tag = "master_power_card"
        }
        systemPowerSwitch = Switch(this).apply {
            textSize = 20f
            minHeight = dp(72)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isChecked = SystemPowerStore.isOn(this@MainActivity)
            text = if (isChecked) "Sistema ENCENDIDO" else "Sistema APAGADO"
            setOnCheckedChangeListener { _, checked ->
                SystemPowerStore.setOn(this@MainActivity, checked)
                refreshAll()
            }
        }
        powerCard.addView(systemPowerSwitch)
        lastPowerChangeText = bodyText("")
        lastPowerChangeText.textSize = 12f
        powerCard.addView(lastPowerChangeText)
        content.addView(powerCard)
        content.addView(sectionTitle("Servicio"))
        statusText = TextView(this).apply {
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            contentDescription = "Estado del servicio de accesibilidad"
        }
        val serviceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        serviceRow.addView(statusText)
        serviceRow.addView(actionButton("Ajustes", primary = false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        content.addView(serviceRow)

        content.addView(sectionTitle("Última oferta"))
        latestOfferCard = card()
        content.addView(latestOfferCard)

        content.addView(sectionTitle("Interruptores"))
        val switchesCard = card()
        demoSwitch = Switch(this).apply {
            text = "Anotar decisiones simuladas"
            textSize = 18f
            minHeight = dp(52)
            isChecked = DemoModeStore.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                DemoModeStore.setEnabled(this@MainActivity, checked)
                refreshAll()
            }
        }
        switchesCard.addView(demoSwitch)
        treeDumpSwitch = Switch(this).apply {
            text = "Capturar estructura de pantalla"
            textSize = 18f
            minHeight = dp(52)
            isChecked = ScreenTreeDumpStore.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                ScreenTreeDumpStore.setEnabled(this@MainActivity, checked)
                showToast(if (checked) "Captura activada" else "Captura desactivada")
            }
        }
        switchesCard.addView(treeDumpSwitch)
        content.addView(switchesCard)

        content.addView(sectionTitle("Contadores de hoy"))
        val dailyCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            tag = "daily_counter_row"
        }
        content.addView(dailyCard)
    }

    private fun buildOffersPage() {
        val content = pageContent(offersPage)
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(sectionTitle("Ofertas registradas"), LinearLayout.LayoutParams(0, dp(48), 1f))
        heading.addView(actionButton("Exportar CSV", primary = false) { exportCsv() })
        content.addView(heading)
        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            tag = "offer_filters"
        }
        for (filter in listOf("Todas", "Aceptaría", "Rechazaría")) {
            val chip = actionButton(filter, primary = false) {
                offerFilter = filter
                refreshOffers()
            }
            chip.tag = filter
            filters.addView(chip, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(4)
            })
        }
        content.addView(filters)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "offers_list"
        }
        content.addView(list)
    }

    private fun buildCostsPage() {
        val content = pageContent(costsPage)
        content.addView(sectionTitle("Vehículo"))
        val vehicleCard = card()
        kmPerLiterInput = labeledInput(vehicleCard, "Consumo del vehículo (km/L)", "18")
        fuelPriceInput = labeledInput(vehicleCard, "Precio del combustible (₡/L)", "720")
        maintenanceInput = labeledInput(vehicleCard, "Mantenimiento (₡/km)", "25")
        content.addView(vehicleCard)

        content.addView(sectionTitle("Mínimos"))
        val minimumCard = card()
        minimumPerKmInput = labeledInput(minimumCard, "Neto mínimo (₡/km)", "250")
        minimumPerHourInput = labeledInput(minimumCard, "Neto mínimo (₡/hora)", "2500")
        content.addView(minimumCard)

        content.addView(sectionTitle("Recogida"))
        val pickupCard = card()
        maxPickupInput = labeledInput(pickupCard, "Distancia máxima de recogida (km)", "2.0")
        content.addView(pickupCard)

        content.addView(sectionTitle("Aire acondicionado"))
        val acCard = card()
        acSwitch = Switch(this).apply {
            text = "A/C encendido"
            textSize = 18f
            minHeight = dp(52)
        }
        acCard.addView(acSwitch)
        acPercentInput = labeledInput(acCard, "Consumo extra con A/C (%)", "10")
        val rangeHelp = bodyText("Rangos: 0–50 % de consumo extra; recogida 0,1–20 km.")
        acCard.addView(rangeHelp)
        content.addView(acCard)

        val resultCard = card()
        resultCard.addView(TextView(this).apply {
            tag = "fuel_cost_preview"
            textSize = 18f
            setPadding(dp(12), dp(10), dp(12), dp(10))
        })
        content.addView(resultCard)

        saveCostsButton = actionButton("Guardar", primary = true) { saveCosts() }
        costsPage.addView(saveCostsButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(56)
        ).apply {
            setMargins(dp(16), dp(6), dp(16), dp(12))
        })
    }

    private fun buildDiagnosticsPage() {
        val content = pageContent(diagnosticsPage)
        content.addView(sectionTitle("Simulador de oferta"))
        val simulatorCard = card()
        simulationInput = EditText(this).apply {
            hint = "Pega el texto de una oferta"
            textSize = 18f
            minLines = 3
            maxLines = 6
            gravity = Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        simulatorCard.addView(simulationInput)
        simulatorCard.addView(actionButton("Usar ejemplo") {
            simulationInput.setText("UberX | CRC1,404.33 | A 7 min (1.4 km) | Viaje: 10 min (2.7 km) | Aceptar")
        })
        simulatorCard.addView(actionButton("Simular", primary = true) { simulateOffer() })
        simulationOutput = bodyText("")
        simulatorCard.addView(simulationOutput)
        content.addView(simulatorCard)

        content.addView(sectionTitle("Diagnóstico en vivo"))
        val diagnosticCard = card()
        liveDiagnosticText = bodyText("")
        diagnosticCard.addView(liveDiagnosticText)
        diagnosticCard.addView(actionButton("Copiar diagnóstico") {
            copyToClipboard("Diagnóstico", LogStore.read(this))
        })
        diagnosticCard.addView(actionButton("Borrar diagnóstico") {
            LogStore.clear(this)
            refreshDiagnostics()
        })
        content.addView(diagnosticCard)

        content.addView(sectionTitle("Viajes aceptados manualmente"))
        val tripCard = card()
        val reasonInput = EditText(this).apply {
            hint = "Motivo (opcional)"
            textSize = 18f
            minLines = 2
        }
        tripCard.addView(reasonInput)
        tripCard.addView(actionButton("Guardar viaje aceptado") {
            val screen = AcceptedTripStore.latestScreen(this)
            if (AcceptedTripStore.add(this, reasonInput.text.toString(), screen)) {
                reasonInput.setText("")
                showToast("Viaje guardado")
                refreshDiagnostics()
            } else {
                showToast("Todavía no hay datos visibles de Uber")
            }
        })
        manualTripsText = bodyText("")
        tripCard.addView(manualTripsText)
        tripCard.addView(actionButton("Borrar viajes aceptados") {
            AcceptedTripStore.clear(this)
            refreshDiagnostics()
        })
        content.addView(tripCard)

        content.addView(sectionTitle("Volcado de estructura de pantalla"))
        val treeCard = card()
        treeDumpText = bodyText("")
        treeCard.addView(treeDumpText)
        treeCard.addView(actionButton("Copiar último volcado") {
            copyToClipboard("Volcado de pantalla", ScreenTreeDumpStore.lastDump(this))
        })
        content.addView(treeCard)

        content.addView(sectionTitle("Contadores del ensayo"))
        val trialCard = card()
        trialCountersText = bodyText("")
        trialCard.addView(trialCountersText)
        trialCard.addView(actionButton("Reiniciar contadores") {
            OfferButtonTrialStore.reset(this)
            refreshDiagnostics()
        })
        content.addView(trialCard)
        collapseDiagnosticSections(content)
        simulationOutput.typeface = Typeface.MONOSPACE
        simulationOutput.textSize = 12f
        liveDiagnosticText.typeface = Typeface.MONOSPACE
        liveDiagnosticText.textSize = 12f
        manualTripsText.typeface = Typeface.MONOSPACE
        manualTripsText.textSize = 12f
        treeDumpText.typeface = Typeface.MONOSPACE
        treeDumpText.textSize = 12f
        trialCountersText.typeface = Typeface.MONOSPACE
        trialCountersText.textSize = 12f
    }

    private fun collapseDiagnosticSections(content: LinearLayout) {
        var index = 0
        while (index + 1 < content.childCount) {
            val heading = content.getChildAt(index) as? TextView
            val section = content.getChildAt(index + 1)
            if (heading == null || section !is LinearLayout) {
                index += 1
                continue
            }
            content.removeViewAt(index + 1)
            content.removeViewAt(index)
            val group = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            heading.text = "▸  ${heading.text}"
            heading.minHeight = dp(48)
            heading.setPadding(dp(8), dp(10), dp(8), dp(10))
            heading.background = roundedDrawable(palette.card, 12, palette.border, 1.5f)
            section.visibility = View.GONE
            heading.setOnClickListener {
                val opening = section.visibility != View.VISIBLE
                section.visibility = if (opening) View.VISIBLE else View.GONE
                heading.text = (if (opening) "▾  " else "▸  ") + heading.text.toString().removePrefix("▸  ").removePrefix("▾  ")
            }
            group.addView(heading)
            group.addView(section)
            group.setPadding(0, 0, 0, dp(8))
            content.addView(group, index)
            index += 1
        }
    }

    private fun showPage(index: Int) {
        activePage = index.coerceIn(0, pageViews.lastIndex)
        for (i in pageViews.indices) {
            pageViews[i].clearAnimation()
            pageViews[i].visibility = if (i == activePage) View.VISIBLE else View.GONE
            tabButtons[i].isSelected = i == activePage
            tabButtons[i].getChildAt(0).isSelected = i == activePage
            tabButtons[i].background = roundedDrawable(
                if (i == activePage) palette.brand else palette.card,
                12,
                if (i == activePage) palette.brand else palette.border,
                1.5f
            )
            val label = tabButtons[i].getChildAt(1) as TextView
            label.setTextColor(if (i == activePage) palette.darkText else palette.muted)
        }
        pageViews[activePage].startAnimation(AlphaAnimation(0.86f, 1f).apply {
            duration = 200
            fillAfter = false
        })
        refreshAll()
    }

    private fun loadCosts() {
        val costs = DriverCostsStore.read(this)
        kmPerLiterInput.setText(costs.kmPerLiter.toString())
        fuelPriceInput.setText(costs.fuelPricePerLiter.toString())
        maintenanceInput.setText(costs.maintenancePerKm.toString())
        minimumPerKmInput.setText(costs.minimumNetPerKm.toString())
        minimumPerHourInput.setText(costs.minimumNetPerHour.toString())
        maxPickupInput.setText(costs.maxPickupKm.toString())
        acSwitch.isChecked = costs.acEnabled
        acPercentInput.setText(costs.acExtraPercent.toString())
        updateFuelCostPreview()
        acSwitch.setOnCheckedChangeListener { _, _ -> updateFuelCostPreview() }
        acPercentInput.addTextChangedListener(SimpleTextWatcher { updateFuelCostPreview() })
        maxPickupInput.addTextChangedListener(SimpleTextWatcher { updateFuelCostPreview() })
        fuelPriceInput.addTextChangedListener(SimpleTextWatcher { updateFuelCostPreview() })
        kmPerLiterInput.addTextChangedListener(SimpleTextWatcher { updateFuelCostPreview() })
    }

    private fun readCostsFromFields(): DriverCosts? {
        val kmPerLiter = kmPerLiterInput.text.toString().toDoubleOrNull()
        val fuelPrice = fuelPriceInput.text.toString().toDoubleOrNull()
        val maintenance = maintenanceInput.text.toString().toDoubleOrNull()
        val minimumPerKm = minimumPerKmInput.text.toString().toDoubleOrNull()
        val minimumPerHour = minimumPerHourInput.text.toString().toDoubleOrNull()
        val maxPickup = maxPickupInput.text.toString().toDoubleOrNull()
        val acPercent = acPercentInput.text.toString().toDoubleOrNull()

        if (kmPerLiter == null || kmPerLiter <= 0.0 ||
            fuelPrice == null || fuelPrice < 0.0 ||
            maintenance == null || maintenance < 0.0 ||
            minimumPerKm == null || minimumPerKm < 0.0 ||
            minimumPerHour == null || minimumPerHour < 0.0 ||
            maxPickup == null || maxPickup !in 0.1..20.0 ||
            acPercent == null || acPercent !in 0.0..50.0
        ) {
            return null
        }

        return DriverCosts(
            kmPerLiter = kmPerLiter,
            fuelPricePerLiter = fuelPrice,
            maintenancePerKm = maintenance,
            minimumNetPerHour = minimumPerHour,
            minimumNetPerKm = minimumPerKm,
            acEnabled = acSwitch.isChecked,
            acExtraPercent = acPercent,
            maxPickupKm = maxPickup
        )
    }

    private fun saveCosts() {
        val costs = readCostsFromFields()
        if (costs == null) {
            showToast("Revisa los valores y sus rangos.")
            return
        }
        DriverCostsStore.save(this, costs)
        updateFuelCostPreview()
        showToast("Guardado")
        refreshAll()
    }

    private fun updateFuelCostPreview() {
        if (!::kmPerLiterInput.isInitialized) {
            return
        }
        val kmPerLiter = kmPerLiterInput.text.toString().toDoubleOrNull()
        val fuelPrice = fuelPriceInput.text.toString().toDoubleOrNull()
        val acPercent = acPercentInput.text.toString().toDoubleOrNull()
        val multiplier = if (::acSwitch.isInitialized && acSwitch.isChecked) {
            1.0 + (acPercent ?: 10.0).coerceIn(0.0, 50.0) / 100.0
        } else {
            1.0
        }
        val value = if (kmPerLiter != null && kmPerLiter > 0.0 && fuelPrice != null) {
            fuelPrice / kmPerLiter * multiplier
        } else {
            null
        }
        val preview = costsPage.findViewWithTag<TextView>("fuel_cost_preview")
        preview?.text = value?.let { "Costo de combustible resultante: ₡${fmt(it)}/km" }
            ?: "Introduce consumo y precio válidos para calcular el costo por km."
    }

    private fun simulateOffer() {
        val parsed = OfferParser.parse(simulationInput.text.toString())
        if (parsed == null) {
            simulationOutput.text = "No se detectó una oferta válida. Incluye tarifa y el texto Aceptar o Me interesa."
            return
        }
        val costs = readCostsFromFields()
        if (costs == null) {
            simulationOutput.text = "Revisa los costos configurables antes de simular."
            return
        }
        if (!parsed.hasRouteDetails) {
            val demo = DemoDecision.evaluate(parsed, null, costs)
            simulationOutput.text = "Tarifa: ₡${fmt(parsed.fare)}\nNeto: sin datos\nNeto/km: sin datos\nNeto/hora: sin datos\nDecisión: ${demo.decision}\nMotivos: ${demo.reasons.joinToString("; ")}"
            return
        }
        val result = calculate(parsed, costs)
        val demo = DemoDecision.evaluate(parsed, result, costs)
        simulationOutput.text = "Tarifa: ₡${fmt(parsed.fare)}\nNeto: ₡${fmt(result.net)}\nNeto/km: ₡${fmt(result.netPerKm)}\nNeto/hora: ₡${fmt(result.netPerHour)}\nA/C: ${if (costs.acEnabled) "sí" else "no"}\nDecisión: ${demo.decision}\nMotivos: ${demo.reasons.joinToString("; ")}"
    }

    private fun calculate(parsed: ParsedOffer, costs: DriverCosts): ProfitResult {
        return Profitability.calculate(
            Offer(
                parsed.fare,
                parsed.pickupKm!!,
                parsed.pickupMinutes!!,
                parsed.tripKm!!,
                parsed.tripMinutes!!
            ),
            costs
        )
    }

    private fun refreshAll() {
        refreshHome()
        refreshOffers()
        refreshDiagnostics()
        updateFuelCostPreview()
    }

    private fun refreshHome() {
        updatePowerPresentation()
        val systemOn = SystemPowerStore.isOn(this)
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabled = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { it.id?.contains(packageName) == true }
        val statusColor = if (systemOn && enabled) palette.accept else palette.reject
        statusText.text = if (!systemOn) "● En pausa" else if (enabled) "● Servicio activo" else "● Servicio apagado"
        statusText.setTextColor(statusColor)
        statusText.background = roundedDrawable(palette.card, 18, palette.border, 1.5f)
        statusText.setPadding(dp(10), dp(5), dp(10), dp(5))
        statusText.minHeight = dp(36)

        latestOfferCard.removeAllViews()
        val latest = OffersStore.read(this).firstOrNull()
        if (!systemOn) {
            latestOfferCard.background = roundedDrawable(palette.card, 16, palette.reject, 2f)
            latestOfferCard.addView(TextView(this).apply {
                text = "En pausa"
                textSize = 28f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.reject)
                setPadding(dp(8), dp(12), dp(8), dp(12))
            })
        } else if (latest == null) {
            latestOfferCard.addView(bodyText("Todavía no hay ofertas registradas."))
        } else {
            val parsed = latest.parsed
            val decision = latest.decision ?: Decision.SIN_DATOS
            latestOfferCard.background = roundedDrawable(palette.card, 16, decisionColor(decision), 2f)
            latestOfferCard.addView(TextView(this).apply {
                text = "₡${fmt(parsed.fare)}"
                textSize = 32f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
                contentDescription = "Tarifa ${fmt(parsed.fare)}"
            })
            latestOfferCard.addView(bodyText("Hora: ${latest.timestamp}", small = true))
            latestOfferCard.addView(bodyText(
                "Recogida: ${parsed.pickupKm?.let { fmt(it) + " km" } ?: "Sin datos"}  ·  Viaje: ${parsed.tripKm?.let { fmt(it) + " km" } ?: "Sin datos"}"
            ))
            latestOfferCard.addView(decisionBadge(decision))
            latest.result?.let { result ->
                val costs = DriverCostsStore.read(this)
                latestOfferCard.addView(progressMetric("Neto por km", result.netPerKm, costs.minimumNetPerKm))
                latestOfferCard.addView(progressMetric("Neto por hora", result.netPerHour, costs.minimumNetPerHour))
            }
            if (latest.reasons.isNotEmpty()) {
                latestOfferCard.addView(bodyText(latest.reasons.joinToString("\n"), small = true))
            }
            latestOfferCard.startAnimation(AlphaAnimation(0.82f, 1f).apply {
                duration = 200
                fillAfter = true
            })
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val daily = OffersStore.read(this).filter { it.timestamp.startsWith(today) }
        val accepted = daily.count { it.decision == Decision.ACEPTARIA }
        val rejected = daily.count { it.decision == Decision.RECHAZARIA }
        val counters = homePage.findViewWithTag<LinearLayout>("daily_counter_row")
        counters?.removeAllViews()
        if (counters != null) {
            counters.addView(counterCard("VISTAS", daily.size, palette.brand), weightedParams())
            counters.addView(counterCard("ACEPTARÍA", accepted, palette.accept), weightedParams())
            counters.addView(counterCard("RECHAZARÍA", rejected, palette.reject), weightedParams())
        }
        if (::demoSwitch.isInitialized) {
            demoSwitch.isChecked = DemoModeStore.isEnabled(this)
        }
        if (::treeDumpSwitch.isInitialized) {
            treeDumpSwitch.isChecked = ScreenTreeDumpStore.isEnabled(this)
        }
    }

    private fun updatePowerPresentation() {
        if (!::systemPowerSwitch.isInitialized || !::pauseBanner.isInitialized) {
            return
        }
        val isOn = SystemPowerStore.isOn(this)
        systemPowerSwitch.setOnCheckedChangeListener(null)
        systemPowerSwitch.isChecked = isOn
        val powerColor = if (isOn) palette.accept else palette.reject
        systemPowerSwitch.text = if (isOn) "●  Sistema ENCENDIDO" else "●  Sistema APAGADO"
        systemPowerSwitch.setTextColor(Color.WHITE)
        systemPowerSwitch.background = roundedDrawable(powerColor, 18, powerColor, 2f)
        systemPowerSwitch.setOnCheckedChangeListener { _, checked ->
            SystemPowerStore.setOn(this, checked)
            refreshAll()
        }
        val changedAt = SystemPowerStore.lastChangedAt(this)
        val formatted = if (changedAt > 0L) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(changedAt))
        } else {
            "Sin cambios registrados"
        }
        if (::lastPowerChangeText.isInitialized) {
            lastPowerChangeText.text = "Último cambio: $formatted"
        }
        pauseBanner.visibility = if (isOn) View.GONE else View.VISIBLE
        pageContent(homePage).findViewWithTag<LinearLayout>("master_power_card")?.background =
            roundedDrawable(powerColor, 18, powerColor, 2f)
        if (::lastPowerChangeText.isInitialized) {
            lastPowerChangeText.setTextColor(Color.WHITE)
        }
    }

    private fun refreshOffers() {
        val list = offersPage.findViewWithTag<LinearLayout>("offers_list") ?: return
        list.removeAllViews()
        val allOffers = OffersStore.read(this)
        val offers = when (offerFilter) {
            "Aceptaría" -> allOffers.filter { it.decision == Decision.ACEPTARIA }
            "Rechazaría" -> allOffers.filter { it.decision == Decision.RECHAZARIA }
            else -> allOffers
        }
        val filters = offersPage.findViewWithTag<LinearLayout>("offer_filters")
        for (i in 0 until (filters?.childCount ?: 0)) {
            val chip = filters?.getChildAt(i) as? Button ?: continue
            styleButton(chip, chip.tag == offerFilter)
        }
        if (offers.isEmpty()) {
            list.addView(bodyText(if (allOffers.isEmpty()) "Aún no hay ofertas. Cuando llegue una, aparecerá aquí." else "No hay ofertas para este filtro. Prueba otra opción."))
            return
        }
        for (offer in offers) {
            val decision = offer.decision ?: Decision.SIN_DATOS
            val color = decisionColor(decision)
            val outer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = roundedDrawable(palette.card, 16, color, 2f)
                clipToOutline = true
            }
            outer.addView(View(this).apply {
                setBackgroundColor(color)
                contentDescription = "Color de decisión"
            }, LinearLayout.LayoutParams(dp(8), ViewGroup.LayoutParams.MATCH_PARENT))
            val offerCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            offerCard.addView(TextView(this).apply {
                text = "₡${fmt(offer.parsed.fare)}"
                textSize = 32f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
            })
            offerCard.addView(bodyText(offer.timestamp + "\n" +
                "Recogida: ${offer.parsed.pickupKm?.let { fmt(it) + " km" } ?: "Sin datos"}  ·  " +
                "Viaje: ${offer.parsed.tripKm?.let { fmt(it) + " km" } ?: "Sin datos"}"))
            offerCard.addView(decisionBadge(decision))
            if (offer.result != null) {
                offerCard.addView(bodyText("Neto/km ₡${fmt(offer.result.netPerKm)}  ·  Neto/hora ₡${fmt(offer.result.netPerHour)}"))
            }
            if (offer.reasons.isNotEmpty()) {
                offerCard.addView(bodyText("Motivos: ${offer.reasons.joinToString("; ")}", small = true))
            }
            outer.addView(offerCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            list.addView(outer, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) })
        }
    }

    private fun refreshDiagnostics() {
        if (!::liveDiagnosticText.isInitialized) {
            return
        }
        liveDiagnosticText.text = LogStore.read(this)
        manualTripsText.text = AcceptedTripStore.read(this)
        treeDumpText.text = ScreenTreeDumpStore.lastDump(this)
            .ifBlank { "Todavía no hay un volcado guardado." }
        val counters = OfferButtonTrialStore.read(this)
        trialCountersText.text =
            "ACEPTARÍA con botón localizado: ${counters.acceptedWithButton}\n" +
                "ACEPTARÍA sin botón: ${counters.acceptedWithoutButton}\n" +
                "Tarjetas ambiguas: ${counters.ambiguous}\nDiscrepancias: ${counters.discrepancies}"
    }

    private fun decisionBadge(decision: Decision): TextView {
        val color = decisionColor(decision)
        val label = when (decision) {
            Decision.ACEPTARIA -> "ACEPTARÍA"
            Decision.RECHAZARIA -> "RECHAZARÍA"
            Decision.SIN_DATOS -> "SIN DATOS"
        }
        return TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.darkText)
            gravity = Gravity.CENTER
            minHeight = dp(40)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = roundedDrawable(color, 22, color, 2f)
        }
    }

    private fun card(
        borderColor: Int = palette.border,
        borderWidth: Float = 1.5f
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedDrawable(palette.card, 16, borderColor, borderWidth)
            elevation = 0f
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(12)
            layoutParams = params
        }
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text.uppercase(Locale.getDefault())
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.08f
            setTextColor(palette.muted)
            setPadding(0, dp(18), 0, dp(8))
        }
    }

    private fun bodyText(text: String, small: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = if (small) 12f else 16f
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextColor(palette.text)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setTextIsSelectable(true)
        }
    }

    private fun labeledInput(parent: LinearLayout, label: String, default: String): EditText {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(palette.muted)
            setPadding(dp(4), dp(8), dp(4), dp(2))
        })
        val field = EditText(this).apply {
            setText(default)
            textSize = 16f
            minHeight = dp(52)
            setTextColor(palette.text)
            setHintTextColor(palette.muted)
            background = roundedDrawable(palette.card, 12, palette.border, 1.5f)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        parent.addView(field, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return field
    }

    private fun actionButton(
        label: String,
        primary: Boolean = false,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            minHeight = dp(52)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { action() }
            styleButton(this, primary)
        }
    }

    private fun styleButton(button: Button, selected: Boolean) {
        button.background = roundedDrawable(
            if (selected) palette.brand else Color.TRANSPARENT,
            14,
            palette.brand,
            2f
        )
        button.setTextColor(if (selected) palette.darkText else palette.brand)
        button.stateListAnimator = null
        button.elevation = 0f
    }

    private fun roundedDrawable(
        color: Int,
        radiusDp: Int,
        strokeColor: Int = palette.border,
        strokeWidthDp: Float = 1.5f
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke((strokeWidthDp * resources.displayMetrics.density).toInt().coerceAtLeast(1), strokeColor)
        }
    }

    private fun themeColor(attribute: Int): Int {
        return when (attribute) {
            android.R.attr.colorBackground -> palette.background
            android.R.attr.colorBackgroundFloating -> palette.card
            android.R.attr.textColorPrimary -> palette.text
            else -> palette.text
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        if (text.isBlank()) {
            showToast("No hay contenido para copiar")
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        showToast("Copiado")
    }

    private fun exportCsv() {
        val file = File(cacheDir, "ofertas_viaje_rentable.csv")
        file.writeText(OffersStore.csv(this), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            this,
            "com.viajerentable.app.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Ofertas registradas")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Compartir CSV"))
    }

    private fun appVersion(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    private fun fmt(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun weightedParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(8)
        }
    }

    private fun counterCard(label: String, value: Int, color: Int): LinearLayout {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = roundedDrawable(palette.card, 16, color, 2f)
        }
        tile.addView(TextView(this).apply {
            text = value.toString()
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color)
            gravity = Gravity.CENTER
        })
        tile.addView(TextView(this).apply {
            text = label
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.muted)
            gravity = Gravity.CENTER
        })
        return tile
    }

    private fun progressMetric(label: String, value: Double, minimum: Double): LinearLayout {
        val met = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        val color = if (value >= minimum) palette.accept else palette.reject
        met.addView(TextView(this).apply {
            text = label.uppercase(Locale.getDefault())
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.muted)
        })
        met.addView(TextView(this).apply {
            text = "₡${fmt(value)}"
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color)
            contentDescription = "$label ${fmt(value)}"
        })
        met.addView(TextView(this).apply {
            text = "Mínimo: ₡${fmt(minimum)}"
            textSize = 12f
            setTextColor(palette.muted)
        })
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = if (minimum > 0.0) ((value / minimum) * 100).toInt().coerceIn(0, 100) else 100
            progressTintList = android.content.res.ColorStateList.valueOf(color)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(palette.border)
            contentDescription = "$label frente al mínimo"
            background = roundedDrawable(palette.card, 6, palette.border, 1.5f)
        }
        met.addView(progress, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(14)
        ))
        return met
    }

    private fun decisionColor(decision: Decision): Int {
        return when (decision) {
            Decision.ACEPTARIA -> palette.accept
            Decision.RECHAZARIA -> palette.reject
            Decision.SIN_DATOS -> palette.neutral
        }
    }

    private fun buildBottomNavigation(root: LinearLayout) {
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = roundedDrawable(palette.card, 16, palette.border, 1.5f)
        }
        for ((index, name) in pageNames.withIndex()) {
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(58)
                contentDescription = name
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setOnClickListener { showPage(index) }
            }
            tab.addView(NavGlyphView(this, index).apply {
                contentDescription = "$name, pestaña"
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            tab.addView(TextView(this).apply {
                text = name
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.muted)
            })
            tabButtons.add(tab)
            navigation.addView(tab, LinearLayout.LayoutParams(0, dp(64), 1f))
        }
        root.addView(navigation, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(80)
        ).apply {
            setMargins(dp(12), dp(4), dp(12), dp(8))
        })
    }
}

private class SimpleTextWatcher(
    private val onChanged: () -> Unit
) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        onChanged()
    }

    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
