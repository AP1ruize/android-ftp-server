package com.example.ftpembed.ddns

object LabelValidator {
    private val reservedLabels = setOf(
        "www",
        "api",
        "ns1",
        "ns2",
        "mail",
        "ftp",
        "auth",
    )
    private val allowedPattern = Regex("^[a-z0-9]{4}$")

    fun normalize(input: String): String = input.trim().lowercase()

    fun validate(input: String): LabelValidationResult {
        val label = normalize(input)
        return when {
            label.length != 4 -> LabelValidationResult.Invalid("Label must be exactly 4 characters.")
            !allowedPattern.matches(label) -> {
                LabelValidationResult.Invalid("Use only lowercase letters and digits.")
            }
            label in reservedLabels -> LabelValidationResult.Invalid("This label is reserved.")
            else -> LabelValidationResult.Valid(label)
        }
    }
}

sealed class LabelValidationResult {
    data class Valid(val label: String) : LabelValidationResult()
    data class Invalid(val message: String) : LabelValidationResult()
}
