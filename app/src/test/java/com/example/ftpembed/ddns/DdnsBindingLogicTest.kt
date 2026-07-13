package com.example.ftpembed.ddns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DdnsBindingLogicTest {
    private fun record(
        label: String,
        ipv4: String,
        updatedAt: String? = null,
        createdAt: String? = null,
    ) = DdnsRecord(
        label = label,
        fqdn = "$label.shard.ah.app",
        ipv4 = ipv4,
        ttl = 15,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun findByLabel_returnsMatchingRecord() {
        val records = listOf(
            record("aaaa", "10.0.0.1"),
            record("bbbb", "10.0.0.2"),
        )
        assertEquals("bbbb", DdnsBindingLogic.findByLabel(records, "bbbb")?.label)
        assertNull(DdnsBindingLogic.findByLabel(records, "zzzz"))
    }

    @Test
    fun pickNewest_usesUpdatedAt() {
        val records = listOf(
            record("old1", "10.0.0.1", updatedAt = "2026-01-01T00:00:00Z"),
            record("new1", "10.0.0.2", updatedAt = "2026-06-01T00:00:00Z"),
            record("mid1", "10.0.0.3", updatedAt = "2026-03-01T00:00:00Z"),
        )
        assertEquals("new1", DdnsBindingLogic.pickNewestByUpdatedAt(records)?.label)
    }

    @Test
    fun pickNewest_fallsBackToCreatedAt() {
        val records = listOf(
            record("c1", "10.0.0.1", createdAt = "2026-02-01T00:00:00Z"),
            record("c2", "10.0.0.2", createdAt = "2026-05-01T00:00:00Z"),
        )
        assertEquals("c2", DdnsBindingLogic.pickNewestByUpdatedAt(records)?.label)
    }

    @Test
    fun pickNewest_doesNotPreferMatchingIp() {
        // Sticky strategy: newest by updated_at, not by current LAN IP.
        val records = listOf(
            record("ipMatch", "192.168.1.10", updatedAt = "2026-01-01T00:00:00Z"),
            record("newest", "10.0.0.1", updatedAt = "2026-07-01T00:00:00Z"),
        )
        assertEquals("newest", DdnsBindingLogic.pickNewestByUpdatedAt(records)?.label)
    }

    @Test
    fun pickEviction_skipsExceptLabel() {
        val records = listOf(
            record("keep", "10.0.0.9", updatedAt = "2020-01-01T00:00:00Z"),
            record("old", "10.0.0.1", updatedAt = "2020-01-03T00:00:00Z"),
            record("newer", "10.0.0.2", updatedAt = "2026-01-01T00:00:00Z"),
        )
        val victim = DdnsBindingLogic.pickEvictionVictim(records, exceptLabel = "keep")
        assertEquals("old", victim?.label)
    }

    @Test
    fun pickEviction_returnsNullWhenOnlyProtected() {
        val records = listOf(
            record("keep", "10.0.0.1", updatedAt = "2020-01-01T00:00:00Z"),
        )
        assertNull(DdnsBindingLogic.pickEvictionVictim(records, exceptLabel = "keep"))
    }

    @Test
    fun pickEviction_picksOldestWhenNoExcept() {
        val records = listOf(
            record("a", "10.0.0.1", updatedAt = "2026-01-01T00:00:00Z"),
            record("b", "10.0.0.2", updatedAt = "2025-01-01T00:00:00Z"),
            record("c", "10.0.0.3", updatedAt = "2024-01-01T00:00:00Z"),
        )
        assertEquals("c", DdnsBindingLogic.pickEvictionVictim(records, exceptLabel = null)?.label)
    }

    @Test
    fun recencyKey_prefersUpdatedAtOverCreatedAt() {
        val record = record(
            "x",
            "1.1.1.1",
            updatedAt = "2026-07-01T00:00:00Z",
            createdAt = "2020-01-01T00:00:00Z",
        )
        assertEquals("2026-07-01T00:00:00Z", DdnsBindingLogic.recencyKey(record))
    }
}
