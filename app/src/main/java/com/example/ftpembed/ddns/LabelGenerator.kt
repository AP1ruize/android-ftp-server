package com.example.ftpembed.ddns

import java.security.SecureRandom

object LabelGenerator {
    private val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray()
    private val random = SecureRandom()

    fun generate(maxAttempts: Int = 32): String {
        repeat(maxAttempts) {
            val chars = CharArray(4) { alphabet[random.nextInt(alphabet.size)] }
            val candidate = String(chars)
            if (LabelValidator.validate(candidate) is LabelValidationResult.Valid) {
                return candidate
            }
        }
        error("Unable to generate a valid DDNS label")
    }
}
