package com.fuellog.app.domain

import com.fuellog.app.data.FuelRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleStatisticsTest {
    private fun record(id: Long, km: Double?, grade: String = "95", price: Double = 7.5, liters: Double = 10.0) = FuelRecord(
        id = id, vehicleId = 1, odometerKm = km, fuelGrade = grade, pricePerLiter = price,
        amountPaid = price * liters, liters = liters, timestamp = id
    )

    @Test fun derivesDistanceAndTotals() {
        val result = calculateVehicleStatistics(listOf(record(1, 1000.0, liters = 5.0), record(2, 2500.0, liters = 15.0)))
        assertEquals(1500.0, result.recordedDistanceKm!!, 0.001)
        assertEquals(20.0, result.totalLiters, 0.001)
        assertEquals(150.0, result.totalAmount, 0.001)
        assertEquals(2, result.recordCount)
    }

    @Test fun usesLatestTenInAscendingOrderAndKeepsGradesSeparate() {
        val records = (1L..12L).map { id -> record(id, id.toDouble(), if (id % 2L == 0L) "92" else "95") }
        val result = calculateVehicleStatistics(records.reversed())
        val all = result.recentPriceSeries.values.flatten()
        assertEquals((3L..12L).toList(), all.sortedBy { it.index }.map { it.record.id })
        assertEquals(listOf(4L, 6L, 8L, 10L, 12L), result.recentPriceSeries.getValue("92").map { it.record.id })
        assertEquals(listOf(3L, 5L, 7L, 9L, 11L), result.recentPriceSeries.getValue("95").map { it.record.id })
    }

    @Test fun zeroAndOneRecordDoNotInventDistanceOrAverage() {
        val empty = calculateVehicleStatistics(emptyList())
        assertNull(empty.recordedDistanceKm)
        assertNull(empty.averageIntervalDays)
        val one = calculateVehicleStatistics(listOf(record(1, 1000.0)))
        assertNull(one.recordedDistanceKm)
        assertNull(one.averageIntervalDays)
        assertEquals(10.0, one.totalLiters, 0.001)
    }

    @Test fun intervalDaysUseMedianAndKeepSameDayIntervals() {
        fun day(day: Int) = java.time.LocalDate.of(2026, 8, 1).plusDays((day - 1).toLong())
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun dated(id: Long, d: Int) = record(id, id.toDouble()).copy(timestamp = day(d))
        // 5, 6, 5, 11, 6 -> median 6.
        assertEquals(6L, averageRefuelIntervalDays(listOf(dated(1, 1), dated(2, 6), dated(3, 12), dated(4, 17), dated(5, 28), dated(6, 34))))
        // 0, 5, 6 -> median 5; same-day records remain valid observations.
        assertEquals(5L, averageRefuelIntervalDays(listOf(dated(1, 1), dated(2, 1), dated(3, 6), dated(4, 12))))
        assertEquals(5L, averageRefuelIntervalDays(listOf(dated(1, 1), dated(2, 6))))
        assertNull(averageRefuelIntervalDays(listOf(dated(1, 1))))
    }

    @Test fun intervalKilometersUseMedianAndIgnoreOnlyNonPositiveGaps() {
        fun recordsFromIntervals(intervals: List<Double>) = buildList {
            add(record(1, 0.0))
            intervals.forEachIndexed { index, distance -> add(record(index + 2L, last().odometerKm!! + distance)) }
        }
        // 300, 305, 315, 320, 620 -> median 315.
        assertEquals(315L, averageRefuelIntervalKm(recordsFromIntervals(listOf(300.0, 305.0, 315.0, 320.0, 620.0))))
        // 300, 310, 320, 340 -> (310 + 320) / 2 = 315.
        assertEquals(315L, averageRefuelIntervalKm(recordsFromIntervals(listOf(300.0, 310.0, 320.0, 340.0))))
        // The 900 km interval remains a real record, but cannot pull the median upward.
        assertEquals(310L, averageRefuelIntervalKm(recordsFromIntervals(listOf(300.0, 310.0, 305.0, 900.0, 315.0))))
        assertEquals(110L, averageRefuelIntervalKm(listOf(record(1, 100.0), record(2, 90.0), record(3, 200.0))))
        assertNull(averageRefuelIntervalKm(emptyList()))
        assertNull(averageRefuelIntervalKm(listOf(record(1, 1000.0))))
        assertEquals(200L, averageRefuelIntervalKm(listOf(record(1, 1000.0), record(2, 1200.0))))
    }

    @Test fun medianHelperSupportsEmptyOddEvenAndDoesNotMutateInput() {
        val source = listOf(620.0, 300.0, 315.0, 305.0, 320.0)
        assertNull(median(emptyList()))
        assertEquals(315.0, median(source)!!, 0.001)
        assertEquals(315.0, median(listOf(340.0, 300.0, 320.0, 310.0))!!, 0.001)
        assertEquals(listOf(620.0, 300.0, 315.0, 305.0, 320.0), source)
    }

    @Test fun fuelAndElectricVehiclesShareTheSameIntervalAlgorithm() {
        val fuel = listOf(record(1, 0.0), record(2, 300.0), record(3, 605.0), record(4, 920.0))
        val electric = fuel.map { it.copy(vehicleId = 2, fuelGrade = "HOME", pricePerLiter = 0.55, amountPaid = 5.5) }
        assertEquals(305L, averageRefuelIntervalKm(fuel))
        assertEquals(305L, averageRefuelIntervalKm(electric))
    }

    @Test fun priceAveragesRemainSeparatedByGrade() {
        val series = calculateVehicleStatistics(
            listOf(record(1, 1.0, "95", 8.20), record(2, 2.0, "95", 8.30), record(3, 3.0, "95", 8.40), record(4, 4.0, "92", 7.80), record(5, 5.0, "92", 8.00))
        ).recentPriceSeries
        val averages = averagePriceByGrade(series)
        assertEquals(8.30, averages.getValue("95"), 0.001)
        assertEquals(7.90, averages.getValue("92"), 0.001)
    }

    @Test fun electricHomeAndPublicPriceSeriesRemainSeparate() {
        val series = calculateVehicleStatistics(
            listOf(
                record(1, 1000.0, "HOME", 0.55, 20.0),
                record(2, 1200.0, "PUBLIC", 1.38, 25.0),
                record(3, 1400.0, "HOME", 0.65, 30.0)
            )
        ).recentPriceSeries
        assertEquals(listOf(1L, 3L), series.getValue("HOME").map { it.record.id })
        assertEquals(listOf(2L), series.getValue("PUBLIC").map { it.record.id })
        val averages = averagePriceByGrade(series)
        assertEquals(0.60, averages.getValue("HOME"), 0.001)
        assertEquals(1.38, averages.getValue("PUBLIC"), 0.001)
    }

    @Test fun unknownOdometersKeepTotalsPricesCountsAndDateIntervalsButNotMileageIntervals() {
        fun day(offset: Long) = java.time.LocalDate.of(2026, 8, 1).plusDays(offset)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val records = listOf(
            record(1, 1_000.0, liters = 5.0).copy(timestamp = day(0)),
            record(2, null, grade = "92", price = 8.0, liters = 6.0).copy(timestamp = day(0)),
            record(3, 1_200.0, liters = 7.0).copy(timestamp = day(5)),
            record(4, 1_400.0, grade = "92", price = 8.0, liters = 8.0).copy(timestamp = day(11))
        )
        val result = calculateVehicleStatistics(records)
        assertEquals(400.0, result.recordedDistanceKm!!, 0.001)
        assertEquals(26.0, result.totalLiters, 0.001)
        assertEquals(202.0, result.totalAmount, 0.001)
        assertEquals(4, result.recordCount)
        assertEquals(5L, result.averageIntervalDays)
        assertEquals(200L, result.averageIntervalKm)
        assertEquals(listOf(2L, 4L), result.recentPriceSeries.getValue("92").map { it.record.id })
    }

    @Test fun allUnknownOdometersStillProduceNonMileageStatisticsForBothEnergySystems() {
        listOf("95", "HOME").forEach { grade ->
            val records = listOf(record(1, null, grade, 1.0, 5.0), record(2, null, grade, 1.0, 7.0))
            val result = calculateVehicleStatistics(records)
            assertNull(result.recordedDistanceKm)
            assertNull(result.averageIntervalKm)
            assertEquals(12.0, result.totalLiters, 0.001)
            assertEquals(12.0, result.totalAmount, 0.001)
            assertEquals(2, result.recordCount)
        }
    }
}
