package com.viajerentable.app
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
data class RecordedOffer(val timestamp:String,val parsed:ParsedOffer,val result:ProfitResult?)
object OffersStore {
 private const val PREFS="offers_log"; private const val KEY="entries"
 @Synchronized fun add(c:Context,p:ParsedOffer,r:ProfitResult?){val prefs=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val old=try{JSONArray(prefs.getString(KEY,"[]")?:"[]")}catch(_:Exception){JSONArray()};val out=JSONArray()
 for(i in (old.length()-199).coerceAtLeast(0) until old.length())out.put(old.getJSONObject(i))
 out.put(JSONObject().put("date",SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(Date())).put("fare",p.fare).put("pickupKm",p.pickupKm?:JSONObject.NULL).put("pickupMin",p.pickupMinutes?:JSONObject.NULL).put("tripKm",p.tripKm?:JSONObject.NULL).put("tripMin",p.tripMinutes?:JSONObject.NULL).put("net",r?.net?:JSONObject.NULL).put("perKm",r?.netPerKm?:JSONObject.NULL).put("perHour",r?.netPerHour?:JSONObject.NULL).put("recommended",r?.recommended?:false));prefs.edit().putString(KEY,out.toString()).apply()}
 fun read(c:Context):List<RecordedOffer>{val a=try{JSONArray(c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"[]")?:"[]")}catch(_:Exception){JSONArray()}
 return (0 until a.length()).mapNotNull{i->try{val o=a.getJSONObject(i);fun d(k:String):Double?=if(o.isNull(k))null else o.getDouble(k);val p=ParsedOffer(o.getDouble("fare"),d("pickupKm"),d("pickupMin"),d("tripKm"),d("tripMin"));val r=if(o.isNull("net"))null else ProfitResult((p.pickupKm?:0.0)+(p.tripKm?:0.0),(p.pickupMinutes?:0.0)+(p.tripMinutes?:0.0),0.0,0.0,o.getDouble("net"),o.getDouble("perKm"),o.getDouble("perHour"),o.optBoolean("recommended"));RecordedOffer(o.getString("date"),p,r)}catch(_:Exception){null}}.reversed()}
 fun csv(c:Context):String{val rows=mutableListOf("fecha,tarifaCRC,recogidaKm,recogidaMin,viajeKm,viajeMin,netoCRC,netoPorKm,netoPorHora,recomendado");read(c).reversed().forEach{x->fun n(v:Double?)=v?.let{String.format(Locale.US,"%.2f",it)}?:"";rows+=listOf(x.timestamp,n(x.parsed.fare),n(x.parsed.pickupKm),n(x.parsed.pickupMinutes),n(x.parsed.tripKm),n(x.parsed.tripMinutes),n(x.result?.net),n(x.result?.netPerKm),n(x.result?.netPerHour),x.result?.recommended?.toString()?:"false").joinToString(",")};return rows.joinToString("\r\n")}
}