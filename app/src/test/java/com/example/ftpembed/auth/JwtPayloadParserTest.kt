package com.example.ftpembed.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class JwtPayloadParserTest {
    @Test
    fun parseExp_readsExpClaimFromPayload() {
        val jwt = jwtWithPayload("""{"sub":"user","exp":1700000000}""")

        assertEquals(1700000000L, JwtPayloadParser.parseExp(jwt))
    }

    @Test
    fun parseEmail_readsEmailClaimFromIdToken() {
        val jwt = jwtWithPayload("""{"email":"user@example.com","exp":1700000000}""")

        assertEquals("user@example.com", JwtPayloadParser.parseEmail(jwt))
    }

    @Test
    fun parseExp_returnsNullForMalformedJwt() {
        assertNull(JwtPayloadParser.parseExp("not-a-jwt"))
        assertNull(JwtPayloadParser.parseExp("a.b"))
    }

    private fun jwtWithPayload(payloadJson: String): String {
        val header = base64Url("""{"alg":"RS256","typ":"JWT"}""")
        val payload = base64Url(payloadJson)
        return "$header.$payload.signature"
    }

    private fun base64Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
}
