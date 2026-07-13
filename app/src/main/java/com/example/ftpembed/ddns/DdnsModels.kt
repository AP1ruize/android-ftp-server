package com.example.ftpembed.ddns

data class MyShard(
    val userShard: String,
    val zone: String,
)

data class DdnsRecord(
    val label: String,
    val fqdn: String,
    val ipv4: String,
    val ttl: Int,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

sealed class DdnsEnsureResult {
    data class Reused(
        val record: DdnsRecord,
        val update: DdnsUpdateResult,
        val evictedFqdn: String? = null,
    ) : DdnsEnsureResult()

    /** Sticky last-used was missing on server and successfully re-created. */
    data class Restored(
        val record: DdnsRecord,
        val evictedFqdn: String? = null,
    ) : DdnsEnsureResult()

    data class Created(
        val record: DdnsRecord,
        val evictedFqdn: String? = null,
    ) : DdnsEnsureResult()

    data class Heartbeat(val update: DdnsUpdateResult) : DdnsEnsureResult()
    data object Skipped : DdnsEnsureResult()
    data object NeedsCreate : DdnsEnsureResult()
    data class NeedsManualAction(
        val message: String,
        val attemptedLabel: String? = null,
        val evictedFqdn: String? = null,
    ) : DdnsEnsureResult()

    data class Failed(val error: Throwable) : DdnsEnsureResult()
}

data class SaveFqdnResult(
    val record: DdnsRecord,
    val evictedFqdn: String? = null,
    val wasExisting: Boolean = false,
)

data class Quota(
    val limit: Int,
    val used: Int,
)

sealed class DdnsUpdateResult {
    data class Updated(val record: DdnsRecord) : DdnsUpdateResult()
    data class NoChange(val record: DdnsRecord) : DdnsUpdateResult()
    data object Throttled : DdnsUpdateResult()
}

sealed class DdnsSyncStatus {
    data object Idle : DdnsSyncStatus()
    data object Syncing : DdnsSyncStatus()
    data object NeedsCreate : DdnsSyncStatus()
    data class Success(val ip: String, val epochMillis: Long) : DdnsSyncStatus()
    data class Failed(val error: DdnsApiError) : DdnsSyncStatus()
}

/** One-shot UI dialogs for restore failure / eviction notices. */
sealed class DdnsUiEvent {
    data class Alert(val title: String, val message: String) : DdnsUiEvent()
}
