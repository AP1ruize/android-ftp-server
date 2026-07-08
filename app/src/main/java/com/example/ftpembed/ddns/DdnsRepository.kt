package com.example.ftpembed.ddns

class DdnsRepository(
    private val apiClient: DdnsApiClient,
    private val prefs: DdnsPrefs,
    @Suppress("unused") private val tokenProvider: AccessTokenProvider,
) {
    suspend fun fetchShard(): Result<MyShard> {
        return apiClient.fetchMyShard().mapCatching { response ->
            val shard = response.toDomain()
            prefs.userShard = shard.userShard
            prefs.zone = shard.zone
            shard
        }
    }

    suspend fun listRecords(): Result<Pair<List<DdnsRecord>, Quota>> {
        return apiClient.fetchRecords().map { response ->
            Pair(
                response.items.map { it.toDomain() },
                response.quota.toDomain(),
            )
        }
    }

    suspend fun createRecord(label: String, ipv4: String): Result<DdnsRecord> {
        val validation = LabelValidator.validate(label)
        if (validation is LabelValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(validation.message))
        }
        val normalizedLabel = (validation as LabelValidationResult.Valid).label

        if (prefs.userShard.isNullOrBlank() || prefs.zone.isNullOrBlank()) {
            fetchShard().onFailure { return Result.failure(it) }
        }

        return apiClient.createRecord(normalizedLabel, ipv4).map { response ->
            val record = response.toDomain()
            prefs.selectedLabel = record.label
            prefs.userShard = response.userShard ?: prefs.userShard
            prefs.lastSyncedIp = record.ipv4
            prefs.lastSyncAtEpochMs = System.currentTimeMillis()
            record
        }
    }

    suspend fun updateIp(label: String, ipv4: String): Result<DdnsUpdateResult> {
        return apiClient.updateRecord(label, ipv4).fold(
            onSuccess = { response ->
                val result = mapUpdateResponse(response)
                if (result is DdnsUpdateResult.Updated || result is DdnsUpdateResult.NoChange) {
                    val ip = when (result) {
                        is DdnsUpdateResult.Updated -> result.record.ipv4
                        is DdnsUpdateResult.NoChange -> result.record.ipv4
                        else -> ipv4
                    }
                    prefs.lastSyncedIp = ip
                    prefs.lastSyncAtEpochMs = System.currentTimeMillis()
                }
                Result.success(result)
            },
            onFailure = { error ->
                mapUpdateFailure(error)?.let { Result.success(it) }
                    ?: Result.failure(error)
            },
        )
    }

    suspend fun deleteRecord(label: String): Result<Unit> =
        apiClient.deleteRecord(label).onSuccess {
            if (prefs.selectedLabel == label) {
                prefs.selectedLabel = null
            }
        }
}

internal fun mapUpdateResponse(response: RecordResponse): DdnsUpdateResult {
    val record = response.toDomain()
    return if (response.noChange == true) {
        DdnsUpdateResult.NoChange(record)
    } else {
        DdnsUpdateResult.Updated(record)
    }
}

internal fun mapUpdateFailure(error: Throwable): DdnsUpdateResult? {
    return if (error is DdnsApiException && error.error.code == "throttled") {
        DdnsUpdateResult.Throttled
    } else {
        null
    }
}
