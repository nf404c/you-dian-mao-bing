package com.fuellog.app.domain

import com.fuellog.app.data.FuelRecord
import com.fuellog.app.data.EnergyType
import java.util.Calendar
import java.util.TimeZone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Replaces only the local calendar date, preserving the original time of day. */
fun timestampWithSelectedDate(originalTimestamp: Long, selectedDateMillis: Long): Long {
    val original = Calendar.getInstance().apply { timeInMillis = originalTimestamp }
    val selected = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
    original.set(Calendar.YEAR, selected.get(Calendar.YEAR))
    original.set(Calendar.MONTH, selected.get(Calendar.MONTH))
    original.set(Calendar.DAY_OF_MONTH, selected.get(Calendar.DAY_OF_MONTH))
    return original.timeInMillis
}

/** Material DatePicker expects UTC midnight, while records are presented in local dates. */
fun datePickerStartMillis(timestamp: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = timestamp }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

fun daysSinceDate(timestamp: Long, today: LocalDate = LocalDate.now(ZoneId.systemDefault())): Long =
    ChronoUnit.DAYS.between(localBusinessDate(timestamp), today)

fun localBusinessDate(timestamp: Long): LocalDate =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

fun isFutureLocalDate(timestamp: Long, today: LocalDate = LocalDate.now(ZoneId.systemDefault())): Boolean =
    localBusinessDate(timestamp).isAfter(today)

fun hasStrictlyIncreasingOdometersInTimeOrder(records: List<FuelRecord>): Boolean =
    records.sortedWith(compareBy<FuelRecord> { it.timestamp }.thenBy { it.id })
        .zipWithNext()
        .all { (previous, current) -> current.odometerKm > previous.odometerKm }

fun validateRecordEdit(
    records: List<FuelRecord>,
    updated: FuelRecord,
    energyType: EnergyType = EnergyType.FUEL
): String? {
    if (RecordEnergyType.fromStorageValue(energyType, updated.fuelGrade) == null) {
        return "补能类型与车辆类型不一致。"
    }
    val values = listOf(updated.odometerKm, updated.pricePerLiter, updated.amountPaid, updated.liters)
    if (values.any { !it.isFinite() }) return "请输入有效数字。"
    if (updated.odometerKm < 0 || updated.pricePerLiter <= 0 || updated.amountPaid < 0 || updated.liters <= 0) {
        return if (energyType == EnergyType.FUEL) "里程、油价和加油升数必须为有效正数。"
        else "里程、电价和充电电量必须为有效正数。"
    }
    if (abs(updated.amountPaid - updated.pricePerLiter * updated.liters) > 0.02) {
        return if (energyType == EnergyType.FUEL) "油价、金额和升数不一致，请检查后再保存。"
        else "电价、金额和电量不一致，请检查后再保存。"
    }
    val proposed = records.map { record -> if (record.id == updated.id) updated else record }
    if (!hasStrictlyIncreasingOdometersInTimeOrder(proposed)) {
        return "修改后的日期或里程会导致记录顺序异常，请检查。"
    }
    return null
}

fun validateNewRecord(
    records: List<FuelRecord>,
    added: FuelRecord,
    energyType: EnergyType = EnergyType.FUEL
): String? {
    if (RecordEnergyType.fromStorageValue(energyType, added.fuelGrade) == null) {
        return "补能类型与车辆类型不一致。"
    }
    val values = listOf(added.odometerKm, added.pricePerLiter, added.amountPaid, added.liters)
    if (values.any { !it.isFinite() }) return "请输入有效数字。"
    if (added.odometerKm < 0 || added.pricePerLiter <= 0 || added.amountPaid < 0 || added.liters <= 0) {
        return if (energyType == EnergyType.FUEL) "里程、油价和加油升数必须为有效正数。"
        else "里程、电价和充电电量必须为有效正数。"
    }
    if (abs(added.amountPaid - added.pricePerLiter * added.liters) > 0.02) {
        return if (energyType == EnergyType.FUEL) "油价、金额和升数不一致，请检查后再保存。"
        else "电价、金额和电量不一致，请检查后再保存。"
    }
    // Room assigns the next id on insert, so model that tie-breaker during pre-insert validation.
    val inserted = added.copy(id = (records.maxOfOrNull { it.id } ?: 0L) + 1L)
    if (!hasStrictlyIncreasingOdometersInTimeOrder(records + inserted)) {
        return "日期或里程会导致记录顺序异常，请检查。"
    }
    return null
}
