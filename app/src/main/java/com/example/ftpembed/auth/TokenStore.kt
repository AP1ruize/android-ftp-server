package com.example.ftpembed.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class StoredTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtEpochSec: Long,
    val email: String?,
) {
    fun isAccessExpired(nowEpochSec: Long = currentEpochSec()): Boolean =
        nowEpochSec >= accessExpiresAtEpochSec - ACCESS_EXPIRY_BUFFER_SEC

    fun isAccessNearExpiry(nowEpochSec: Long = currentEpochSec()): Boolean =
        nowEpochSec >= accessExpiresAtEpochSec - NEAR_EXPIRY_WINDOW_SEC

    companion object {
        const val ACCESS_EXPIRY_BUFFER_SEC = 30L
        const val NEAR_EXPIRY_WINDOW_SEC = 60L

        fun currentEpochSec(): Long = System.currentTimeMillis() / 1000L
    }
}

class TokenStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFS_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(accessToken: String, refreshToken: String, idToken: String?) {
        val exp = JwtPayloadParser.parseExp(accessToken)
            ?: throw IllegalArgumentException("access_token missing exp claim")
        val email = idToken?.let { JwtPayloadParser.parseEmail(it) }

        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, exp)
            .apply {
                if (email != null) {
                    putString(KEY_EMAIL, email)
                } else {
                    remove(KEY_EMAIL)
                }
            }
            .apply()
    }

    fun load(): StoredTokens? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        if (!prefs.contains(KEY_ACCESS_EXPIRES_AT)) return null

        return StoredTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessExpiresAtEpochSec = prefs.getLong(KEY_ACCESS_EXPIRES_AT, 0L),
            email = prefs.getString(KEY_EMAIL, null),
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "auth_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at_epoch_sec"
        private const val KEY_EMAIL = "email"
    }
}
