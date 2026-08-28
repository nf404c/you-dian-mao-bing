package com.fuellog.app.domain

import com.fuellog.app.data.FuelRecord
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class RecordWithConsumption(
    val record: FuelRecord,
    val distanceKm: Double?,
    val litersPer100Km: Double?,
    val daysSincePrevious: Long?
)

object Consumption {
    fun calculate(recordsAscending: List<FuelRecord>): List<RecordWithConsumption> =
        recordsAscending.mapIndexed { index, current ->
            val previous = recordsAscending.getOrNull(index - 1)
            val distance = previous?.let { current.odometerKm - it.odometerKm }
            val consumption = if (distance != null && distance > 0 && current.liters.isFinite()) {
                (current.liters / distance * 100).takeIf { it.isFinite() }
            } else null
            val days = previous?.let {
                ChronoUnit.DAYS.between(it.timestamp.toLocalDate(), current.timestamp.toLocalDate()).coerceAtLeast(0)
            }
            RecordWithConsumption(current, distance?.takeIf { it > 0 }, consumption, days)
        }

    /**
     * Whole-record-period consumption: the first record is a distance baseline only,
     * so its fuel quantity is deliberately excluded.
     */
    fun overall(recordsAscending: List<FuelRecord>): Double? {
        if (recordsAscending.size < 2) return null
        val first = recordsAscending.first()
        val last = recordsAscending.last()
        val distance = last.odometerKm - first.odometerKm
        val fuel = recordsAscending.drop(1).sumOf { it.liters }
        if (!distance.isFinite() || distance <= 0 || !fuel.isFinite() || fuel <= 0) return null
        return (fuel / distance * 100).takeIf { it.isFinite() }
    }
}

private fun Long.toLocalDate() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun validateRecordInput(
    previousOdometer: Double?, odometer: Double, price: Double, amount: Double, liters: Double
): String? {
    val values = listOf(odometer, price, amount, liters)
    if (values.any { !it.isFinite() }) return "请输入有效数字。"
    if (values.any { it < 0 }) return "输入数值不能为负数。"
    if (liters <= 0) return "加油升数必须大于 0。"
    if (previousOdometer != null && odometer < previousOdometer) return "当前里程不能低于上一条记录。"
    if (previousOdometer != null && odometer == previousOdometer) return "当前里程必须高于上一条记录。"
    return null
}
