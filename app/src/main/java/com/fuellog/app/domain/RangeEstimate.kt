package com.fuellog.app.domain

import com.fuellog.app.data.FuelRecord
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

private const val MaxRangeSamples = 30

sealed interface RangeEstimate {
    data object MissingCapacity : RangeEstimate
    data class InsufficientData(val sampleCount: Int) : RangeEstimate
    data class TypicalOnly(val sampleCount: Int, val typicalKm: Long) : RangeEstimate
    data class FullEstimate(
        val sampleCount: Int,
        val conservativeKm: Long,
        val typicalKm: Long,
        val idealKm: Long
    ) : RangeEstimate
}

fun validateEnergyCapacity(capacity: Double?): String? = when {
    capacity == null -> null
    !capacity.isFinite() -> "请输入有效的能源容量。"
    capacity <= 0 -> "能源容量必须大于 0。"
    else -> null
}

/** Linear-interpolated percentile at index (n - 1) * percentile. */
fun percentile(values: List<Double>, percentile: Double): Double? {
    require(percentile in 0.0..1.0) { "Percentile must be between 0 and 1." }
    val sorted = values.filter(Double::isFinite).sorted()
    if (sorted.isEmpty()) return null
    val index = (sorted.size - 1) * percentile
    val lowerIndex = floor(index).toInt()
    val upperIndex = ceil(index).toInt()
    if (lowerIndex == upperIndex) return sorted[lowerIndex]
    val fraction = index - lowerIndex
    return sorted[lowerIndex] + (sorted[upperIndex] - sorted[lowerIndex]) * fraction
}

fun rangeSample(capacity: Double, distanceKm: Double, energyAmount: Double): Double? {
    if (!capacity.isFinite() || capacity <= 0 ||
        !distanceKm.isFinite() || distanceKm <= 0 ||
        !energyAmount.isFinite() || energyAmount <= 0
    ) return null
    val intervalConsumption = energyAmount / distanceKm * 100
    if (!intervalConsumption.isFinite() || intervalConsumption <= 0) return null
    return (capacity / intervalConsumption * 100).takeIf { it.isFinite() && it > 0 }
}

/** Uses one vehicle's chronologically newest 30 valid adjacent-record samples. */
fun calculateRangeEstimate(capacity: Double?, records: List<FuelRecord>): RangeEstimate {
    if (validateEnergyCapacity(capacity) != null || capacity == null) {
        return RangeEstimate.MissingCapacity
    }
    val ordered = records.sortedWith(compareBy<FuelRecord> { it.timestamp }.thenBy { it.id })
    val samples = Consumption.calculate(ordered).mapNotNull { interval ->
        val consumption = interval.litersPer100Km
        if (consumption == null || consumption <= 0 || !consumption.isFinite()) null
        else (capacity / consumption * 100).takeIf { it.isFinite() && it > 0 }
    }.takeLast(MaxRangeSamples)

    return when (samples.size) {
        in 0..2 -> RangeEstimate.InsufficientData(samples.size)
        in 3..5 -> RangeEstimate.TypicalOnly(
            sampleCount = samples.size,
            typicalKm = percentile(samples, 0.50)!!.roundToLong()
        )
        else -> RangeEstimate.FullEstimate(
            sampleCount = samples.size,
            conservativeKm = percentile(samples, 0.20)!!.roundToLong(),
            typicalKm = percentile(samples, 0.50)!!.roundToLong(),
            idealKm = percentile(samples, 0.80)!!.roundToLong()
        )
    }
}
