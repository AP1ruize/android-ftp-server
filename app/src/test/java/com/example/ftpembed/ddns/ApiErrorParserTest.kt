package com.example.ftpembed.ddns

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiErrorParserTest {
    @Test
    fun parse_readsCodeAndMessageFromJsonBody() {
        val error = ApiErrorParser.parse(
            409,
            """{"code":"conflict","message":"label already exists"}""",
        )

        assertEquals(409, error.httpStatus)
        assertEquals("conflict", error.code)
        assertEquals("label already exists", error.message)
    }

    @Test
    fun parse_usesStatusFallbackWhenBodyIsMissing() {
        val error = ApiErrorParser.parse(429, null)

        assertEquals("throttled", error.code)
        assertEquals("DDNS updates are too frequent. Try again later.", error.message)
    }
}
