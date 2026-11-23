package dev.maxxing.github.auth

import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.DeviceCodeState
import dev.maxxing.github.model.ProviderType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val _accounts = MutableStateFlow<List<AuthSession>>(emptyList())
    val accounts: StateFlow<List<AuthSession>> = _accounts.asStateFlow()

    private val _activeAccountId = MutableStateFlow<String?>(null)
    val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()

    private val _session = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    private val _reauthRequiredAccountIds = MutableStateFlow<Set<String>>(emptySet())
    val reauthRequiredAccountIds: StateFlow<Set<String>> = _reauthRequiredAccountIds.asStateFlow()

    private val _clientId = MutableStateFlow(defaultClientId)
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _themePreferences = MutableStateFlow(ThemePreferences())
    val themePreferences: StateFlow<ThemePreferences> = _themePreferences.asStateFlow()

    private val _notificationPreferences = MutableStateFlow(NotificationPreferences())
    val notificationPreferences: StateFlow<NotificationPreferences> = _notificationPreferences.asStateFlow()

    private val _updatePreferences = MutableStateFlow(UpdatePreferences())
    val updatePreferences: StateFlow<UpdatePreferences> = _updatePreferences.asStateFlow()

    private val _pinnedRepos = MutableStateFlow<List<String>>(emptyList())
    val pinnedRepos: StateFlow<List<String>> = _pinnedRepos.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    suspend fun initialize() {
        coroutineScope {
            val clientIdDeferred = async { tokenStore.loadClientId() }
            val themeDeferred = async { tokenStore.loadThemePreferences() }
            val notifDeferred = async { tokenStore.loadNotificationPreferences() }
            val updateDeferred = async { tokenStore.loadUpdatePreferences() }
            val indexDeferred = async { tokenStore.loadAccountIndex() }

            val savedClientId = clientIdDeferred.await()
            if (!savedClientId.isNullOrBlank()) {
                _clientId.value = savedClientId
            }
            _themePreferences.value = themeDeferred.await()
            _notificationPreferences.value = notifDeferred.await()
            _updatePreferences.value = updateDeferred.await()

            val index = indexDeferred.await()
            val loadedAccounts = mutableListOf<AuthSession>()
            var resolvedActiveId: String? = null

            if (index != null && index.accountIds.isNotEmpty()) {
                for (id in index.accountIds.distinct()) {
                    val account = tokenStore.loadAccount(id)
                    if (account != null) {
                        val normalized = normalizeStoredAccount(account)
                        loadedAccounts += normalized
                    }
                }
                resolvedActiveId = index.activeAccountId
                    ?.takeIf { active -> loadedAccounts.any { it.resolvedAccountId() == active } }
                    ?: loadedAccounts.firstOrNull()?.resolvedAccountId()
            } else {
                // Migrate legacy single-session storage transparently on first startup.
                val legacy = tokenStore.loadSession()
                if (legacy != null) {
                    val id = legacy.resolvedAccountId()
                    if (tokenStore.supportsMultiAccountStorage) {
                        val normalized = normalizeStoredAccount(legacy)
                        loadedAccounts += normalized
                        resolvedActiveId = id
                        runCatching {
                            tokenStore.saveAccount(normalized)
                            tokenStore.saveAccountIndex(AccountIndex(listOf(id), id))
                        }
                    } else {
                        loadedAccounts += legacy
                        resolvedActiveId = id
                    }
                }
            }

            _accounts.value = loadedAccounts
            _activeAccountId.value = resolvedActiveId
            _session.value = loadedAccounts.firstOrNull { it.resolvedAccountId() == resolvedActiveId }
            _pinnedRepos.value = if (resolvedActiveId != null) {
                loadNormalizedPinnedRepos(resolvedActiveId)
            } else {
                emptyList()
            }
        }
        _isInitialized.value = true
    }

    private fun normalizeStoredAccount(session: AuthSession): AuthSession {
        if (!tokenStore.supportsMultiAccountStorage && session.accountId.isEmpty()) return session
        val resolvedId = session.resolvedAccountId()
        return if (session.accountId == resolvedId) session else session.copy(accountId = resolvedId)
    }

    private suspend fun loadNormalizedPinnedRepos(accountId: String): List<String> {
        val perAccount = tokenStore.loadPinnedReposForAccount(accountId)
        val source = if (perAccount.isNotEmpty()) perAccount else tokenStore.loadPinnedRepos()
        return source.map { it.trim() }.filter { '/' in it }.distinct()
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
                addOrUpdateAccount(state.session, makeActive = true)
                if (targetId.isNotEmpty()) {
                    _clientId.value = targetId
                    tokenStore.saveClientId(targetId)
                }
            }
        }
    }

    suspend fun signInWithPat(token: String): Result<AuthSession> {
        return deviceFlowClient.verifyPersonalAccessToken(token).onSuccess { verified ->
            addOrUpdateAccount(verified, makeActive = true)
        }
    }

    suspend fun signInWithForgejoPat(
        baseUrl: String,
        token: String,
        makeActive: Boolean = true
    ): Result<AuthSession> {
        return deviceFlowClient.verifyForgejoPat(baseUrl, token).onSuccess { verified ->
            addOrUpdateAccount(verified, makeActive = makeActive)
        }
    }

    /**
     * Adds a new account or updates an existing entry with the same [AuthSession.resolvedAccountId]
     * without overwriting any other stored accounts.
     */
    suspend fun addOrUpdateAccount(newSession: AuthSession, makeActive: Boolean = true): AuthSession {
        val normalized = normalizeStoredAccount(newSession)
        val id = normalized.resolvedAccountId()
        refreshMutex.withLock {
            clearUnsavedRefreshStateLocked(id)
            tokenStore.saveAccount(normalized)
            val currentList = _accounts.value.toMutableList()
            val existingIdx = currentList.indexOfFirst { it.resolvedAccountId() == id }
            if (existingIdx >= 0) {
                currentList[existingIdx] = normalized
            } else {
                currentList += normalized
            }
            val nextActiveId = if (makeActive || _activeAccountId.value == null) {
                id
            } else {
                _activeAccountId.value
            }
            val ids = currentList.map { it.resolvedAccountId() }
            tokenStore.saveAccountIndex(AccountIndex(accountIds = ids, activeAccountId = nextActiveId))
            _accounts.value = currentList
            _activeAccountId.value = nextActiveId
            _session.value = currentList.firstOrNull { it.resolvedAccountId() == nextActiveId }
            _reauthRequiredAccountIds.update { it - id }
        }
        val activeId = _activeAccountId.value
        if (activeId == id) {
            _pinnedRepos.value = loadNormalizedPinnedRepos(id)
        }
        return normalized
    }

    suspend fun switchAccount(accountId: String): Boolean {
        val target = _accounts.value.firstOrNull { it.resolvedAccountId() == accountId } ?: return false
        refreshMutex.withLock {
            val ids = _accounts.value.map { it.resolvedAccountId() }
            tokenStore.saveAccountIndex(AccountIndex(accountIds = ids, activeAccountId = accountId))
            _activeAccountId.value = accountId
            _session.value = target
        }
        _pinnedRepos.value = loadNormalizedPinnedRepos(accountId)
        return true
    }

    /** Updates cached display metadata (such as avatar URL) for an account without clobbering tokens. */
    suspend fun updateAccountProfileMetadata(accountId: String, userLogin: String?, avatarUrl: String?) {
        val cleanAvatar = avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
        val cleanLogin = userLogin?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanAvatar == null && cleanLogin == null) return
        refreshMutex.withLock {
            val currentList = _accounts.value
            val idx = currentList.indexOfFirst { it.resolvedAccountId() == accountId }
            if (idx < 0) return
            val existing = currentList[idx]
            val updated = existing.copy(
                userLogin = cleanLogin ?: existing.userLogin,
                avatarUrl = cleanAvatar ?: existing.avatarUrl
            )
            if (updated == existing) return
            tokenStore.saveAccount(updated)
            val nextList = currentList.toMutableList().apply { this[idx] = updated }
            _accounts.value = nextList
            if (_activeAccountId.value == accountId) {
                _session.value = updated
            }
        }
    }

    fun markReauthRequired(accountId: String) {
        if (accountId.isBlank()) return
        _reauthRequiredAccountIds.update { it + accountId }
    }

    fun clearReauthRequired(accountId: String) {
        if (accountId.isBlank()) return
        _reauthRequiredAccountIds.update { it - accountId }
    }

    suspend fun updateThemePreferences(prefs: ThemePreferences) {
        _themePreferences.value = prefs
        tokenStore.saveThemePreferences(prefs)
    }

    suspend fun updateNotificationPreferences(prefs: NotificationPreferences) {
        _notificationPreferences.value = prefs
        tokenStore.saveNotificationPreferences(prefs)
    }

    suspend fun updateUpdatePreferences(prefs: UpdatePreferences) {
        _updatePreferences.value = prefs
        tokenStore.saveUpdatePreferences(prefs)
    }

    suspend fun loadSeenNotificationMarkers(accountId: String? = _activeAccountId.value): Map<String, String> =
        if (!accountId.isNullOrBlank()) {
            tokenStore.loadSeenNotificationMarkersForAccount(accountId)
        } else {
            tokenStore.loadSeenNotificationMarkers()
        }

    suspend fun saveSeenNotificationMarkers(
        markers: Map<String, String>,
        accountId: String? = _activeAccountId.value
    ) {
        if (!accountId.isNullOrBlank()) {
            tokenStore.saveSeenNotificationMarkersForAccount(accountId, markers)
        } else {
            tokenStore.saveSeenNotificationMarkers(markers)
        }
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
        val activeId = _activeAccountId.value
        if (!activeId.isNullOrBlank()) {
            tokenStore.savePinnedReposForAccount(activeId, next)
        } else {
            tokenStore.savePinnedRepos(next)
        }
        return nowPinned
    }

    /**
     * Proactively refreshes an expiring OAuth access token shortly before it expires and returns the session to
     * use for the next request (possibly unchanged).
     *
     * Cheap when no refresh is due or possible: returns without locking or touching the token store.
     */
    suspend fun refreshSessionIfExpiringSoon(accountId: String? = _activeAccountId.value): AuthSession? {
        val targetId = accountId ?: return null
        val current = accountById(targetId) ?: return null
        if (current.providerType != ProviderType.GITHUB ||
            current.refreshToken.isNullOrBlank() ||
            !current.isExpiringSoon()
        ) {
            return current
        }
        return refreshSession(
            accountId = targetId,
            failedAccessToken = current.accessToken,
            onlyIfExpiringSoon = true
        ) ?: accountById(targetId)
    }

    /**
     * Called after HTTP 401 for [rejectedAccessToken] belonging to [accountId]. Returns a session whose
     * access token differs from [rejectedAccessToken] (refreshed here or by another [SessionManager] in this
     * process), or null when no refresh is possible.
     */
    suspend fun refreshAfterUnauthorized(
        rejectedAccessToken: String,
        accountId: String? = _activeAccountId.value
    ): AuthSession? {
        val targetId = accountId ?: return null
        return refreshSession(
            accountId = targetId,
            failedAccessToken = rejectedAccessToken,
            onlyIfExpiringSoon = false
        )?.takeIf { it.accessToken != rejectedAccessToken }
    }

    private fun accountById(accountId: String): AuthSession? =
        _accounts.value.firstOrNull { it.resolvedAccountId() == accountId }
            ?: _session.value?.takeIf { it.resolvedAccountId() == accountId }

    private suspend fun refreshSession(
        accountId: String,
        failedAccessToken: String,
        onlyIfExpiringSoon: Boolean
    ): AuthSession? {
        // Checked before and again inside the lock: requests queued behind a failed attempt must not each re-read
        // the token store (a keyring process on desktop) or retry the network.
        if (isRefreshBlocked(accountId, failedAccessToken)) return null
        return refreshMutex.withLock {
            if (isRefreshBlocked(accountId, failedAccessToken)) return@withLock null
            // NonCancellable: GitHub invalidates the old refresh token as soon as it answers, so the new pair must
            // reach the token store even if the request that triggered the refresh is cancelled meanwhile.
            withContext(NonCancellable) {
                val unsaved = unsavedRefreshedSessions[accountId]
                if (unsaved != null && runCatching { tokenStore.saveAccount(unsaved) }.isSuccess) {
                    clearUnsavedRefreshStateLocked(accountId)
                }
                val stored = tokenStore.loadAccount(accountId)
                val activeUnsaved = unsavedRefreshedSessions[accountId]
                val supersededToken = supersededAccessTokens[accountId]
                val current = when {
                    activeUnsaved != null && (stored == null || stored.accessToken == supersededToken) -> activeUnsaved
                    stored != null -> {
                        if (activeUnsaved != null && stored.accessToken != supersededToken) {
                            clearUnsavedRefreshStateLocked(accountId)
                        }
                        stored
                    }
                    else -> return@withContext null
                }
                if (current.providerType != ProviderType.GITHUB) {
                    return@withContext null
                }
                if (current.accessToken != failedAccessToken) {
                    // Another instance already refreshed (or the user signed in again).
                    adoptRefreshedSessionLocked(accountId, current)
                    return@withContext current
                }
                if (onlyIfExpiringSoon && !current.isExpiringSoon()) return@withContext current
                if (current.isRefreshTokenUnusable()) return@withContext null
                val refreshToken = current.refreshToken ?: return@withContext null

                deviceFlowClient.refreshAccessToken(current).fold(
                    onSuccess = { refreshed ->
                        val normalized = normalizeStoredAccount(refreshed)
                        adoptRefreshedSessionLocked(accountId, normalized)
                        refreshRetryNotBefore.update { it - refreshToken }
                        _reauthRequiredAccountIds.update { it - accountId }
                        val saved = runCatching { tokenStore.saveAccount(normalized) }.isSuccess
                        if (saved) {
                            clearUnsavedRefreshStateLocked(accountId)
                        } else {
                            unsavedRefreshedSessions = unsavedRefreshedSessions + (accountId to normalized)
                            supersededAccessTokens = supersededAccessTokens + (accountId to (stored?.accessToken ?: current.accessToken))
                        }
                        normalized
                    },
                    onFailure = { error ->
                        val now = deviceFlowClient.nowEpochSeconds()
                        val retryAt = if (error is RefreshTokenRejectedException) {
                            _reauthRequiredAccountIds.update { it + accountId }
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

    private fun adoptRefreshedSessionLocked(accountId: String, updated: AuthSession) {
        val currentList = _accounts.value
        val idx = currentList.indexOfFirst { it.resolvedAccountId() == accountId }
        if (idx >= 0) {
            _accounts.value = currentList.toMutableList().apply { this[idx] = updated }
        }
        if (_activeAccountId.value == accountId || _session.value?.resolvedAccountId() == accountId) {
            _session.value = updated
        }
    }

    private fun clearUnsavedRefreshStateLocked(accountId: String) {
        unsavedRefreshedSessions = unsavedRefreshedSessions - accountId
        supersededAccessTokens = supersededAccessTokens - accountId
    }

    private fun isRefreshBlocked(accountId: String, failedAccessToken: String): Boolean {
        val current = accountById(accountId) ?: return false
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

    /**
     * Removes a single account by [accountId]. If it was active, switches automatically to the next
     * remaining account, or clears the active session (returning to signed-out state) when none remain.
     */
    suspend fun removeAccount(accountId: String): AuthSession? {
        var nextActive: AuthSession? = null
        refreshMutex.withLock {
            clearUnsavedRefreshStateLocked(accountId)
            tokenStore.deleteAccount(accountId)
            tokenStore.saveSeenNotificationMarkersForAccount(accountId, emptyMap())
            tokenStore.savePinnedReposForAccount(accountId, emptyList())

            val remaining = _accounts.value.filterNot { it.resolvedAccountId() == accountId }
            val nextActiveId = if (_activeAccountId.value == accountId) {
                remaining.firstOrNull()?.resolvedAccountId()
            } else {
                _activeAccountId.value?.takeIf { active -> remaining.any { it.resolvedAccountId() == active } }
                    ?: remaining.firstOrNull()?.resolvedAccountId()
            }
            nextActive = remaining.firstOrNull { it.resolvedAccountId() == nextActiveId }
            tokenStore.saveAccountIndex(
                AccountIndex(
                    accountIds = remaining.map { it.resolvedAccountId() },
                    activeAccountId = nextActiveId
                )
            )
            if (remaining.isEmpty()) {
                tokenStore.clearSession()
            }
            _accounts.value = remaining
            _activeAccountId.value = nextActiveId
            _session.value = nextActive
            _reauthRequiredAccountIds.update { it - accountId }
        }
        val activeId = _activeAccountId.value
        _pinnedRepos.value = if (activeId != null) {
            loadNormalizedPinnedRepos(activeId)
        } else {
            emptyList()
        }
        return nextActive
    }

    suspend fun signOut() {
        val currentActiveId = _activeAccountId.value
        if (currentActiveId != null) {
            removeAccount(currentActiveId)
        } else {
            refreshMutex.withLock {
                unsavedRefreshedSessions = emptyMap()
                supersededAccessTokens = emptyMap()
                tokenStore.clearSession()
                _session.value = null
                _accounts.value = emptyList()
                _activeAccountId.value = null
            }
            tokenStore.saveSeenNotificationMarkers(emptyMap())
            tokenStore.savePinnedRepos(emptyList())
            _pinnedRepos.value = emptyList()
        }
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

        @kotlin.concurrent.Volatile
        private var unsavedRefreshedSessions: Map<String, AuthSession> = emptyMap()

        @kotlin.concurrent.Volatile
        private var supersededAccessTokens: Map<String, String> = emptyMap()
    }
}
