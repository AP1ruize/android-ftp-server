package com.example.ftpembed.auth

import com.example.ftpembed.BuildConfig

object AuthConfig {
    val apiBase: String = BuildConfig.DDNS_API_BASE
    val issuer: String = BuildConfig.OIDC_ISSUER
    val clientId: String = BuildConfig.OIDC_CLIENT_ID
    val redirectScheme: String = BuildConfig.OIDC_REDIRECT_SCHEME
    val redirectHost: String = BuildConfig.OIDC_REDIRECT_HOST
    val redirectUri: String = BuildConfig.OIDC_REDIRECT_URI
    val scope: String = BuildConfig.OIDC_SCOPE

    val discoveryUrl: String = "${issuer}.well-known/openid-configuration"
    val authorizationUrl: String = "${issuer}oidc/authorize"
    val tokenUrl: String = "${issuer}oidc/token"
    val logoutUrl: String = "${issuer}oidc/logout"
}
