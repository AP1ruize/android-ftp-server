package com.example.ftpembed.ddns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DdnsApiModelsTest {
    @Test
    fun recordResponse_mapsToDomainUsingServerFqdn() {
        val response = RecordResponse(
            id = "0192e8f0-1234-7000-8000-000000000001",
            label = "ab12",
            userShard = "K3M9X2",
            fqdn = "ab12.k3m9x2.ah.app",
            ipv4 = "192.168.0.42",
            ttl = 15,
            createdAt = "2026-07-08T10:00:00Z",
            updatedAt = "2026-07-08T10:00:00Z",
        )

        val record = response.toDomain()

        assertEquals("ab12", record.label)
        assertEquals("ab12.k3m9x2.ah.app", record.fqdn)
        assertEquals("192.168.0.42", record.ipv4)
        assertEquals(15, record.ttl)
        assertEquals("2026-07-08T10:00:00Z", record.createdAt)
        assertEquals("2026-07-08T10:00:00Z", record.updatedAt)
    }

    @Test
    fun myShardResponse_mapsToDomain() {
        val response = MyShardResponse(userShard = "K3M9X2", zone = "ah.app")

        val shard = response.toDomain()

        assertEquals("K3M9X2", shard.userShard)
        assertEquals("ah.app", shard.zone)
    }

    @Test
    fun quotaResponse_mapsToDomain() {
        val response = QuotaResponse(limit = 5, used = 2)

        val quota = response.toDomain()

        assertEquals(5, quota.limit)
        assertEquals(2, quota.used)
    }

    @Test
    fun updateResult_noChangeFlagIsPreservedOnRecordResponse() {
        val response = RecordResponse(
            label = "ab12",
            fqdn = "ab12.k3m9x2.ah.app",
            ipv4 = "192.168.0.42",
            ttl = 15,
            noChange = true,
        )

        assertTrue(response.noChange == true)
        assertEquals("ab12.k3m9x2.ah.app", response.toDomain().fqdn)
    }
}
