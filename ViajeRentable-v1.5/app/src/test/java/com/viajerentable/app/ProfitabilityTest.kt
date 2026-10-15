package com.viajerentable.app
import org.junit.Assert.*
import org.junit.Test
class ProfitabilityTest {
 @Test fun calculatesAndRejectsLowHourly(){ val r=Profitability.calculate(Offer(3200.0,1.8,5.0,6.3,60.0),DriverCosts(kmPerLiter=18.0,fuelPricePerLiter=720.0,maintenancePerKm=25.0)); assertEquals(8.1,r.totalKm,.001); assertEquals(324.0,r.fuelCost,.001); assertEquals(202.5,r.maintenanceCost,.001); assertEquals(2673.5,r.net,.001); assertFalse(r.recommended) }
 @Test fun recommendedAtExactLimits(){ val r=Profitability.calculate(Offer(1000.0,1.0,12.0,1.0,12.0),DriverCosts(kmPerLiter=1000000.0,fuelPricePerLiter=0.0,maintenancePerKm=0.0,minimumNetPerHour=2500.0,minimumNetPerKm=500.0)); assertEquals(500.0,r.netPerKm,.001); assertEquals(2500.0,r.netPerHour,.001); assertTrue(r.recommended) }
 @Test fun acAddsFuelCostButDoesNotChangeMaintenance() {
  val offer = Offer(3200.0, 1.8, 5.0, 6.3, 60.0)
  val normal = Profitability.calculate(
   offer,
   DriverCosts(kmPerLiter = 18.0, fuelPricePerLiter = 720.0, maintenancePerKm = 25.0)
  )
  val withAc = Profitability.calculate(
   offer,
   DriverCosts(kmPerLiter = 18.0, fuelPricePerLiter = 720.0, maintenancePerKm = 25.0,
    acEnabled = true, acExtraPercent = 10.0)
  )
  assertEquals(normal.fuelCost * 1.10, withAc.fuelCost, 0.001)
  assertEquals(normal.maintenanceCost, withAc.maintenanceCost, 0.001)
  assertEquals(44.0, withAc.fuelCostPerKm, 0.001)
 }
 @Test fun acOffKeepsBaseFuelCost() {
  val result = Profitability.calculate(
   Offer(1000.0, 1.0, 5.0, 2.0, 10.0),
   DriverCosts(kmPerLiter = 20.0, fuelPricePerLiter = 800.0, acEnabled = false, acExtraPercent = 50.0)
  )
  assertEquals(40.0, result.fuelCostPerKm, 0.001)
 }
}