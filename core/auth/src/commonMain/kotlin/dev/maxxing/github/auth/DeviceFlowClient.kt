package dev.maxxing.github.auth

import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.DeviceCodeState
import dev.maxxing.github.model.DeviceFlowErrorKind
import dev.maxxing.github.model.DeviceFlowWaitReason
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    @SerialName("interval") val interval: Int? = null
)

@Serializable
private data class PatUserCheckDto(
    val login: String
)

class DeviceFlowClient(
    private val httpClient: HttpClient = defaultAuthHttpClient(),
    private val githubBaseUrl: String = "https://github.com",
    private val apiBaseUrl: String = "https://api.github.com",
    private val delayMillis: suspend (Long) -> Unit = { delay(it) }
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
        } catch (e: Exception) {
            emit(DeviceCodeState.Error("Failed to contact GitHub: ${e.message ?: "Network error"}", kind = DeviceFlowErrorKind.NETWORK))
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
                val session = AuthSession(
                    accessToken = tokenRes.accessToken,
                    tokenType = tokenRes.tokenType ?: "bearer",
                    scope = tokenRes.scope ?: scope,
                    authMethod = AuthMethod.DEVICE_FLOW,
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchUserLogin(token: String): String? {
        return try {
            val res = httpClient.get("$apiBaseUrl/user") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }
            if (res.status == HttpStatusCode.OK) res.body<PatUserCheckDto>().login else null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val DEFAULT_OAUTH_SCOPE = "repo gist notifications read:user user:follow read:org"

        fun defaultAuthHttpClient(): HttpClient = HttpClient {
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
