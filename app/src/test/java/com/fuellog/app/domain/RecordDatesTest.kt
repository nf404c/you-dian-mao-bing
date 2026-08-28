package com.fuellog.app.domain

import com.fuellog.app.data.FuelRecord
import com.fuellog.app.data.EnergyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.time.LocalDate

class RecordDatesTest {
    private fun timestamp(year: Int, month: Int, day: Int, hour: Int = 15, minute: Int = 36): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 20)
        }.timeInMillis

    private fun record(id: Long, km: Double?, at: Long, grade: String = "95") = FuelRecord(
        id = id, vehicleId = 1, odometerKm = km, fuelGrade = grade,
        pricePerLiter = 8.0, amountPaid = 48.0, liters = 6.0, timestamp = at
    )

    @Test fun selectedDateReplacesOnlyCalendarDateAndKeepsTime() {
        val original = timestamp(2026, 8, 27, 15, 36)
        val selected = timestamp(2026, 8, 25, 0, 0)
        val updated = Calendar.getInstance().apply { timeInMillis = timestampWithSelectedDate(original, selected) }
        assertEquals(2026, updated.get(Calendar.YEAR))
        assertEquals(Calendar.AUGUST, updated.get(Calendar.MONTH))
        assertEquals(25, updated.get(Calendar.DAY_OF_MONTH))
        assertEquals(15, updated.get(Calendar.HOUR_OF_DAY))
        assertEquals(36, updated.get(Calendar.MINUTE))
    }

    @Test fun datePickerStartUsesTheSameLocalCalendarDate() {
        val start = datePickerStartMillis(timestamp(2026, 8, 27, 15, 36))
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = start }
        assertEquals(2026, utc.get(Calendar.YEAR))
        assertEquals(Calendar.AUGUST, utc.get(Calendar.MONTH))
        assertEquals(27, utc.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun daysSinceDateUsesLocalCalendarDates() {
        assertEquals(8L, daysSinceDate(timestamp(2026, 8, 20), LocalDate.of(2026, 8, 28)))
        assertEquals(0L, daysSinceDate(timestamp(2026, 8, 20), LocalDate.of(2026, 8, 20)))
    }

    @Test fun timeOrderMustAlsoKeepOdometersStrictlyIncreasing() {
        val first = record(1, 4000.0, timestamp(2026, 8, 1))
        val second = record(2, 4200.0, timestamp(2026, 8, 10))
        assertTrue(hasStrictlyIncreasingOdometersInTimeOrder(listOf(first, second)))
        val movedSecond = second.copy(timestamp = timestamp(2026, 7, 20))
        assertFalse(hasStrictlyIncreasingOdometersInTimeOrder(listOf(first, movedSecond)))
    }

    @Test fun recordEditValidationChecksFormulaAndChronologicalOdometers() {
        val first = record(1, 4000.0, timestamp(2026, 8, 1))
        val second = record(2, 4200.0, timestamp(2026, 8, 10))
        assertNull(validateRecordEdit(listOf(first, second), second))
        assertEquals("油价、金额和升数不一致，请检查后再保存。", validateRecordEdit(listOf(first, second), second.copy(amountPaid = 99.0)))
        assertEquals(
            "修改后的日期或里程会导致记录顺序异常，请检查。",
            validateRecordEdit(listOf(first, second), second.copy(timestamp = timestamp(2026, 7, 20)))
        )
    }

    @Test fun newHistoricalRecordMustFitChronologicalOdometers() {
        val first = record(1, 3000.0, timestamp(2026, 8, 10))
        val last = record(2, 3200.0, timestamp(2026, 8, 20))
        assertNull(validateNewRecord(listOf(first, last), record(0, 3100.0, timestamp(2026, 8, 15))))
        assertEquals(
            "日期或里程会导致记录顺序异常，请检查。",
            validateNewRecord(listOf(first, last), record(0, 3500.0, timestamp(2026, 8, 15)))
        )
    }

    @Test fun futureLocalDateIsRejected() {
        assertFalse(isFutureLocalDate(timestamp(2026, 8, 28), LocalDate.of(2026, 8, 28)))
        assertTrue(isFutureLocalDate(timestamp(2026, 8, 29), LocalDate.of(2026, 8, 28)))
    }

    @Test fun unknownOdometerIsValidForNewAndExistingFuelAndElectricRecords() {
        val at = timestamp(2026, 8, 10)
        assertNull(validateNewRecord(emptyList(), record(0, null, at), EnergyType.FUEL))
        val electric = record(1, null, at, "HOME").copy(pricePerLiter = 0.5, amountPaid = 20.0, liters = 40.0)
        assertNull(validateNewRecord(emptyList(), electric, EnergyType.ELECTRIC))
        assertNull(validateRecordEdit(listOf(electric), electric, EnergyType.ELECTRIC))
    }

    @Test fun unknownRecordDoesNotBlockLaterKnownAnchorAndCanLaterBeFilled() {
        val first = record(1, 3_000.0, timestamp(2026, 8, 1))
        val unknown = record(2, null, timestamp(2026, 8, 10))
        val last = record(3, 3_400.0, timestamp(2026, 8, 20))
        assertNull(validateNewRecord(listOf(first, unknown), last))
        assertNull(validateRecordEdit(listOf(first, unknown, last), unknown))
        assertNull(validateRecordEdit(listOf(first, unknown, last), unknown.copy(odometerKm = 3_200.0)))
        assertEquals(
            "修改后的日期或里程会导致记录顺序异常，请检查。",
            validateRecordEdit(listOf(first, unknown, last), unknown.copy(odometerKm = 3_500.0))
        )
    }

    @Test fun editingOtherFieldsKeepsExistingRealOdometer() {
        val original = record(1, 4_000.0, timestamp(2026, 8, 1))
        val edited = original.copy(pricePerLiter = 8.1, amountPaid = 48.6)
        assertNull(validateRecordEdit(listOf(original), edited))
        assertEquals(4_000.0, edited.odometerKm!!, 0.001)
    }
}
