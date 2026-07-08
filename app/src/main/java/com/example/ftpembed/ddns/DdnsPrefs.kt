package com.example.ftpembed.ddns

import android.content.Context

class DdnsPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedLabel: String?
        get() = prefs.getString(KEY_SELECTED_LABEL, null)
        set(value) = prefs.edit().putString(KEY_SELECTED_LABEL, value).apply()

    var userShard: String?
        get() = prefs.getString(KEY_USER_SHARD, null)
        set(value) = prefs.edit().putString(KEY_USER_SHARD, value).apply()

    var zone: String?
        get() = prefs.getString(KEY_ZONE, null)
        set(value) = prefs.edit().putString(KEY_ZONE, value).apply()

    var lastSyncedIp: String?
        get() = prefs.getString(KEY_LAST_SYNCED_IP, null)
        set(value) = prefs.edit().putString(KEY_LAST_SYNCED_IP, value).apply()

    var lastSyncAtEpochMs: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT_EPOCH_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT_EPOCH_MS, value).apply()

    companion object {
        private const val PREFS_NAME = "ddns_prefs"
        private const val KEY_SELECTED_LABEL = "selected_label"
        private const val KEY_USER_SHARD = "user_shard"
        private const val KEY_ZONE = "zone"
        private const val KEY_LAST_SYNCED_IP = "last_synced_ip"
        private const val KEY_LAST_SYNC_AT_EPOCH_MS = "last_sync_at_epoch_ms"
    }
}
