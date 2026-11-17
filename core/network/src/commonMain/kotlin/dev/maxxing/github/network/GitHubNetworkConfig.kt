package dev.maxxing.github.network

import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.HttpStatusError
import dev.maxxing.github.model.RateLimitInfo
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
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

    fun createHttpClient(): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(DefaultRequest) {
            sessionManager.session.value?.accessToken?.let { token ->
                header(HttpHeaders.Authorization, "Bearer $token")
            }
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
}
