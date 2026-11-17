package dev.maxxing.github.model

import kotlinx.serialization.Serializable

@Serializable
enum class AuthMethod {
    DEVICE_FLOW,
    PERSONAL_ACCESS_TOKEN
}

@Serializable
data class AuthSession(
    val accessToken: String,
    val tokenType: String = "bearer",
    val scope: String = "",
    val authMethod: AuthMethod = AuthMethod.DEVICE_FLOW,
    val userLogin: String? = null
)

/** Why the device flow is still waiting; lets the UI localize [DeviceCodeState.AwaitingUser.statusMessage]. */
enum class DeviceFlowWaitReason {
    CODE_ISSUED,
    AUTHORIZATION_PENDING,
    NETWORK_RETRY,
    SLOW_DOWN
}

/** Typed cause of a device flow failure; lets the UI localize [DeviceCodeState.Error.message]. */
enum class DeviceFlowErrorKind {
    MISSING_CLIENT_ID,
    CODE_REQUEST_REJECTED,
    NETWORK,
    EXPIRED,
    DENIED,
    TIMED_OUT,
    OTHER
}

sealed interface DeviceCodeState {
    data object Idle : DeviceCodeState
    data object RequestingCode : DeviceCodeState
    data class AwaitingUser(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresInSeconds: Int,
        val intervalSeconds: Int,
        val statusMessage: String? = null,
        val waitReason: DeviceFlowWaitReason? = null
    ) : DeviceCodeState

    data class Verifying(val message: String = "Signing in...") : DeviceCodeState
    data class Authenticated(val session: AuthSession) : DeviceCodeState
    data class Error(
        val message: String,
        val isRecoverable: Boolean = true,
        val kind: DeviceFlowErrorKind = DeviceFlowErrorKind.OTHER,
        val httpStatusCode: Int? = null,
        /** OAuth `error` code from GitHub (e.g. "device_flow_disabled") when [kind] is [DeviceFlowErrorKind.OTHER]. */
        val oauthErrorCode: String? = null
    ) : DeviceCodeState
}

@Serializable
data class RateLimitInfo(
    val limit: Int = 5000,
    val remaining: Int = 5000,
    val resetEpochSeconds: Long = 0L
)
