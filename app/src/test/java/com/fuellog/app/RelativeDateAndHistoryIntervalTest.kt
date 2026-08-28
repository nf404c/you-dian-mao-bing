package com.fuellog.app

import com.fuellog.app.data.FuelRecord
import com.fuellog.app.domain.Consumption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RelativeDateAndHistoryIntervalTest {
    private fun timestamp(date: LocalDate, hour: Int = 12, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun record(id: Long, date: LocalDate, odometerKm: Double?) = FuelRecord(
        id = id,
        vehicleId = 1,
        odometerKm = odometerKm,
        fuelGrade = "95",
        pricePerLiter = 8.0,
        amountPaid = 48.0,
        liters = 6.0,
        timestamp = timestamp(date)
    )

    @Test fun homeRelativeDateUsesLocalCalendarDates() {
        val today = LocalDate.of(2026, 8, 28)
        assertEquals("今天", homeRelativeDateLabel(timestamp(today), today))
        assertEquals("昨天", homeRelativeDateLabel(timestamp(today.minusDays(1)), today))
        assertEquals("2天前", homeRelativeDateLabel(timestamp(today.minusDays(2)), today))
        assertEquals("10天前", homeRelativeDateLabel(timestamp(today.minusDays(10)), today))
        assertEquals("昨天", homeRelativeDateLabel(timestamp(today.minusDays(1), 23, 50), today))
    }

    @Test fun historyIntervalFormatsFirstSameDayIntegerAndDecimalDistances() {
        assertEquals("首次记录", historyIntervalLabel(null, null))
        assertEquals("间隔 6天 · 213km", historyIntervalLabel(6, 213.0))
        assertEquals("间隔 0天 · 213.5km", historyIntervalLabel(0, 213.5))
    }

    @Test fun historyDateRemainsVisibleWhenAdjacentDistanceIsUnavailable() {
        assertEquals("间隔 6天", historyIntervalLabel(6, null))
        assertEquals("间隔 6天", historyIntervalLabel(6, 0.0))
    }

    @Test fun unknownOdometerBreaksTheSingleIntervalWithoutBeingSkipped() {
        val records = Consumption.calculate(
            listOf(
                record(1, LocalDate.of(2026, 8, 20), 10_000.0),
                record(2, LocalDate.of(2026, 8, 23), null),
                record(3, LocalDate.of(2026, 8, 26), 10_400.0)
            )
        )
        assertNull(records[2].distanceKm)
        assertEquals(3L, records[2].daysSincePrevious)
        assertEquals("间隔 3天", historyIntervalLabel(records[2].daysSincePrevious, records[2].distanceKm))
    }
}
