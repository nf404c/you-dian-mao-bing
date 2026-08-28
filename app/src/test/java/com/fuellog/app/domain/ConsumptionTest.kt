package com.fuellog.app.domain

import com.fuellog.app.data.FuelRecord
import org.junit.Assert.*
import org.junit.Test

class ConsumptionTest {
    private fun record(id: Long, km: Double?, liters: Double, grade: String = "95") = FuelRecord(
        id = id, vehicleId = 1, odometerKm = km, fuelGrade = grade,
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

    @Test fun middleUnknownOdometersBreakSingleIntervalsButContributeToOverall() {
        val records = listOf(
            record(1, 10_000.0, 6.0),
            record(2, null, 6.2),
            record(3, null, 6.7),
            record(4, 10_400.0, 6.3)
        )
        val intervals = Consumption.calculate(records)
        assertTrue(intervals.drop(1).all { it.distanceKm == null && it.litersPer100Km == null })
        assertEquals((6.2 + 6.7 + 6.3) / 400.0 * 100, Consumption.overall(records)!!, 0.001)
    }

    @Test fun leadingAndTrailingUnknownEnergyRemainOutsideBoundedOverallIntervals() {
        val records = listOf(
            record(1, null, 5.0),
            record(2, 10_000.0, 6.0),
            record(3, null, 6.2),
            record(4, 10_400.0, 6.3),
            record(5, null, 7.0)
        )
        assertEquals((6.2 + 6.3) / 400.0 * 100, Consumption.overall(records)!!, 0.001)
    }

    @Test fun consecutiveAnchorsAndAggregatedUnknownIntervalsKeepBaselineBoundariesExact() {
        val records = listOf(
            record(1, 10_000.0, 5.0),
            record(2, 10_200.0, 8.0),
            record(3, null, 6.0),
            record(4, 10_500.0, 9.0)
        )
        assertEquals((8.0 + 6.0 + 9.0) / 500.0 * 100, Consumption.overall(records)!!, 0.001)
    }

    @Test fun nonIncreasingAnchorOnlyInvalidatesItsOwnBoundedSegment() {
        val records = listOf(
            record(1, 1_000.0, 5.0),
            record(2, 900.0, 6.0),
            record(3, null, 7.0),
            record(4, 1_200.0, 8.0)
        )
        assertEquals((7.0 + 8.0) / 300.0 * 100, Consumption.overall(records)!!, 0.001)
    }

    @Test fun allUnknownOdometersHaveNoDistanceConsumptionForFuelOrElectric() {
        listOf("95", "HOME").forEach { grade ->
            val records = listOf(record(1, null, 6.0, grade), record(2, null, 7.0, grade))
            assertTrue(Consumption.calculate(records).all { it.distanceKm == null && it.litersPer100Km == null })
            assertNull(Consumption.overall(records))
        }
    }
}
