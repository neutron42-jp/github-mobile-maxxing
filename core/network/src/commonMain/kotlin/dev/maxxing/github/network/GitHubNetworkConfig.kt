package dev.maxxing.github.network

import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.HttpStatusError
import dev.maxxing.github.model.ProviderType
import dev.maxxing.github.model.RateLimitInfo
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.DEFAULT_PORT
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class GitHubApiException(
    val statusCode: Int,
    override val message: String,
    /** See [HttpStatusError.isSyntheticStatus]; true for statuses the client picked itself. */
    override val isSyntheticStatus: Boolean = false
) : Exception(message), HttpStatusError {
    override val httpStatusCode: Int get() = statusCode
}

class GitHubNetworkConfig(
    private val sessionManager: SessionManager,
    val baseUrl: String = "https://api.github.com"
) {
    private val _rateLimit = MutableStateFlow(RateLimitInfo())
    val rateLimit: StateFlow<RateLimitInfo> = _rateLimit.asStateFlow()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val apiUrl = Url(baseUrl)

    /**
     * Creates the API client. [engine] is for tests; null uses the platform default engine.
     *
     * The access token is attached per request by an [HttpSend] interceptor (see [installAuthorization]) so an
     * expiring OAuth token can be refreshed before it expires and once more after an HTTP 401.
     */
    fun createHttpClient(engine: HttpClientEngine? = null): HttpClient {
        val client = if (engine != null) HttpClient(engine) { configure() } else createDefaultPlatformHttpClient { configure() }
        return client.apply { installAuthorization() }
    }

    private fun HttpClientConfig<*>.configure() {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000L
            socketTimeoutMillis = 30_000L
            requestTimeoutMillis = 30_000L
        }
        install(DefaultRequest) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            header(HttpHeaders.UserAgent, AppBranding.USER_AGENT)
        }
        HttpResponseValidator {
            validateResponse { response ->
                val limit = response.headers["X-RateLimit-Limit"]?.toIntOrNull()
                val remaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull()
                val reset = response.headers["X-RateLimit-Reset"]?.toLongOrNull()
                if (limit != null && remaining != null) {
                    _rateLimit.value = RateLimitInfo(
                        limit = limit,
                        remaining = remaining,
                        resetEpochSeconds = reset ?: 0L
                    )
                }
            }
        }
    }

    private fun HttpClient.installAuthorization() {
        plugin(HttpSend).intercept { request ->
            val active = sessionManager.session.value
            if (active == null) {
                request.headers.remove(HttpHeaders.Authorization)
                return@intercept execute(request)
            }
            val accountId = active.resolvedAccountId()
            when (active.providerType) {
                ProviderType.GITHUB -> {
                    if (!request.url.isAuthorizedGithubRequest(request.method)) {
                        // Never send the token to other hosts (e.g. a redirect to a log download URL) or
                        // to an endpoint outside the one user-attachment upload path.
                        request.headers.remove(HttpHeaders.Authorization)
                        return@intercept execute(request)
                    }
                    val token = sessionManager.refreshSessionIfExpiringSoon(accountId)?.accessToken
                    request.setAuthHeader("Bearer", token)
                    val call = execute(request)
                    if (token == null || call.response.status != HttpStatusCode.Unauthorized) return@intercept call

                    // The token may have expired or been replaced by another SessionManager; retry once with a new one.
                    val refreshedToken = sessionManager.refreshAfterUnauthorized(token, accountId)?.accessToken
                    if (refreshedToken == null || refreshedToken == token) {
                        sessionManager.markReauthRequired(accountId)
                        return@intercept call
                    }
                    request.setAuthHeader("Bearer", refreshedToken)
                    val retryCall = execute(request)
                    if (retryCall.response.status == HttpStatusCode.Unauthorized) {
                        sessionManager.markReauthRequired(accountId)
                    }
                    retryCall
                }
                ProviderType.FORGEJO_GITEA -> {
                    if (!request.url.isAuthorizedForgejoRequest(active.resolvedWebBaseUrl(), active.resolvedApiBaseUrl())) {
                        request.headers.remove(HttpHeaders.Authorization)
                        return@intercept execute(request)
                    }
                    request.headers.remove("X-GitHub-Api-Version")
                    request.headers[HttpHeaders.Accept] = "application/json"
                    val scheme = if (active.tokenType.equals("token", ignoreCase = true) ||
                        active.authMethod == dev.maxxing.github.model.AuthMethod.PERSONAL_ACCESS_TOKEN
                    ) "token" else "Bearer"
                    request.setAuthHeader(scheme, active.accessToken)
                    val call = execute(request)
                    if (call.response.status == HttpStatusCode.Unauthorized) {
                        sessionManager.markReauthRequired(accountId)
                    }
                    call
                }
                ProviderType.LOCAL -> {
                    request.headers.remove(HttpHeaders.Authorization)
                    execute(request)
                }
            }
        }
    }

    private fun HttpRequestBuilder.setAuthHeader(scheme: String, token: String?) {
        headers.remove(HttpHeaders.Authorization)
        if (!token.isNullOrBlank()) headers.append(HttpHeaders.Authorization, "$scheme $token")
    }

    private fun URLBuilder.isAuthorizedGithubRequest(method: HttpMethod): Boolean =
        isApiUrl() || isUserAttachmentUpload(method)

    private fun URLBuilder.isAuthorizedForgejoRequest(webBaseUrl: String, apiBaseUrl: String): Boolean {
        val apiParsed = runCatching { Url(apiBaseUrl) }.getOrNull()
        val webParsed = runCatching { Url(webBaseUrl) }.getOrNull()
        val matchesApi = apiParsed != null &&
            protocol == apiParsed.protocol &&
            host.equals(apiParsed.host, ignoreCase = true) &&
            port.effectivePort(protocol.defaultPort) == apiParsed.port.effectivePort(apiParsed.protocol.defaultPort)
        val matchesWeb = webParsed != null &&
            protocol == webParsed.protocol &&
            host.equals(webParsed.host, ignoreCase = true) &&
            port.effectivePort(protocol.defaultPort) == webParsed.port.effectivePort(webParsed.protocol.defaultPort)
        return matchesApi || matchesWeb
    }

    private fun URLBuilder.isApiUrl(): Boolean =
        protocol == apiUrl.protocol &&
            host.equals(apiUrl.host, ignoreCase = true) &&
            port.effectivePort(protocol.defaultPort) == apiUrl.port

    /**
     * The user-attachments upload endpoint is the only non-API request that may receive the session token.
     * Both the origin and the exact POST path are pinned; queries carry the name, content type and repository id.
     */
    private fun URLBuilder.isUserAttachmentUpload(method: HttpMethod): Boolean =
        method == HttpMethod.Post &&
            protocol == URLProtocol.HTTPS &&
            host == USER_ATTACHMENT_UPLOAD_HOST &&
            port.effectivePort(protocol.defaultPort) == 443 &&
            encodedPathSegments.joinToString("/") == USER_ATTACHMENT_UPLOAD_PATH &&
            user.isNullOrEmpty() &&
            password.isNullOrEmpty()
}

/** [URLBuilder.port] is [DEFAULT_PORT] (0) when the URL does not name a port. */
private fun Int.effectivePort(defaultPort: Int): Int = if (this == DEFAULT_PORT) defaultPort else this
