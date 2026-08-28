package com.fuellog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelLinkingTest {
    @Test fun electricPriceAndEnergyAmountUseTheSameThreeWayLinking() {
        val fromPriceAndEnergy = FuelLinking.onUserEdit(
            FuelLinking.onUserEdit(FuelInputs("", "", ""), FuelField.PRICE, "0.55"),
            FuelField.LITERS,
            "32.60"
        )
        assertEquals("17.93", fromPriceAndEnergy.amount)

        val fromAmountAndEnergy = FuelLinking.onUserEdit(
            FuelLinking.onUserEdit(FuelInputs("", "", ""), FuelField.AMOUNT, "17.93"),
            FuelField.LITERS,
            "32.60"
        )
        assertEquals("0.55", fromAmountAndEnergy.price)

        val fromPriceAndAmount = FuelLinking.onUserEdit(
            FuelLinking.onUserEdit(FuelInputs("", "", ""), FuelField.PRICE, "0.55"),
            FuelField.AMOUNT,
            "17.93"
        )
        assertEquals("32.60", fromPriceAndAmount.liters)
    }

    @Test fun priceAndAmountDeriveLiters() {
        val result = FuelLinking.onUserEdit(
            FuelLinking.onUserEdit(FuelInputs("", "", ""), FuelField.PRICE, "7.50"),
            FuelField.AMOUNT, "60"
        )
        assertEquals("7.50", result.price)
        assertEquals("60", result.amount)
        assertEquals("8.00", result.liters)
    }

    @Test fun priceAndLitersDeriveAmount() {
        val result = FuelLinking.onUserEdit(
            FuelLinking.onUserEdit(FuelInputs("", "", ""), FuelField.PRICE, "7.50"),
            FuelField.LITERS, "8"
        )
        assertEquals("60.00", result.amount)
    }

    @Test fun amountAndLitersDerivePrice() {
        val result = FuelLinking.onUserEdit(
            FuelLinking.onUserEdit(FuelInputs("", "", ""), FuelField.AMOUNT, "60"),
            FuelField.LITERS, "8"
        )
        assertEquals("7.50", result.price)
    }

    @Test fun latestUserEditAlwaysWinsAndDerivesOldestField() {
        var result = FuelInputs("", "", "")
        result = FuelLinking.onUserEdit(result, FuelField.PRICE, "7.50")
        result = FuelLinking.onUserEdit(result, FuelField.AMOUNT, "60")
        assertEquals("8.00", result.liters)
        result = FuelLinking.onUserEdit(result, FuelField.AMOUNT, "75")
        assertEquals("75", result.amount)
        assertEquals("10.00", result.liters)
        result = FuelLinking.onUserEdit(result, FuelField.LITERS, "10")
        assertEquals("10", result.liters)
        assertEquals("7.50", result.price)
    }

    @Test fun manualEditOfDerivedFieldBecomesAuthoritative() {
        var result = FuelInputs("", "", "")
        result = FuelLinking.onUserEdit(result, FuelField.PRICE, "7.50")
        result = FuelLinking.onUserEdit(result, FuelField.AMOUNT, "60")
        result = FuelLinking.onUserEdit(result, FuelField.LITERS, "7")
        assertEquals("7", result.liters)
        assertEquals("60", result.amount)
        assertEquals("8.57", result.price)
    }

    @Test fun editingLitersWithAmountAndPriceKeepsLitersAndRecomputesPrice() {
        var result = FuelInputs("", "", "")
        result = FuelLinking.onUserEdit(result, FuelField.PRICE, "7.50")
        result = FuelLinking.onUserEdit(result, FuelField.AMOUNT, "60")
        result = FuelLinking.onUserEdit(result, FuelField.LITERS, "10")
        assertEquals("10", result.liters)
        assertEquals("6.00", result.price)
        assertEquals("60", result.amount)
    }

    @Test fun editingPriceWithAmountAndLitersKeepsPriceAndRecomputesLiters() {
        var result = FuelInputs("", "", "")
        result = FuelLinking.onUserEdit(result, FuelField.PRICE, "7.50")
        result = FuelLinking.onUserEdit(result, FuelField.AMOUNT, "60")
        result = FuelLinking.onUserEdit(result, FuelField.PRICE, "6.00")
        assertEquals("6.00", result.price)
        assertEquals("60", result.amount)
        assertEquals("10.00", result.liters)
    }

    @Test fun systemPricePrefillDoesNotBecomeAUserEditAndRecalculatesSafely() {
        var result = FuelLinking.onSystemPricePrefill(FuelInputs("", "", ""), "8.31")
        assertEquals("8.31", result.price)
        assertTrue(result.editOrder.isEmpty())

        result = FuelLinking.onUserEdit(result, FuelField.AMOUNT, "50")
        assertEquals("6.02", result.liters)
        assertEquals(listOf(FuelField.AMOUNT), result.editOrder)

        result = FuelLinking.onSystemPricePrefill(result, "7.80")
        assertEquals("7.80", result.price)
        assertEquals("50", result.amount)
        assertEquals("6.41", result.liters)
        assertEquals(listOf(FuelField.AMOUNT), result.editOrder)
    }

    @Test fun emptyAndIntermediateValuesDoNotDeriveOrCreateNonFiniteValues() {
        var result = FuelInputs("", "", "")
        result = FuelLinking.onUserEdit(result, FuelField.PRICE, "7.")
        result = FuelLinking.onUserEdit(result, FuelField.AMOUNT, "60")
        assertEquals("", result.liters)
        result = FuelLinking.onUserEdit(result, FuelField.PRICE, "7.50")
        assertEquals("8.00", result.liters)
        result = FuelLinking.onUserEdit(result, FuelField.AMOUNT, "")
        assertEquals("7.50", result.price)
        assertEquals("", result.amount)
        assertEquals("8.00", result.liters)

        var invalid = FuelInputs("", "", "")
        invalid = FuelLinking.onUserEdit(invalid, FuelField.PRICE, "7.50")
        invalid = FuelLinking.onUserEdit(invalid, FuelField.AMOUNT, "0")
        assertEquals("", invalid.liters)
        invalid = FuelLinking.onUserEdit(invalid, FuelField.LITERS, "8")
        assertEquals("60.00", invalid.amount)
        assertTrue("generated amount must be finite", invalid.amount.toDoubleOrNull()?.isFinite() == true)
    }
}
