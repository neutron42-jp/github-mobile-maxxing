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
import kotlinx.coroutines.launch

data class IssuePullTarget(
    val owner: String,
    val repo: String,
    val number: Int,
    val hintTitle: String? = null
)

class AppContainer {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionManager: SessionManager = SessionManager(
        defaultClientId = MaxxingBuildConfig.DEFAULT_GITHUB_CLIENT_ID.ifBlank { AppBranding.DEFAULT_OAUTH_CLIENT_ID }
    )
    val repository: GitHubRepository = GitHubRepository(sessionManager)
    val notificationMonitor: NotificationMonitor = NotificationMonitor(sessionManager, repository) {
        !PlatformNotifier.areOsNotificationsBlocked()
    }

    private val _viewerProfile = MutableStateFlow<ViewerProfile?>(null)
    val viewerProfile: StateFlow<ViewerProfile?> = _viewerProfile.asStateFlow()

    private val _unreadNotificationCount = MutableStateFlow(0)
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount.asStateFlow()

    private val _unreadThreads = MutableStateFlow<List<NotificationThread>?>(null)
    val unreadThreads: StateFlow<List<NotificationThread>?> = _unreadThreads.asStateFlow()

    fun updateUnreadCount(count: Int) {
        _unreadNotificationCount.value = count.coerceAtLeast(0)
    }

    fun updateUnreadThreads(threads: List<NotificationThread>) {
        _unreadThreads.value = threads
        _unreadNotificationCount.value = threads.count { it.unread }
    }

    suspend fun syncNotifications(): Result<NotificationCheckResult> {
        return notificationMonitor.checkForNewNotifications().onSuccess { check ->
            _unreadThreads.value = check.allUnread
            _unreadNotificationCount.value = check.allUnread.size
            if (check.newlyArrivedForOs.isNotEmpty()) {
                PlatformNotifier.showGitHubNotifications(check.newlyArrivedForOs)
            }
            val topTargets = check.allUnread.mapNotNull { thread ->
                thread.subjectNumber?.let { num ->
                    IssuePullTarget(thread.repoOwner, thread.repoName, num, thread.subjectTitle)
                }
            }.take(3)
            prefetchIssuePullTargets(topTargets)
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
        _unreadNotificationCount.value = 0
        _unreadThreads.value = null
        backgroundScope.launch {
            repository.clearCache()
        }
    }
}
