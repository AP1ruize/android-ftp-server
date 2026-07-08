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
)

sealed class DdnsSyncStatus {
    data object Idle : DdnsSyncStatus()
    data object Syncing : DdnsSyncStatus()
    data class Success(val ip: String, val epochMillis: Long) : DdnsSyncStatus()
    data class Failed(val error: DdnsApiError) : DdnsSyncStatus()
}
