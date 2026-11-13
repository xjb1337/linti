package com.xjb.linti.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExamInputValidationTest {
    @Test
    fun `question count accepts only integers in the available range`() {
        assertEquals(0, ExamInputValidation.count("0", 0))
        assertEquals(5, ExamInputValidation.count("005", 5))
        listOf("", "-1", "6", "2.0", "1a", "2147483648").forEach {
            assertNull(ExamInputValidation.count(it, 5))
        }
        assertNull(ExamInputValidation.count("1", 0))
    }

    @Test
    fun `points preserve half point increments and bounds`() {
        mapOf(".5" to 0.5, "0.50" to 0.5, "2." to 2.0, "2.5" to 2.5, "10" to 10.0)
            .forEach { (input, expected) ->
                assertEquals(expected, ExamInputValidation.points(input)!!, 0.0)
            }
        listOf("", ".", "0", "0.25", "10.5", "3.3", "2..0", "1e0", "0.5000000000000000001")
            .forEach { assertNull(ExamInputValidation.points(it)) }
    }
}
