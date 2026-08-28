package com.fuellog.app.domain

import com.fuellog.app.data.FuelRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeEstimateTest {
    @Test fun fuelRangeSampleUsesIntervalConsumption() {
        assertEquals(247.0588, rangeSample(8.0, 210.0, 6.8)!!, 0.0001)
    }

    @Test fun electricRangeSampleUsesTheSameModel() {
        assertEquals(400.0, rangeSample(60.0, 300.0, 45.0)!!, 0.0001)
    }

    @Test fun energyAboveCapacityIsStillAValidSample() {
        assertEquals(200.0, rangeSample(8.0, 400.0, 16.0)!!, 0.0001)
    }

    @Test fun invalidDistanceOrEnergyDoesNotProduceASample() {
        assertNull(rangeSample(8.0, 0.0, 6.0))
        assertNull(rangeSample(8.0, -1.0, 6.0))
        assertNull(rangeSample(8.0, 100.0, 0.0))
        assertNull(rangeSample(8.0, 100.0, -1.0))
    }

    @Test fun capacityIsOptionalButMustBeFiniteAndPositiveWhenPresent() {
        assertNull(validateEnergyCapacity(null))
        assertNull(validateEnergyCapacity(1.5))
        assertTrue(validateEnergyCapacity(0.0) != null)
        assertTrue(validateEnergyCapacity(-1.0) != null)
        assertTrue(validateEnergyCapacity(Double.NaN) != null)
        assertTrue(validateEnergyCapacity(Double.POSITIVE_INFINITY) != null)
        assertEquals(RangeEstimate.MissingCapacity, calculateRangeEstimate(null, emptyList()))
        assertEquals(RangeEstimate.MissingCapacity, calculateRangeEstimate(0.0, emptyList()))
    }

    @Test fun percentileUsesFixedLinearInterpolationForOddAndEvenInputs() {
        val even = listOf(0.0, 10.0, 20.0, 30.0)
        assertEquals(6.0, percentile(even, 0.20)!!, 0.0001)
        assertEquals(15.0, percentile(even, 0.50)!!, 0.0001)
        assertEquals(24.0, percentile(even, 0.80)!!, 0.0001)
        assertEquals(2.0, percentile(listOf(3.0, 1.0, 2.0), 0.50)!!, 0.0001)
    }

    @Test fun sampleCountSelectsEachDisplayStateBoundary() {
        listOf(0, 1, 2).forEach { count ->
            val estimate = calculateRangeEstimate(10.0, recordsForRanges(List(count) { 200.0 }))
            assertTrue("count=$count", estimate is RangeEstimate.InsufficientData)
            assertEquals(count, (estimate as RangeEstimate.InsufficientData).sampleCount)
        }
        listOf(3, 5).forEach { count ->
            val estimate = calculateRangeEstimate(10.0, recordsForRanges(List(count) { 200.0 }))
            assertTrue("count=$count", estimate is RangeEstimate.TypicalOnly)
            assertEquals(count, (estimate as RangeEstimate.TypicalOnly).sampleCount)
        }
        listOf(6, 30, 31).forEach { count ->
            val estimate = calculateRangeEstimate(10.0, recordsForRanges(List(count) { 200.0 }))
            assertTrue("count=$count", estimate is RangeEstimate.FullEstimate)
            assertEquals(minOf(count, 30), (estimate as RangeEstimate.FullEstimate).sampleCount)
        }
    }

    @Test fun newestThirtyAreChosenChronologicallyBeforePercentilesAreSorted() {
        val oldOutlierThenRecent = listOf(10_000.0) + (100..129).map(Int::toDouble)
        val estimate = calculateRangeEstimate(10.0, recordsForRanges(oldOutlierThenRecent)) as RangeEstimate.FullEstimate
        assertEquals(30, estimate.sampleCount)
        assertEquals(106L, estimate.conservativeKm)
        assertEquals(115L, estimate.typicalKm)
        assertEquals(123L, estimate.idealKm)
    }

    @Test fun p20AndP80DoNotBecomeSingleExtremeMinimumAndMaximum() {
        val estimate = calculateRangeEstimate(
            10.0,
            recordsForRanges(listOf(150.0, 240.0, 245.0, 250.0, 255.0, 260.0, 400.0))
        ) as RangeEstimate.FullEstimate
        assertTrue(estimate.conservativeKm > 150)
        assertTrue(estimate.idealKm < 400)
    }

    @Test fun fuelAndElectricVehicleInputsRemainIndependent() {
        val fuel = calculateRangeEstimate(8.0, recordsForRanges(listOf(240.0, 245.0, 250.0), capacity = 8.0)) as RangeEstimate.TypicalOnly
        val electric = calculateRangeEstimate(60.0, recordsForRanges(listOf(360.0, 400.0, 440.0), capacity = 60.0)) as RangeEstimate.TypicalOnly
        assertEquals(245L, fuel.typicalKm)
        assertEquals(400L, electric.typicalKm)
    }

    private fun recordsForRanges(ranges: List<Double>, capacity: Double = 10.0): List<FuelRecord> {
        var odometer = 0.0
        return buildList {
            add(record(id = 1, odometer = odometer, energy = capacity, timestamp = 1))
            ranges.forEachIndexed { index, range ->
                odometer += range
                add(record(id = index + 2L, odometer = odometer, energy = capacity, timestamp = index + 2L))
            }
        }
    }

    private fun record(id: Long, odometer: Double, energy: Double, timestamp: Long) = FuelRecord(
        id = id,
        vehicleId = 1,
        odometerKm = odometer,
        fuelGrade = "95",
        pricePerLiter = 1.0,
        amountPaid = energy,
        liters = energy,
        timestamp = timestamp
    )
}
