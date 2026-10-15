package com.viajerentable.app
import android.content.Context
object DriverCostsStore {
 private const val PREFS="driver_costs"
 fun read(c:Context):DriverCosts { val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  fun v(k:String,d:Double,min:Double=0.0):Double=(p.getString(k,d.toString())?.toDoubleOrNull()?:d).takeIf{it>=min}?:d
  return DriverCosts(v("kmPerLiter",18.0,0.000001),v("fuelPricePerLiter",720.0),v("maintenancePerKm",25.0),v("minimumNetPerHour",2500.0),v("minimumNetPerKm",250.0))
 }
 fun maxPickupKm(c:Context):Double { val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);return (p.getString("maxPickupKm","2.0")?.toDoubleOrNull()?:2.0).takeIf{it>=0.0}?:2.0 }\n fun saveMaxPickupKm(c:Context,value:Double){if(value>=0.0&&value.isFinite()){c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("maxPickupKm",value.toString()).apply()}}\n fun save(c:Context,x:DriverCosts){c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("kmPerLiter",x.kmPerLiter.toString()).putString("fuelPricePerLiter",x.fuelPricePerLiter.toString()).putString("maintenancePerKm",x.maintenancePerKm.toString()).putString("minimumNetPerHour",x.minimumNetPerHour.toString()).putString("minimumNetPerKm",x.minimumNetPerKm.toString()).apply()}
}