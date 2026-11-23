package dev.maxxing.github.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderType {
    GITHUB,
    FORGEJO_GITEA,
    LOCAL
}

data class ProviderCapabilities(
    val supportsRepositories: Boolean = true,
    val supportsIssuesAndPulls: Boolean = true,
    val supportsInbox: Boolean = true,
    val supportsWorkingTreeDiff: Boolean = false,
    val supportsGraphQl: Boolean,
    val supportsGists: Boolean,
    val supportsDiscussions: Boolean,
    val supportsSecurity: Boolean,
    val supportsActions: Boolean,
    val supportsReactions: Boolean,
    val supportsPendingReviewDrafts: Boolean,
    val supportsFileViewedSync: Boolean,
    val supportsDraftToggle: Boolean,
    val supportsAttachmentsUpload: Boolean,
    val supportsRepoCompare: Boolean,
    val supportsNotificationsDone: Boolean
)

fun ProviderType.capabilities(): ProviderCapabilities = when (this) {
    ProviderType.GITHUB -> ProviderCapabilities(
        supportsRepositories = true,
        supportsIssuesAndPulls = true,
        supportsInbox = true,
        supportsWorkingTreeDiff = false,
        supportsGraphQl = true,
        supportsGists = true,
        supportsDiscussions = true,
        supportsSecurity = true,
        supportsActions = true,
        supportsReactions = true,
        supportsPendingReviewDrafts = true,
        supportsFileViewedSync = true,
        supportsDraftToggle = true,
        supportsAttachmentsUpload = true,
        supportsRepoCompare = true,
        supportsNotificationsDone = true
    )
    ProviderType.FORGEJO_GITEA -> ProviderCapabilities(
        supportsRepositories = true,
        supportsIssuesAndPulls = true,
        supportsInbox = true,
        supportsWorkingTreeDiff = false,
        supportsGraphQl = false,
        supportsGists = false,
        supportsDiscussions = false,
        supportsSecurity = false,
        supportsActions = false,
        supportsReactions = false,
        supportsPendingReviewDrafts = false,
        supportsFileViewedSync = false,
        supportsDraftToggle = false,
        supportsAttachmentsUpload = false,
        supportsRepoCompare = false,
        supportsNotificationsDone = false
    )
    ProviderType.LOCAL -> ProviderCapabilities(
        supportsRepositories = true,
        supportsIssuesAndPulls = false,
        supportsInbox = false,
        supportsWorkingTreeDiff = true,
        supportsGraphQl = false,
        supportsGists = false,
        supportsDiscussions = false,
        supportsSecurity = false,
        supportsActions = false,
        supportsReactions = false,
        supportsPendingReviewDrafts = false,
        supportsFileViewedSync = false,
        supportsDraftToggle = false,
        supportsAttachmentsUpload = false,
        supportsRepoCompare = false,
        supportsNotificationsDone = false
    )
}

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
    val userLogin: String? = null,
    /** Single-use token for renewing an expiring OAuth access token; null for non-expiring tokens and PATs. */
    val refreshToken: String? = null,
    /** When [accessToken] stops working (Unix epoch seconds); null when the token does not expire. */
    val accessTokenExpiresAtEpochSeconds: Long? = null,
    /** When [refreshToken] stops working (Unix epoch seconds); null when unknown or not applicable. */
    val refreshTokenExpiresAtEpochSeconds: Long? = null,
    /** The OAuth client id the token was issued to, so it can be refreshed after the client id setting changes. */
    val clientId: String? = null,
    /** Stable per-account identifier used to isolate token storage and caches across accounts. */
    val accountId: String = "",
    val providerType: ProviderType = ProviderType.GITHUB,
    val baseUrl: String = GITHUB_WEB_BASE_URL,
    val apiBaseUrl: String = GITHUB_API_BASE_URL,
    val userId: Long? = null,
    val avatarUrl: String = ""
) {
    fun resolvedAccountId(): String {
        val explicit = accountId.trim()
        if (explicit.isNotEmpty()) return explicit
        val cleanLogin = userLogin?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val identity = userId?.toString() ?: cleanLogin ?: accessToken.hashCode().toString()
        return when (providerType) {
            ProviderType.GITHUB -> "github:$identity"
            ProviderType.FORGEJO_GITEA -> {
                val hostKey = normalizeInstanceBaseUrl(baseUrl)
                    ?.removePrefix("https://")
                    ?.removePrefix("http://")
                    ?.lowercase()
                    ?: "forgejo"
                "forgejo:$hostKey:$identity"
            }
            ProviderType.LOCAL -> "local:$identity"
        }
    }

    fun resolvedWebBaseUrl(): String = when (providerType) {
        ProviderType.GITHUB -> GITHUB_WEB_BASE_URL
        ProviderType.FORGEJO_GITEA -> normalizeInstanceBaseUrl(baseUrl) ?: DEFAULT_CODEBERG_BASE_URL
        ProviderType.LOCAL -> ""
    }

    fun resolvedApiBaseUrl(): String = when (providerType) {
        ProviderType.GITHUB -> GITHUB_API_BASE_URL
        ProviderType.FORGEJO_GITEA -> {
            val clean = normalizeInstanceBaseUrl(baseUrl)
                ?: normalizeInstanceBaseUrl(apiBaseUrl.removeSuffix("/api/v1"))
                ?: DEFAULT_CODEBERG_BASE_URL
            "$clean/api/v1"
        }
        ProviderType.LOCAL -> ""
    }

    fun instanceHostLabel(): String = when (providerType) {
        ProviderType.GITHUB -> "GitHub"
        ProviderType.FORGEJO_GITEA -> resolvedWebBaseUrl()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
        ProviderType.LOCAL -> {
            val root = scope.trim().takeIf { it.isNotEmpty() }
            if (root != null) "$root · P2P" else "Local · P2P"
        }
    }

    companion object {
        const val GITHUB_WEB_BASE_URL = "https://github.com"
        const val GITHUB_API_BASE_URL = "https://api.github.com"
        const val DEFAULT_CODEBERG_BASE_URL = "https://codeberg.org"
    }
}

/**
 * Normalizes a user-entered Forgejo/Gitea instance URL (for example `codeberg.org` or
 * `https://git.example.com/api/v1/`) to its origin/root (`https://codeberg.org`).
 * Returns null when the input is blank or does not contain a valid host.
 */
fun normalizeInstanceBaseUrl(raw: String): String? {
    var text = raw.trim()
    if (text.isEmpty()) return null
    val hasScheme = text.startsWith("https://", ignoreCase = true) ||
        text.startsWith("http://", ignoreCase = true)
    if (!hasScheme) {
        if ("://" in text) return null
        text = "https://$text"
    }
    val schemeSep = text.indexOf("://")
    val scheme = text.substring(0, schemeSep).lowercase()
    if (scheme != "http" && scheme != "https") return null
    var rest = text.substring(schemeSep + 3)
    rest = rest.substringBefore('?').substringBefore('#').trimEnd('/')
    if (rest.isEmpty() || rest.any { it.isWhitespace() }) return null
    val slashIdx = rest.indexOf('/')
    val authority = if (slashIdx >= 0) rest.substring(0, slashIdx) else rest
    var path = if (slashIdx >= 0) rest.substring(slashIdx).trimEnd('/') else ""
    if (authority.isBlank() || '@' in authority) return null
    if (path.endsWith("/api/v1", ignoreCase = true)) {
        path = path.dropLast("/api/v1".length).trimEnd('/')
    }
    return "$scheme://$authority$path"
}

/**
 * Builds the Forgejo / Gitea / Codeberg Personal Access Token generation URL for [rawBaseUrl].
 */
fun buildForgejoTokenCreationUrl(rawBaseUrl: String): String {
    val base = normalizeInstanceBaseUrl(rawBaseUrl) ?: AuthSession.DEFAULT_CODEBERG_BASE_URL
    return "$base/user/settings/applications"
}

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
