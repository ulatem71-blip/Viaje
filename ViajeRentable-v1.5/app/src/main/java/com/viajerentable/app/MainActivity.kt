package com.viajerentable.app
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
class MainActivity:Activity(){
 private lateinit var status:TextView;private lateinit var screen:TextView;private lateinit var treeDumpView:TextView;private lateinit var treeDumpSwitch:Switch;private lateinit var trips:TextView;private lateinit var offers:TextView;private lateinit var reason:EditText;private lateinit var logs:TextView
 private lateinit var kmL:EditText;private lateinit var fuel:EditText;private lateinit var maint:EditText;private lateinit var hour:EditText;private lateinit var km:EditText;private lateinit var demoSwitch:Switch;private lateinit var simulationInput:EditText;private lateinit var simulationOutput:TextView;private lateinit var buttonTrialCounters:TextView;private lateinit var autoAcceptanceSwitch:Switch;private lateinit var pickupMax:EditText;private lateinit var auditView:TextView;private var updatingAcceptanceSwitch=false
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val pad=(16*resources.displayMetrics.density).toInt();val c=LinearLayout(this).apply{orientation=1;setPadding(pad,pad,pad,pad)}
 c.addView(TextView(this).apply{text="Viaje Rentable ${appVersion()}";textSize=25f});status=TextView(this);c.addView(status)
 c.addView(Button(this).apply{text="Abrir ajustes de accesibilidad";setOnClickListener{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}})
 c.addView(Button(this).apply{text="Actualizar datos de Uber";setOnClickListener{refresh()}})
 c.addView(title("Última pantalla de Uber"));c.addView(TextView(this).apply{text="Texto accesible de Uber, no una captura de imagen."});screen=TextView(this).apply{setTextIsSelectable(true)};c.addView(screen)
 c.addView(title("Volcado de estructura de pantalla"));treeDumpSwitch=Switch(this).apply{text="Capturar estructura de pantalla";isChecked=ScreenTreeDumpStore.isEnabled(this@MainActivity);setOnCheckedChangeListener{_,checked->ScreenTreeDumpStore.setEnabled(this@MainActivity,checked);status.text=if(checked)"Captura de estructura activada; solo con Aceptar o Me interesa." else "Captura de estructura desactivada.";}};c.addView(treeDumpSwitch)
 c.addView(Button(this).apply{text="Copiar último volcado";setOnClickListener{val dump=ScreenTreeDumpStore.lastDump(this@MainActivity);if(dump.isBlank()){status.text="Todavía no hay un volcado guardado.";return@setOnClickListener};val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager;clipboard.setPrimaryClip(ClipData.newPlainText("Último volcado de pantalla",dump));status.text="Último volcado copiado al portapapeles."}})
 treeDumpView=TextView(this).apply{setTextIsSelectable(true)};c.addView(treeDumpView)
 c.addView(title("Guardar viaje aceptado"));reason=EditText(this).apply{hint="Motivo de aceptación";minLines=2};c.addView(reason)
 c.addView(Button(this).apply{text="Guardar como viaje aceptado";setOnClickListener{if(AcceptedTripStore.add(this@MainActivity,reason.text.toString(),AcceptedTripStore.latestScreen(this@MainActivity))){TripAlertSound.play(this@MainActivity);reason.setText("");refresh();status.text=status.text.toString()+"\nViaje guardado localmente; alerta sonora reproducida."}else status.text="No hay texto de Uber guardado todavía."}})
 c.addView(title("Viajes aceptados guardados"));trips=TextView(this).apply{setTextIsSelectable(true)};c.addView(trips);c.addView(Button(this).apply{text="Borrar viajes aceptados";setOnClickListener{AcceptedTripStore.clear(this@MainActivity);refresh()}})
 c.addView(title("Modo demo"));demoSwitch=Switch(this).apply{text="Modo demo";isChecked=DemoModeStore.isEnabled(this@MainActivity);setOnCheckedChangeListener{_,checked->DemoModeStore.setEnabled(this@MainActivity,checked);refresh()}};c.addView(demoSwitch);c.addView(TextView(this).apply{text="Solo simula: no acepta ni rechaza viajes en Uber"})
 c.addView(title("Ofertas registradas"));offers=TextView(this).apply{setTextIsSelectable(true)};c.addView(offers);c.addView(Button(this).apply{text="Exportar CSV";setOnClickListener{exportCsv()}})
 c.addView(title("Simular oferta"));simulationInput=EditText(this).apply{hint="Pega aquí el texto de una oferta";minLines=4;maxLines=8;gravity=android.view.Gravity.TOP;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE};c.addView(simulationInput)
 c.addView(Button(this).apply{text="Usar ejemplo";setOnClickListener{simulationInput.setText("UberX | CRC1,404.33 | A 7 min (1.4 km) | Viaje: 10 min (2.7 km) | Aceptar");simulationOutput.text=""}})
 c.addView(Button(this).apply{text="Simular";setOnClickListener{simulateOffer()}});simulationOutput=TextView(this).apply{setTextIsSelectable(true)};c.addView(simulationOutput)
 c.addView(title("Costos configurables"));kmL=input(c,"kmPerLiter","18");fuel=input(c,"fuelPricePerLiter","720");maint=input(c,"maintenancePerKm","25");hour=input(c,"minimumNetPerHour","2500");km=input(c,"minimumNetPerKm","250");pickupMax=input(c,"Recogida máxima km (aceptación real)","2.0")
 c.addView(Button(this).apply{text="Guardar costos";setOnClickListener{saveCosts()}})
 c.addView(title("Ensayo de localización del botón"));buttonTrialCounters=TextView(this).apply{setTextIsSelectable(true)};c.addView(buttonTrialCounters);c.addView(Button(this).apply{text="Reiniciar contadores del ensayo";setOnClickListener{OfferButtonTrialStore.reset(this@MainActivity);refresh()}})
 c.addView(title("Auditoría de aceptación real"));auditView=TextView(this).apply{setTextIsSelectable(true)};c.addView(auditView)
 c.addView(Button(this).apply{text="Copiar auditoría";setOnClickListener{val audit=LogStore.readAudit(this@MainActivity);val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager;clipboard.setPrimaryClip(ClipData.newPlainText("Auditoría de aceptación real",audit));status.text="Auditoría copiada."}})
 c.addView(Button(this).apply{text="Exportar auditoría CSV";setOnClickListener{exportAuditCsv()}})
 c.addView(title("Diagnóstico de captura"));logs=TextView(this).apply{setTextIsSelectable(true)};c.addView(logs);c.addView(Button(this).apply{text="Borrar diagnósticos";setOnClickListener{LogStore.clear(this@MainActivity);refresh()}})
 val root=LinearLayout(this).apply{orientation=1}
 val stopButton=Button(this).apply{text="DETENER TODO";textSize=22f;setOnClickListener{AutoAcceptanceStore.setEnabled(this@MainActivity,false);setAcceptanceSwitchChecked(false);LogStore.add(this@MainActivity,"ACEPTACIÓN AUTOMÁTICA APAGADA: DETENER TODO pulsado");LogStore.addAudit(this@MainActivity,java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(java.util.Date())+"| | | | | | |DETENER TODO")}
 root.addView(stopButton,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
 autoAcceptanceSwitch=Switch(this).apply{text="Aceptación automática (REAL)";isChecked=AutoAcceptanceStore.isEnabled(this@MainActivity)}
 autoAcceptanceSwitch.setOnCheckedChangeListener{_,checked->
  if(updatingAcceptanceSwitch)return@setOnCheckedChangeListener
  if(checked){
   setAcceptanceSwitchChecked(false)
   AlertDialog.Builder(this).setMessage("Esto pulsa Aceptar en Uber Driver por ti. Uber puede desactivar tu cuenta por automatizar la app. Tú asumes el riesgo.")
    .setNegativeButton("Cancelar"){_,_->setAcceptanceSwitchChecked(false)}
    .setPositiveButton("Entiendo, activar"){_,_->AutoAcceptanceStore.resetSafetyCounters(this);AutoAcceptanceStore.setEnabled(this,true);setAcceptanceSwitchChecked(true);LogStore.add(this,"ACEPTACIÓN AUTOMÁTICA ACTIVADA por confirmación explícita");refresh()}
    .setOnCancelListener{setAcceptanceSwitchChecked(false)}.show()
  }else{
   AutoAcceptanceStore.setEnabled(this,false)
   LogStore.add(this,"ACEPTACIÓN AUTOMÁTICA APAGADA por el interruptor")
  }
 }
 root.addView(autoAcceptanceSwitch,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
 val scroll=ScrollView(this);scroll.addView(c);root.addView(scroll,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));setContentView(root);loadCosts();refresh()}
 override fun onResume(){super.onResume();if(::status.isInitialized)refresh()}
 private fun appVersion():String {
  return try {
   packageManager.getPackageInfo(packageName,0).versionName ?: "?"
  } catch (e:Exception) {
   "?"
  }
 }
 private fun title(s:String)=TextView(this).apply{text=s;textSize=18f;setPadding(0,16,0,4)}
 private fun input(parent:LinearLayout,label:String,default:String):EditText{parent.addView(TextView(this).apply{text=label});return EditText(this).apply{setText(default);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;parent.addView(this,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))}}
 private fun setAcceptanceSwitchChecked(checked:Boolean){
  updatingAcceptanceSwitch=true
  autoAcceptanceSwitch.isChecked=checked
  updatingAcceptanceSwitch=false
 }
 private fun loadCosts(){val x=DriverCostsStore.read(this);kmL.setText(x.kmPerLiter.toString());fuel.setText(x.fuelPricePerLiter.toString());maint.setText(x.maintenancePerKm.toString());hour.setText(x.minimumNetPerHour.toString());km.setText(x.minimumNetPerKm.toString());pickupMax.setText(DriverCostsStore.maxPickupKm(this).toString())}
 private fun saveCosts(){val a=kmL.text.toString().toDoubleOrNull();val b=fuel.text.toString().toDoubleOrNull();val d=maint.text.toString().toDoubleOrNull();val e=hour.text.toString().toDoubleOrNull();val f=km.text.toString().toDoubleOrNull();val pmax=pickupMax.text.toString().toDoubleOrNull()
 if(a==null||a<=0||b==null||b<0||d==null||d<0||e==null||e<0||f==null||f<0||pmax==null||pmax<0){status.text="Valores inválidos; km/l debe ser > 0 y los demás no negativos.";return};DriverCostsStore.save(this,DriverCosts(a,b,d,e,f));DriverCostsStore.saveMaxPickupKm(this,pmax);status.text="Costos y recogida máxima guardados."}
 private fun simulateOffer(){
  val parsed=OfferParser.parse(simulationInput.text.toString())
  if(parsed==null){simulationOutput.text="No se detectó una oferta válida. Incluye tarifa y el texto Aceptar o Me interesa.";return}
  val costs=readCostsFromFields()
  if(costs==null){simulationOutput.text="Revisa los costos configurables antes de simular.";return}
  if(!parsed.hasRouteDetails){
   val demo=DemoDecision.evaluate(parsed,null,costs)
   simulationOutput.text="Tarifa: ₡${fmt(parsed.fare)}\nNeto: sin datos\nNeto/km: sin datos\nNeto/hora: sin datos\nDecisión demo: ${demo.decision}\nMotivos: ${demo.reasons.joinToString("; ")}"
   return
  }
  val result=Profitability.calculate(Offer(parsed.fare,parsed.pickupKm!!,parsed.pickupMinutes!!,parsed.tripKm!!,parsed.tripMinutes!!),costs)
  val demo=DemoDecision.evaluate(parsed,result,costs)
  simulationOutput.text="Tarifa: ₡${fmt(parsed.fare)}\nNeto: ₡${fmt(result.net)}\nNeto/km: ₡${fmt(result.netPerKm)}\nNeto/hora: ₡${fmt(result.netPerHour)}\nDecisión demo: ${demo.decision}\nMotivos: ${demo.reasons.joinToString("; ")}"
 }
 private fun readCostsFromFields():DriverCosts?{
  val a=kmL.text.toString().toDoubleOrNull();val b=fuel.text.toString().toDoubleOrNull();val d=maint.text.toString().toDoubleOrNull();val e=hour.text.toString().toDoubleOrNull();val f=km.text.toString().toDoubleOrNull()
  if(a==null||a<=0||b==null||b<0||d==null||d<0||e==null||e<0||f==null||f<0)return null
  return DriverCosts(a,b,d,e,f)
 }
 private fun exportAuditCsv(){
  val file=File(cacheDir,"auditoria_aceptacion_real.csv")
  file.writeText(LogStore.csvAudit(this),Charsets.UTF_8)
  val uri=FileProvider.getUriForFile(this,"com.viajerentable.app.fileprovider",file)
  val intent=Intent(Intent.ACTION_SEND).apply{type="text/csv";putExtra(Intent.EXTRA_SUBJECT,"Auditoría de aceptación real");putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
  startActivity(Intent.createChooser(intent,"Compartir auditoría CSV"))
 }
 private fun exportCsv(){val file=File(cacheDir,"ofertas_viaje_rentable.csv");file.writeText(OffersStore.csv(this),Charsets.UTF_8);val uri=FileProvider.getUriForFile(this,"com.viajerentable.app.fileprovider",file);val i=Intent(Intent.ACTION_SEND).apply{type="text/csv";putExtra(Intent.EXTRA_SUBJECT,"Ofertas registradas");putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};startActivity(Intent.createChooser(i,"Compartir CSV"))}
 private fun refresh(){val m=getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager;val enabled=m.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any{it.id?.contains(packageName)==true};status.text="Servicio de accesibilidad habilitado: $enabled"
 screen.text=AcceptedTripStore.latestScreen(this).ifBlank{"Todavía no hay texto capturado. Abre Uber Driver y deja los datos visibles."};trips.text=AcceptedTripStore.read(this)
 if(::treeDumpView.isInitialized){treeDumpView.text=ScreenTreeDumpStore.lastDump(this).ifBlank{"Todavía no hay un volcado. Activa la opción y deja visible una oferta con Aceptar o Me interesa."}}
 offers.text=OffersStore.read(this).joinToString("\n\n"){x->val p=x.parsed;val r=x.result;val d=x.decision?.let{"\nDemo: $it; motivos: ${x.reasons.joinToString("; ")}"}?:"";"${x.timestamp} | ${if(p.hasRouteDetails)"OFERTA DETALLADA" else "OFERTA COMPACTA"}\nTarifa ₡${fmt(p.fare)}; recogida ${p.pickupKm?.let{fmt(it)+" km / "+fmt(p.pickupMinutes!!)+" min"}?:"pendiente"}; viaje ${p.tripKm?.let{fmt(it)+" km / "+fmt(p.tripMinutes!!)+" min"}?:"pendiente"}\n"+(r?.let{"Neto ₡${fmt(it.net)}; ₡/km ${fmt(it.netPerKm)}; ₡/hora ${fmt(it.netPerHour)}; recomendado=${it.recommended}"}?:"Rentabilidad pendiente")+d}.ifBlank{"Todavía no hay ofertas registradas."}
 val trial=OfferButtonTrialStore.read(this);if(::buttonTrialCounters.isInitialized){buttonTrialCounters.text="ACEPTARIA con botón localizado: ${trial.acceptedWithButton}\nACEPTARIA sin botón: ${trial.acceptedWithoutButton}\nAmbiguas: ${trial.ambiguous}\nDiscrepancias: ${trial.discrepancies}"}
 logs.text=LogStore.read(this);if(::auditView.isInitialized){auditView.text=LogStore.readAudit(this)};if(::autoAcceptanceSwitch.isInitialized){setAcceptanceSwitchChecked(AutoAcceptanceStore.isEnabled(this))}}
 private fun fmt(v:Double)=String.format(Locale.US,"%.2f",v)
}