package com.fuellog.app.domain

import com.fuellog.app.data.EnergyType

enum class RecordEnergyType(
    val vehicleEnergyType: EnergyType,
    val storageValue: String,
    val displayName: String
) {
    FUEL_92(EnergyType.FUEL, "92", "92"),
    FUEL_95(EnergyType.FUEL, "95", "95"),
    ELECTRIC_HOME(EnergyType.ELECTRIC, "HOME", "家充"),
    ELECTRIC_PUBLIC(EnergyType.ELECTRIC, "PUBLIC", "公共");

    companion object {
        fun forVehicle(energyType: EnergyType): List<RecordEnergyType> =
            entries.filter { it.vehicleEnergyType == energyType }

        fun fromStorageValue(energyType: EnergyType, value: String): RecordEnergyType? =
            entries.firstOrNull { it.vehicleEnergyType == energyType && it.storageValue == value }
    }
}

fun EnergyType.defaultRecordEnergyType(): RecordEnergyType = when (this) {
    EnergyType.FUEL -> RecordEnergyType.FUEL_95
    EnergyType.ELECTRIC -> RecordEnergyType.ELECTRIC_HOME
}

fun EnergyType.displayName(): String = when (this) {
    EnergyType.FUEL -> "燃油车"
    EnergyType.ELECTRIC -> "电动车"
}

fun EnergyType.quantityUnit(): String = if (this == EnergyType.FUEL) "L" else "kWh"
fun EnergyType.priceUnit(): String = if (this == EnergyType.FUEL) "元/L" else "元/kWh"
fun EnergyType.priceDisplayUnit(): String = if (this == EnergyType.FUEL) "/L" else "/kWh"
fun EnergyType.consumptionUnit(): String = if (this == EnergyType.FUEL) "L/100km" else "kWh/100km"

fun canChangeVehicleEnergyType(recordCount: Int): Boolean = recordCount == 0
