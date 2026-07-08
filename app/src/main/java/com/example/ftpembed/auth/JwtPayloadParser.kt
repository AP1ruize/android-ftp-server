package com.example.ftpembed.auth

import java.util.Base64

/**
 * Reads JWT payload claims without signature verification (client-side expiry / UI only).
 */
object JwtPayloadParser {
    fun parseExp(jwt: String): Long? = parseLongClaim(jwt, "exp")

    fun parseEmail(jwt: String): String? = parseStringClaim(jwt, "email")

    internal fun parseLongClaim(jwt: String, claim: String): Long? {
        val payload = decodePayload(jwt) ?: return null
        val pattern = Regex(""""$claim"\s*:\s*(\d+)""")
        return pattern.find(payload)?.groupValues?.get(1)?.toLongOrNull()
    }

    internal fun parseStringClaim(jwt: String, claim: String): String? {
        val payload = decodePayload(jwt) ?: return null
        val pattern = Regex(""""$claim"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        return pattern.find(payload)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }

    internal fun decodePayload(jwt: String): String? {
        val parts = jwt.split('.')
        if (parts.size < 2) return null
        return decodeBase64Url(parts[1])
    }

    internal fun decodeBase64Url(segment: String): String? {
        return try {
            val padded = segment.padBase64Url()
            val bytes = Base64.getUrlDecoder().decode(padded)
            String(bytes, Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun String.padBase64Url(): String {
        val remainder = length % 4
        if (remainder == 0) return this
        return this + "=".repeat(4 - remainder)
    }
}
