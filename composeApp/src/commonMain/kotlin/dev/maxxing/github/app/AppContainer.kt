package dev.maxxing.github.app

import dev.maxxing.github.app.notifications.PlatformNotifier
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.NotificationThread
import dev.maxxing.github.model.ViewerProfile
import dev.maxxing.github.network.GitHubRepository
import dev.maxxing.github.network.NotificationCheckResult
import dev.maxxing.github.network.NotificationMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

data class IssuePullTarget(
    val owner: String,
    val repo: String,
    val number: Int,
    val hintTitle: String? = null
)

/**
 * The single application graph shared by the UI, notification actions and the
 * APNs bridge. Every dependency is a defaulted constructor parameter so
 * `AppContainer()` keeps the existing behaviour and tests can inject fakes.
 */
class AppContainer(
    val sessionManager: SessionManager = SessionManager(
        defaultClientId = MaxxingBuildConfig.DEFAULT_GITHUB_CLIENT_ID.ifBlank { AppBranding.DEFAULT_OAUTH_CLIENT_ID }
    ),
    val repository: GitHubRepository = GitHubRepository(sessionManager),
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

    suspend fun syncNotifications(): Result<NotificationCheckResult> {
        return syncNotificationsMutex.withLock {
            val syncStartedAtMs = trackerNowMs()
            notificationMonitor.checkForNewNotifications().map { check ->
                val reconciled = updateTracker {
                    it.reconcileFromServer(
                        fetchedUnread = check.allUnread,
                        syncStartedAtMs = syncStartedAtMs,
                        nowMs = trackerNowMs()
                    )
                }.currentThreads.orEmpty()
                if (check.newlyArrivedForOs.isNotEmpty()) {
                    showOsNotifications(check.newlyArrivedForOs)
                }
                val topTargets = reconciled.mapNotNull { thread ->
                    thread.subjectNumber?.let { num ->
                        IssuePullTarget(thread.repoOwner, thread.repoName, num, thread.subjectTitle)
                    }
                }.take(3)
                prefetchIssuePullTargets(topTargets)
                check.copy(allUnread = reconciled)
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
        backgroundScope.launch {
            // 1. Inbox tabs (Unread, All, Participating)
            launch { repository.getNotifications(all = false, participating = false, forceRefresh = false) }
            launch { repository.getNotifications(all = true, participating = false, forceRefresh = false) }
            launch { repository.getNotifications(all = false, participating = true, forceRefresh = false) }
            // 2. Repos tabs (My Repos, Starred, Explore Trending)
            launch { repository.getViewerRepositories(forceRefresh = false) }
            launch { repository.getStarredRepositories(forceRefresh = false) }
            launch { repository.getTrendingRepositories(language = null, forceRefresh = false) }
            if (!login.isNullOrBlank()) {
                // 3. Home feed tabs + Profile
                launch { repository.getContributionCalendar(login, forceRefresh = false) }
                launch { repository.getReceivedEvents(login, forceRefresh = false) }
                launch { repository.getUserEvents(login, forceRefresh = false) }
                launch { repository.getUserProfile(login, forceRefresh = false) }
                launch { repository.getUserOrganizations(login, forceRefresh = false) }
                // 4. Issues & PRs presets (Created, Assigned, Review Requested, Mentioned)
                IssueFilterPreset.entries.forEach { preset ->
                    launch {
                        repository.getMyIssuesAndPulls(
                            preset = preset,
                            viewerLogin = login,
                            stateFilter = IssueStateFilter.OPEN,
                            forceRefresh = false
                        ).onSuccess { list ->
                            if (preset == IssueFilterPreset.CREATED) {
                                prefetchIssuePullTargets(
                                    list.take(3).map { IssuePullTarget(it.repoOwner, it.repoName, it.number, it.title) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun refreshViewerProfile(): ViewerProfile? {
        return repository.getViewerProfile().getOrNull()?.also {
            _viewerProfile.value = it
        }
    }

    fun clearCachedProfile() {
        _viewerProfile.value = null
        updateTracker { UnreadNotificationTrackerState() }
        backgroundScope.launch {
            repository.clearCache()
        }
    }
}
