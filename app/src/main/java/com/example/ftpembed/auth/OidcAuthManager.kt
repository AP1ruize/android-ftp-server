package com.example.ftpembed.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import com.example.ftpembed.debug.AppEventLog
import com.example.ftpembed.debug.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

data class OidcTokenResult(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String?,
)

class OidcAuthManager(context: Context) {
    private val appContext = context.applicationContext
    private val authService = AuthorizationService(appContext)
    private val pendingAuthStore = OidcPendingAuthStore(appContext)
    private val httpClient = HttpClients.create("Rauthy")

    private var serviceConfig: AuthorizationServiceConfiguration? = null

    suspend fun fetchConfiguration(): AuthorizationServiceConfiguration {
        serviceConfig?.let { return it }

        val discoveryUrl = AuthConfig.discoveryUrl
        AppEventLog.log("Rauthy", "Discovery GET $discoveryUrl")

        val json = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(discoveryUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Discovery failed HTTP ${response.code}: $body")
                }
                body
            }
        }

        val config = OidcDiscoveryParser.parse(json)
        serviceConfig = config
        AppEventLog.log("Rauthy", "Discovery OK: authorize=${config.authorizationEndpoint}")
        return config
    }

    suspend fun startAuthorization(
        activity: Activity,
        launcher: ActivityResultLauncher<Intent>,
    ) {
        val config = fetchConfiguration()
        val request = AuthorizationRequest.Builder(
            config,
            AuthConfig.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(AuthConfig.redirectUri),
        )
            .setScope(AuthConfig.scope)
            .build()

        val codeVerifier = request.codeVerifier
            ?: throw IllegalStateException("PKCE code_verifier missing from authorization request")

        pendingAuthStore.saveCodeVerifier(codeVerifier)
        AppEventLog.log("Rauthy", "Opening authorize URL for client=${AuthConfig.clientId}")

        val customTabsIntent = authService.createCustomTabsIntentBuilder().build()
        val authIntent = authService.getAuthorizationRequestIntent(request, customTabsIntent)
        launcher.launch(authIntent)
    }

    suspend fun handleAuthorizationResponse(intent: Intent): OidcTokenResult {
        val response = AuthorizationResponse.fromIntent(intent)
        val authException = AuthorizationException.fromIntent(intent)

        AppEventLog.log(
            "Rauthy",
            "OAuth callback: code=${if (response?.authorizationCode.isNullOrBlank()) "missing" else "present"} " +
                "error=${authException?.error ?: "none"}",
        )

        if (authException != null) {
            pendingAuthStore.clear()
            throw authException
        }
        if (response == null) {
            pendingAuthStore.clear()
            throw IllegalStateException("OAuth redirect did not contain an authorization response")
        }

        val codeVerifier = pendingAuthStore.loadCodeVerifier()
            ?: throw IllegalStateException("OAuth PKCE state lost; please login again")

        val config = fetchConfiguration()
        val tokenRequest = TokenRequest.Builder(config, AuthConfig.clientId)
            .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
            .setAuthorizationCode(response.authorizationCode)
            .setRedirectUri(Uri.parse(AuthConfig.redirectUri))
            .setCodeVerifier(codeVerifier)
            .build()

        AppEventLog.log("Rauthy", "Token exchange grant=${tokenRequest.grantType}")

        val tokenResult = exchangeToken(tokenRequest)
        pendingAuthStore.clear()

        AppEventLog.log(
            "Rauthy",
            "Token exchange OK; refresh=${if (tokenResult.refreshToken.isNullOrBlank()) "missing" else "present"}",
        )
        return tokenResult
    }

    suspend fun refreshToken(refreshToken: String): OidcTokenResult {
        val config = fetchConfiguration()
        val tokenRequest = TokenRequest.Builder(config, AuthConfig.clientId)
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(refreshToken)
            .build()

        AppEventLog.log("Rauthy", "Refresh token request")
        return exchangeToken(tokenRequest)
    }

    fun endSession(idTokenHint: String? = null) {
        val config = serviceConfig ?: return
        val endSessionRequest = EndSessionRequest.Builder(config)
            .setIdTokenHint(idTokenHint)
            .setPostLogoutRedirectUri(Uri.parse(AuthConfig.redirectUri))
            .build()
        val endSessionIntent = authService.getEndSessionRequestIntent(endSessionRequest)
        endSessionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(endSessionIntent)
    }

    fun dispose() {
        authService.dispose()
    }

    private suspend fun exchangeToken(tokenRequest: TokenRequest): OidcTokenResult =
        withContext(Dispatchers.IO) {
            val tokenEndpoint = tokenRequest.configuration.tokenEndpoint?.toString()
                ?: throw IllegalStateException("Token endpoint missing from discovery")

            val formBuilder = FormBody.Builder()
                .add("grant_type", tokenRequest.grantType)
                .add("client_id", tokenRequest.clientId)

            tokenRequest.redirectUri?.let { formBuilder.add("redirect_uri", it.toString()) }
            tokenRequest.authorizationCode?.let { formBuilder.add("code", it) }
            tokenRequest.refreshToken?.let { formBuilder.add("refresh_token", it) }
            tokenRequest.codeVerifier?.let { formBuilder.add("code_verifier", it) }

            val request = Request.Builder()
                .url(tokenEndpoint)
                .post(formBuilder.build())
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Token exchange failed HTTP ${response.code}: $body")
                }
                parseTokenResult(body)
            }
        }

    private fun parseTokenResult(body: String): OidcTokenResult {
        val json = JSONObject(body)
        val access = json.optString("access_token")
        if (access.isBlank()) {
            throw IllegalStateException("Token response missing access_token")
        }
        return OidcTokenResult(
            accessToken = access,
            refreshToken = json.optString("refresh_token").ifBlank { null },
            idToken = json.optString("id_token").ifBlank { null },
        )
    }
}
