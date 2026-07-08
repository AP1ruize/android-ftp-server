package com.example.ftpembed.ddns

object ApiErrorParser {
    private val codePattern = Regex("\"code\"\\s*:\\s*\"([^\"]+)\"")
    private val messagePattern = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"")

    fun parse(statusCode: Int, body: String?): DdnsApiError {
        val code = body?.let { codePattern.find(it)?.groupValues?.getOrNull(1) }
        val message = body?.let { messagePattern.find(it)?.groupValues?.getOrNull(1) }
        return DdnsApiError(
            httpStatus = statusCode,
            code = code ?: fallbackCode(statusCode),
            message = message?.ifBlank { null } ?: fallbackMessage(statusCode),
        )
    }

    private fun fallbackCode(statusCode: Int): String = when (statusCode) {
        400 -> "bad_request"
        401 -> "unauthorized"
        403 -> "forbidden"
        404 -> "not_found"
        409 -> "conflict"
        429 -> "throttled"
        else -> "http_$statusCode"
    }

    private fun fallbackMessage(statusCode: Int): String = when (statusCode) {
        401 -> "Login expired. Please sign in again."
        403 -> "This account is not allowed to perform that action."
        409 -> "That label is already in use."
        429 -> "DDNS updates are too frequent. Try again later."
        else -> "Request failed with HTTP $statusCode."
    }
}

data class DdnsApiError(
    val httpStatus: Int,
    val code: String,
    val message: String,
)
