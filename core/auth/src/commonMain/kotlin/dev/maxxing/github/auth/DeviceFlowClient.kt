package dev.maxxing.github.auth

import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.DeviceCodeState
import dev.maxxing.github.model.DeviceFlowErrorKind
import dev.maxxing.github.model.DeviceFlowWaitReason
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.DEFAULT_PORT
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("interval") val interval: Int = 5
)

@Serializable
private data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("scope") val scope: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("interval") val interval: Int? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("refresh_token_expires_in") val refreshTokenExpiresIn: Long? = null
)

/**
 * GitHub refused to exchange a refresh token (for example `bad_refresh_token` when it is expired, revoked or
 * already used). Retrying with the same refresh token cannot succeed; the user has to sign in again.
 */
class RefreshTokenRejectedException(
    val error: String,
    val errorDescription: String? = null
) : Exception(errorDescription ?: error) {
    val isBadRefreshToken: Boolean get() = error == BAD_REFRESH_TOKEN

    companion object {
        const val BAD_REFRESH_TOKEN = "bad_refresh_token"
    }
}

@Serializable
private data class PatUserCheckDto(
    val login: String
)

class DeviceFlowClient(
    private val httpClient: HttpClient = defaultAuthHttpClient(),
    private val githubBaseUrl: String = "https://github.com",
    private val apiBaseUrl: String = "https://api.github.com",
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    /** Current time in Unix epoch seconds; turns the relative `expires_in` values into absolute expiry times. */
    val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds }
) {
    fun startDeviceFlow(
        clientId: String,
        scope: String = DEFAULT_OAUTH_SCOPE
    ): Flow<DeviceCodeState> = flow {
        val trimmedId = clientId.trim()
        if (trimmedId.isEmpty()) {
            emit(DeviceCodeState.Error("OAuth Client ID is required.", kind = DeviceFlowErrorKind.MISSING_CLIENT_ID))
            return@flow
        }

        emit(DeviceCodeState.RequestingCode)

        val codeResponse = try {
            val response = httpClient.post("$githubBaseUrl/login/device/code") {
                header(HttpHeaders.Accept, "application/json")
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", trimmedId)
                            append("scope", scope)
                        }
                    )
                )
            }
            if (response.status != HttpStatusCode.OK) {
                emit(
                    DeviceCodeState.Error(
                        "GitHub rejected device code request (${response.status.value}). Verify your Client ID.",
                        kind = DeviceFlowErrorKind.CODE_REQUEST_REJECTED,
                        httpStatusCode = response.status.value
                    )
                )
                return@flow
            }
            response.body<DeviceCodeResponse>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(DeviceCodeState.Error("Failed to contact GitHub: ${e.message ?: "Network error"}", kind = DeviceFlowErrorKind.NETWORK))
            return@flow
        }

        // The verification URL is opened in the user's browser, so only GitHub's device-flow page is accepted.
        if (!isTrustedDeviceVerificationUri(codeResponse.verificationUri)) {
            emit(
                DeviceCodeState.Error(
                    "GitHub returned an unexpected verification URL: ${codeResponse.verificationUri}",
                    kind = DeviceFlowErrorKind.OTHER
                )
            )
            return@flow
        }

        var intervalSec = codeResponse.interval.coerceAtLeast(1)
        var elapsedSec = 0

        emit(
            DeviceCodeState.AwaitingUser(
                deviceCode = codeResponse.deviceCode,
                userCode = codeResponse.userCode,
                verificationUri = codeResponse.verificationUri,
                expiresInSeconds = codeResponse.expiresIn,
                intervalSeconds = intervalSec,
                statusMessage = "Enter this code on GitHub to authorize ${AppBranding.APP_NAME}.",
                waitReason = DeviceFlowWaitReason.CODE_ISSUED
            )
        )

        while (elapsedSec < codeResponse.expiresIn) {
            delayMillis(intervalSec * 1000L)
            elapsedSec += intervalSec

            val tokenRes = try {
                httpClient.post("$githubBaseUrl/login/oauth/access_token") {
                    header(HttpHeaders.Accept, "application/json")
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", trimmedId)
                                append("device_code", codeResponse.deviceCode)
                                append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                            }
                        )
                    )
                }.body<AccessTokenResponse>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(
                    DeviceCodeState.AwaitingUser(
                        deviceCode = codeResponse.deviceCode,
                        userCode = codeResponse.userCode,
                        verificationUri = codeResponse.verificationUri,
                        expiresInSeconds = (codeResponse.expiresIn - elapsedSec).coerceAtLeast(0),
                        intervalSeconds = intervalSec,
                        statusMessage = "Temporary network hiccup, retrying...",
                        waitReason = DeviceFlowWaitReason.NETWORK_RETRY
                    )
                )
                continue
            }

            if (!tokenRes.accessToken.isNullOrBlank()) {
                emit(DeviceCodeState.Verifying("Fetching profile..."))
                val login = fetchUserLogin(tokenRes.accessToken)
                val session = tokenRes.toSession(
                    accessToken = tokenRes.accessToken,
                    resolvedScope = tokenRes.scope ?: scope,
                    clientId = trimmedId,
                    userLogin = login
                )
                emit(DeviceCodeState.Authenticated(session))
                return@flow
            }

            when (tokenRes.error) {
                "authorization_pending" -> {
                    emit(
                        DeviceCodeState.AwaitingUser(
                            deviceCode = codeResponse.deviceCode,
                            userCode = codeResponse.userCode,
                            verificationUri = codeResponse.verificationUri,
                            expiresInSeconds = (codeResponse.expiresIn - elapsedSec).coerceAtLeast(0),
                            intervalSeconds = intervalSec,
                            statusMessage = "Waiting for browser authorization...",
                            waitReason = DeviceFlowWaitReason.AUTHORIZATION_PENDING
                        )
                    )
                }

                "slow_down" -> {
                    intervalSec = tokenRes.interval ?: (intervalSec + 5)
                    emit(
                        DeviceCodeState.AwaitingUser(
                            deviceCode = codeResponse.deviceCode,
                            userCode = codeResponse.userCode,
                            verificationUri = codeResponse.verificationUri,
                            expiresInSeconds = (codeResponse.expiresIn - elapsedSec).coerceAtLeast(0),
                            intervalSeconds = intervalSec,
                            statusMessage = "Rate limited by GitHub; polling every ${intervalSec}s...",
                            waitReason = DeviceFlowWaitReason.SLOW_DOWN
                        )
                    )
                }

                "expired_token" -> {
                    emit(DeviceCodeState.Error("The device code expired. Request a new code.", kind = DeviceFlowErrorKind.EXPIRED))
                    return@flow
                }

                "access_denied" -> {
                    emit(DeviceCodeState.Error("Authorization was denied in the browser.", kind = DeviceFlowErrorKind.DENIED))
                    return@flow
                }

                else -> {
                    val desc = tokenRes.errorDescription ?: tokenRes.error ?: "Unknown OAuth error"
                    emit(DeviceCodeState.Error(desc, oauthErrorCode = tokenRes.error))
                    return@flow
                }
            }
        }

        emit(DeviceCodeState.Error("Device code timed out. Please try again.", kind = DeviceFlowErrorKind.TIMED_OUT))
    }

    suspend fun verifyPersonalAccessToken(rawToken: String): Result<AuthSession> {
        val token = rawToken.trim()
        if (token.isEmpty()) {
            return Result.failure(IllegalArgumentException("Token cannot be empty"))
        }
        return try {
            val response = httpClient.get("$apiBaseUrl/user") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
            if (response.status != HttpStatusCode.OK) {
                Result.failure(IllegalStateException("GitHub returned HTTP ${response.status.value}. Check token scopes."))
            } else {
                val user = response.body<PatUserCheckDto>()
                val resolvedScope = response.headers["X-OAuth-Scopes"]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "pat"
                Result.success(
                    AuthSession(
                        accessToken = token,
                        tokenType = "bearer",
                        scope = resolvedScope,
                        authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN,
                        userLogin = user.login
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exchanges [session]'s refresh token for a new access/refresh token pair
     * (https://docs.github.com/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps).
     *
     * Device-flow tokens are refreshed without a client secret. Both old tokens stop working once GitHub answers,
     * so the caller must persist the returned session. Fails with [RefreshTokenRejectedException] when GitHub
     * rejects the refresh token, or with the underlying exception on network errors.
     */
    suspend fun refreshAccessToken(session: AuthSession): Result<AuthSession> {
        val refreshToken = session.refreshToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Session has no refresh token"))
        val clientId = session.clientId?.trim()?.takeIf { it.isNotEmpty() }
            ?: return Result.failure(IllegalStateException("Session has no OAuth client id to refresh with"))
        return try {
            val response = httpClient.post("$githubBaseUrl/login/oauth/access_token") {
                header(HttpHeaders.Accept, "application/json")
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", clientId)
                            append("grant_type", "refresh_token")
                            append("refresh_token", refreshToken)
                        }
                    )
                )
            }
            // GitHub reports OAuth errors with HTTP 200 and an "error" field, so look at the body first.
            val tokenRes = runCatching { response.body<AccessTokenResponse>() }.getOrNull()
            val newAccessToken = tokenRes?.accessToken?.takeIf { it.isNotBlank() }
            when {
                tokenRes?.error != null ->
                    Result.failure(RefreshTokenRejectedException(tokenRes.error, tokenRes.errorDescription))

                response.status != HttpStatusCode.OK || newAccessToken == null ->
                    Result.failure(IllegalStateException("GitHub token refresh failed (HTTP ${response.status.value})"))

                else -> Result.success(
                    tokenRes.toSession(
                        accessToken = newAccessToken,
                        // A blank scope in a refresh response keeps the scopes granted at sign-in.
                        resolvedScope = tokenRes.scope?.takeIf { it.isNotBlank() } ?: session.scope,
                        clientId = clientId,
                        userLogin = session.userLogin,
                        authMethod = session.authMethod,
                        fallbackTokenType = session.tokenType
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun AccessTokenResponse.toSession(
        accessToken: String,
        resolvedScope: String,
        clientId: String,
        userLogin: String?,
        authMethod: AuthMethod = AuthMethod.DEVICE_FLOW,
        fallbackTokenType: String = "bearer"
    ): AuthSession {
        val now = nowEpochSeconds()
        return AuthSession(
            accessToken = accessToken,
            tokenType = tokenType?.takeIf { it.isNotBlank() } ?: fallbackTokenType,
            scope = resolvedScope,
            authMethod = authMethod,
            userLogin = userLogin,
            refreshToken = refreshToken?.takeIf { it.isNotBlank() },
            accessTokenExpiresAtEpochSeconds = expiresIn?.takeIf { it > 0 }?.let { now + it },
            refreshTokenExpiresAtEpochSeconds = refreshTokenExpiresIn?.takeIf { it > 0 }?.let { now + it },
            clientId = clientId
        )
    }

    private suspend fun fetchUserLogin(token: String): String? {
        return try {
            val res = httpClient.get("$apiBaseUrl/user") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }
            if (res.status == HttpStatusCode.OK) res.body<PatUserCheckDto>().login else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val DEFAULT_OAUTH_SCOPE = "repo gist notifications read:user user:follow read:org"

        /** Only this device-flow page is accepted as the verification URL, since the UI opens it in a browser. */
        const val EXPECTED_VERIFICATION_URI = "https://github.com/login/device"

        internal fun isTrustedDeviceVerificationUri(uri: String): Boolean {
            val trimmed = uri.trim()
            val parsed = runCatching { Url(trimmed) }.getOrNull() ?: return false
            if (parsed.protocol != URLProtocol.HTTPS) return false
            if (!parsed.host.equals("github.com", ignoreCase = true)) return false
            if (parsed.specifiedPort != DEFAULT_PORT && parsed.specifiedPort != URLProtocol.HTTPS.defaultPort) return false
            if (parsed.user != null || parsed.password != null) return false
            val segments = parsed.rawSegments.filter { it.isNotEmpty() }
            if (segments != listOf("login", "device")) return false
            return parsed.encodedPath == "/login/device" || parsed.encodedPath == "/login/device/"
        }

        /** Upper bound for one request to github.com, so a stalled token refresh cannot hold up API calls long. */
        const val AUTH_REQUEST_TIMEOUT_MILLIS = 15_000L

        fun defaultAuthHttpClient(): HttpClient = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = AUTH_REQUEST_TIMEOUT_MILLIS
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                )
            }
        }
    }
}
