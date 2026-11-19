package dev.maxxing.github.network

import dev.maxxing.github.model.AppBranding
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.DEFAULT_PORT
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol

fun createGitHubImageHttpClient(
    httpClient: HttpClient = createDefaultPlatformHttpClient(),
    accessToken: () -> String?
): HttpClient = httpClient.apply {
    plugin(HttpSend).intercept { request ->
        request.headers.append(HttpHeaders.UserAgent, AppBranding.USER_AGENT)
        val host = request.url.host.lowercase()
        val isGitHubOwned = host == "raw.githubusercontent.com" ||
            host == "github.com" ||
            host == "api.github.com"
        if (isGitHubOwned &&
            request.url.protocol == URLProtocol.HTTPS &&
            (request.url.port == DEFAULT_PORT || request.url.port == URLProtocol.HTTPS.defaultPort)
        ) {
            accessToken()?.let { token ->
                if (!request.headers.contains(HttpHeaders.Authorization)) {
                    request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        }
        execute(request)
    }
}
