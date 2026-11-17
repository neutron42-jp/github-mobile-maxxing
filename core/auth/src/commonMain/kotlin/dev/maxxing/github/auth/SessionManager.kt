package dev.maxxing.github.auth

import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.DeviceCodeState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SessionManager(
    private val tokenStore: TokenStore = createPlatformTokenStore(),
    private val deviceFlowClient: DeviceFlowClient = DeviceFlowClient(),
    private val defaultClientId: String = ""
) {
    private val _session = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    private val _clientId = MutableStateFlow(defaultClientId)
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _themePreferences = MutableStateFlow(ThemePreferences())
    val themePreferences: StateFlow<ThemePreferences> = _themePreferences.asStateFlow()

    private val _notificationPreferences = MutableStateFlow(NotificationPreferences())
    val notificationPreferences: StateFlow<NotificationPreferences> = _notificationPreferences.asStateFlow()

    private val _pinnedRepos = MutableStateFlow<List<String>>(emptyList())
    val pinnedRepos: StateFlow<List<String>> = _pinnedRepos.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    suspend fun initialize() {
        val savedClientId = tokenStore.loadClientId()
        if (!savedClientId.isNullOrBlank()) {
            _clientId.value = savedClientId
        }
        _themePreferences.value = tokenStore.loadThemePreferences()
        _notificationPreferences.value = tokenStore.loadNotificationPreferences()
        _pinnedRepos.value = tokenStore.loadPinnedRepos()
            .map { it.trim() }
            .filter { '/' in it }
            .distinct()
        _session.value = tokenStore.loadSession()
        _isInitialized.value = true
    }

    suspend fun updateClientId(newClientId: String) {
        val trimmed = newClientId.trim()
        _clientId.value = trimmed
        tokenStore.saveClientId(trimmed)
    }

    fun startDeviceFlow(clientIdOverride: String? = null): Flow<DeviceCodeState> {
        val targetId = (clientIdOverride ?: _clientId.value).trim()
        return deviceFlowClient.startDeviceFlow(targetId).onEach { state ->
            if (state is DeviceCodeState.Authenticated) {
                tokenStore.saveSession(state.session)
                _session.value = state.session
                if (targetId.isNotEmpty()) {
                    _clientId.value = targetId
                    tokenStore.saveClientId(targetId)
                }
            }
        }
    }

    suspend fun signInWithPat(token: String): Result<AuthSession> {
        return deviceFlowClient.verifyPersonalAccessToken(token).onSuccess { verified ->
            tokenStore.saveSession(verified)
            _session.value = verified
        }
    }

    suspend fun updateThemePreferences(prefs: ThemePreferences) {
        _themePreferences.value = prefs
        tokenStore.saveThemePreferences(prefs)
    }

    suspend fun updateNotificationPreferences(prefs: NotificationPreferences) {
        _notificationPreferences.value = prefs
        tokenStore.saveNotificationPreferences(prefs)
    }

    suspend fun loadSeenNotificationMarkers(): Map<String, String> =
        tokenStore.loadSeenNotificationMarkers()

    suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) {
        tokenStore.saveSeenNotificationMarkers(markers)
    }

    suspend fun togglePinnedRepo(fullName: String): Boolean {
        val normalized = fullName.trim()
        if ('/' !in normalized) return false
        val current = _pinnedRepos.value
        val existingIdx = current.indexOfFirst { it.equals(normalized, ignoreCase = true) }
        val next: List<String>
        val nowPinned: Boolean
        if (existingIdx >= 0) {
            next = current.toMutableList().apply { removeAt(existingIdx) }
            nowPinned = false
        } else {
            next = (listOf(normalized) + current).take(24)
            nowPinned = true
        }
        _pinnedRepos.value = next
        tokenStore.savePinnedRepos(next)
        return nowPinned
    }

    /**
     * Proactively refreshes an expiring OAuth access token shortly before it expires and returns the session to
     * use for the next request (possibly unchanged).
     *
     * Cheap when no refresh is due or possible: returns without locking or touching the token store.
     */
    suspend fun refreshSessionIfExpiringSoon(): AuthSession? {
        val current = _session.value ?: return null
        if (current.refreshToken.isNullOrBlank() || !current.isExpiringSoon()) return current
        return refreshSession(failedAccessToken = current.accessToken, onlyIfExpiringSoon = true) ?: _session.value
    }

    /**
     * Called after GitHub answered HTTP 401 for [rejectedAccessToken]. Returns a session whose access token
     * differs from [rejectedAccessToken] (refreshed here or by another [SessionManager] in this process), or null
     * when no refresh is possible (no refresh token, refresh token rejected or expired, a refresh failed moments
     * ago). The current session is kept either way; signing out stays the user's decision.
     */
    suspend fun refreshAfterUnauthorized(rejectedAccessToken: String): AuthSession? =
        refreshSession(failedAccessToken = rejectedAccessToken, onlyIfExpiringSoon = false)
            ?.takeIf { it.accessToken != rejectedAccessToken }

    private suspend fun refreshSession(failedAccessToken: String, onlyIfExpiringSoon: Boolean): AuthSession? {
        // Checked before and again inside the lock: requests queued behind a failed attempt must not each re-read
        // the token store (a keyring process on desktop) or retry the network.
        if (isRefreshBlocked(failedAccessToken)) return null
        return refreshMutex.withLock {
            if (isRefreshBlocked(failedAccessToken)) return@withLock null
            // NonCancellable: GitHub invalidates the old refresh token as soon as it answers, so the new pair must
            // reach the token store even if the request that triggered the refresh is cancelled meanwhile.
            withContext(NonCancellable) {
                // The store is the source of truth shared by every SessionManager in the process.
                val stored = tokenStore.loadSession() ?: return@withContext null
                if (stored.accessToken != failedAccessToken) {
                    // Another instance already refreshed (or the user signed in again).
                    _session.value = stored
                    return@withContext stored
                }
                if (onlyIfExpiringSoon && !stored.isExpiringSoon()) return@withContext stored
                if (stored.isRefreshTokenUnusable()) return@withContext null
                val refreshToken = stored.refreshToken ?: return@withContext null

                deviceFlowClient.refreshAccessToken(stored).fold(
                    onSuccess = { refreshed ->
                        // GitHub has already invalidated the old pair, so keep the new one in memory first; if saving
                        // fails, this process can still use it (the next sign-in or refresh rewrites the store).
                        _session.value = refreshed
                        refreshRetryNotBefore.update { it - refreshToken }
                        runCatching { tokenStore.saveSession(refreshed) }
                        refreshed
                    },
                    onFailure = { error ->
                        // A refresh token GitHub refused is never retried; other failures (network, timeout, 5xx)
                        // are retried after a pause. The session is kept, so API calls keep failing with 401 until
                        // a refresh succeeds or the user signs in again.
                        val now = deviceFlowClient.nowEpochSeconds()
                        val retryAt = if (error is RefreshTokenRejectedException) {
                            Long.MAX_VALUE
                        } else {
                            now + REFRESH_RETRY_BACKOFF_SECONDS
                        }
                        refreshRetryNotBefore.update { blocked ->
                            blocked.filterValues { it > now } + (refreshToken to retryAt)
                        }
                        null
                    }
                )
            }
        }
    }

    /**
     * True when this instance still holds [failedAccessToken] and its refresh token cannot be used right now.
     * Judged from memory only, so it never reads the token store. When the in-memory token differs, the locked
     * path runs instead and adopts whatever another instance stored.
     */
    private fun isRefreshBlocked(failedAccessToken: String): Boolean {
        val current = _session.value ?: return false
        return current.accessToken == failedAccessToken && current.isRefreshTokenUnusable()
    }

    /** No refresh token, refresh token expired, rejected by GitHub, or a refresh with it failed moments ago. */
    private fun AuthSession.isRefreshTokenUnusable(): Boolean {
        val token = refreshToken?.takeIf { it.isNotBlank() } ?: return true
        val now = deviceFlowClient.nowEpochSeconds()
        val refreshExpiresAt = refreshTokenExpiresAtEpochSeconds
        if (refreshExpiresAt != null && refreshExpiresAt <= now) return true
        val retryAt = refreshRetryNotBefore.value[token] ?: return false
        if (retryAt == Long.MAX_VALUE) return true
        // A clock that jumped backwards must not stretch the pause.
        return now < retryAt && retryAt - now <= REFRESH_RETRY_BACKOFF_SECONDS
    }

    private fun AuthSession.isExpiringSoon(): Boolean {
        val expiresAt = accessTokenExpiresAtEpochSeconds ?: return false
        return expiresAt - deviceFlowClient.nowEpochSeconds() <= PROACTIVE_REFRESH_WINDOW_SECONDS
    }

    suspend fun signOut() {
        // Wait for an in-flight token refresh so it cannot save the session again after it was cleared.
        refreshMutex.withLock {
            tokenStore.clearSession()
            _session.value = null
        }
        tokenStore.saveSeenNotificationMarkers(emptyMap())
        tokenStore.savePinnedRepos(emptyList())
        _pinnedRepos.value = emptyList()
    }

    companion object {
        /** Refresh an expiring access token this many seconds before it expires. */
        const val PROACTIVE_REFRESH_WINDOW_SECONDS = 5 * 60L

        /**
         * Shared by all instances: Android's notification worker creates its own SessionManager, and a refresh
         * token can be used only once, so refreshes in one process must not overlap.
         */
        private val refreshMutex = Mutex()

        /** After a refresh fails for a reason other than a rejected refresh token, wait this long before retrying. */
        const val REFRESH_RETRY_BACKOFF_SECONDS = 30L

        /**
         * Refresh token -> epoch second before which it is not sent again ([Long.MAX_VALUE] once GitHub rejected
         * it). Written under [refreshMutex]; an immutable map in a StateFlow so it can be read without the lock.
         */
        private val refreshRetryNotBefore = MutableStateFlow<Map<String, Long>>(emptyMap())
    }
}
