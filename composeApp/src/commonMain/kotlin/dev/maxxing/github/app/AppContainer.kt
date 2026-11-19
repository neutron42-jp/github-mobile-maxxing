package dev.maxxing.github.app

import dev.maxxing.github.app.notifications.PlatformNotifier
import dev.maxxing.github.app.update.AvailableAppUpdate
import dev.maxxing.github.app.update.PlatformAppUpdater
import dev.maxxing.github.app.update.isNightlyBuildVersion
import dev.maxxing.github.app.update.selectAvailableUpdate
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.openUrlInBrowser
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.NotificationSubjectType
import dev.maxxing.github.model.NotificationThread
import dev.maxxing.github.model.ViewerProfile
import dev.maxxing.github.network.GitHubRepository
import dev.maxxing.github.network.NotificationCheckResult
import dev.maxxing.github.network.NotificationMonitor
import dev.maxxing.github.network.createDefaultPersistentCacheStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

data class IssuePullTarget(
    val owner: String,
    val repo: String,
    val number: Int,
    val hintTitle: String? = null,
    val kindHint: IssueOrPullKind? = null
)

sealed interface AppUpdateCheckState {
    data object Idle : AppUpdateCheckState
    data object Checking : AppUpdateCheckState
    data object UpToDate : AppUpdateCheckState
    data class Available(val update: AvailableAppUpdate) : AppUpdateCheckState
    data class Error(val detail: String?) : AppUpdateCheckState
}

sealed interface AppUpdateInstallState {
    data object Idle : AppUpdateInstallState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : AppUpdateInstallState
    data class ReadyToInstall(val versionKey: String, val filePath: String) : AppUpdateInstallState
    data class PermissionRequired(val versionKey: String, val filePath: String) : AppUpdateInstallState
    data class Error(val detail: String?) : AppUpdateInstallState
}

/**
 * The single application graph shared by the UI, notification actions and the
 * APNs bridge. Every dependency is a defaulted constructor parameter so
 * `AppContainer()` keeps the existing behaviour and tests can inject fakes.
 */
class AppContainer(
    val sessionManager: SessionManager = SessionManager(
        defaultClientId = MaxxingBuildConfig.DEFAULT_GITHUB_CLIENT_ID.ifBlank { AppBranding.DEFAULT_OAUTH_CLIENT_ID }
    ),
    val repository: GitHubRepository = GitHubRepository(
        sessionManager = sessionManager,
        diskCache = createDefaultPersistentCacheStore()
    ),
    val notificationMonitor: NotificationMonitor = NotificationMonitor(sessionManager, repository) {
        !PlatformNotifier.areOsNotificationsBlocked()
    },
    private val showOsNotifications: (List<NotificationThread>) -> Unit = {
        PlatformNotifier.showGitHubNotifications(it)
    },
    private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /**
     * Serializes [syncNotifications]. The foreground `ON_RESUME` sync, the
     * periodic poller and the iOS notification-action path can all race;
     * `NotificationMonitor` performs a read-modify-write of the seen markers,
     * so overlapping runs could lose markers. Only the observable effect
     * changes: a concurrent call waits for the in-flight one.
     */
    private val syncNotificationsMutex = Mutex()

    private val _viewerProfile = MutableStateFlow<ViewerProfile?>(null)
    val viewerProfile: StateFlow<ViewerProfile?> = _viewerProfile.asStateFlow()

    private val _unreadNotificationCount = MutableStateFlow(0)
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount.asStateFlow()

    private val _unreadThreads = MutableStateFlow<List<NotificationThread>?>(null)
    val unreadThreads: StateFlow<List<NotificationThread>?> = _unreadThreads.asStateFlow()

    private val _updateCheckState = MutableStateFlow<AppUpdateCheckState>(AppUpdateCheckState.Idle)
    val updateCheckState: StateFlow<AppUpdateCheckState> = _updateCheckState.asStateFlow()

    private val _updateInstallState = MutableStateFlow<AppUpdateInstallState>(AppUpdateInstallState.Idle)
    val updateInstallState: StateFlow<AppUpdateInstallState> = _updateInstallState.asStateFlow()

    private val _showUpdateSheet = MutableStateFlow(false)
    val showUpdateSheet: StateFlow<Boolean> = _showUpdateSheet.asStateFlow()

    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var warmUpJob: Job? = null
    private var warmUpLogin: String? = null

    @Volatile
    var isAppInForeground: Boolean = false
        private set

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    private val trackerState = MutableStateFlow(UnreadNotificationTrackerState())
    private val trackerClockOrigin = TimeSource.Monotonic.markNow()

    private fun trackerNowMs(): Long = trackerClockOrigin.elapsedNow().inWholeMilliseconds

    private fun updateTracker(transform: (UnreadNotificationTrackerState) -> UnreadNotificationTrackerState): UnreadNotificationTrackerState {
        val next = trackerState.updateAndGet(transform)
        _unreadThreads.value = next.currentThreads
        _unreadNotificationCount.value = next.unreadCount
        return next
    }

    fun updateUnreadCount(count: Int) {
        updateTracker { it.withDirectCount(count) }
    }

    fun updateUnreadThreads(threads: List<NotificationThread>) {
        val nowMs = trackerNowMs()
        updateTracker { it.reconcileFromServer(threads, syncStartedAtMs = nowMs, nowMs = nowMs) }
    }

    fun markNotificationRead(threadId: String) {
        val nowMs = trackerNowMs()
        updateTracker { it.markReadLocally(threadId, nowMs) }
        backgroundScope.launch {
            repository.markNotificationRead(threadId).onFailure {
                updateTracker { it.rollbackSuppression(threadId) }
            }
        }
    }

    fun markIssueTargetNotificationRead(target: IssuePullTarget) {
        val matches = trackerState.value.currentThreads.orEmpty().filter { thread ->
            thread.unread &&
                thread.repoOwner.equals(target.owner, ignoreCase = true) &&
                thread.repoName.equals(target.repo, ignoreCase = true) &&
                thread.subjectNumber == target.number
        }
        matches.forEach { markNotificationRead(it.id) }
    }

    fun markNotificationDone(
        threadId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        val nowMs = trackerNowMs()
        updateTracker { it.markDoneLocally(threadId, nowMs) }
        backgroundScope.launch {
            repository.markNotificationDone(threadId)
                .onSuccess { onSuccess() }
                .onFailure { err ->
                    updateTracker { it.rollbackSuppression(threadId) }
                    syncNotifications()
                    onFailure(err)
                }
        }
    }

    fun markAllNotificationsRead(
        onSuccess: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        val nowMs = trackerNowMs()
        updateTracker { it.markAllReadLocally(nowMs) }
        backgroundScope.launch {
            repository.markAllNotificationsRead()
                .onSuccess { onSuccess() }
                .onFailure { err ->
                    updateTracker { it.rollbackMarkAll() }
                    syncNotifications()
                    onFailure(err)
                }
        }
    }

    suspend fun markNotificationsSeen(threads: List<NotificationThread>) {
        notificationMonitor.markThreadsSeen(threads)
    }

    suspend fun syncNotifications(
        suppressOsAlerts: Boolean = isAppInForeground
    ): Result<NotificationCheckResult> {
        return syncNotificationsMutex.withLock {
            val syncStartedAtMs = trackerNowMs()
            notificationMonitor.checkForNewNotifications(suppressOsAlerts = suppressOsAlerts).map { check ->
                val reconciled = updateTracker {
                    it.reconcileFromServer(
                        fetchedUnread = check.allUnread,
                        syncStartedAtMs = syncStartedAtMs,
                        nowMs = trackerNowMs()
                    )
                }.currentThreads.orEmpty()
                if (!suppressOsAlerts && check.newlyArrivedForOs.isNotEmpty()) {
                    showOsNotifications(check.newlyArrivedForOs)
                }
                val topTargets = reconciled.mapNotNull { thread ->
                    thread.subjectNumber?.let { num ->
                        val hint = when (thread.subjectType) {
                            NotificationSubjectType.PULL_REQUEST -> IssueOrPullKind.PULL_REQUEST
                            NotificationSubjectType.ISSUE -> IssueOrPullKind.ISSUE
                            else -> null
                        }
                        IssuePullTarget(thread.repoOwner, thread.repoName, num, thread.subjectTitle, kindHint = hint)
                    }
                }.take(3)
                prefetchIssuePullTargets(topTargets)
                check.copy(allUnread = reconciled)
            }
        }
    }

    suspend fun hydrateFromDisk(loginHint: String?) {
        repository.hydrateDiskCache(loginHint)
        if (_viewerProfile.value == null) {
            repository.peekViewerProfile()?.let { _viewerProfile.value = it }
        }
        if (trackerState.value.currentThreads == null) {
            repository.peekNotifications(all = false, participating = false)?.let { cachedUnread ->
                val nowMs = trackerNowMs()
                updateTracker {
                    it.reconcileFromServer(
                        fetchedUnread = cachedUnread,
                        syncStartedAtMs = nowMs,
                        nowMs = nowMs
                    )
                }
            }
        }
    }

    fun prefetchIssuePullTargets(targets: List<IssuePullTarget>) {
        targets.take(3).forEach { target ->
            backgroundScope.launch {
                repository.prefetchIssueOrPull(target.owner, target.repo, target.number)
            }
        }
    }

    fun warmUpWorkspace(login: String?) {
        val cleanLogin = login?.trim()?.takeIf { it.isNotEmpty() }
        if (warmUpJob?.isActive == true && warmUpLogin.equals(cleanLogin, ignoreCase = true)) {
            return
        }
        warmUpJob?.cancel()
        warmUpLogin = cleanLogin
        warmUpJob = backgroundScope.launch {
            val gate = Semaphore(permits = 4)
            suspend fun throttled(block: suspend () -> Unit) {
                gate.withPermit { block() }
            }

            var createdList: List<dev.maxxing.github.model.IssueOrPullSummary>? = null

            // Tier 1: Critical first-paint & primary repo/inbox tabs
            coroutineScope {
                val jobs = mutableListOf<Job>()
                jobs += launch { throttled { repository.getViewerRepositories(forceRefresh = false) } }
                jobs += launch { throttled { repository.getStarredRepositories(forceRefresh = false) } }
                jobs += launch { throttled { repository.getNotifications(all = false, participating = false, forceRefresh = false) } }
                if (cleanLogin != null) {
                    jobs += launch { throttled { repository.getContributionCalendar(cleanLogin, forceRefresh = false) } }
                    jobs += launch { throttled { repository.getReceivedEvents(cleanLogin, forceRefresh = false) } }
                    jobs += launch {
                        throttled {
                            createdList = repository.getMyIssuesAndPulls(
                                preset = IssueFilterPreset.CREATED,
                                viewerLogin = cleanLogin,
                                stateFilter = IssueStateFilter.OPEN,
                                forceRefresh = false
                            ).getOrNull()
                        }
                    }
                }
                jobs.joinAll()
            }

            // Tier 2: Secondary tabs & filter presets
            coroutineScope {
                val jobs = mutableListOf<Job>()
                jobs += launch { throttled { repository.getNotifications(all = true, participating = false, forceRefresh = false) } }
                jobs += launch { throttled { repository.getNotifications(all = false, participating = true, forceRefresh = false) } }
                jobs += launch { throttled { repository.getTrendingRepositories(language = null, forceRefresh = false) } }
                if (cleanLogin != null) {
                    jobs += launch { throttled { repository.getUserEvents(cleanLogin, forceRefresh = false) } }
                    jobs += launch { throttled { repository.getUserProfile(cleanLogin, forceRefresh = false) } }
                    jobs += launch { throttled { repository.getUserOrganizations(cleanLogin, forceRefresh = false) } }
                    IssueFilterPreset.entries
                        .filter { it != IssueFilterPreset.CREATED }
                        .forEach { preset ->
                            jobs += launch {
                                throttled {
                                    repository.getMyIssuesAndPulls(
                                        preset = preset,
                                        viewerLogin = cleanLogin,
                                        stateFilter = IssueStateFilter.OPEN,
                                        forceRefresh = false
                                    )
                                }
                            }
                        }
                }
                jobs.joinAll()
            }

            // Tier 3: Speculative issue/PR detail prefetch for top items
            createdList?.take(3)?.map {
                IssuePullTarget(it.repoOwner, it.repoName, it.number, it.title, kindHint = it.kind)
            }?.let { prefetchIssuePullTargets(it) }
        }
    }

    suspend fun refreshViewerProfile(): ViewerProfile? {
        return repository.getViewerProfile().getOrNull()?.also {
            _viewerProfile.value = it
        }
    }

    fun clearCachedProfile() {
        warmUpJob?.cancel()
        warmUpJob = null
        warmUpLogin = null
        _viewerProfile.value = null
        updateTracker { UnreadNotificationTrackerState() }
        backgroundScope.launch {
            repository.clearCache()
        }
    }

    fun checkForUpdates(
        manual: Boolean,
        forceRefresh: Boolean = manual,
        showSheetIfFound: Boolean = true
    ) {
        if (!platformCapabilities.supportsInAppUpdates) return
        if (updateCheckJob?.isActive == true) {
            if (!manual) return
            updateCheckJob?.cancel()
        }
        updateCheckJob = backgroundScope.launch {
            _updateCheckState.value = AppUpdateCheckState.Checking
            val prefs = sessionManager.updatePreferences.value
            val includePrereleases = prefs.resolveIncludePrereleases(
                isCurrentBuildNightly = isNightlyBuildVersion(MaxxingBuildConfig.APP_VERSION_NAME)
            )
            repository.getReleases(
                owner = AppBranding.REPO_OWNER,
                repo = AppBranding.REPO_NAME,
                forceRefresh = forceRefresh
            ).onSuccess { releases ->
                val update = selectAvailableUpdate(
                    releases = releases,
                    currentVersionName = MaxxingBuildConfig.APP_VERSION_NAME,
                    currentCommitSha = MaxxingBuildConfig.APP_COMMIT_SHA,
                    includePrereleases = includePrereleases,
                    platform = PlatformAppUpdater.platformKind
                )
                if (update == null) {
                    _updateCheckState.value = AppUpdateCheckState.UpToDate
                    if (_updateInstallState.value !is AppUpdateInstallState.Downloading) {
                        _updateInstallState.value = AppUpdateInstallState.Idle
                    }
                } else {
                    _updateCheckState.value = AppUpdateCheckState.Available(update)
                    val existingInstall = _updateInstallState.value
                    val existingKey = when (existingInstall) {
                        is AppUpdateInstallState.ReadyToInstall -> existingInstall.versionKey
                        is AppUpdateInstallState.PermissionRequired -> existingInstall.versionKey
                        else -> null
                    }
                    if (existingKey != null && existingKey != update.versionKey) {
                        _updateInstallState.value = AppUpdateInstallState.Idle
                    }
                    if (showSheetIfFound && (manual || prefs.dismissedVersionKey != update.versionKey)) {
                        _showUpdateSheet.value = true
                    }
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _updateCheckState.value = if (manual) {
                    AppUpdateCheckState.Error(err.message)
                } else {
                    AppUpdateCheckState.Idle
                }
            }
        }
    }

    fun openUpdateSheet() {
        if (_updateCheckState.value is AppUpdateCheckState.Available) {
            _showUpdateSheet.value = true
        }
    }

    fun dismissUpdateSheet(rememberDismissal: Boolean) {
        _showUpdateSheet.value = false
        if (!rememberDismissal) return
        val available = _updateCheckState.value as? AppUpdateCheckState.Available ?: return
        val prefs = sessionManager.updatePreferences.value
        if (prefs.dismissedVersionKey != available.update.versionKey) {
            backgroundScope.launch {
                sessionManager.updateUpdatePreferences(
                    prefs.copy(dismissedVersionKey = available.update.versionKey)
                )
            }
        }
    }

    fun startUpdateAction(
        onMessage: (String) -> Unit,
        browserFailedMessage: String,
        installerFailedMessage: String
    ) {
        val available = (_updateCheckState.value as? AppUpdateCheckState.Available)?.update ?: return
        if (!PlatformAppUpdater.supportsInAppInstall) {
            val targetUrl = available.asset.downloadUrl.ifBlank { available.release.htmlUrl }
            if (openUrlInBrowser(targetUrl)) {
                _showUpdateSheet.value = false
            } else {
                _updateInstallState.value = AppUpdateInstallState.Error(browserFailedMessage)
                onMessage(browserFailedMessage)
            }
            return
        }

        val currentInstall = _updateInstallState.value
        if (currentInstall is AppUpdateInstallState.ReadyToInstall &&
            currentInstall.versionKey == available.versionKey
        ) {
            triggerAndroidInstall(currentInstall.filePath, available.versionKey, installerFailedMessage)
            return
        }
        if (currentInstall is AppUpdateInstallState.PermissionRequired &&
            currentInstall.versionKey == available.versionKey
        ) {
            triggerAndroidInstall(currentInstall.filePath, available.versionKey, installerFailedMessage)
            return
        }

        if (updateDownloadJob?.isActive == true) return
        updateDownloadJob = backgroundScope.launch {
            val expectedSize = available.asset.sizeBytes.takeIf { it > 0L }
            _updateInstallState.value = AppUpdateInstallState.Downloading(0L, expectedSize)
            PlatformAppUpdater.downloadUpdateAsset(
                url = available.asset.downloadUrl,
                fileName = available.asset.name,
                expectedSizeBytes = expectedSize,
                onProgress = { downloaded, total ->
                    _updateInstallState.value = AppUpdateInstallState.Downloading(downloaded, total)
                }
            ).onSuccess { filePath ->
                triggerAndroidInstall(filePath, available.versionKey, installerFailedMessage)
            }.onFailure { err ->
                if (err is CancellationException) {
                    _updateInstallState.value = AppUpdateInstallState.Idle
                } else {
                    _updateInstallState.value = AppUpdateInstallState.Error(err.message)
                }
            }
        }
    }

    fun installPendingPackage(installerFailedMessage: String) {
        when (val state = _updateInstallState.value) {
            is AppUpdateInstallState.ReadyToInstall ->
                triggerAndroidInstall(state.filePath, state.versionKey, installerFailedMessage)
            is AppUpdateInstallState.PermissionRequired ->
                triggerAndroidInstall(state.filePath, state.versionKey, installerFailedMessage)
            else -> Unit
        }
    }

    fun retryPendingInstallIfPermitted(installerFailedMessage: String) {
        val state = _updateInstallState.value as? AppUpdateInstallState.PermissionRequired ?: return
        if (PlatformAppUpdater.canInstallPackages()) {
            triggerAndroidInstall(state.filePath, state.versionKey, installerFailedMessage)
        }
    }

    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        _updateInstallState.value = AppUpdateInstallState.Idle
    }

    private fun triggerAndroidInstall(
        filePath: String,
        versionKey: String,
        installerFailedMessage: String
    ) {
        if (!PlatformAppUpdater.canInstallPackages()) {
            _updateInstallState.value = AppUpdateInstallState.PermissionRequired(versionKey, filePath)
            return
        }
        if (PlatformAppUpdater.installDownloadedPackage(filePath)) {
            _updateInstallState.value = AppUpdateInstallState.ReadyToInstall(versionKey, filePath)
        } else {
            _updateInstallState.value = AppUpdateInstallState.Error(installerFailedMessage)
        }
    }
}
