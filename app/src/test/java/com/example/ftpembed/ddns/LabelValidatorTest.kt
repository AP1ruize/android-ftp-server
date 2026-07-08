package com.example.ftpembed.ddns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelValidatorTest {
    @Test
    fun validate_acceptsFourLowercaseLettersAndDigits() {
        val result = LabelValidator.validate("Ab12")

        assertEquals(LabelValidationResult.Valid("ab12"), result)
    }

    @Test
    fun validate_rejectsWrongLength() {
        val result = LabelValidator.validate("abc")

        assertTrue(result is LabelValidationResult.Invalid)
    }

    @Test
    fun validate_rejectsReservedLabels() {
        val result = LabelValidator.validate("auth")

        assertTrue(result is LabelValidationResult.Invalid)
    }

    @Test
    fun validate_rejectsSymbols() {
        val result = LabelValidator.validate("a-12")

        assertTrue(result is LabelValidationResult.Invalid)
    }
}
