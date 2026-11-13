package com.xjb.linti.ui.screens

import java.math.BigDecimal

internal object ExamInputValidation {
    private val integer = Regex("[0-9]+")
    private val decimal = Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")
    private val half = BigDecimal("0.5")
    private val maxPoints = BigDecimal("10")

    fun count(text: String, available: Int): Int? {
        if (!integer.matches(text)) return null
        return text.toIntOrNull()?.takeIf { it in 0..available }
    }

    fun points(text: String): Double? {
        if (!decimal.matches(text)) return null
        val value = text.toBigDecimalOrNull() ?: return null
        if (value < half || value > maxPoints || value.remainder(half).signum() != 0) return null
        return value.toDouble()
    }
}
