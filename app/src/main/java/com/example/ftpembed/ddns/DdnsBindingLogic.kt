package com.example.ftpembed.ddns

/**
 * Pure helpers for sticky-FQDN binding.
 * Timestamps are RFC3339 strings from the control plane; lexicographic order matches time order.
 */
object DdnsBindingLogic {
    fun findByLabel(records: List<DdnsRecord>, label: String): DdnsRecord? =
        records.firstOrNull { it.label == label }

    fun pickNewestByUpdatedAt(records: List<DdnsRecord>): DdnsRecord? =
        records.maxByOrNull { recencyKey(it) }

    /**
     * Picks the oldest-updated record whose label is not [exceptLabel].
     */
    fun pickEvictionVictim(
        records: List<DdnsRecord>,
        exceptLabel: String?,
    ): DdnsRecord? {
        return records
            .filter { exceptLabel.isNullOrBlank() || it.label != exceptLabel }
            .minByOrNull { recencyKey(it) }
    }

    fun recencyKey(record: DdnsRecord): String =
        record.updatedAt?.takeIf { it.isNotBlank() }
            ?: record.createdAt?.takeIf { it.isNotBlank() }
            ?: ""
}
