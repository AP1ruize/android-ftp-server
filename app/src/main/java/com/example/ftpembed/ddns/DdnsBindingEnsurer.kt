package com.example.ftpembed.ddns

import com.example.ftpembed.network.LocalIpProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DdnsBindingEnsurer(
    private val repository: DdnsRepository,
    private val prefs: DdnsPrefs,
    private val getLanIpv4: () -> String? = LocalIpProvider::getLanIpv4,
) {
    private val _activeRecord = MutableStateFlow<DdnsRecord?>(null)
    val activeRecord: StateFlow<DdnsRecord?> = _activeRecord.asStateFlow()

    /**
     * Heartbeat when LAN IP and selected label are unchanged; otherwise full sticky resolve.
     * Without a sticky label, skips (does not auto-create).
     */
    suspend fun ensureOrHeartbeat(): DdnsEnsureResult {
        val lanIp = getLanIpv4() ?: return DdnsEnsureResult.Skipped
        val selected = prefs.selectedLabel
        if (selected.isNullOrBlank()) {
            return DdnsEnsureResult.Skipped
        }
        if (prefs.lastSyncedIp == lanIp) {
            return repository.updateIp(selected, lanIp).fold(
                onSuccess = { result ->
                    recordFromUpdate(result)?.let { publish(it) }
                    DdnsEnsureResult.Heartbeat(result)
                },
                onFailure = { DdnsEnsureResult.Failed(it) },
            )
        }
        // IP changed: PATCH the sticky label only (do not switch/create).
        return bindExistingLabel(selected, lanIp, listed = null)
    }

    suspend fun resolveStickyBinding(lanIp: String? = getLanIpv4()): DdnsEnsureResult {
        val ip = lanIp ?: return DdnsEnsureResult.Skipped
        val listed = repository.listRecords()
        if (listed.isFailure) {
            return DdnsEnsureResult.Failed(listed.exceptionOrNull()!!)
        }
        val (records, quota) = listed.getOrThrow()
        val lastUsed = prefs.selectedLabel

        if (!lastUsed.isNullOrBlank()) {
            val existing = DdnsBindingLogic.findByLabel(records, lastUsed)
            if (existing != null) {
                return selectAndPatch(existing, ip)
            }
            return restoreMissingLastUsed(lastUsed, ip, records to quota)
        }

        if (records.isEmpty()) {
            return DdnsEnsureResult.NeedsCreate
        }
        val newest = DdnsBindingLogic.pickNewestByUpdatedAt(records)
            ?: return DdnsEnsureResult.NeedsCreate
        return selectAndPatch(newest, ip)
    }

    /** @deprecated Use [resolveStickyBinding]. */
    suspend fun ensureBinding(lanIp: String? = getLanIpv4()): DdnsEnsureResult =
        resolveStickyBinding(lanIp)

    /**
     * Manual save: switch to existing label or create (with quota eviction).
     */
    suspend fun saveUserLabel(label: String, ipv4: String): Result<SaveFqdnResult> {
        val validation = LabelValidator.validate(label)
        if (validation is LabelValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(validation.message))
        }
        val normalized = (validation as LabelValidationResult.Valid).label

        val listed = repository.listRecords()
        if (listed.isFailure) {
            return Result.failure(listed.exceptionOrNull()!!)
        }
        val (records, quota) = listed.getOrThrow()
        val existing = DdnsBindingLogic.findByLabel(records, normalized)
        if (existing != null) {
            prefs.selectedLabel = existing.label
            return repository.updateIp(existing.label, ipv4).fold(
                onSuccess = { result ->
                    val record = recordFromUpdate(result) ?: existing.copy(ipv4 = ipv4)
                    publish(record)
                    Result.success(SaveFqdnResult(record = record, wasExisting = true))
                },
                onFailure = { error ->
                    // Still bind locally if we have the record.
                    publish(existing)
                    if (error is DdnsApiException && error.error.code == "throttled") {
                        Result.success(SaveFqdnResult(record = existing, wasExisting = true))
                    } else {
                        Result.failure(error)
                    }
                },
            )
        }

        return when (
            val created = createWithQuota(
                label = normalized,
                ipv4 = ipv4,
                listed = records to quota,
                protectLabel = prefs.selectedLabel,
            )
        ) {
            is CreateQuotaResult.Ok -> {
                publish(created.record)
                Result.success(
                    SaveFqdnResult(
                        record = created.record,
                        evictedFqdn = created.evictedFqdn,
                        wasExisting = false,
                    ),
                )
            }
            is CreateQuotaResult.Err -> Result.failure(created.error)
        }
    }

    private suspend fun restoreMissingLastUsed(
        label: String,
        ipv4: String,
        listed: Pair<List<DdnsRecord>, Quota>,
    ): DdnsEnsureResult {
        val created = createWithQuota(
            label = label,
            ipv4 = ipv4,
            listed = listed,
            protectLabel = null,
        )
        return when (created) {
            is CreateQuotaResult.Ok -> {
                publish(created.record)
                DdnsEnsureResult.Restored(record = created.record, evictedFqdn = created.evictedFqdn)
            }
            is CreateQuotaResult.Err -> {
                prefs.selectedLabel = null
                DdnsEnsureResult.NeedsManualAction(
                    message = "恢复上次 FQDN（$label）失败，请手动选择或创建",
                    attemptedLabel = label,
                    evictedFqdn = created.evictedFqdn,
                )
            }
        }
    }

    private suspend fun bindExistingLabel(
        label: String,
        ipv4: String,
        listed: Pair<List<DdnsRecord>, Quota>?,
    ): DdnsEnsureResult {
        val records = if (listed != null) {
            listed.first
        } else {
            val fetched = repository.listRecords()
            if (fetched.isFailure) {
                // Fall back to direct PATCH without list.
                return patchLabel(label, ipv4)
            }
            fetched.getOrThrow().first
        }
        val existing = DdnsBindingLogic.findByLabel(records, label)
        if (existing != null) {
            return selectAndPatch(existing, ipv4)
        }
        // Sticky label vanished between heartbeats — try restore.
        val quota = listed?.second ?: repository.listRecords().getOrNull()?.second
            ?: Quota(limit = 5, used = records.size)
        return restoreMissingLastUsed(label, ipv4, records to quota)
    }

    private suspend fun selectAndPatch(record: DdnsRecord, ipv4: String): DdnsEnsureResult {
        prefs.selectedLabel = record.label
        return repository.updateIp(record.label, ipv4).fold(
            onSuccess = { result ->
                val bound = recordFromUpdate(result) ?: record.copy(ipv4 = ipv4)
                publish(bound)
                DdnsEnsureResult.Reused(bound, result)
            },
            onFailure = { error ->
                publish(record)
                DdnsEnsureResult.Failed(error)
            },
        )
    }

    private suspend fun patchLabel(label: String, ipv4: String): DdnsEnsureResult {
        return repository.updateIp(label, ipv4).fold(
            onSuccess = { result ->
                val record = recordFromUpdate(result)
                if (record != null) {
                    prefs.selectedLabel = record.label
                    publish(record)
                    DdnsEnsureResult.Reused(record, result)
                } else {
                    DdnsEnsureResult.Heartbeat(result)
                }
            },
            onFailure = { error ->
                val api = (error as? DdnsApiException)?.error
                if (api?.code == "not_found" || api?.httpStatus == 404) {
                    val listed = repository.listRecords()
                    if (listed.isFailure) {
                        prefs.selectedLabel = null
                        return@fold DdnsEnsureResult.NeedsManualAction(
                            message = "恢复上次 FQDN（$label）失败，请手动选择或创建",
                            attemptedLabel = label,
                        )
                    }
                    return@fold restoreMissingLastUsed(label, ipv4, listed.getOrThrow())
                }
                DdnsEnsureResult.Failed(error)
            },
        )
    }

    /**
     * Creates [label] with [ipv4], evicting oldest unprotected record when at quota.
     */
    private suspend fun createWithQuota(
        label: String,
        ipv4: String,
        listed: Pair<List<DdnsRecord>, Quota>,
        protectLabel: String?,
        allowQuotaRetry: Boolean = true,
    ): CreateQuotaResult {
        val (records, quota) = listed
        var evictedFqdn: String? = null

        if (quota.used >= quota.limit) {
            val victim = DdnsBindingLogic.pickEvictionVictim(records, exceptLabel = protectLabel)
                ?: return CreateQuotaResult.Err(
                    error = IllegalStateException("DDNS 配额已满，且没有可删除的旧记录"),
                )
            val deleted = repository.deleteRecord(victim.label)
            if (deleted.isFailure) {
                return CreateQuotaResult.Err(
                    error = deleted.exceptionOrNull()!!,
                )
            }
            evictedFqdn = victim.fqdn
        }

        val created = repository.createRecord(label, ipv4)
        if (created.isSuccess) {
            return CreateQuotaResult.Ok(created.getOrThrow(), evictedFqdn)
        }
        val error = created.exceptionOrNull()!!
        val apiError = (error as? DdnsApiException)?.error
        if (apiError?.code == "quota_exceeded" && allowQuotaRetry) {
            val refreshed = repository.listRecords()
            if (refreshed.isFailure) {
                return CreateQuotaResult.Err(refreshed.exceptionOrNull()!!, evictedFqdn)
            }
            return when (
                val retry = createWithQuota(
                    label = label,
                    ipv4 = ipv4,
                    listed = refreshed.getOrThrow(),
                    protectLabel = protectLabel,
                    allowQuotaRetry = false,
                )
            ) {
                is CreateQuotaResult.Ok -> CreateQuotaResult.Ok(
                    retry.record,
                    evictedFqdn ?: retry.evictedFqdn,
                )
                is CreateQuotaResult.Err -> CreateQuotaResult.Err(
                    retry.error,
                    evictedFqdn ?: retry.evictedFqdn,
                )
            }
        }
        return CreateQuotaResult.Err(error, evictedFqdn)
    }

    private fun publish(record: DdnsRecord) {
        _activeRecord.value = record
    }

    private fun recordFromUpdate(result: DdnsUpdateResult): DdnsRecord? = when (result) {
        is DdnsUpdateResult.Updated -> result.record
        is DdnsUpdateResult.NoChange -> result.record
        is DdnsUpdateResult.Throttled -> null
    }
}

private sealed class CreateQuotaResult {
    data class Ok(val record: DdnsRecord, val evictedFqdn: String?) : CreateQuotaResult()
    data class Err(val error: Throwable, val evictedFqdn: String? = null) : CreateQuotaResult()
}
