package com.fuellog.app.domain

import com.fuellog.app.data.FuelRecord
import org.junit.Assert.*
import org.junit.Test

class ConsumptionTest {
    private fun record(id: Long, km: Double, liters: Double) = FuelRecord(
        id = id, vehicleId = 1, odometerKm = km, fuelGrade = "95",
        pricePerLiter = 7.5, amountPaid = liters * 7.5, liters = liters, timestamp = id
    )

    @Test fun requiredCasesAndMiddleDeletion() {
        val a = record(1, 10000.0, 6.0)
        val b = record(2, 10200.0, 8.0)
        val c = record(3, 10450.0, 8.5)
        val all = Consumption.calculate(listOf(a, b, c))
        assertNull(all[0].litersPer100Km)
        assertEquals(200.0, all[1].distanceKm!!, 0.001)
        assertEquals(4.0, all[1].litersPer100Km!!, 0.001)
        assertEquals(250.0, all[2].distanceKm!!, 0.001)
        assertEquals(3.4, all[2].litersPer100Km!!, 0.001)

        assertNull(Consumption.overall(listOf(a)))
        assertEquals(4.0, Consumption.overall(listOf(a, b))!!, 0.001)
        assertEquals(16.5 / 450.0 * 100, Consumption.overall(listOf(a, b, c))!!, 0.001)

        val afterDelete = Consumption.calculate(listOf(a, c))
        assertEquals(450.0, afterDelete[1].distanceKm!!, 0.001)
        assertEquals(8.5 / 450.0 * 100, afterDelete[1].litersPer100Km!!, 0.001)
        assertEquals(8.5 / 450.0 * 100, Consumption.overall(listOf(a, c))!!, 0.001)
    }
}
