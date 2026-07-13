package com.example.ftpembed.ddns

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MyShardResponse(
    @Json(name = "user_shard") val userShard: String,
    @Json(name = "zone") val zone: String,
)

@JsonClass(generateAdapter = true)
data class RecordsListResponse(
    @Json(name = "items") val items: List<RecordResponse>,
    @Json(name = "quota") val quota: QuotaResponse,
)

@JsonClass(generateAdapter = true)
data class QuotaResponse(
    @Json(name = "limit") val limit: Int,
    @Json(name = "used") val used: Int,
)

@JsonClass(generateAdapter = true)
data class RecordResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "label") val label: String,
    @Json(name = "user_shard") val userShard: String? = null,
    @Json(name = "fqdn") val fqdn: String,
    @Json(name = "ipv4") val ipv4: String,
    @Json(name = "ttl") val ttl: Int,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "no_change") val noChange: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class CreateRecordRequest(
    @Json(name = "label") val label: String,
    @Json(name = "ipv4") val ipv4: String,
)

@JsonClass(generateAdapter = true)
data class UpdateRecordRequest(
    @Json(name = "ipv4") val ipv4: String,
)

internal fun MyShardResponse.toDomain(): MyShard =
    MyShard(userShard = userShard, zone = zone)

internal fun QuotaResponse.toDomain(): Quota =
    Quota(limit = limit, used = used)

internal fun RecordResponse.toDomain(): DdnsRecord =
    DdnsRecord(
        label = label,
        fqdn = fqdn,
        ipv4 = ipv4,
        ttl = ttl,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
