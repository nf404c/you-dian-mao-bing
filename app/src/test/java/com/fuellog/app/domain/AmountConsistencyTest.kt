package com.fuellog.app.domain

import com.fuellog.app.data.EnergyType
import com.fuellog.app.data.FuelRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountConsistencyTest {
    private fun record(price: Double, amount: Double, quantity: Double, grade: String) = FuelRecord(
        vehicleId = 1, odometerKm = 100.0, fuelGrade = grade,
        pricePerLiter = price, amountPaid = amount, liters = quantity, timestamp = 1
    )

    @Test fun allThreeAutoDerivedDirectionsSaveAfterDisplayRoundingForBothEnergyTypes() {
        listOf(EnergyType.FUEL to "95", EnergyType.ELECTRIC to "HOME").forEach { (type, grade) ->
            // 50 / 8.31 = 6.0168..., displayed as 6.02.
            assertNull(validateNewRecord(emptyList(), record(8.31, 50.0, 6.02, grade), type))
            // 7.50 * 8 = 60.00.
            assertNull(validateNewRecord(emptyList(), record(7.50, 60.0, 8.0, grade), type))
            // 60 / 8 = 7.50.
            assertNull(validateNewRecord(emptyList(), record(7.50, 60.0, 8.0, grade), type))
        }
    }

    @Test fun repeatingDecimalCalculationAndRoundedUiValueSave() {
        val derived = FuelLinking.onUserEdit(
            FuelLinking.onUserEdit(FuelInputs("", "", ""), FuelField.PRICE, "8.31"),
            FuelField.AMOUNT, "50"
        )
        assertEquals("6.02", derived.liters)
        assertNull(validateNewRecord(emptyList(), record(8.31, 50.0, derived.liters.toDouble(), "95")))
    }

    @Test fun clearlyInconsistentValuesRemainRejected() {
        val error = validateNewRecord(emptyList(), record(8.31, 50.0, 6.10, "95"))
        assertEquals("油价、金额和升数不一致，请检查后再保存。", error)
    }
}
