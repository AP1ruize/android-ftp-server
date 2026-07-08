package com.example.ftpembed.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredTokensTest {
    @Test
    fun isAccessExpired_appliesThirtySecondBuffer() {
        val tokens = tokens(expiresAt = 1_000L)

        assertFalse(tokens.isAccessExpired(nowEpochSec = 969L))
        assertTrue(tokens.isAccessExpired(nowEpochSec = 970L))
        assertTrue(tokens.isAccessExpired(nowEpochSec = 1_000L))
    }

    @Test
    fun isAccessNearExpiry_trueWithinSixtySecondsOfExp() {
        val tokens = tokens(expiresAt = 1_000L)

        assertFalse(tokens.isAccessNearExpiry(nowEpochSec = 939L))
        assertTrue(tokens.isAccessNearExpiry(nowEpochSec = 940L))
        assertTrue(tokens.isAccessNearExpiry(nowEpochSec = 999L))
    }

    private fun tokens(expiresAt: Long): StoredTokens =
        StoredTokens(
            accessToken = "access",
            refreshToken = "refresh",
            accessExpiresAtEpochSec = expiresAt,
            email = null,
        )
}
