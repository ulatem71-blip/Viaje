package com.viajerentable.app
import android.content.Context
object LogStore {
 private const val PREFS="diagnostic_log";private const val KEY="entries";private const val LIMIT=4000
 @Synchronized fun add(c:Context,line:String){val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val rows=((p.getString(KEY,"")?:"").lines().filter{it.isNotBlank()}+line.lines().filter{it.isNotBlank()}).toMutableList();while(rows.joinToString("\n").length>LIMIT&&rows.isNotEmpty())rows.removeAt(0);p.edit().putString(KEY,rows.joinToString("\n")).apply()}
 fun read(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"Sin registros todavía.")?:""
 fun clear(c:Context){c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY).apply()}
}