package com.viajerentable.app
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
object AcceptedTripStore {
 private const val PREFS="accepted_trips";private const val KEY_RECORDS="records";private const val KEY_SCREEN="latest_uber_screen"
 private var lastWrite=0L;private var lastText=""
 @Synchronized fun updateLatestScreen(c:Context,text:String){if(text.isBlank()||text==lastText)return;val now=System.currentTimeMillis();if(now-lastWrite<2000L)return;c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_SCREEN,text.take(7000)).apply();lastWrite=now;lastText=text}
 fun latestScreen(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_SCREEN,"")?:""
 fun add(c:Context,reason:String,screenText:String):Boolean{if(screenText.isBlank())return false;val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val stamp=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(Date());fun clean(s:String)=s.replace("\r"," ").replace("\n"," ↵ ").trim();val rec="VIAJE ACEPTADO [$stamp]\nMotivo: ${clean(reason.ifBlank{"No indicado"})}\nDatos visibles de Uber: ${clean(screenText).take(5000)}";val old=p.getString(KEY_RECORDS,"")?:"";p.edit().putString(KEY_RECORDS,(rec+"\n---\n"+old).take(30000)).apply();return true}
 fun read(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_RECORDS,"")?.takeIf{it.isNotBlank()}?:"Todavía no has guardado viajes aceptados."
 fun clear(c:Context){c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY_RECORDS).apply()}
}