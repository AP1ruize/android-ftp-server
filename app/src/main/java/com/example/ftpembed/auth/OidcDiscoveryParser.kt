package com.example.ftpembed.auth

import android.net.Uri
import net.openid.appauth.AuthorizationServiceConfiguration
import org.json.JSONObject

/**
 * Parses standard OIDC discovery JSON (snake_case field names).
 *
 * AppAuth's [AuthorizationServiceConfiguration.fromJson] expects its own camelCase
 * serialization format, not the raw `.well-known/openid-configuration` document.
 */
object OidcDiscoveryParser {
    fun parse(json: String): AuthorizationServiceConfiguration {
        val obj = JSONObject(json)
        val authorizationEndpoint = requireUri(obj, "authorization_endpoint")
        val tokenEndpoint = requireUri(obj, "token_endpoint")
        val registrationEndpoint = optionalUri(obj, "registration_endpoint")
        val endSessionEndpoint = optionalUri(obj, "end_session_endpoint")

        return if (registrationEndpoint != null || endSessionEndpoint != null) {
            AuthorizationServiceConfiguration(
                authorizationEndpoint,
                tokenEndpoint,
                registrationEndpoint,
                endSessionEndpoint,
            )
        } else {
            AuthorizationServiceConfiguration(
                authorizationEndpoint,
                tokenEndpoint,
            )
        }
    }

    private fun requireUri(obj: JSONObject, key: String): Uri {
        val value = obj.optString(key).trim()
        if (value.isBlank()) {
            throw IllegalStateException("OIDC discovery missing $key")
        }
        return Uri.parse(value)
    }

    private fun optionalUri(obj: JSONObject, key: String): Uri? {
        val value = obj.optString(key).trim()
        return value.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }
}
