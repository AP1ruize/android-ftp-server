package com.example.ftpembed.auth

import android.content.Context

class OidcPendingAuthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveCodeVerifier(codeVerifier: String) {
        prefs.edit().putString(KEY_CODE_VERIFIER, codeVerifier).apply()
    }

    fun loadCodeVerifier(): String? = prefs.getString(KEY_CODE_VERIFIER, null)

    fun clear() {
        prefs.edit().remove(KEY_CODE_VERIFIER).apply()
    }

    companion object {
        private const val PREFS_NAME = "oidc_pending_auth"
        private const val KEY_CODE_VERIFIER = "code_verifier"
    }
}
