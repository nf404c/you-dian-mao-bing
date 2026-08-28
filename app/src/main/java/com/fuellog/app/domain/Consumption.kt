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
            val distance = previous?.odometerKm?.let { previousKm ->
                current.odometerKm?.let { currentKm ->
                    if (previousKm.isFinite() && previousKm >= 0 && currentKm.isFinite() && currentKm >= 0) {
                        (currentKm - previousKm).takeIf { it.isFinite() && it > 0 }
                    } else null
                }
            }
            val consumption = if (distance != null && current.liters.isFinite() && current.liters > 0) {
                (current.liters / distance * 100).takeIf { it.isFinite() }
            } else null
            val days = previous?.let {
                ChronoUnit.DAYS.between(it.timestamp.toLocalDate(), current.timestamp.toLocalDate()).coerceAtLeast(0)
            }
            RecordWithConsumption(current, distance, consumption, days)
        }

    /**
     * Aggregates every positive interval between consecutive real odometer anchors.
     * Each interval includes energy recorded after its starting anchor through its
     * ending anchor, including records with unknown odometers. The starting anchor's
     * energy remains the baseline and unbounded leading/trailing energy is excluded.
     */
    fun overall(recordsAscending: List<FuelRecord>): Double? {
        var previousAnchorIndex: Int? = null
        var totalDistance = 0.0
        var totalEnergy = 0.0
        recordsAscending.forEachIndexed { index, record ->
            val currentKm = record.odometerKm
                ?.takeIf { it.isFinite() && it >= 0 }
                ?: return@forEachIndexed
            previousAnchorIndex?.let { startIndex ->
                val startKm = recordsAscending[startIndex].odometerKm!!
                val distance = currentKm - startKm
                val energy = recordsAscending.subList(startIndex + 1, index + 1).sumOf { it.liters }
                if (distance.isFinite() && distance > 0 && energy.isFinite() && energy > 0) {
                    totalDistance += distance
                    totalEnergy += energy
                }
            }
            previousAnchorIndex = index
        }
        if (!totalDistance.isFinite() || totalDistance <= 0 ||
            !totalEnergy.isFinite() || totalEnergy <= 0
        ) return null
        return (totalEnergy / totalDistance * 100).takeIf { it.isFinite() }
    }
}

private fun Long.toLocalDate() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun validateRecordInput(
    previousOdometer: Double?, odometer: Double?, price: Double, amount: Double, liters: Double
): String? {
    val values = listOfNotNull(odometer, price, amount, liters)
    if (values.any { !it.isFinite() }) return "请输入有效数字。"
    if (values.any { it < 0 }) return "输入数值不能为负数。"
    if (liters <= 0) return "加油升数必须大于 0。"
    if (odometer != null && previousOdometer != null && odometer < previousOdometer) return "当前里程不能低于上一条记录。"
    if (odometer != null && previousOdometer != null && odometer == previousOdometer) return "当前里程必须高于上一条记录。"
    return null
}
