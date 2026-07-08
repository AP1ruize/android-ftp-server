package com.example.ftpembed.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.ftpembed.debug.AppEventLog
import com.example.ftpembed.ddns.AccessTokenProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.openid.appauth.AuthorizationException

sealed class AuthState {
    data object LoggedOut : AuthState()
    data object Loading : AuthState()
    data class LoggedIn(val email: String?) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthRepository(
    private val tokenStore: TokenStore,
    private val oidcAuthManager: OidcAuthManager,
) : AccessTokenProvider {
    companion object {
        fun create(context: Context): AuthRepository {
            val appContext = context.applicationContext
            return AuthRepository(
                tokenStore = TokenStore(appContext),
                oidcAuthManager = OidcAuthManager(appContext),
            )
        }
    }
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val refreshMutex = Mutex()

    suspend fun initialize() {
        AppEventLog.log("Auth", "Initializing session")
        val tokens = tokenStore.load()
        _state.value = when {
            tokens == null -> {
                AppEventLog.log("Auth", "No stored tokens -> LoggedOut")
                AuthState.LoggedOut
            }
            !tokens.isAccessExpired() -> {
                AppEventLog.log("Auth", "Restored session for ${tokens.email ?: "user"}")
                AuthState.LoggedIn(tokens.email)
            }
            tokens.refreshToken.isNotBlank() -> {
                AppEventLog.log("Auth", "Access expired; refresh token available")
                AuthState.LoggedIn(tokens.email)
            }
            else -> {
                tokenStore.clear()
                AppEventLog.log("Auth", "Tokens expired and no refresh -> LoggedOut")
                AuthState.LoggedOut
            }
        }
    }

    suspend fun startLogin(
        activity: Activity,
        launcher: ActivityResultLauncher<Intent>,
    ) {
        _state.value = AuthState.Loading
        AppEventLog.log("Auth", "Starting PKCE login")
        try {
            oidcAuthManager.startAuthorization(activity, launcher)
        } catch (e: Exception) {
            AppEventLog.log("Auth", "Start login failed: ${e.message}")
            _state.value = AuthState.Error(e.message ?: "Failed to start login")
            throw e
        }
    }

    fun onLoginCancelled() {
        if (_state.value == AuthState.Loading) {
            AppEventLog.log("Auth", "Login cancelled by user")
            _state.value = AuthState.LoggedOut
        }
    }

    suspend fun handleRedirectIntent(intent: Intent): Boolean {
        if (!isOAuthRedirect(intent) && !hasAuthorizationResponse(intent)) return false

        _state.value = AuthState.Loading
        AppEventLog.log("Auth", "Processing OAuth callback")
        return try {
            val tokenResult = oidcAuthManager.handleAuthorizationResponse(intent)
            persistTokenResult(tokenResult)
            AppEventLog.log("Auth", "Login success")
            true
        } catch (e: AuthorizationException) {
            AppEventLog.log("Auth", "Login AuthorizationException: ${e.error} ${e.errorDescription}")
            _state.value = if (e == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW) {
                AuthState.LoggedOut
            } else {
                AuthState.Error(e.errorDescription ?: e.error ?: "Login failed")
            }
            true
        } catch (e: Exception) {
            AppEventLog.log("Auth", "Login failed: ${e.message}")
            _state.value = AuthState.Error(e.message ?: "Login failed")
            true
        }
    }

    override suspend fun getAccessToken(forceRefresh: Boolean): String? =
        getValidAccessToken(forceRefresh)

    suspend fun getValidAccessToken(forceRefresh: Boolean = false): String? {
        val tokens = tokenStore.load() ?: run {
            _state.value = AuthState.LoggedOut
            return null
        }

        if (forceRefresh && !shouldRefresh(tokens, forceRefresh = true)) {
            return null
        }

        if (!shouldRefresh(tokens, forceRefresh)) {
            return tokens.accessToken
        }

        return refreshAndGetAccessToken(forceRefresh)
    }

    suspend fun logout(endSessionInBrowser: Boolean = false) {
        tokenStore.clear()
        _state.value = AuthState.LoggedOut
        if (endSessionInBrowser) {
            oidcAuthManager.endSession(idTokenHint = null)
        }
    }

    private fun shouldRefresh(tokens: StoredTokens, forceRefresh: Boolean): Boolean {
        return if (forceRefresh) {
            tokens.isAccessExpired() || tokens.isAccessNearExpiry()
        } else {
            tokens.isAccessExpired()
        }
    }

    private suspend fun refreshAndGetAccessToken(forceRefresh: Boolean): String? =
        refreshMutex.withLock {
            val latest = tokenStore.load() ?: run {
                _state.value = AuthState.LoggedOut
                return null
            }

            if (!shouldRefresh(latest, forceRefresh)) {
                return latest.accessToken
            }

            if (latest.refreshToken.isBlank()) {
                tokenStore.clear()
                _state.value = AuthState.LoggedOut
                return null
            }

            return try {
                val refreshed = oidcAuthManager.refreshToken(latest.refreshToken)
                val refreshToken = refreshed.refreshToken ?: latest.refreshToken
                tokenStore.save(
                    accessToken = refreshed.accessToken,
                    refreshToken = refreshToken,
                    idToken = refreshed.idToken,
                )
                val saved = tokenStore.load()
                _state.value = AuthState.LoggedIn(saved?.email)
                refreshed.accessToken
            } catch (_: Exception) {
                tokenStore.clear()
                _state.value = AuthState.LoggedOut
                null
            }
        }

    private fun persistTokenResult(tokenResult: OidcTokenResult) {
        val refreshToken = tokenResult.refreshToken
        if (refreshToken.isNullOrBlank()) {
            AppEventLog.log("Auth", "Login failed: missing refresh_token (check offline_access scope)")
            _state.value = AuthState.Error("登录失败：未获得 refresh_token，请确认 scope 含 offline_access")
            return
        }

        tokenStore.save(
            accessToken = tokenResult.accessToken,
            refreshToken = refreshToken,
            idToken = tokenResult.idToken,
        )
        val saved = tokenStore.load()
        _state.value = AuthState.LoggedIn(saved?.email)
    }

    private fun isOAuthRedirect(intent: Intent): Boolean {
        val data = intent.data ?: return false
        return data.scheme == AuthConfig.redirectScheme &&
            data.host == AuthConfig.redirectHost
    }

    private fun hasAuthorizationResponse(intent: Intent): Boolean {
        return net.openid.appauth.AuthorizationResponse.fromIntent(intent) != null ||
            AuthorizationException.fromIntent(intent) != null
    }
}
