package com.fuellog.app.domain

import com.fuellog.app.data.EnergyType
import com.fuellog.app.data.FuelRecord
import org.junit.Assert.*
import org.junit.Test

class EnergySemanticsTest {
    private fun electricRecord(id: Long, km: Double?, kWh: Double, timestamp: Long = id) = FuelRecord(
        id = id,
        vehicleId = 2,
        odometerKm = km,
        fuelGrade = "HOME",
        pricePerLiter = 0.55,
        amountPaid = kWh * 0.55,
        liters = kWh,
        timestamp = timestamp
    )

    @Test fun storageMappingsAreExplicitAndEnergySpecific() {
        assertEquals(listOf("92", "95"), RecordEnergyType.forVehicle(EnergyType.FUEL).map { it.storageValue })
        assertEquals(listOf("HOME", "PUBLIC"), RecordEnergyType.forVehicle(EnergyType.ELECTRIC).map { it.storageValue })
        assertEquals("95", EnergyType.FUEL.defaultRecordEnergyType().storageValue)
        assertEquals("HOME", EnergyType.ELECTRIC.defaultRecordEnergyType().storageValue)
        assertNull(RecordEnergyType.fromStorageValue(EnergyType.ELECTRIC, "95"))
        assertNull(RecordEnergyType.fromStorageValue(EnergyType.FUEL, "HOME"))
    }

    @Test fun vehicleTypeLocksAfterFirstRecord() {
        assertTrue(canChangeVehicleEnergyType(0))
        assertFalse(canChangeVehicleEnergyType(1))
        assertFalse(canChangeVehicleEnergyType(20))
    }

    @Test fun electricConsumptionUsesTheSameBaselineFormula() {
        val first = electricRecord(1, 1000.0, 30.0)
        val second = electricRecord(2, 1200.0, 32.0)
        val third = electricRecord(3, 1500.0, 45.0)
        val intervals = Consumption.calculate(listOf(first, second, third))
        assertNull(intervals[0].litersPer100Km)
        assertEquals(16.0, intervals[1].litersPer100Km!!, 0.001)
        assertEquals(15.0, intervals[2].litersPer100Km!!, 0.001)
        assertEquals(77.0 / 500.0 * 100, Consumption.overall(listOf(first, second, third))!!, 0.001)
        assertEquals(107.0, calculateVehicleStatistics(listOf(first, second, third)).totalLiters, 0.001)
    }

    @Test fun historicalElectricInsertionRecalculatesIntervalsInStableOrder() {
        val first = electricRecord(1, 1000.0, 20.0, timestamp = 10)
        val last = electricRecord(2, 1400.0, 40.0, timestamp = 30)
        val inserted = electricRecord(3, 1200.0, 30.0, timestamp = 20)
        assertNull(validateNewRecord(listOf(first, last), inserted, EnergyType.ELECTRIC))
        val calculated = Consumption.calculate(listOf(first, inserted, last))
        assertEquals(15.0, calculated[1].litersPer100Km!!, 0.001)
        assertEquals(20.0, calculated[2].litersPer100Km!!, 0.001)
    }

    @Test fun electricOverallUsesUnknownOdometerEnergyInsideRealAnchors() {
        val records = listOf(
            electricRecord(1, 1_000.0, 30.0),
            electricRecord(2, null, 12.0),
            electricRecord(3, 1_300.0, 33.0)
        )
        assertEquals((12.0 + 33.0) / 300.0 * 100, Consumption.overall(records)!!, 0.001)
        assertNull(Consumption.calculate(records)[1].litersPer100Km)
        assertNull(Consumption.calculate(records)[2].litersPer100Km)
    }

    @Test fun recordTypeCannotBeInterpretedAcrossEnergySystems() {
        val electric = electricRecord(1, 1000.0, 20.0)
        assertNull(validateNewRecord(emptyList(), electric, EnergyType.ELECTRIC))
        assertEquals("补能类型与车辆类型不一致。", validateNewRecord(emptyList(), electric, EnergyType.FUEL))
        val legacyFuel = electric.copy(fuelGrade = "95", pricePerLiter = 8.0, amountPaid = 160.0)
        assertEquals("补能类型与车辆类型不一致。", validateNewRecord(emptyList(), legacyFuel, EnergyType.ELECTRIC))
    }
}
