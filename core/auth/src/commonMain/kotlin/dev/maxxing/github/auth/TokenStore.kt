package dev.maxxing.github.auth

import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.FontSelection
import kotlinx.serialization.Serializable

@Serializable
data class ThemePreferences(
    val seedColorHex: Long = 0xFF6750A4,
    val useDynamicColor: Boolean = true,
    val darkModeOverride: Boolean? = null,
    val primaryFont: FontSelection = FontSelection.SystemDefault,
    val fallbackFont: FontSelection = FontSelection.SystemDefault
)

@Serializable
data class NotificationPreferences(
    val osNotificationsEnabled: Boolean = true,
    val urgentOnly: Boolean = false,
    val pollIntervalMinutes: Int = 1
)

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
    suspend fun saveSeenNotificationMarkers(markers: Map<String, String>)
    suspend fun loadSeenNotificationMarkers(): Map<String, String>
    suspend fun savePinnedRepos(repos: List<String>) {}
    suspend fun loadPinnedRepos(): List<String> = emptyList()
}

expect fun createPlatformTokenStore(): TokenStore

expect fun openUrlInBrowser(url: String): Boolean
