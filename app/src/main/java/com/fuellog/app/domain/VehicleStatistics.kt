package com.fuellog.app.domain

import com.fuellog.app.data.FuelRecord
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

data class FuelPriceTrendPoint(val index: Int, val record: FuelRecord)

data class VehicleStatistics(
    val recordedDistanceKm: Double?,
    val totalLiters: Double,
    val totalAmount: Double,
    val recordCount: Int,
    val averageIntervalDays: Long?,
    val averageIntervalKm: Long?,
    val recentPriceSeries: Map<String, List<FuelPriceTrendPoint>>
)

/** Statistics are derived only from one vehicle's records in chronological business order. */
fun calculateVehicleStatistics(records: List<FuelRecord>): VehicleStatistics {
    val ordered = records.sortedWith(compareBy<FuelRecord> { it.timestamp }.thenBy { it.id })
    val knownOdometers = ordered.mapNotNull { record ->
        record.odometerKm?.takeIf { it.isFinite() && it >= 0 }
    }
    val distance = knownOdometers.takeIf { it.size >= 2 }
        ?.let { it.last() - it.first() }
        ?.takeIf { it.isFinite() && it > 0 }
    val recent = ordered.takeLast(10)
    return VehicleStatistics(
        recordedDistanceKm = distance,
        totalLiters = ordered.sumOf { it.liters }.takeIf { it.isFinite() } ?: 0.0,
        totalAmount = ordered.sumOf { it.amountPaid }.takeIf { it.isFinite() } ?: 0.0,
        recordCount = ordered.size,
        averageIntervalDays = averageRefuelIntervalDays(ordered),
        averageIntervalKm = averageRefuelIntervalKm(ordered),
        recentPriceSeries = recent.mapIndexed { index, record -> FuelPriceTrendPoint(index, record) }
            .groupBy { it.record.fuelGrade }
    )
}

fun averageRefuelIntervalDays(records: List<FuelRecord>): Long? {
    val ordered = records.sortedWith(compareBy<FuelRecord> { it.timestamp }.thenBy { it.id })
    if (ordered.size < 2) return null
    val intervals = ordered.zipWithNext().map { (previous, current) ->
        ChronoUnit.DAYS.between(
            localBusinessDate(previous.timestamp),
            localBusinessDate(current.timestamp)
        ).coerceAtLeast(0)
    }
    return median(intervals.map(Long::toDouble))?.roundToLong()
}

fun averageRefuelIntervalKm(records: List<FuelRecord>): Long? {
    val ordered = records.sortedWith(compareBy<FuelRecord> { it.timestamp }.thenBy { it.id })
    if (ordered.size < 2) return null
    val intervals = ordered.zipWithNext()
        .mapNotNull { (previous, current) ->
            val previousKm = previous.odometerKm
            val currentKm = current.odometerKm
            if (previousKm == null || currentKm == null ||
                !previousKm.isFinite() || previousKm < 0 ||
                !currentKm.isFinite() || currentKm < 0
            ) null else currentKm - previousKm
        }
        .filter { it.isFinite() && it > 0 }
    return median(intervals)?.roundToLong()
}

/** Returns the middle value without changing the caller's collection. */
fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle]
    else (sorted[middle - 1] + sorted[middle]) / 2.0
}

fun averagePriceByGrade(series: Map<String, List<FuelPriceTrendPoint>>): Map<String, Double> =
    series.mapValues { (_, points) -> points.map { it.record.pricePerLiter }.average() }
