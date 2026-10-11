package com.viajerentable.app
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
class MainActivity:Activity(){
 private lateinit var status:TextView;private lateinit var screen:TextView;private lateinit var trips:TextView;private lateinit var offers:TextView;private lateinit var reason:EditText;private lateinit var logs:TextView
 private lateinit var kmL:EditText;private lateinit var fuel:EditText;private lateinit var maint:EditText;private lateinit var hour:EditText;private lateinit var km:EditText
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val pad=(16*resources.displayMetrics.density).toInt();val c=LinearLayout(this).apply{orientation=1;setPadding(pad,pad,pad,pad)}
 c.addView(TextView(this).apply{text="Viaje Rentable 1.12";textSize=25f});status=TextView(this);c.addView(status)
 c.addView(Button(this).apply{text="Abrir ajustes de accesibilidad";setOnClickListener{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}})
 c.addView(Button(this).apply{text="Actualizar datos de Uber";setOnClickListener{refresh()}})
 c.addView(title("Última pantalla de Uber"));c.addView(TextView(this).apply{text="Texto accesible de Uber, no una captura de imagen."});screen=TextView(this).apply{setTextIsSelectable(true)};c.addView(screen)
 c.addView(title("Guardar viaje aceptado"));reason=EditText(this).apply{hint="Motivo de aceptación";minLines=2};c.addView(reason)
 c.addView(Button(this).apply{text="Guardar como viaje aceptado";setOnClickListener{if(AcceptedTripStore.add(this@MainActivity,reason.text.toString(),AcceptedTripStore.latestScreen(this@MainActivity))){reason.setText("");refresh();status.text=status.text.toString()+"\nViaje guardado localmente."}else status.text="No hay texto de Uber guardado todavía."}})
 c.addView(title("Viajes aceptados guardados"));trips=TextView(this).apply{setTextIsSelectable(true)};c.addView(trips);c.addView(Button(this).apply{text="Borrar viajes aceptados";setOnClickListener{AcceptedTripStore.clear(this@MainActivity);refresh()}})
 c.addView(title("Ofertas registradas"));offers=TextView(this).apply{setTextIsSelectable(true)};c.addView(offers);c.addView(Button(this).apply{text="Exportar CSV";setOnClickListener{exportCsv()}})
 c.addView(title("Costos configurables"));kmL=input(c,"kmPerLiter","18");fuel=input(c,"fuelPricePerLiter","720");maint=input(c,"maintenancePerKm","25");hour=input(c,"minimumNetPerHour","2500");km=input(c,"minimumNetPerKm","250")
 c.addView(Button(this).apply{text="Guardar costos";setOnClickListener{saveCosts()}})
 c.addView(title("Diagnóstico de captura"));logs=TextView(this).apply{setTextIsSelectable(true)};c.addView(logs);c.addView(Button(this).apply{text="Borrar diagnósticos";setOnClickListener{LogStore.clear(this@MainActivity);refresh()}})
 val scroll=ScrollView(this);scroll.addView(c);setContentView(scroll);loadCosts();refresh()}
 override fun onResume(){super.onResume();if(::status.isInitialized)refresh()}
 private fun title(s:String)=TextView(this).apply{text=s;textSize=18f;setPadding(0,16,0,4)}
 private fun input(parent:LinearLayout,label:String,default:String):EditText{parent.addView(TextView(this).apply{text=label});return EditText(this).apply{setText(default);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;parent.addView(this,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))}}
 private fun loadCosts(){val x=DriverCostsStore.read(this);kmL.setText(x.kmPerLiter.toString());fuel.setText(x.fuelPricePerLiter.toString());maint.setText(x.maintenancePerKm.toString());hour.setText(x.minimumNetPerHour.toString());km.setText(x.minimumNetPerKm.toString())}
 private fun saveCosts(){val a=kmL.text.toString().toDoubleOrNull();val b=fuel.text.toString().toDoubleOrNull();val d=maint.text.toString().toDoubleOrNull();val e=hour.text.toString().toDoubleOrNull();val f=km.text.toString().toDoubleOrNull()
 if(a==null||a<=0||b==null||b<0||d==null||d<0||e==null||e<0||f==null||f<0){status.text="Valores inválidos; km/l debe ser > 0 y los demás no negativos.";return};DriverCostsStore.save(this,DriverCosts(a,b,d,e,f));status.text="Costos guardados para los próximos cálculos."}
 private fun exportCsv(){val file=File(cacheDir,"ofertas_viaje_rentable.csv");file.writeText(OffersStore.csv(this),Charsets.UTF_8);val uri=FileProvider.getUriForFile(this,"com.viajerentable.app.fileprovider",file);val i=Intent(Intent.ACTION_SEND).apply{type="text/csv";putExtra(Intent.EXTRA_SUBJECT,"Ofertas registradas");putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};startActivity(Intent.createChooser(i,"Compartir CSV"))}
 private fun refresh(){val m=getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager;val enabled=m.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any{it.id?.contains(packageName)==true};status.text="Servicio de accesibilidad habilitado: $enabled"
 screen.text=AcceptedTripStore.latestScreen(this).ifBlank{"Todavía no hay texto capturado. Abre Uber Driver y deja los datos visibles."};trips.text=AcceptedTripStore.read(this)
 offers.text=OffersStore.read(this).joinToString("\n\n"){x->val p=x.parsed;val r=x.result;"${x.timestamp} | ${if(p.hasRouteDetails)"OFERTA DETALLADA" else "OFERTA COMPACTA"}\nTarifa ₡${fmt(p.fare)}; recogida ${p.pickupKm?.let{fmt(it)+" km / "+fmt(p.pickupMinutes!!)+" min"}?:"pendiente"}; viaje ${p.tripKm?.let{fmt(it)+" km / "+fmt(p.tripMinutes!!)+" min"}?:"pendiente"}\n"+(r?.let{"Neto ₡${fmt(it.net)}; ₡/km ${fmt(it.netPerKm)}; ₡/hora ${fmt(it.netPerHour)}; recomendado=${it.recommended}"}?:"Rentabilidad pendiente")}.ifBlank{"Todavía no hay ofertas registradas."}
 logs.text=LogStore.read(this)}
 private fun fmt(v:Double)=String.format(Locale.US,"%.2f",v)
}