package dev.maxxing.github.network

import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.model.ActivityEvent
import dev.maxxing.github.model.BranchCompareResult
import dev.maxxing.github.model.BranchRef
import dev.maxxing.github.model.CheckRunSummary
import dev.maxxing.github.model.CodeSearchHit
import dev.maxxing.github.model.CommitDetail
import dev.maxxing.github.model.CommitSummary
import dev.maxxing.github.model.ContributionCalendar
import dev.maxxing.github.model.ContributorStat
import dev.maxxing.github.model.DependabotAlertSummary
import dev.maxxing.github.model.DiscussionComment
import dev.maxxing.github.model.DiscussionDetail
import dev.maxxing.github.model.DiscussionListData
import dev.maxxing.github.model.DiscussionSummary
import dev.maxxing.github.model.FileContent
import dev.maxxing.github.model.GistDetail
import dev.maxxing.github.model.GistSummary
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueOrPullDetail
import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueOrPullSummary
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.LabelTag
import dev.maxxing.github.model.MergeMethod
import dev.maxxing.github.model.MilestoneSummary
import dev.maxxing.github.model.NotificationThread
import dev.maxxing.github.model.OrganizationSummary
import dev.maxxing.github.model.PullFileChange
import dev.maxxing.github.model.RateLimitInfo
import dev.maxxing.github.model.ReleaseSummary
import dev.maxxing.github.model.RepoDetail
import dev.maxxing.github.model.RepoInsightsData
import dev.maxxing.github.model.RepoSecurityOverview
import dev.maxxing.github.model.RepoSummary
import dev.maxxing.github.model.ReviewEvent
import dev.maxxing.github.model.SecurityAdvisorySummary
import dev.maxxing.github.model.TagRef
import dev.maxxing.github.model.TreeEntry
import dev.maxxing.github.model.UserProfile
import dev.maxxing.github.model.UserSummary
import dev.maxxing.github.model.ViewerProfile
import dev.maxxing.github.model.WeeklyCommitActivity
import dev.maxxing.github.model.WorkflowJobSummary
import dev.maxxing.github.model.WorkflowRunSummary
import dev.maxxing.github.model.WorkflowSummary
import dev.maxxing.github.model.computeLanguageShares
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private data class CacheEntry<V>(
    val value: V,
    val recordedAt: TimeMark = TimeSource.Monotonic.markNow()
)

private class LruMemoryCache<K, V>(
    private val ttl: Duration,
    private val maxEntries: Int = 64
) {
    private val mutex = Mutex()

    @Volatile
    private var snapshot: Map<K, CacheEntry<V>> = emptyMap()

    fun peek(key: K): V? = snapshot[key]?.value

    fun entriesSnapshot(): Map<K, V> = snapshot.mapValues { it.value.value }

    suspend fun updateEntries(transform: (K, V) -> V) {
        mutex.withLock {
            if (snapshot.isEmpty()) return
            val next = LinkedHashMap<K, CacheEntry<V>>(snapshot.size)
            for ((k, entry) in snapshot) {
                next[k] = CacheEntry(transform(k, entry.value), entry.recordedAt)
            }
            snapshot = next
        }
    }

    fun peekIfFresh(key: K): V? {
        val entry = snapshot[key] ?: return null
        return if (entry.recordedAt.elapsedNow() < ttl) entry.value else null
    }

    suspend fun put(key: K, value: V) {
        mutex.withLock {
            val next = LinkedHashMap<K, CacheEntry<V>>(snapshot.size + 1)
            for ((k, v) in snapshot) {
                if (k != key) next[k] = v
            }
            next[key] = CacheEntry(value)
            while (next.size > maxEntries) {
                val eldest = next.keys.firstOrNull() ?: break
                next.remove(eldest)
            }
            snapshot = next
        }
    }

    suspend fun invalidate(key: K) {
        mutex.withLock {
            if (key in snapshot) {
                val next = LinkedHashMap(snapshot)
                next.remove(key)
                snapshot = next
            }
        }
    }

    suspend fun invalidateWhere(predicate: (K) -> Boolean) {
        mutex.withLock {
            if (snapshot.keys.any(predicate)) {
                val next = LinkedHashMap<K, CacheEntry<V>>()
                for ((k, v) in snapshot) {
                    if (!predicate(k)) next[k] = v
                }
                snapshot = next
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            snapshot = emptyMap()
        }
    }
}

private data class NotificationsKey(val all: Boolean, val participating: Boolean, val page: Int)
private data class MyIssuesKey(val preset: IssueFilterPreset, val viewerLogin: String, val stateFilter: IssueStateFilter)
private data class RepoKey(val owner: String, val repo: String)
private data class RepoMilestonesKey(val owner: String, val repo: String, val state: String)
private data class IssuePullKey(val owner: String, val repo: String, val number: Int)
private data class RepoIssuesKey(val owner: String, val repo: String, val pullRequests: Boolean, val stateFilter: IssueStateFilter)
private data class RepoPathKey(val owner: String, val repo: String, val path: String, val ref: String?)
private data class RepoCommitKey(val owner: String, val repo: String, val sha: String)
private data class RepoCompareKey(val owner: String, val repo: String, val base: String, val head: String)
private data class WorkflowRunsKey(val owner: String, val repo: String, val workflowId: Long?)
private data class WorkflowRunIdKey(val owner: String, val repo: String, val runId: Long)
private data class DiscussionsKey(val owner: String, val repo: String, val categoryId: String?)

private fun normalizeRepoPair(owner: String, repo: String): Pair<String, String> {
    val cleanOwner = owner.trim().lowercase()
    val cleanRepo = repo.trim().lowercase()
    return if (cleanOwner.isBlank() && '/' in cleanRepo) {
        cleanRepo.substringBefore('/').trim() to cleanRepo.substringAfter('/').trim()
    } else {
        cleanOwner to cleanRepo.substringAfterLast('/')
    }
}

private fun repoKey(owner: String, repo: String): RepoKey {
    val (o, r) = normalizeRepoPair(owner, repo)
    return RepoKey(o, r)
}

private fun repoMilestonesKey(owner: String, repo: String, state: String): RepoMilestonesKey {
    val (o, r) = normalizeRepoPair(owner, repo)
    return RepoMilestonesKey(o, r, state.trim().lowercase())
}

private fun repoPathKey(owner: String, repo: String, path: String, ref: String?): RepoPathKey {
    val (o, r) = normalizeRepoPair(owner, repo)
    return RepoPathKey(o, r, path.trimStart('/'), ref?.trim()?.takeIf { it.isNotEmpty() })
}

private fun repoCommitKey(owner: String, repo: String, sha: String): RepoCommitKey {
    val (o, r) = normalizeRepoPair(owner, repo)
    return RepoCommitKey(o, r, sha.trim())
}

private fun repoCompareKey(owner: String, repo: String, base: String, head: String): RepoCompareKey {
    val (o, r) = normalizeRepoPair(owner, repo)
    return RepoCompareKey(o, r, base.trim(), head.trim())
}

private fun issuePullKey(owner: String, repo: String, number: Int): IssuePullKey {
    val (o, r) = normalizeRepoPair(owner, repo)
    return IssuePullKey(o, r, number)
}

/**
 * Runs [block] and wraps non-cancellation failures in [Result.failure], while rethrowing
 * [CancellationException] (and verifying the caller coroutine is still active) so leaving
 * composition never surfaces as a network error.
 */
private suspend inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        coroutineContext.ensureActive()
        Result.failure(e)
    }

/**
 * Executes a write operation and its cache updates inside [NonCancellable] so navigating away
 * mid-flight does not abort the mutation.
 *
 * If the caller was cancelled meanwhile, a success is dropped (callers navigate on success, which must
 * not happen after the screen is gone), but a failure is still returned so the caller can report it.
 */
private suspend inline fun <T> runMutationCancellable(
    crossinline block: suspend () -> T
): Result<T> {
    val result = withContext(NonCancellable) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
    if (result.isSuccess) coroutineContext.ensureActive()
    return result
}

class GitHubRepository(
    private val sessionManager: SessionManager,
    private val networkConfig: GitHubNetworkConfig = GitHubNetworkConfig(sessionManager),
    private val httpClient: HttpClient = networkConfig.createHttpClient(),
    private val restService: GitHubRestService = GitHubRestService(httpClient, networkConfig.baseUrl),
    private val graphQlService: GitHubGraphQlService = GitHubGraphQlService(httpClient, networkConfig.baseUrl)
) {
    val rateLimit: StateFlow<RateLimitInfo> = networkConfig.rateLimit

    /** Releases the underlying HttpClient. Only for short-lived instances that own their client. */
    fun close() {
        httpClient.close()
    }

    private val issueOverridesMutex = Mutex()
    @Volatile
    private var recentIssueStateOverrides: Map<NormalizedIssueKey, RecentIssueStateEntry> = emptyMap()
    @Volatile
    private var presetIssueHints: Map<Pair<IssueFilterPreset, String>, Map<NormalizedIssueKey, IssueOrPullSummary>> = emptyMap()
    private val _issueStateRevision = MutableStateFlow(0L)
    val issueStateRevision: StateFlow<Long> = _issueStateRevision.asStateFlow()

    private val profileCache = LruMemoryCache<String, ViewerProfile>(ttl = 300.seconds, maxEntries = 2)
    private val notificationsCache = LruMemoryCache<NotificationsKey, List<NotificationThread>>(ttl = 30.seconds, maxEntries = 12)
    private val myIssuesCache = LruMemoryCache<MyIssuesKey, List<IssueOrPullSummary>>(ttl = 60.seconds, maxEntries = 24)
    private val issueDetailCache = LruMemoryCache<IssuePullKey, IssueOrPullDetail>(ttl = 45.seconds, maxEntries = 64)
    private val pullFilesCache = LruMemoryCache<IssuePullKey, List<PullFileChange>>(ttl = 60.seconds, maxEntries = 48)
    private val viewerReposCache = LruMemoryCache<String, List<RepoSummary>>(ttl = 120.seconds, maxEntries = 4)
    private val searchReposCache = LruMemoryCache<String, List<RepoSummary>>(ttl = 90.seconds, maxEntries = 16)
    private val repoDetailCache = LruMemoryCache<RepoKey, RepoDetail>(ttl = 120.seconds, maxEntries = 32)
    private val repoIssuesCache = LruMemoryCache<RepoIssuesKey, List<IssueOrPullSummary>>(ttl = 60.seconds, maxEntries = 48)
    private val dirCache = LruMemoryCache<RepoPathKey, List<TreeEntry>>(ttl = 180.seconds, maxEntries = 96)
    private val fileCache = LruMemoryCache<RepoPathKey, FileContent>(ttl = 180.seconds, maxEntries = 64)
    private val readmeCache = LruMemoryCache<RepoPathKey, String?>(ttl = 180.seconds, maxEntries = 32)
    private val contributionCache = LruMemoryCache<String, ContributionCalendar>(ttl = 300.seconds, maxEntries = 12)
    private val receivedEventsCache = LruMemoryCache<String, List<ActivityEvent>>(ttl = 60.seconds, maxEntries = 8)
    private val userEventsCache = LruMemoryCache<String, List<ActivityEvent>>(ttl = 60.seconds, maxEntries = 12)
    private val userProfileCache = LruMemoryCache<String, UserProfile>(ttl = 180.seconds, maxEntries = 24)
    private val userReposCache = LruMemoryCache<String, List<RepoSummary>>(ttl = 120.seconds, maxEntries = 24)
    private val userStarredCache = LruMemoryCache<String, List<RepoSummary>>(ttl = 120.seconds, maxEntries = 24)
    private val userOrgsCache = LruMemoryCache<String, List<OrganizationSummary>>(ttl = 180.seconds, maxEntries = 24)
    private val userFollowersCache = LruMemoryCache<String, List<UserSummary>>(ttl = 120.seconds, maxEntries = 24)
    private val userFollowingCache = LruMemoryCache<String, List<UserSummary>>(ttl = 120.seconds, maxEntries = 24)
    private val searchIssuesCache = LruMemoryCache<String, List<IssueOrPullSummary>>(ttl = 90.seconds, maxEntries = 16)
    private val searchCodeCache = LruMemoryCache<String, List<CodeSearchHit>>(ttl = 90.seconds, maxEntries = 16)
    private val searchUsersCache = LruMemoryCache<String, List<UserSummary>>(ttl = 90.seconds, maxEntries = 16)
    private val trendingReposCache = LruMemoryCache<String, List<RepoSummary>>(ttl = 180.seconds, maxEntries = 12)
    private val repoSubscriptionCache = LruMemoryCache<RepoKey, Boolean>(ttl = 120.seconds, maxEntries = 32)
    private val repoLabelsCache = LruMemoryCache<RepoKey, List<LabelTag>>(ttl = 180.seconds, maxEntries = 32)
    private val repoMilestonesCache = LruMemoryCache<RepoMilestonesKey, List<MilestoneSummary>>(ttl = 180.seconds, maxEntries = 32)
    private val repoAssigneesCache = LruMemoryCache<RepoKey, List<UserSummary>>(ttl = 180.seconds, maxEntries = 32)
    private val commitsCache = LruMemoryCache<RepoPathKey, List<CommitSummary>>(ttl = 60.seconds, maxEntries = 48)
    private val commitDetailCache = LruMemoryCache<RepoCommitKey, CommitDetail>(ttl = 180.seconds, maxEntries = 48)
    private val tagsCache = LruMemoryCache<RepoKey, List<TagRef>>(ttl = 180.seconds, maxEntries = 32)
    private val compareCache = LruMemoryCache<RepoCompareKey, BranchCompareResult>(ttl = 60.seconds, maxEntries = 32)
    private val workflowsCache = LruMemoryCache<RepoKey, List<WorkflowSummary>>(ttl = 180.seconds, maxEntries = 32)
    private val workflowRunsCache = LruMemoryCache<WorkflowRunsKey, List<WorkflowRunSummary>>(ttl = 30.seconds, maxEntries = 48)
    private val workflowJobsCache = LruMemoryCache<WorkflowRunIdKey, List<WorkflowJobSummary>>(ttl = 30.seconds, maxEntries = 48)
    private val checkRunsCache = LruMemoryCache<RepoPathKey, List<CheckRunSummary>>(ttl = 30.seconds, maxEntries = 48)
    private val releasesCache = LruMemoryCache<RepoKey, List<ReleaseSummary>>(ttl = 120.seconds, maxEntries = 32)
    private val discussionsCache = LruMemoryCache<DiscussionsKey, DiscussionListData>(ttl = 60.seconds, maxEntries = 32)
    private val discussionDetailCache = LruMemoryCache<IssuePullKey, DiscussionDetail>(ttl = 45.seconds, maxEntries = 32)
    private val securityOverviewCache = LruMemoryCache<RepoKey, RepoSecurityOverview>(ttl = 90.seconds, maxEntries = 32)
    private val insightsCache = LruMemoryCache<RepoKey, RepoInsightsData>(ttl = 120.seconds, maxEntries = 32)
    private val gistsCache = LruMemoryCache<String, List<GistSummary>>(ttl = 60.seconds, maxEntries = 16)
    private val gistDetailCache = LruMemoryCache<String, GistDetail>(ttl = 60.seconds, maxEntries = 32)

    suspend fun clearCache() {
        profileCache.clear()
        notificationsCache.clear()
        myIssuesCache.clear()
        issueDetailCache.clear()
        pullFilesCache.clear()
        viewerReposCache.clear()
        searchReposCache.clear()
        repoDetailCache.clear()
        repoIssuesCache.clear()
        dirCache.clear()
        fileCache.clear()
        readmeCache.clear()
        contributionCache.clear()
        receivedEventsCache.clear()
        userEventsCache.clear()
        userProfileCache.clear()
        userReposCache.clear()
        userStarredCache.clear()
        userOrgsCache.clear()
        userFollowersCache.clear()
        userFollowingCache.clear()
        searchIssuesCache.clear()
        searchCodeCache.clear()
        searchUsersCache.clear()
        trendingReposCache.clear()
        repoSubscriptionCache.clear()
        repoLabelsCache.clear()
        repoMilestonesCache.clear()
        repoAssigneesCache.clear()
        commitsCache.clear()
        commitDetailCache.clear()
        tagsCache.clear()
        compareCache.clear()
        workflowsCache.clear()
        workflowRunsCache.clear()
        workflowJobsCache.clear()
        checkRunsCache.clear()
        releasesCache.clear()
        discussionsCache.clear()
        discussionDetailCache.clear()
        securityOverviewCache.clear()
        insightsCache.clear()
        gistsCache.clear()
        gistDetailCache.clear()
        recentIssueStateOverrides = emptyMap()
        presetIssueHints = emptyMap()
    }

    // ---- Synchronous lock-free cache peeks for instant UI rendering ----

    fun peekNotifications(all: Boolean = false, participating: Boolean = false, page: Int = 1): List<NotificationThread>? =
        notificationsCache.peek(NotificationsKey(all, participating, page))

    fun peekMyIssuesAndPulls(
        preset: IssueFilterPreset,
        viewerLogin: String,
        stateFilter: IssueStateFilter = IssueStateFilter.OPEN
    ): List<IssueOrPullSummary>? = myIssuesCache.peek(MyIssuesKey(preset, viewerLogin, stateFilter))

    fun peekIssueOrPullDetail(owner: String, repo: String, number: Int): IssueOrPullDetail? =
        issueDetailCache.peek(issuePullKey(owner, repo, number))

    fun peekPullFiles(owner: String, repo: String, number: Int): List<PullFileChange>? =
        pullFilesCache.peek(issuePullKey(owner, repo, number))

    fun peekViewerRepositories(): List<RepoSummary>? = viewerReposCache.peek("MY")

    fun peekStarredRepositories(): List<RepoSummary>? = viewerReposCache.peek("STARRED")

    fun peekSearchRepositories(query: String): List<RepoSummary>? = searchReposCache.peek(query.trim())

    fun peekRepoDetail(owner: String, repo: String): RepoDetail? = repoDetailCache.peek(repoKey(owner, repo))

    fun peekRepoIssuesOrPulls(
        owner: String,
        repo: String,
        pullRequests: Boolean,
        stateFilter: IssueStateFilter = IssueStateFilter.OPEN
    ): List<IssueOrPullSummary>? = repoIssuesCache.peek(RepoIssuesKey(owner, repo, pullRequests, stateFilter))

    fun peekDirectoryContents(owner: String, repo: String, path: String = "", ref: String? = null): List<TreeEntry>? =
        dirCache.peek(repoPathKey(owner, repo, path, ref))

    fun peekFileContent(owner: String, repo: String, path: String, ref: String? = null): FileContent? =
        fileCache.peek(repoPathKey(owner, repo, path, ref))

    fun peekReadme(owner: String, repo: String, ref: String? = null): String? =
        readmeCache.peek(repoPathKey(owner, repo, "", ref))

    fun peekContributionCalendar(login: String): ContributionCalendar? =
        contributionCache.peek(login)

    fun peekReceivedEvents(login: String): List<ActivityEvent>? =
        receivedEventsCache.peek(login)

    fun peekUserEvents(login: String): List<ActivityEvent>? =
        userEventsCache.peek(login)

    fun peekUserProfile(login: String): UserProfile? =
        userProfileCache.peek(login)

    fun peekUserRepositories(login: String): List<RepoSummary>? =
        userReposCache.peek(login)

    // ---- Cached / Parallel Network Operations ----

    suspend fun getViewerProfile(forceRefresh: Boolean = false): Result<ViewerProfile> {
        if (!forceRefresh) {
            profileCache.peekIfFresh("viewer")?.let { return Result.success(it) }
        }
        return runCatchingCancellable { graphQlService.fetchViewerProfile() }
            .onSuccess { profileCache.put("viewer", it) }
    }

    suspend fun getNotifications(
        all: Boolean = false,
        participating: Boolean = false,
        page: Int = 1,
        forceRefresh: Boolean = true
    ): Result<List<NotificationThread>> {
        val key = NotificationsKey(all, participating, page)
        if (!forceRefresh) {
            notificationsCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchNotifications(all = all, participating = participating, page = page)
        }.onSuccess {
            notificationsCache.put(key, it)
        }
    }

    suspend fun markNotificationRead(threadId: String): Result<Unit> = runMutationCancellable {
        restService.markNotificationRead(threadId)
        notificationsCache.clear()
    }

    suspend fun markNotificationDone(threadId: String): Result<Unit> = runMutationCancellable {
        restService.markNotificationDone(threadId)
        notificationsCache.clear()
    }

    suspend fun markAllNotificationsRead(): Result<Unit> = runMutationCancellable {
        restService.markAllNotificationsRead()
        notificationsCache.clear()
    }

    private fun activeIssueOverrides(): Map<Pair<String, Int>, IssueOrPullState> {
        val result = LinkedHashMap<Pair<String, Int>, IssueOrPullState>()
        for ((key, entry) in recentIssueStateOverrides) {
            if (entry.isActive()) {
                result["${key.ownerLower}/${key.repoLower}" to key.number] = entry.targetState
            }
        }
        return result
    }

    private fun reconcileMyIssues(
        preset: IssueFilterPreset,
        viewerLogin: String,
        stateFilter: IssueStateFilter,
        items: List<IssueOrPullSummary>
    ): List<IssueOrPullSummary> {
        val overrides = activeIssueOverrides()
        val hints = presetIssueHints[preset to viewerLogin]?.values?.toList().orEmpty()
        return reconcileIssueListWithStateOverrides(
            items = items,
            stateFilter = stateFilter,
            overrides = overrides,
            hintCandidates = hints
        )
    }

    private fun reconcileRepoIssues(
        owner: String,
        repo: String,
        pullRequests: Boolean,
        stateFilter: IssueStateFilter,
        items: List<IssueOrPullSummary>
    ): List<IssueOrPullSummary> {
        val overrides = activeIssueOverrides()
        val targetKind = if (pullRequests) IssueOrPullKind.PULL_REQUEST else IssueOrPullKind.ISSUE
        val hints = recentIssueStateOverrides.values.mapNotNull { entry ->
            entry.summaryHint?.takeIf {
                entry.isActive() &&
                    it.repoOwner.equals(owner, ignoreCase = true) &&
                    it.repoName.equals(repo, ignoreCase = true) &&
                    it.kind == targetKind
            }
        }
        return reconcileIssueListWithStateOverrides(
            items = items,
            stateFilter = stateFilter,
            overrides = overrides,
            hintCandidates = hints
        )
    }

    private suspend fun recordIssueStateTransition(
        owner: String,
        repo: String,
        number: Int,
        newState: IssueOrPullState
    ) {
        val normalizedKey = NormalizedIssueKey.of(owner, repo, number)
        val mySnapshot = myIssuesCache.entriesSnapshot()
        val repoSnapshot = repoIssuesCache.entriesSnapshot()

        val affectedBuckets = LinkedHashMap<Pair<IssueFilterPreset, String>, IssueOrPullSummary>()
        var bestSummary: IssueOrPullSummary? = null

        for ((cacheKey, list) in mySnapshot) {
            val found = list.firstOrNull { NormalizedIssueKey.of(it) == normalizedKey }
            if (found != null) {
                affectedBuckets[cacheKey.preset to cacheKey.viewerLogin] = found
                if (bestSummary == null) bestSummary = found
            }
        }
        if (bestSummary == null) {
            for ((cacheKey, list) in repoSnapshot) {
                if (cacheKey.owner.equals(owner, ignoreCase = true) && cacheKey.repo.equals(repo, ignoreCase = true)) {
                    val found = list.firstOrNull { NormalizedIssueKey.of(it) == normalizedKey }
                    if (found != null) {
                        bestSummary = found
                        break
                    }
                }
            }
        }
        if (bestSummary == null) {
            bestSummary = issueDetailCache.peek(issuePullKey(owner, repo, number))?.toSummary()
        }

        issueOverridesMutex.withLock {
            val nextOverrides = LinkedHashMap<NormalizedIssueKey, RecentIssueStateEntry>()
            for ((k, v) in recentIssueStateOverrides) {
                if (v.isActive() && k != normalizedKey) nextOverrides[k] = v
            }
            nextOverrides[normalizedKey] = RecentIssueStateEntry(targetState = newState, summaryHint = bestSummary)
            recentIssueStateOverrides = nextOverrides

            if (affectedBuckets.isNotEmpty() || presetIssueHints.isNotEmpty()) {
                val nextHints = LinkedHashMap<Pair<IssueFilterPreset, String>, Map<NormalizedIssueKey, IssueOrPullSummary>>(presetIssueHints)
                for ((bucket, summary) in affectedBuckets) {
                    val existing = LinkedHashMap(nextHints[bucket].orEmpty())
                    existing[normalizedKey] = summary.copy(state = newState)
                    nextHints[bucket] = existing
                }
                for ((bucket, existingMap) in presetIssueHints) {
                    if (normalizedKey in existingMap && bucket !in affectedBuckets) {
                        val updated = LinkedHashMap(existingMap)
                        updated[normalizedKey] = updated.getValue(normalizedKey).copy(state = newState)
                        nextHints[bucket] = updated
                    }
                }
                presetIssueHints = nextHints
            }
        }

        myIssuesCache.updateEntries { k, list ->
            val bucket = k.preset to k.viewerLogin
            val hint = affectedBuckets[bucket] ?: presetIssueHints[bucket]?.get(normalizedKey)
            if (hint != null || list.any { NormalizedIssueKey.of(it) == normalizedKey }) {
                patchIssueListForSingleMutation(list, k.stateFilter, normalizedKey, newState, hint ?: bestSummary)
            } else {
                list
            }
        }
        repoIssuesCache.updateEntries { k, list ->
            if (k.owner.equals(owner, ignoreCase = true) && k.repo.equals(repo, ignoreCase = true)) {
                val kindFilteredHint = bestSummary?.takeIf { (it.kind == IssueOrPullKind.PULL_REQUEST) == k.pullRequests }
                patchIssueListForSingleMutation(list, k.stateFilter, normalizedKey, newState, kindFilteredHint)
            } else {
                list
            }
        }
        _issueStateRevision.value += 1L
        repoDetailCache.invalidate(repoKey(owner, repo))
    }

    suspend fun getMyIssuesAndPulls(
        preset: IssueFilterPreset,
        viewerLogin: String,
        stateFilter: IssueStateFilter = IssueStateFilter.OPEN,
        forceRefresh: Boolean = false
    ): Result<List<IssueOrPullSummary>> {
        val key = MyIssuesKey(preset, viewerLogin, stateFilter)
        if (!forceRefresh) {
            myIssuesCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            val fetched = graphQlService.fetchIssuesAndPulls(preset, viewerLogin, stateFilter)
            reconcileMyIssues(preset, viewerLogin, stateFilter, fetched)
        }.onSuccess {
            myIssuesCache.put(key, it)
        }
    }

    suspend fun getIssueOrPullDetail(
        owner: String,
        repo: String,
        number: Int,
        forceRefresh: Boolean = false
    ): Result<IssueOrPullDetail> {
        val key = issuePullKey(owner, repo, number)
        if (!forceRefresh) {
            issueDetailCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            graphQlService.fetchIssueOrPullDetail(owner, repo, number)
        }.onSuccess {
            issueDetailCache.put(key, it)
        }
    }

    suspend fun getPullFiles(
        owner: String,
        repo: String,
        number: Int,
        forceRefresh: Boolean = false
    ): Result<List<PullFileChange>> {
        val key = issuePullKey(owner, repo, number)
        if (!forceRefresh) {
            pullFilesCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchPullFiles(owner, repo, number)
        }.onSuccess {
            pullFilesCache.put(key, it)
        }
    }

    suspend fun prefetchIssueOrPull(owner: String, repo: String, number: Int) {
        val detail = getIssueOrPullDetail(owner, repo, number, forceRefresh = false).getOrNull() ?: return
        if (detail.kind == IssueOrPullKind.PULL_REQUEST && peekPullFiles(owner, repo, number) == null) {
            getPullFiles(owner, repo, number, forceRefresh = false)
        }
    }

    suspend fun postComment(
        owner: String,
        repo: String,
        number: Int,
        body: String
    ): Result<Unit> = runMutationCancellable {
        restService.postComment(owner, repo, number, body)
        issueDetailCache.invalidate(issuePullKey(owner, repo, number))
    }

    suspend fun setIssueOrPullState(
        owner: String,
        repo: String,
        number: Int,
        open: Boolean
    ): Result<Unit> = runMutationCancellable {
        restService.setIssueOrPullState(owner, repo, number, open)
        val nextState = if (open) IssueOrPullState.OPEN else IssueOrPullState.CLOSED
        recordIssueStateTransition(owner, repo, number, nextState)
        issueDetailCache.invalidate(issuePullKey(owner, repo, number))
    }

    suspend fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String,
        labels: List<String> = emptyList()
    ): Result<Int> = runMutationCancellable {
        restService.createIssue(owner, repo, title, body, labels).also {
            myIssuesCache.clear()
            repoIssuesCache.invalidateWhere { it.owner == owner && it.repo == repo && !it.pullRequests }
            repoDetailCache.invalidate(repoKey(owner, repo))
        }
    }

    suspend fun submitPullReview(
        owner: String,
        repo: String,
        number: Int,
        event: ReviewEvent,
        body: String
    ): Result<Unit> = runMutationCancellable {
        restService.submitPullReview(owner, repo, number, event, body)
        issueDetailCache.invalidate(issuePullKey(owner, repo, number))
        myIssuesCache.clear()
    }

    suspend fun mergePullRequest(
        owner: String,
        repo: String,
        number: Int,
        method: MergeMethod,
        commitTitle: String? = null
    ): Result<Unit> = runMutationCancellable {
        restService.mergePullRequest(owner, repo, number, method, commitTitle)
        recordIssueStateTransition(owner, repo, number, IssueOrPullState.MERGED)
        issueDetailCache.invalidate(issuePullKey(owner, repo, number))
    }

    suspend fun getViewerRepositories(forceRefresh: Boolean = false): Result<List<RepoSummary>> {
        if (!forceRefresh) {
            viewerReposCache.peekIfFresh("MY")?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            try {
                graphQlService.fetchViewerRepositories()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                restService.fetchViewerRepositoriesRest()
            }
        }.onSuccess {
            viewerReposCache.put("MY", it)
        }
    }

    suspend fun getStarredRepositories(forceRefresh: Boolean = false): Result<List<RepoSummary>> {
        if (!forceRefresh) {
            viewerReposCache.peekIfFresh("STARRED")?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            try {
                graphQlService.fetchStarredRepositories()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                restService.fetchStarredRepositoriesRest()
            }
        }.onSuccess {
            viewerReposCache.put("STARRED", it)
        }
    }

    suspend fun searchRepositories(query: String, forceRefresh: Boolean = false): Result<List<RepoSummary>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        if (!forceRefresh) {
            searchReposCache.peekIfFresh(trimmed)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            try {
                graphQlService.searchRepositories(trimmed)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                restService.searchRepositoriesRest(trimmed)
            }
        }.onSuccess {
            searchReposCache.put(trimmed, it)
        }
    }

    suspend fun getRepoDetail(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<RepoDetail> {
        val key = repoKey(owner, repo)
        if (!forceRefresh) {
            repoDetailCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            coroutineScope {
                val detailDeferred = async { graphQlService.fetchRepoDetail(owner, repo) }
                val branchesDeferred = async { runCatchingCancellable { restService.fetchBranches(owner, repo) }.getOrDefault(emptyList()) }
                detailDeferred.await().copy(branches = branchesDeferred.await())
            }
        }.onSuccess {
            repoDetailCache.put(key, it)
        }
    }

    suspend fun getRepoIssuesOrPulls(
        owner: String,
        repo: String,
        pullRequests: Boolean,
        stateFilter: IssueStateFilter = IssueStateFilter.OPEN,
        forceRefresh: Boolean = false
    ): Result<List<IssueOrPullSummary>> {
        val key = RepoIssuesKey(owner, repo, pullRequests, stateFilter)
        if (!forceRefresh) {
            repoIssuesCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            val fetched = graphQlService.fetchRepoIssuesAndPulls(owner, repo, pullRequests, stateFilter)
            reconcileRepoIssues(owner, repo, pullRequests, stateFilter, fetched)
        }.onSuccess {
            repoIssuesCache.put(key, it)
        }
    }

    suspend fun getBranches(owner: String, repo: String): Result<List<BranchRef>> = runCatchingCancellable {
        restService.fetchBranches(owner, repo)
    }

    suspend fun getDirectoryContents(
        owner: String,
        repo: String,
        path: String = "",
        ref: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<TreeEntry>> {
        val key = repoPathKey(owner, repo, path, ref)
        if (!forceRefresh) {
            dirCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchDirectoryContents(owner, repo, path, ref)
        }.onSuccess {
            dirCache.put(key, it)
        }
    }

    suspend fun getFileContent(
        owner: String,
        repo: String,
        path: String,
        ref: String? = null,
        forceRefresh: Boolean = false
    ): Result<FileContent> {
        val key = repoPathKey(owner, repo, path, ref)
        if (!forceRefresh) {
            fileCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchFileContent(owner, repo, path, ref)
        }.onSuccess {
            fileCache.put(key, it)
        }
    }

    suspend fun getReadme(
        owner: String,
        repo: String,
        ref: String? = null,
        forceRefresh: Boolean = false
    ): Result<String?> {
        val key = repoPathKey(owner, repo, "", ref)
        if (!forceRefresh) {
            readmeCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchReadme(owner, repo, ref)
        }.onSuccess {
            readmeCache.put(key, it)
        }
    }

    suspend fun upsertRepoFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String? = null,
        existingSha: String? = null
    ): Result<String> = runMutationCancellable {
        restService.upsertRepoFile(owner, repo, path, content, message, branch, existingSha).also {
            invalidateRepoTreeCaches(owner, repo)
        }
    }

    suspend fun deleteRepoFile(
        owner: String,
        repo: String,
        path: String,
        sha: String,
        message: String,
        branch: String? = null
    ): Result<String> = runMutationCancellable {
        restService.deleteRepoFile(owner, repo, path, sha, message, branch).also {
            invalidateRepoTreeCaches(owner, repo)
        }
    }

    private suspend fun invalidateRepoTreeCaches(owner: String, repo: String) {
        val key = repoKey(owner, repo)
        dirCache.invalidateWhere { it.owner == key.owner && it.repo == key.repo }
        fileCache.invalidateWhere { it.owner == key.owner && it.repo == key.repo }
        readmeCache.invalidateWhere { it.owner == key.owner && it.repo == key.repo }
        commitsCache.invalidateWhere { it.owner == key.owner && it.repo == key.repo }
        compareCache.invalidateWhere { it.owner == key.owner && it.repo == key.repo }
        repoDetailCache.invalidate(key)
    }

    suspend fun setStarred(owner: String, repo: String, starred: Boolean): Result<Unit> = runMutationCancellable {
        restService.setStarred(owner, repo, starred)
        repoDetailCache.invalidate(RepoKey(owner, repo))
        viewerReposCache.invalidate("STARRED")
        viewerReposCache.invalidate("MY")
        searchReposCache.clear()
        trendingReposCache.clear()
    }

    suspend fun getContributionCalendar(
        login: String,
        forceRefresh: Boolean = false
    ): Result<ContributionCalendar> {
        if (!forceRefresh) {
            contributionCache.peekIfFresh(login)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            graphQlService.fetchContributionCalendar(login)
        }.onSuccess {
            contributionCache.put(login, it)
        }
    }

    suspend fun getReceivedEvents(
        login: String,
        forceRefresh: Boolean = false
    ): Result<List<ActivityEvent>> {
        if (!forceRefresh) {
            receivedEventsCache.peekIfFresh(login)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchReceivedEvents(login)
        }.onSuccess {
            receivedEventsCache.put(login, it)
        }
    }

    suspend fun getUserEvents(
        login: String,
        forceRefresh: Boolean = false
    ): Result<List<ActivityEvent>> {
        if (!forceRefresh) {
            userEventsCache.peekIfFresh(login)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchUserEvents(login)
        }.onSuccess {
            userEventsCache.put(login, it)
        }
    }

    suspend fun getUserProfile(
        login: String,
        forceRefresh: Boolean = false
    ): Result<UserProfile> {
        if (!forceRefresh) {
            userProfileCache.peekIfFresh(login)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchUserProfileRest(login)
        }.onSuccess {
            userProfileCache.put(login, it)
        }
    }

    suspend fun setFollowingUser(login: String, follow: Boolean): Result<Unit> = runMutationCancellable {
        restService.setFollowingUser(login, follow)
        userProfileCache.invalidate(login)
        profileCache.clear()
    }

    suspend fun getUserRepositories(
        login: String,
        isOrganization: Boolean = false,
        forceRefresh: Boolean = false
    ): Result<List<RepoSummary>> {
        if (!forceRefresh) {
            userReposCache.peekIfFresh(login)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchUserRepositories(login, isOrganization = isOrganization)
        }.onSuccess {
            userReposCache.put(login, it)
        }
    }

    suspend fun getUserStarredRepositories(
        login: String,
        forceRefresh: Boolean = false
    ): Result<List<RepoSummary>> {
        if (!forceRefresh) {
            userStarredCache.peekIfFresh(login)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchUserStarredRepositories(login)
        }.onSuccess {
            userStarredCache.put(login, it)
        }
    }

    suspend fun getUserOrganizations(
        login: String,
        forceRefresh: Boolean = false
    ): Result<List<OrganizationSummary>> {
        if (!forceRefresh) {
            userOrgsCache.peekIfFresh(login)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchUserOrganizations(login)
        }.onSuccess {
            userOrgsCache.put(login, it)
        }
    }

    suspend fun getUserFollowers(
        login: String,
        forceRefresh: Boolean = false
    ): Result<List<UserSummary>> {
        if (!forceRefresh) {
            userFollowersCache.peekIfFresh(login)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchUserFollowers(login)
        }.onSuccess {
            userFollowersCache.put(login, it)
        }
    }

    suspend fun getUserFollowing(
        login: String,
        forceRefresh: Boolean = false
    ): Result<List<UserSummary>> {
        if (!forceRefresh) {
            userFollowingCache.peekIfFresh(login)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchUserFollowing(login)
        }.onSuccess {
            userFollowingCache.put(login, it)
        }
    }

    suspend fun searchIssuesAndPullsGlobal(
        query: String,
        forceRefresh: Boolean = false
    ): Result<List<IssueOrPullSummary>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        if (!forceRefresh) {
            searchIssuesCache.peekIfFresh(trimmed)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.searchIssuesAndPullsRest(trimmed)
        }.onSuccess {
            searchIssuesCache.put(trimmed, it)
        }
    }

    suspend fun searchCode(
        query: String,
        forceRefresh: Boolean = false
    ): Result<List<CodeSearchHit>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        if (!forceRefresh) {
            searchCodeCache.peekIfFresh(trimmed)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.searchCodeRest(trimmed)
        }.onSuccess {
            searchCodeCache.put(trimmed, it)
        }
    }

    suspend fun searchUsers(
        query: String,
        forceRefresh: Boolean = false
    ): Result<List<UserSummary>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        if (!forceRefresh) {
            searchUsersCache.peekIfFresh(trimmed)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.searchUsersRest(trimmed)
        }.onSuccess {
            searchUsersCache.put(trimmed, it)
        }
    }

    fun peekSearchIssuesAndPullsGlobal(query: String): List<IssueOrPullSummary>? =
        searchIssuesCache.peek(query.trim())

    fun peekSearchCode(query: String): List<CodeSearchHit>? =
        searchCodeCache.peek(query.trim())

    fun peekSearchUsers(query: String): List<UserSummary>? =
        searchUsersCache.peek(query.trim())

    fun peekTrendingRepositories(language: String? = null): List<RepoSummary>? =
        trendingReposCache.peek(language?.trim().orEmpty())

    suspend fun getTrendingRepositories(
        language: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<RepoSummary>> {
        val key = language?.trim().orEmpty()
        if (!forceRefresh) {
            trendingReposCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchTrendingRepositories(key.takeIf(String::isNotEmpty))
        }.onSuccess {
            trendingReposCache.put(key, it)
        }
    }

    suspend fun createRepository(
        name: String,
        description: String = "",
        isPrivate: Boolean = false,
        autoInit: Boolean = true
    ): Result<RepoSummary> = runMutationCancellable {
        restService.createRepositoryRest(name, description, isPrivate, autoInit).also {
            viewerReposCache.invalidate("MY")
            userReposCache.clear()
        }
    }

    suspend fun forkRepository(owner: String, repo: String): Result<RepoSummary> = runMutationCancellable {
        restService.forkRepositoryRest(owner, repo).also { forked ->
            viewerReposCache.invalidate("MY")
            userReposCache.clear()
            repoDetailCache.invalidate(repoKey(owner, repo))
            invalidateRepoTreeCaches(forked.owner, forked.name)
        }
    }

    fun peekRepoSubscription(owner: String, repo: String): Boolean? =
        repoSubscriptionCache.peek(RepoKey(owner, repo))

    suspend fun getRepoSubscription(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<Boolean> {
        val key = RepoKey(owner, repo)
        if (!forceRefresh) {
            repoSubscriptionCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchRepoSubscription(owner, repo)
        }.onSuccess {
            repoSubscriptionCache.put(key, it)
        }
    }

    suspend fun setRepoSubscription(
        owner: String,
        repo: String,
        subscribed: Boolean
    ): Result<Unit> = runMutationCancellable {
        restService.setRepoSubscription(owner, repo, subscribed)
        repoSubscriptionCache.put(RepoKey(owner, repo), subscribed)
        repoDetailCache.invalidate(RepoKey(owner, repo))
    }

    suspend fun getRepoStargazers(owner: String, repo: String): Result<List<UserSummary>> = runCatchingCancellable {
        val cleanOwner = owner.trim().ifBlank { repo.substringBefore('/', "").trim() }
        val cleanRepo = repo.substringAfterLast('/').trim()
        try {
            restService.fetchRepoStargazers(cleanOwner, cleanRepo)
        } catch (e: CancellationException) {
            throw e
        } catch (restErr: Throwable) {
            try {
                graphQlService.fetchRepoStargazers(cleanOwner, cleanRepo)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                throw restErr
            }
        }
    }

    suspend fun getRepoForks(
        owner: String,
        repo: String,
        sort: String = "stargazers"
    ): Result<List<RepoSummary>> = runCatchingCancellable {
        val cleanOwner = owner.trim().ifBlank { repo.substringBefore('/', "").trim() }
        val cleanRepo = repo.substringAfterLast('/').trim()
        restService.fetchRepoForks(cleanOwner, cleanRepo, sort = sort)
    }

    suspend fun getRepoContributors(owner: String, repo: String): Result<List<UserSummary>> = runCatchingCancellable {
        val cleanOwner = owner.trim().ifBlank { repo.substringBefore('/', "").trim() }
        val cleanRepo = repo.substringAfterLast('/').trim()
        restService.fetchRepoContributors(cleanOwner, cleanRepo)
    }

    fun peekRepoLabels(owner: String, repo: String): List<LabelTag>? =
        repoLabelsCache.peek(repoKey(owner, repo))

    suspend fun getRepoLabels(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<List<LabelTag>> {
        val key = repoKey(owner, repo)
        if (!forceRefresh) {
            repoLabelsCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchRepoLabels(owner, repo)
        }.onSuccess {
            repoLabelsCache.put(key, it)
        }
    }

    suspend fun createRepoLabel(
        owner: String,
        repo: String,
        name: String,
        colorHex: String,
        description: String = ""
    ): Result<LabelTag> = runMutationCancellable {
        restService.createRepoLabel(owner, repo, name, colorHex, description).also {
            repoLabelsCache.invalidate(repoKey(owner, repo))
        }
    }

    suspend fun updateRepoLabel(
        owner: String,
        repo: String,
        currentName: String,
        newName: String,
        colorHex: String,
        description: String = ""
    ): Result<LabelTag> = runMutationCancellable {
        restService.updateRepoLabel(owner, repo, currentName, newName, colorHex, description).also {
            val key = repoKey(owner, repo)
            repoLabelsCache.invalidate(key)
            issueDetailCache.invalidateWhere { it.owner.equals(key.owner, ignoreCase = true) && it.repo.equals(key.repo, ignoreCase = true) }
            repoIssuesCache.invalidateWhere { it.owner.equals(key.owner, ignoreCase = true) && it.repo.equals(key.repo, ignoreCase = true) }
        }
    }

    suspend fun deleteRepoLabel(
        owner: String,
        repo: String,
        name: String
    ): Result<Unit> = runMutationCancellable {
        restService.deleteRepoLabel(owner, repo, name).also {
            val key = repoKey(owner, repo)
            repoLabelsCache.invalidate(key)
            issueDetailCache.invalidateWhere { it.owner.equals(key.owner, ignoreCase = true) && it.repo.equals(key.repo, ignoreCase = true) }
            repoIssuesCache.invalidateWhere { it.owner.equals(key.owner, ignoreCase = true) && it.repo.equals(key.repo, ignoreCase = true) }
        }
    }

    fun peekRepoMilestones(owner: String, repo: String, state: String = "open"): List<MilestoneSummary>? =
        repoMilestonesCache.peek(repoMilestonesKey(owner, repo, state))

    suspend fun getRepoMilestones(
        owner: String,
        repo: String,
        state: String = "open",
        forceRefresh: Boolean = false
    ): Result<List<MilestoneSummary>> {
        val key = repoMilestonesKey(owner, repo, state)
        if (!forceRefresh) {
            repoMilestonesCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchRepoMilestones(owner, repo, state = state)
        }.onSuccess {
            repoMilestonesCache.put(key, it)
        }
    }

    suspend fun createRepoMilestone(
        owner: String,
        repo: String,
        title: String,
        state: String = "open",
        description: String? = null,
        dueOn: String? = null
    ): Result<MilestoneSummary> = runMutationCancellable {
        restService.createRepoMilestone(owner, repo, title, state, description, dueOn).also {
            val key = repoKey(owner, repo)
            repoMilestonesCache.invalidateWhere { it.owner == key.owner && it.repo == key.repo }
        }
    }

    suspend fun updateRepoMilestone(
        owner: String,
        repo: String,
        number: Int,
        title: String? = null,
        state: String? = null,
        description: String? = null,
        dueOn: String? = null,
        clearDueOn: Boolean = false
    ): Result<MilestoneSummary> = runMutationCancellable {
        restService.updateRepoMilestone(owner, repo, number, title, state, description, dueOn, clearDueOn).also {
            val key = repoKey(owner, repo)
            repoMilestonesCache.invalidateWhere { it.owner == key.owner && it.repo == key.repo }
            issueDetailCache.invalidateWhere { it.owner.equals(key.owner, ignoreCase = true) && it.repo.equals(key.repo, ignoreCase = true) }
            repoIssuesCache.invalidateWhere { it.owner.equals(key.owner, ignoreCase = true) && it.repo.equals(key.repo, ignoreCase = true) }
        }
    }

    suspend fun deleteRepoMilestone(
        owner: String,
        repo: String,
        number: Int
    ): Result<Unit> = runMutationCancellable {
        restService.deleteRepoMilestone(owner, repo, number).also {
            val key = repoKey(owner, repo)
            repoMilestonesCache.invalidateWhere { it.owner == key.owner && it.repo == key.repo }
            issueDetailCache.invalidateWhere { it.owner.equals(key.owner, ignoreCase = true) && it.repo.equals(key.repo, ignoreCase = true) }
            repoIssuesCache.invalidateWhere { it.owner.equals(key.owner, ignoreCase = true) && it.repo.equals(key.repo, ignoreCase = true) }
        }
    }

    suspend fun getRepoAssignees(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<List<UserSummary>> {
        val key = RepoKey(owner, repo)
        if (!forceRefresh) {
            repoAssigneesCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchRepoAssignees(owner, repo)
        }.onSuccess {
            repoAssigneesCache.put(key, it)
        }
    }

    suspend fun updateIssueOrPullMetadata(
        owner: String,
        repo: String,
        number: Int,
        labels: List<String>? = null,
        assignees: List<String>? = null,
        milestoneNumber: Int? = null,
        clearMilestone: Boolean = false
    ): Result<Unit> = runMutationCancellable {
        restService.updateIssueMetadata(owner, repo, number, labels, assignees, milestoneNumber, clearMilestone)
        issueDetailCache.invalidate(issuePullKey(owner, repo, number))
        myIssuesCache.clear()
        repoIssuesCache.invalidateWhere { it.owner == owner && it.repo == repo }
        if (milestoneNumber != null || clearMilestone) {
            val key = repoKey(owner, repo)
            repoMilestonesCache.invalidateWhere { it.owner == key.owner && it.repo == key.repo }
        }
    }

    fun peekRepoCommits(owner: String, repo: String, ref: String? = null, path: String = ""): List<CommitSummary>? =
        commitsCache.peek(repoPathKey(owner, repo, path, ref))

    fun peekCommitDetail(owner: String, repo: String, sha: String): CommitDetail? =
        commitDetailCache.peek(repoCommitKey(owner, repo, sha))

    fun peekCompareRefs(owner: String, repo: String, base: String, head: String): BranchCompareResult? =
        compareCache.peek(repoCompareKey(owner, repo, base, head))

    suspend fun getRepoCommits(
        owner: String,
        repo: String,
        ref: String? = null,
        path: String = "",
        forceRefresh: Boolean = false
    ): Result<List<CommitSummary>> {
        val key = repoPathKey(owner, repo, path, ref)
        if (!forceRefresh) {
            commitsCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchRepoCommits(owner, repo, ref, path.takeIf(String::isNotBlank))
        }.onSuccess {
            commitsCache.put(key, it)
        }
    }

    suspend fun getCommitDetail(
        owner: String,
        repo: String,
        sha: String,
        forceRefresh: Boolean = false
    ): Result<CommitDetail> {
        val key = repoCommitKey(owner, repo, sha)
        if (!forceRefresh) {
            commitDetailCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchCommitDetail(owner, repo, sha)
        }.onSuccess {
            commitDetailCache.put(key, it)
        }
    }

    suspend fun getRepoTags(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<List<TagRef>> {
        val key = repoKey(owner, repo)
        if (!forceRefresh) {
            tagsCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchRepoTags(owner, repo)
        }.onSuccess {
            tagsCache.put(key, it)
        }
    }

    suspend fun compareRefs(
        owner: String,
        repo: String,
        base: String,
        head: String,
        forceRefresh: Boolean = false
    ): Result<BranchCompareResult> {
        val key = repoCompareKey(owner, repo, base, head)
        if (!forceRefresh) {
            compareCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.compareRefs(owner, repo, base, head)
        }.onSuccess {
            compareCache.put(key, it)
        }
    }

    fun peekWorkflowRuns(owner: String, repo: String, workflowId: Long? = null): List<WorkflowRunSummary>? =
        workflowRunsCache.peek(WorkflowRunsKey(owner, repo, workflowId))

    suspend fun getWorkflows(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<List<WorkflowSummary>> {
        val key = RepoKey(owner, repo)
        if (!forceRefresh) {
            workflowsCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchWorkflows(owner, repo)
        }.onSuccess {
            workflowsCache.put(key, it)
        }
    }

    suspend fun getWorkflowRuns(
        owner: String,
        repo: String,
        workflowId: Long? = null,
        forceRefresh: Boolean = false
    ): Result<List<WorkflowRunSummary>> {
        val key = WorkflowRunsKey(owner, repo, workflowId)
        if (!forceRefresh) {
            workflowRunsCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchWorkflowRuns(owner, repo, workflowId)
        }.onSuccess {
            workflowRunsCache.put(key, it)
        }
    }

    suspend fun getWorkflowRun(
        owner: String,
        repo: String,
        runId: Long,
        forceRefresh: Boolean = false
    ): Result<WorkflowRunSummary> {
        val repoKeyVal = repoKey(owner, repo)
        if (!forceRefresh) {
            workflowRunsCache.entriesSnapshot()
                .filterKeys { it.owner == repoKeyVal.owner && it.repo == repoKeyVal.repo }
                .values
                .asSequence()
                .flatten()
                .firstOrNull { it.id == runId }
                ?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchWorkflowRun(owner, repo, runId)
        }
    }

    suspend fun getWorkflowRunJobs(
        owner: String,
        repo: String,
        runId: Long,
        forceRefresh: Boolean = false
    ): Result<List<WorkflowJobSummary>> {
        val key = WorkflowRunIdKey(owner, repo, runId)
        if (!forceRefresh) {
            workflowJobsCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchWorkflowRunJobs(owner, repo, runId)
        }.onSuccess {
            workflowJobsCache.put(key, it)
        }
    }

    suspend fun getJobLogs(owner: String, repo: String, jobId: Long): Result<String> = runCatchingCancellable {
        restService.fetchJobLogs(owner, repo, jobId)
    }

    suspend fun rerunWorkflow(
        owner: String,
        repo: String,
        runId: Long,
        failedOnly: Boolean = false
    ): Result<Unit> = runMutationCancellable {
        restService.rerunWorkflow(owner, repo, runId, failedOnly)
        workflowRunsCache.invalidateWhere { it.owner == owner && it.repo == repo }
        workflowJobsCache.invalidate(WorkflowRunIdKey(owner, repo, runId))
    }

    suspend fun cancelWorkflowRun(owner: String, repo: String, runId: Long): Result<Unit> = runMutationCancellable {
        restService.cancelWorkflowRun(owner, repo, runId)
        workflowRunsCache.invalidateWhere { it.owner == owner && it.repo == repo }
        workflowJobsCache.invalidate(WorkflowRunIdKey(owner, repo, runId))
    }

    suspend fun dispatchWorkflow(
        owner: String,
        repo: String,
        workflowId: Long,
        ref: String,
        inputs: Map<String, String> = emptyMap()
    ): Result<Unit> = runMutationCancellable {
        restService.dispatchWorkflow(owner, repo, workflowId, ref, inputs)
        workflowRunsCache.invalidateWhere { it.owner == owner && it.repo == repo }
    }

    suspend fun getCheckRunsForRef(
        owner: String,
        repo: String,
        ref: String,
        forceRefresh: Boolean = false
    ): Result<List<CheckRunSummary>> {
        val key = RepoPathKey(owner, repo, "", ref)
        if (!forceRefresh) {
            checkRunsCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchCheckRunsForRef(owner, repo, ref)
        }.onSuccess {
            checkRunsCache.put(key, it)
        }
    }

    suspend fun getCheckRunsForCheckSuite(
        owner: String,
        repo: String,
        checkSuiteId: Long,
        forceRefresh: Boolean = false
    ): Result<List<CheckRunSummary>> {
        val key = RepoPathKey(owner, repo, "", "check-suite:$checkSuiteId")
        if (!forceRefresh) {
            checkRunsCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchCheckRunsForCheckSuite(owner, repo, checkSuiteId)
        }.onSuccess {
            checkRunsCache.put(key, it)
        }
    }

    fun peekReleases(owner: String, repo: String): List<ReleaseSummary>? =
        releasesCache.peek(RepoKey(owner, repo))

    suspend fun getReleases(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<List<ReleaseSummary>> {
        val key = RepoKey(owner, repo)
        if (!forceRefresh) {
            releasesCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchReleases(owner, repo)
        }.onSuccess {
            releasesCache.put(key, it)
        }
    }

    suspend fun createRelease(
        owner: String,
        repo: String,
        tagName: String,
        name: String,
        body: String = "",
        targetCommitish: String? = null,
        isDraft: Boolean = false,
        isPrerelease: Boolean = false,
        generateReleaseNotes: Boolean = true
    ): Result<ReleaseSummary> = runMutationCancellable {
        restService.createRelease(
            owner = owner,
            repo = repo,
            tagName = tagName,
            name = name,
            body = body,
            targetCommitish = targetCommitish,
            isDraft = isDraft,
            isPrerelease = isPrerelease,
            generateReleaseNotes = generateReleaseNotes
        ).also {
            releasesCache.invalidate(RepoKey(owner, repo))
            tagsCache.invalidate(RepoKey(owner, repo))
        }
    }

    fun peekDiscussions(owner: String, repo: String, categoryId: String? = null): DiscussionListData? =
        discussionsCache.peek(DiscussionsKey(owner, repo, categoryId))

    suspend fun getDiscussions(
        owner: String,
        repo: String,
        categoryId: String? = null,
        forceRefresh: Boolean = false
    ): Result<DiscussionListData> {
        val key = DiscussionsKey(owner, repo, categoryId)
        if (!forceRefresh) {
            discussionsCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            graphQlService.fetchDiscussions(owner, repo, categoryId = categoryId)
        }.onSuccess {
            discussionsCache.put(key, it)
        }
    }

    fun peekDiscussionDetail(owner: String, repo: String, number: Int): DiscussionDetail? =
        discussionDetailCache.peek(issuePullKey(owner, repo, number))

    suspend fun getDiscussionDetail(
        owner: String,
        repo: String,
        number: Int,
        forceRefresh: Boolean = false
    ): Result<DiscussionDetail> {
        val key = issuePullKey(owner, repo, number)
        if (!forceRefresh) {
            discussionDetailCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            graphQlService.fetchDiscussionDetail(owner, repo, number)
        }.onSuccess {
            discussionDetailCache.put(key, it)
        }
    }

    suspend fun createDiscussion(
        owner: String,
        repo: String,
        repositoryId: String,
        categoryId: String,
        title: String,
        body: String
    ): Result<DiscussionSummary> = runMutationCancellable {
        graphQlService.createDiscussion(repositoryId, categoryId, title, body).also {
            discussionsCache.invalidateWhere { it.owner == owner && it.repo == repo }
        }
    }

    suspend fun addDiscussionComment(
        owner: String,
        repo: String,
        discussionNumber: Int,
        discussionId: String,
        body: String,
        replyToId: String? = null
    ): Result<DiscussionComment> = runMutationCancellable {
        graphQlService.addDiscussionComment(discussionId, body, replyToId).also {
            discussionDetailCache.invalidate(issuePullKey(owner, repo, discussionNumber))
            discussionsCache.invalidateWhere { it.owner == owner && it.repo == repo }
        }
    }

    suspend fun toggleDiscussionUpvote(
        owner: String,
        repo: String,
        subjectId: String,
        currentlyUpvoted: Boolean,
        discussionNumber: Int? = null
    ): Result<Pair<Boolean, Int>> = runMutationCancellable {
        graphQlService.toggleDiscussionUpvote(subjectId, currentlyUpvoted).also {
            discussionsCache.invalidateWhere { it.owner == owner && it.repo == repo }
            if (discussionNumber != null) {
                discussionDetailCache.invalidate(issuePullKey(owner, repo, discussionNumber))
            }
        }
    }

    fun peekRepoSecurityOverview(owner: String, repo: String): RepoSecurityOverview? =
        securityOverviewCache.peek(RepoKey(owner, repo))

    suspend fun getRepoSecurityOverview(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<RepoSecurityOverview> {
        val key = RepoKey(owner, repo)
        if (!forceRefresh) {
            securityOverviewCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            coroutineScope {
                val alertsDeferred = async { runCatchingCancellable { restService.fetchDependabotAlerts(owner, repo) } }
                val advisoriesDeferred = async { runCatchingCancellable { restService.fetchSecurityAdvisories(owner, repo) } }

                val alertsRes = alertsDeferred.await()
                val advisoriesRes = advisoriesDeferred.await()

                var notice: String? = null
                val noticeStatus = (alertsRes.exceptionOrNull() as? GitHubApiException)
                    ?.takeUnless { it.isSyntheticStatus }
                    ?.statusCode
                val alerts: List<DependabotAlertSummary> = alertsRes.getOrElse { err ->
                    val code = (err as? GitHubApiException)?.statusCode
                    when (code) {
                        403, 404 -> {
                            notice = if (code == 403) {
                                "Dependabot alerts require repository admin or security_events permission."
                            } else {
                                "Dependabot vulnerability alerts are not enabled for this repository."
                            }
                            emptyList()
                        }
                        else -> {
                            notice = err.message ?: "Unable to fetch Dependabot alerts"
                            emptyList()
                        }
                    }
                }
                val advisories: List<SecurityAdvisorySummary> = advisoriesRes.getOrElse { emptyList() }
                RepoSecurityOverview(
                    alerts = alerts,
                    advisories = advisories,
                    alertsAccessNotice = notice,
                    alertsAccessStatusCode = noticeStatus
                )
            }
        }.onSuccess {
            securityOverviewCache.put(key, it)
        }
    }

    suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        body: String,
        head: String,
        base: String,
        draft: Boolean = false
    ): Result<Int> = runMutationCancellable {
        restService.createPullRequest(owner, repo, title, body, head, base, draft).also {
            repoIssuesCache.invalidateWhere { it.owner == owner && it.repo == repo }
            myIssuesCache.clear()
            repoDetailCache.invalidate(repoKey(owner, repo))
        }
    }

    suspend fun setPullRequestDraftState(
        owner: String,
        repo: String,
        number: Int,
        pullRequestId: String,
        draft: Boolean
    ): Result<Unit> = runMutationCancellable {
        graphQlService.setPullRequestDraftState(pullRequestId, draft)
        issueDetailCache.invalidate(issuePullKey(owner, repo, number))
        repoIssuesCache.invalidateWhere { it.owner == owner && it.repo == repo }
    }

    suspend fun requestPullReviewers(
        owner: String,
        repo: String,
        number: Int,
        reviewers: List<String>
    ): Result<Unit> = runMutationCancellable {
        restService.requestPullReviewers(owner, repo, number, reviewers)
        issueDetailCache.invalidate(issuePullKey(owner, repo, number))
    }

    suspend fun createPullDiffComment(
        owner: String,
        repo: String,
        number: Int,
        commitId: String,
        path: String,
        body: String,
        line: Int? = null,
        side: String = "RIGHT"
    ): Result<Unit> = runMutationCancellable {
        restService.createPullDiffComment(owner, repo, number, commitId, path, body, line, side)
        issueDetailCache.invalidate(issuePullKey(owner, repo, number))
    }

    suspend fun toggleReaction(
        owner: String,
        repo: String,
        issueOrPullNumber: Int,
        subjectId: String,
        content: String,
        currentlyReacted: Boolean
    ): Result<Boolean> = runMutationCancellable {
        graphQlService.toggleReaction(subjectId, content, currentlyReacted).also {
            issueDetailCache.invalidate(issuePullKey(owner, repo, issueOrPullNumber))
        }
    }

    suspend fun updateIssueComment(
        owner: String,
        repo: String,
        issueOrPullNumber: Int,
        commentId: String,
        body: String
    ): Result<Unit> = runMutationCancellable {
        graphQlService.updateIssueComment(commentId, body)
        issueDetailCache.invalidate(issuePullKey(owner, repo, issueOrPullNumber))
    }

    suspend fun deleteIssueComment(
        owner: String,
        repo: String,
        issueOrPullNumber: Int,
        commentId: String
    ): Result<Unit> = runMutationCancellable {
        graphQlService.deleteIssueComment(commentId)
        issueDetailCache.invalidate(issuePullKey(owner, repo, issueOrPullNumber))
    }

    private fun gistCacheKey(starred: Boolean, userLogin: String?): String = when {
        starred -> "STARRED"
        !userLogin.isNullOrBlank() -> "USER:${userLogin.trim()}"
        else -> "MINE"
    }

    fun peekGists(starred: Boolean = false, userLogin: String? = null): List<GistSummary>? =
        gistsCache.peek(gistCacheKey(starred, userLogin))

    suspend fun getGists(
        starred: Boolean = false,
        userLogin: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<GistSummary>> {
        val key = gistCacheKey(starred, userLogin)
        if (!forceRefresh) {
            gistsCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchGists(starred = starred, userLogin = userLogin)
        }.onSuccess {
            gistsCache.put(key, it)
        }
    }

    fun peekGistDetail(gistId: String): GistDetail? = gistDetailCache.peek(gistId)

    suspend fun getGistDetail(gistId: String, forceRefresh: Boolean = false): Result<GistDetail> {
        if (!forceRefresh) {
            gistDetailCache.peekIfFresh(gistId)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            restService.fetchGistDetail(gistId)
        }.onSuccess {
            gistDetailCache.put(gistId, it)
        }
    }

    suspend fun createGist(
        description: String,
        isPublic: Boolean,
        files: Map<String, String>
    ): Result<GistDetail> = runMutationCancellable {
        restService.createGist(description = description, isPublic = isPublic, files = files).also { detail ->
            gistsCache.clear()
            gistDetailCache.put(detail.summary.id, detail)
        }
    }

    suspend fun updateGist(
        gistId: String,
        description: String? = null,
        updatedFiles: Map<String, String?>
    ): Result<GistDetail> = runMutationCancellable {
        restService.updateGist(gistId = gistId, description = description, updatedFiles = updatedFiles).also { detail ->
            gistsCache.clear()
            gistDetailCache.put(gistId, detail)
        }
    }

    suspend fun setGistStarred(gistId: String, starred: Boolean): Result<Unit> = runMutationCancellable {
        restService.setGistStarred(gistId, starred)
        gistsCache.invalidate("STARRED")
        gistDetailCache.peek(gistId)?.let { existing ->
            gistDetailCache.put(gistId, existing.copy(viewerHasStarred = starred))
        }
    }

    suspend fun deleteGist(gistId: String): Result<Unit> = runMutationCancellable {
        restService.deleteGist(gistId)
        gistsCache.clear()
        gistDetailCache.invalidate(gistId)
    }

    fun peekRepoInsights(owner: String, repo: String): RepoInsightsData? =
        insightsCache.peek(RepoKey(owner, repo))

    suspend fun getRepoInsights(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<RepoInsightsData> {
        val cleanOwner = owner.trim().ifBlank { repo.substringBefore('/', "").trim() }
        val cleanRepo = repo.substringAfterLast('/').trim()
        val key = RepoKey(cleanOwner, cleanRepo)
        if (!forceRefresh) {
            insightsCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatchingCancellable {
            coroutineScope {
                val langJob = async { runCatchingCancellable { restService.fetchRepoLanguages(cleanOwner, cleanRepo) } }
                val activityJob = async { runCatchingCancellable { restService.fetchRepoCommitActivity(cleanOwner, cleanRepo) } }
                val contribJob = async { runCatchingCancellable { restService.fetchRepoContributorStats(cleanOwner, cleanRepo) } }

                val langRes = langJob.await()
                val actRes = activityJob.await()
                val contribRes = contribJob.await()

                val firstFatal = listOf(langRes, actRes, contribRes).firstOrNull { it.isFailure }?.exceptionOrNull()
                if (langRes.isFailure && actRes.isFailure && contribRes.isFailure) {
                    throw firstFatal ?: IllegalStateException("Failed to load repository insights")
                }

                val langMap = langRes.getOrElse { emptyMap() }
                val (weekly, actComputing) = actRes.getOrElse { emptyList<WeeklyCommitActivity>() to false }
                val (contributors, contribComputing) = contribRes.getOrElse { emptyList<ContributorStat>() to false }
                val finalContributors = if (contributors.isEmpty() && contribComputing) {
                    runCatchingCancellable {
                        restService.fetchRepoContributors(cleanOwner, cleanRepo, perPage = 10).map { user ->
                            ContributorStat(
                                login = user.login,
                                avatarUrl = user.avatarUrl,
                                htmlUrl = user.htmlUrl,
                                totalCommits = 0
                            )
                        }
                    }.getOrDefault(emptyList())
                } else {
                    contributors
                }

                RepoInsightsData(
                    languages = computeLanguageShares(langMap),
                    totalLanguageBytes = langMap.values.filter { it > 0L }.sum(),
                    weeklyActivity = weekly,
                    contributors = finalContributors,
                    statsComputing = actComputing || contribComputing
                )
            }
        }.onSuccess { data ->
            if (!data.statsComputing) {
                insightsCache.put(key, data)
            }
        }
    }
}
