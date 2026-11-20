package dev.maxxing.github.auth

import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.MaxxingThemeDefaults
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Persisted appearance preferences.
 *
 * [seedColorHex] and [monochromePresetSelected] are always written, even when they hold their
 * defaults, so a record saved by this version can be told apart from a legacy record that
 * predates the Graphite preset (see [decodeThemePreferences]).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ThemePreferences(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val seedColorHex: Long = MaxxingThemeDefaults.DEFAULT_SEED_COLOR_HEX,
    val useDynamicColor: Boolean = true,
    val darkModeOverride: Boolean? = null,
    val primaryFont: FontSelection = FontSelection.SystemDefault,
    val fallbackFont: FontSelection = FontSelection.SystemDefault,
    val language: AppLanguage = AppLanguage.DEFAULT,
    /**
     * True only when the Graphite preset itself is selected. A custom color that happens to
     * equal the Graphite seed keeps this false and keeps the Expressive palette style.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val monochromePresetSelected: Boolean = seedColorHex == MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX
)

@Serializable
data class NotificationPreferences(
    val osNotificationsEnabled: Boolean = true,
    val urgentOnly: Boolean = false,
    val pollIntervalMinutes: Int = 1
)

@Serializable
data class UpdatePreferences(
    /** Null follows the running build channel: ON for nightly builds, OFF for stable builds. */
    val includePrereleases: Boolean? = null,
    val autoCheckOnLaunch: Boolean = true,
    val dismissedVersionKey: String? = null
) {
    fun resolveIncludePrereleases(isCurrentBuildNightly: Boolean): Boolean =
        includePrereleases ?: isCurrentBuildNightly
}

interface TokenStore {
    suspend fun saveSession(session: AuthSession)
    suspend fun loadSession(): AuthSession?
    suspend fun clearSession()
    suspend fun saveClientId(clientId: String)
    suspend fun loadClientId(): String?
    suspend fun saveThemePreferences(prefs: ThemePreferences)
    suspend fun loadThemePreferences(): ThemePreferences
    suspend fun saveNotificationPreferences(prefs: NotificationPreferences)
    suspend fun loadNotificationPreferences(): NotificationPreferences
    suspend fun saveUpdatePreferences(prefs: UpdatePreferences) {}
    suspend fun loadUpdatePreferences(): UpdatePreferences = UpdatePreferences()
    suspend fun saveSeenNotificationMarkers(markers: Map<String, String>)
    suspend fun loadSeenNotificationMarkers(): Map<String, String>
    suspend fun savePinnedRepos(repos: List<String>) {}
    suspend fun loadPinnedRepos(): List<String> = emptyList()
}

expect fun createPlatformTokenStore(): TokenStore

expect fun openUrlInBrowser(url: String): Boolean

/** Only http/https URLs may be launched in an external browser. */
internal fun isAllowedBrowserUrlScheme(scheme: String?): Boolean =
    scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)

/** Opens a platform-native share sheet when available; returns false if the caller should fall back to clipboard copy. */
expect fun shareText(text: String, title: String? = null): Boolean

/** Snapshot of OS-level GitHub (`github.com`) web link handling on Android. */
data class GitHubLinkHandlingState(
    val supportedOnPlatform: Boolean = false,
    val thisAppHandlesGitHubLinks: Boolean = false,
    val officialGitHubInstalled: Boolean = false,
    val officialGitHubHandlesLinks: Boolean? = null
)

expect fun inspectGitHubLinkHandling(): GitHubLinkHandlingState

expect fun openThisAppLinkSettings(): Boolean

expect fun openOfficialGitHubAppLinkSettings(): Boolean
