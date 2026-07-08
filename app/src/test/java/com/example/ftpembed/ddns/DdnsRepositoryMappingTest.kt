package com.example.ftpembed.ddns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DdnsRepositoryMappingTest {
    @Test
    fun mapUpdateResponse_updatedWhenNoChangeFlagMissing() {
        val response = RecordResponse(
            label = "ab12",
            fqdn = "ab12.k3m9x2.ah.app",
            ipv4 = "192.168.0.20",
            ttl = 15,
        )

        val result = mapUpdateResponse(response)

        assertTrue(result is DdnsUpdateResult.Updated)
        assertEquals("192.168.0.20", (result as DdnsUpdateResult.Updated).record.ipv4)
    }

    @Test
    fun mapUpdateResponse_noChangeWhenFlagTrue() {
        val response = RecordResponse(
            label = "ab12",
            fqdn = "ab12.k3m9x2.ah.app",
            ipv4 = "192.168.0.20",
            ttl = 15,
            noChange = true,
        )

        val result = mapUpdateResponse(response)

        assertTrue(result is DdnsUpdateResult.NoChange)
    }

    @Test
    fun mapApiError_throttledMapsToSchedulerResult() {
        val error = DdnsApiException(
            DdnsApiError(httpStatus = 429, code = "throttled", message = "too fast"),
        )

        val result = mapUpdateFailure(error)

        assertEquals(DdnsUpdateResult.Throttled, result)
    }
}
