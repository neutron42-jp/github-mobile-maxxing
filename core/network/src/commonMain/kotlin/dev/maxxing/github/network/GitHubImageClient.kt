package dev.maxxing.github.network

import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.ProviderType
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.DEFAULT_PORT
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url

fun createGitHubImageHttpClient(
    httpClient: HttpClient = createDefaultPlatformHttpClient(),
    accessToken: () -> String?
): HttpClient = createProviderImageHttpClient(httpClient = httpClient) {
    val token = accessToken()?.takeIf { it.isNotBlank() } ?: return@createProviderImageHttpClient null
    AuthSession(accessToken = token, providerType = ProviderType.GITHUB)
}

fun createProviderImageHttpClient(
    httpClient: HttpClient = createDefaultPlatformHttpClient(),
    sessionProvider: () -> AuthSession?
): HttpClient = httpClient.apply {
    plugin(HttpSend).intercept { request ->
        if (!request.headers.contains(HttpHeaders.UserAgent)) {
            request.headers.append(HttpHeaders.UserAgent, AppBranding.USER_AGENT)
        }
        val currentAccept = request.headers[HttpHeaders.Accept]
        if (currentAccept == null || currentAccept == "*/*") {
            request.headers[HttpHeaders.Accept] =
                "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        }
        val session = sessionProvider()
        val host = request.url.host.lowercase()
        val isHttpsDefaultPort = request.url.protocol == URLProtocol.HTTPS &&
            (request.url.port == DEFAULT_PORT || request.url.port == URLProtocol.HTTPS.defaultPort)

        if (session != null && session.accessToken.isNotBlank()) {
            when (session.providerType) {
                ProviderType.GITHUB -> {
                    val isGitHubOwned = host == "raw.githubusercontent.com" ||
                        host == "github.com" ||
                        host == "api.github.com"
                    if (isGitHubOwned && isHttpsDefaultPort && !request.headers.contains(HttpHeaders.Authorization)) {
                        request.headers.append(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
                    }
                }
                ProviderType.FORGEJO_GITEA -> {
                    val instanceUrl = runCatching { Url(session.resolvedWebBaseUrl()) }.getOrNull()
                    if (instanceUrl != null &&
                        request.url.protocol == instanceUrl.protocol &&
                        host.equals(instanceUrl.host, ignoreCase = true) &&
                        !request.headers.contains(HttpHeaders.Authorization)
                    ) {
                        val scheme = if (session.tokenType.equals("bearer", ignoreCase = true)) "Bearer" else "token"
                        request.headers.append(HttpHeaders.Authorization, "$scheme ${session.accessToken}")
                    }
                }
                ProviderType.LOCAL -> Unit
            }
        }
        execute(request)
    }
}
