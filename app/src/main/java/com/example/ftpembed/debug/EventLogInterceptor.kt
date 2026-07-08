package com.example.ftpembed.debug

import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

class EventLogInterceptor(private val tag: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBody = request.body?.let { body ->
            Buffer().also { body.writeTo(it) }.readUtf8()
        }.orEmpty()

        val authHeader = request.header("Authorization")
        val safeHeaders = buildString {
            request.headers.forEach { (name, value) ->
                append(name)
                append(": ")
                append(
                    if (name.equals("Authorization", ignoreCase = true)) {
                        maskBearer(value)
                    } else {
                        value
                    },
                )
                append('\n')
            }
        }

        AppEventLog.log(tag, ">>> ${request.method} ${request.url}")
        if (safeHeaders.isNotBlank()) {
            AppEventLog.log(tag, ">>> Headers:\n$safeHeaders")
        }
        if (requestBody.isNotBlank()) {
            AppEventLog.log(tag, ">>> Body: $requestBody")
        }

        val response = chain.proceed(request)
        val responseBody = response.peekBody(MAX_LOG_BODY_BYTES).string()

        AppEventLog.log(tag, "<<< ${response.code} ${request.url}")
        if (responseBody.isNotBlank()) {
            AppEventLog.log(tag, "<<< Body: ${maskTokensInJson(responseBody)}")
        }
        return response
    }

    private fun maskBearer(value: String): String {
        if (!value.startsWith("Bearer ", ignoreCase = true)) return value
        val token = value.removePrefix("Bearer ").removePrefix("bearer ")
        return "Bearer ${maskSecret(token)}"
    }

    private fun maskTokensInJson(body: String): String {
        return body
            .replace(Regex(""""access_token"\s*:\s*"[^"]+""""), """"access_token":"***"""")
            .replace(Regex(""""refresh_token"\s*:\s*"[^"]+""""), """"refresh_token":"***"""")
            .replace(Regex(""""id_token"\s*:\s*"[^"]+""""), """"id_token":"***"""")
    }

    private fun maskSecret(value: String): String {
        if (value.length <= 12) return "***"
        return "${value.take(6)}...${value.takeLast(4)}"
    }

    companion object {
        private const val MAX_LOG_BODY_BYTES = 64 * 1024L
    }
}
