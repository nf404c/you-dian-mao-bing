package com.fuellog.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class FuelField { PRICE, AMOUNT, LITERS }

fun String.toFuelField(): FuelField? = runCatching { FuelField.valueOf(this) }.getOrNull()

data class FuelInputs(
    val price: String,
    val amount: String,
    val liters: String,
    val editOrder: List<FuelField> = emptyList()
)

/**
 * Keeps user-entered values authoritative and derives at most one other field.
 * Automatic values never become part of editOrder, so a recalculation cannot loop.
 */
object FuelLinking {
    fun onUserEdit(inputs: FuelInputs, field: FuelField, value: String): FuelInputs {
        val edited = when (field) {
            FuelField.PRICE -> inputs.copy(price = value)
            FuelField.AMOUNT -> inputs.copy(amount = value)
            FuelField.LITERS -> inputs.copy(liters = value)
        }
        val order = if (value.isBlank()) {
            edited.editOrder - field
        } else {
            (edited.editOrder - field) + field
        }
        val enteredNumber = value.toCalculationDecimalOrNull()
        // Preserve an empty, zero, negative, or otherwise unfinished edit exactly as typed.
        // A later valid edit will resume deriving the missing field.
        if (enteredNumber == null || enteredNumber <= BigDecimal.ZERO) {
            return edited.copy(editOrder = order)
        }
        return derive(edited.copy(editOrder = order))
    }

    /**
     * A remembered grade price is a system prefill, not a user edit. It therefore
     * never changes editOrder, while the explicitly selected grade price still
     * participates in one safe recalculation.
     */
    fun onSystemPricePrefill(inputs: FuelInputs, value: String): FuelInputs {
        val prefilled = inputs.copy(price = value)
        val price = value.toCalculationDecimalOrNull()
        if (price == null || price <= BigDecimal.ZERO) return prefilled

        fun isValid(field: FuelField): Boolean = prefilled.valueOf(field)
            .toCalculationDecimalOrNull()
            ?.let { it > BigDecimal.ZERO } == true

        val other = inputs.editOrder.asReversed().firstOrNull { it != FuelField.PRICE && isValid(it) }
            ?: FuelField.entries.firstOrNull { it != FuelField.PRICE && isValid(it) }
            ?: return prefilled
        return derive(prefilled, forcedBasis = listOf(FuelField.PRICE, other))
    }

    private fun derive(inputs: FuelInputs, forcedBasis: List<FuelField>? = null): FuelInputs {
        val parsed = mapOf(
            FuelField.PRICE to inputs.price.toCalculationDecimalOrNull(),
            FuelField.AMOUNT to inputs.amount.toCalculationDecimalOrNull(),
            FuelField.LITERS to inputs.liters.toCalculationDecimalOrNull()
        )
        val valid = parsed.filterValues { it != null && it > BigDecimal.ZERO }.keys
        if (valid.size < 2) return inputs

        val recent = inputs.editOrder.asReversed().filter { it in valid }.distinct()
        val basis = forcedBasis ?: (recent + valid.filterNot { it in recent }).take(2)
        if (basis.size < 2 || basis.any { it !in valid }) return inputs
        val derived = FuelField.entries.first { it !in basis }
        val price = parsed[FuelField.PRICE]
        val amount = parsed[FuelField.AMOUNT]
        val liters = parsed[FuelField.LITERS]
        val computed = when (derived) {
            FuelField.PRICE -> {
                if (amount == null || liters == null || liters <= BigDecimal.ZERO) return inputs
                amount.divide(liters, 6, RoundingMode.HALF_UP)
            }
            FuelField.AMOUNT -> {
                if (price == null || liters == null) return inputs
                price.multiply(liters)
            }
            FuelField.LITERS -> {
                if (amount == null || price == null || price <= BigDecimal.ZERO) return inputs
                amount.divide(price, 6, RoundingMode.HALF_UP)
            }
        }
        val value = computed.setScale(2, RoundingMode.HALF_UP).toPlainString()
        return when (derived) {
            FuelField.PRICE -> inputs.copy(price = value)
            FuelField.AMOUNT -> inputs.copy(amount = value)
            FuelField.LITERS -> inputs.copy(liters = value)
        }
    }
}

private fun FuelInputs.valueOf(field: FuelField): String = when (field) {
    FuelField.PRICE -> price
    FuelField.AMOUNT -> amount
    FuelField.LITERS -> liters
}

/** Treat a trailing decimal point as an unfinished edit, even though BigDecimal accepts it. */
private fun String.toCalculationDecimalOrNull(): BigDecimal? {
    val normalized = trim()
    if (normalized.isEmpty() || normalized.endsWith('.')) return null
    return normalized.toBigDecimalOrNull()
}
