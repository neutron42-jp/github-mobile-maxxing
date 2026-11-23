package dev.maxxing.github.network

import dev.maxxing.github.model.ActivityEvent
import dev.maxxing.github.model.ActivityEventKind
import dev.maxxing.github.model.BranchRef
import dev.maxxing.github.model.CommitDetail
import dev.maxxing.github.model.CommitSummary
import dev.maxxing.github.model.ContributionCalendar
import dev.maxxing.github.model.ContributionDay
import dev.maxxing.github.model.ContributionWeek
import dev.maxxing.github.model.FileChangeStatus
import dev.maxxing.github.model.FileContent
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueOrPullDetail
import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueOrPullSummary
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.LabelTag
import dev.maxxing.github.model.MergeMethod
import dev.maxxing.github.model.MilestoneSummary
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.NotificationSubjectType
import dev.maxxing.github.model.NotificationThread
import dev.maxxing.github.model.OrganizationSummary
import dev.maxxing.github.model.PullFileChange
import dev.maxxing.github.model.ReleaseAsset
import dev.maxxing.github.model.ReleaseSummary
import dev.maxxing.github.model.RepoDetail
import dev.maxxing.github.model.RepoInsightsData
import dev.maxxing.github.model.RepoParentSummary
import dev.maxxing.github.model.RepoSummary
import dev.maxxing.github.model.ReviewEvent
import dev.maxxing.github.model.TagRef
import dev.maxxing.github.model.TimelineEvent
import dev.maxxing.github.model.TreeEntry
import dev.maxxing.github.model.TreeEntryType
import dev.maxxing.github.model.UserProfile
import dev.maxxing.github.model.UserSummary
import dev.maxxing.github.model.ViewerProfile
import dev.maxxing.github.model.computeLanguageShares
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPath
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
private data class ForgejoUserDto(
    val id: Long = 0L,
    val login: String = "",
    val username: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val description: String = "",
    val location: String = "",
    val website: String = "",
    val email: String = "",
    @SerialName("followers_count") val followersCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("html_url") val htmlUrl: String = "",
    val created: String = ""
) {
    fun resolvedLogin(): String = login.ifBlank { username }
}

@Serializable
private data class ForgejoOrgDto(
    val id: Long = 0L,
    val name: String = "",
    val username: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val description: String = ""
) {
    fun resolvedLogin(): String = username.ifBlank { name }
}

@Serializable
private data class ForgejoPermissionsDto(
    val admin: Boolean = false,
    val push: Boolean = false,
    val pull: Boolean = false
) {
    fun highestRole(): String? = when {
        admin -> "ADMIN"
        push -> "WRITE"
        pull -> "READ"
        else -> null
    }
}

@Serializable
private data class ForgejoRepoDto(
    val id: Long = 0L,
    val name: String = "",
    @SerialName("full_name") val fullName: String = "",
    val owner: ForgejoUserDto? = null,
    val description: String? = null,
    val language: String? = null,
    @SerialName("stars_count") val starsCount: Int = 0,
    @SerialName("forks_count") val forksCount: Int = 0,
    @SerialName("open_issues_count") val openIssuesCount: Int = 0,
    @SerialName("open_pr_counter") val openPrCounter: Int = 0,
    val private: Boolean = false,
    val fork: Boolean = false,
    val archived: Boolean = false,
    @SerialName("has_issues") val hasIssues: Boolean = true,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("updated_at") val updatedAt: String = "",
    val website: String? = null,
    @SerialName("watchers_count") val watchersCount: Int = 0,
    val permissions: ForgejoPermissionsDto? = null,
    val parent: ForgejoRepoDto? = null,
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class ForgejoRepoSearchEnvelope(
    val data: List<ForgejoRepoDto> = emptyList()
)

@Serializable
private data class ForgejoUserSearchEnvelope(
    val data: List<ForgejoUserDto> = emptyList()
)

@Serializable
private data class ForgejoNotificationThreadDto(
    val id: Long = 0L,
    val unread: Boolean = true,
    val pinned: Boolean = false,
    @SerialName("updated_at") val updatedAt: String = "",
    val subject: ForgejoNotificationSubjectDto? = null,
    val repository: ForgejoRepoDto? = null
)

@Serializable
private data class ForgejoNotificationSubjectDto(
    val title: String = "",
    val type: String = "",
    val url: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("latest_comment_url") val latestCommentUrl: String? = null,
    val state: String = ""
)

@Serializable
private data class ForgejoLabelDto(
    val id: Long = 0L,
    val name: String = "",
    val color: String = "6E40C9",
    val description: String? = null
)

@Serializable
private data class ForgejoMilestoneDto(
    val id: Long = 0L,
    val title: String = "",
    val description: String? = null,
    val state: String = "open",
    @SerialName("due_on") val dueOn: String? = null,
    @SerialName("open_issues") val openIssues: Int = 0,
    @SerialName("closed_issues") val closedIssues: Int = 0
)

@Serializable
private data class ForgejoPullMetaDto(
    val merged: Boolean = false,
    @SerialName("merged_at") val mergedAt: String? = null
)

@Serializable
private data class ForgejoIssueDto(
    val id: Long = 0L,
    val number: Int = 0,
    val title: String = "",
    val body: String? = null,
    val state: String = "open",
    val user: ForgejoUserDto? = null,
    val labels: List<ForgejoLabelDto> = emptyList(),
    val assignees: List<ForgejoUserDto> = emptyList(),
    val milestone: ForgejoMilestoneDto? = null,
    val comments: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("pull_request") val pullRequest: ForgejoPullMetaDto? = null,
    val repository: ForgejoRepoDto? = null
)

@Serializable
private data class ForgejoBranchRefDto(
    val label: String = "",
    val ref: String = "",
    val sha: String = "",
    val repo: ForgejoRepoDto? = null
)

@Serializable
private data class ForgejoPullDto(
    val id: Long = 0L,
    val number: Int = 0,
    val title: String = "",
    val body: String? = null,
    val state: String = "open",
    val user: ForgejoUserDto? = null,
    val labels: List<ForgejoLabelDto> = emptyList(),
    val assignees: List<ForgejoUserDto> = emptyList(),
    val milestone: ForgejoMilestoneDto? = null,
    val comments: Int = 0,
    val merged: Boolean = false,
    val mergeable: Boolean = true,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val head: ForgejoBranchRefDto? = null,
    val base: ForgejoBranchRefDto? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    @SerialName("changed_files") val changedFiles: Int = 0,
    @SerialName("requested_reviewers") val requestedReviewers: List<ForgejoUserDto> = emptyList()
)

@Serializable
private data class ForgejoIssueCommentDto(
    val id: Long = 0L,
    val body: String = "",
    val user: ForgejoUserDto? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
private data class ForgejoReviewDto(
    val id: Long = 0L,
    val user: ForgejoUserDto? = null,
    val state: String = "COMMENT",
    val body: String = "",
    @SerialName("submitted_at") val submittedAt: String = ""
)

@Serializable
private data class ForgejoChangedFileDto(
    val filename: String = "",
    @SerialName("previous_filename") val previousFilename: String? = null,
    val status: String = "modified",
    val additions: Int = 0,
    val deletions: Int = 0,
    val patch: String? = null
)

@Serializable
private data class ForgejoBranchDto(
    val name: String = "",
    val commit: ForgejoBranchCommitDto? = null
)

@Serializable
private data class ForgejoBranchCommitDto(
    val id: String = ""
)

@Serializable
private data class ForgejoContentsDto(
    val name: String = "",
    val path: String = "",
    val sha: String = "",
    val size: Long = 0L,
    val type: String = "file",
    val content: String? = null,
    val encoding: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)

@Serializable
private data class ForgejoCommitDto(
    val sha: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val commit: ForgejoCommitInnerDto? = null,
    val author: ForgejoUserDto? = null,
    val parents: List<ForgejoCommitParentDto> = emptyList(),
    val files: List<ForgejoCommitFileDto> = emptyList(),
    val stats: ForgejoCommitStatsDto? = null
)

@Serializable
private data class ForgejoCommitInnerDto(
    val message: String = "",
    val author: ForgejoCommitAuthorDto? = null
)

@Serializable
private data class ForgejoCommitAuthorDto(
    val name: String = "",
    val date: String = ""
)

@Serializable
private data class ForgejoCommitParentDto(
    val sha: String = ""
)

@Serializable
private data class ForgejoCommitFileDto(
    val filename: String = "",
    val status: String = "modified"
)

@Serializable
private data class ForgejoCommitStatsDto(
    val additions: Int = 0,
    val deletions: Int = 0,
    val total: Int = 0
)

@Serializable
private data class ForgejoTagDto(
    val name: String = "",
    val id: String = "",
    val commit: ForgejoBranchCommitDto? = null
)

@Serializable
private data class ForgejoReleaseDto(
    val id: Long = 0L,
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val author: ForgejoUserDto? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    val assets: List<ForgejoReleaseAssetDto> = emptyList(),
    @SerialName("tarball_url") val tarballUrl: String? = null,
    @SerialName("zipball_url") val zipballUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class ForgejoReleaseAssetDto(
    val id: Long = 0L,
    val name: String = "",
    val size: Long = 0L,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

@Serializable
private data class ForgejoHeatmapItemDto(
    val timestamp: Long = 0L,
    val contributions: Int = 0
)

@Serializable
private data class ForgejoActivityFeedDto(
    val id: Long = 0L,
    @SerialName("op_type") val opType: String = "",
    @SerialName("act_user") val actUser: ForgejoUserDto? = null,
    val repo: ForgejoRepoDto? = null,
    val content: String = "",
    @SerialName("ref_name") val refName: String = "",
    val created: String = ""
)

/**
 * Client for Forgejo / Gitea / Codeberg `/api/v1` REST APIs.
 */
class ForgejoGiteaService(
    private val httpClient: HttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private suspend inline fun <reified T> HttpResponse.decodeOrDefault(defaultValue: () -> T): T {
        if (status == HttpStatusCode.NoContent) return defaultValue()
        val raw = bodyAsText()
        if (raw.isBlank()) return defaultValue()
        return json.decodeFromString(raw)
    }

    private fun cleanApiBase(apiBaseUrl: String): String = apiBaseUrl.trimEnd('/')

    private fun repoUrl(apiBaseUrl: String, owner: String, repo: String): String =
        "${cleanApiBase(apiBaseUrl)}/repos/${owner.trim().encodeURLPathPart()}/${repo.trim().encodeURLPathPart()}"

    suspend fun fetchViewerProfile(apiBaseUrl: String): ViewerProfile {
        val base = cleanApiBase(apiBaseUrl)
        val res = httpClient.get("$base/user")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load profile (${res.status.value})")
        }
        val dto = res.body<ForgejoUserDto>()
        val reposCount = runCatching {
            val r = httpClient.get("$base/user/repos") { parameter("limit", 1) }
            r.headers["X-Total-Count"]?.toIntOrNull()
        }.getOrNull() ?: 0
        return ViewerProfile(
            login = dto.resolvedLogin(),
            name = dto.fullName.takeIf { it.isNotBlank() },
            avatarUrl = dto.avatarUrl,
            bio = dto.description.takeIf { it.isNotBlank() },
            location = dto.location.takeIf { it.isNotBlank() },
            followersCount = dto.followersCount,
            followingCount = dto.followingCount,
            publicReposCount = reposCount,
            htmlUrl = dto.htmlUrl
        )
    }

    suspend fun fetchNotifications(
        apiBaseUrl: String,
        all: Boolean = false,
        participating: Boolean = false,
        page: Int = 1,
        limit: Int = 40
    ): List<NotificationThread> {
        val base = cleanApiBase(apiBaseUrl)
        val res = httpClient.get("$base/notifications") {
            parameter("all", all)
            if (!all) {
                parameter("status-types", "unread")
            }
            parameter("page", page)
            parameter("limit", limit)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load notifications (${res.status.value})")
        }
        return res.decodeOrDefault<List<ForgejoNotificationThreadDto>> { emptyList() }
            .filter { thread -> if (participating) thread.pinned || thread.unread else true }
            .map { dto ->
                val repoFull = dto.repository?.fullName.orEmpty()
                val owner = dto.repository?.owner?.resolvedLogin()?.ifBlank { null }
                    ?: repoFull.substringBefore('/', "")
                val name = dto.repository?.name?.ifBlank { null }
                    ?: repoFull.substringAfter('/', "")
                val subjectType = when (dto.subject?.type?.lowercase()) {
                    "issue" -> NotificationSubjectType.ISSUE
                    "pull", "pullrequest", "pull_request" -> NotificationSubjectType.PULL_REQUEST
                    "commit" -> NotificationSubjectType.COMMIT
                    "release" -> NotificationSubjectType.RELEASE
                    else -> NotificationSubjectType.UNKNOWN
                }
                NotificationThread(
                    id = dto.id.toString(),
                    unread = dto.unread,
                    reason = NotificationReason.OTHER,
                    updatedAt = dto.updatedAt,
                    subjectTitle = dto.subject?.title.orEmpty(),
                    subjectType = subjectType,
                    subjectUrl = dto.subject?.url ?: dto.subject?.htmlUrl,
                    latestCommentUrl = dto.subject?.latestCommentUrl,
                    repoFullName = repoFull.ifBlank { "$owner/$name" },
                    repoOwner = owner,
                    repoName = name,
                    repoOwnerAvatarUrl = dto.repository?.owner?.avatarUrl.orEmpty(),
                    isPrivate = dto.repository?.private ?: false
                )
            }
    }

    suspend fun markNotificationRead(apiBaseUrl: String, threadId: String) {
        val base = cleanApiBase(apiBaseUrl)
        val patchRes = httpClient.patch("$base/notifications/threads/$threadId")
        if (patchRes.status.isSuccess()) return
        val putRes = httpClient.put("$base/notifications/threads/$threadId")
        if (!putRes.status.isSuccess()) {
            throw GitHubApiException(putRes.status.value, "Failed to mark thread read (${putRes.status.value})")
        }
    }

    suspend fun markAllNotificationsRead(apiBaseUrl: String) {
        val base = cleanApiBase(apiBaseUrl)
        val res = httpClient.put("$base/notifications")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to mark all notifications read (${res.status.value})")
        }
    }

    suspend fun fetchMyIssuesAndPulls(
        apiBaseUrl: String,
        preset: IssueFilterPreset,
        stateFilter: IssueStateFilter,
        limit: Int = 30
    ): List<IssueOrPullSummary> {
        val base = cleanApiBase(apiBaseUrl)
        if (stateFilter == IssueStateFilter.ALL) {
            val open = fetchMyIssuesForState(base, preset, "open", limit)
            val closed = fetchMyIssuesForState(base, preset, "closed", limit)
            return (open + closed).distinctBy { "${it.repoFullName}#${it.number}" }
                .sortedByDescending { it.updatedAt }
        }
        val stateParam = if (stateFilter == IssueStateFilter.CLOSED) "closed" else "open"
        return fetchMyIssuesForState(base, preset, stateParam, limit)
    }

    private suspend fun fetchMyIssuesForState(
        apiBaseUrl: String,
        preset: IssueFilterPreset,
        state: String,
        limit: Int
    ): List<IssueOrPullSummary> {
        val res = httpClient.get("$apiBaseUrl/repos/issues/search") {
            parameter("state", state)
            parameter("limit", limit)
            when (preset) {
                IssueFilterPreset.CREATED -> parameter("created", true)
                IssueFilterPreset.ASSIGNED -> parameter("assigned", true)
                IssueFilterPreset.REVIEW_REQUESTED -> parameter("review_requested", true)
                IssueFilterPreset.MENTIONED -> parameter("mentioned", true)
            }
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load issues (${res.status.value})")
        }
        return res.decodeOrDefault<List<ForgejoIssueDto>> { emptyList() }.map { it.toSummary() }
    }

    suspend fun searchIssuesAndPulls(
        apiBaseUrl: String,
        query: String,
        limit: Int = 30
    ): List<IssueOrPullSummary> {
        val base = cleanApiBase(apiBaseUrl)
        val res = httpClient.get("$base/repos/issues/search") {
            parameter("q", query)
            parameter("limit", limit)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Issue search failed (${res.status.value})")
        }
        return res.decodeOrDefault<List<ForgejoIssueDto>> { emptyList() }.map { it.toSummary() }
    }

    suspend fun fetchViewerRepositories(apiBaseUrl: String, limit: Int = 50): List<RepoSummary> {
        val base = cleanApiBase(apiBaseUrl)
        val res = httpClient.get("$base/user/repos") {
            parameter("limit", limit)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load repositories (${res.status.value})")
        }
        return res.decodeOrDefault<List<ForgejoRepoDto>> { emptyList() }.map { it.toRepoSummary() }
    }

    suspend fun fetchStarredRepositories(apiBaseUrl: String, limit: Int = 50): List<RepoSummary> {
        val base = cleanApiBase(apiBaseUrl)
        val res = httpClient.get("$base/user/starred") {
            parameter("limit", limit)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load starred repositories (${res.status.value})")
        }
        return res.decodeOrDefault<List<ForgejoRepoDto>> { emptyList() }.map {
            it.toRepoSummary(viewerHasStarred = true)
        }
    }

    suspend fun searchRepositories(apiBaseUrl: String, query: String, limit: Int = 30): List<RepoSummary> {
        val base = cleanApiBase(apiBaseUrl)
        val res = httpClient.get("$base/repos/search") {
            parameter("q", query)
            parameter("limit", limit)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Repository search failed (${res.status.value})")
        }
        return res.decodeOrDefault<ForgejoRepoSearchEnvelope> { ForgejoRepoSearchEnvelope() }.data.map {
            it.toRepoSummary()
        }
    }

    suspend fun fetchTrendingRepositories(
        apiBaseUrl: String,
        language: String? = null,
        limit: Int = 25
    ): List<RepoSummary> {
        val base = cleanApiBase(apiBaseUrl)
        val res = httpClient.get("$base/repos/search") {
            parameter("sort", "stars")
            parameter("order", "desc")
            parameter("limit", limit)
            language?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load explore repositories (${res.status.value})")
        }
        return res.decodeOrDefault<ForgejoRepoSearchEnvelope> { ForgejoRepoSearchEnvelope() }.data.map {
            it.toRepoSummary()
        }
    }

    suspend fun searchUsers(apiBaseUrl: String, query: String, limit: Int = 30): List<UserSummary> {
        val base = cleanApiBase(apiBaseUrl)
        val res = httpClient.get("$base/users/search") {
            parameter("q", query)
            parameter("limit", limit)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "User search failed (${res.status.value})")
        }
        return res.decodeOrDefault<ForgejoUserSearchEnvelope> { ForgejoUserSearchEnvelope() }.data.map {
            UserSummary(login = it.resolvedLogin(), avatarUrl = it.avatarUrl, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun fetchRepoDetail(apiBaseUrl: String, owner: String, repo: String): RepoDetail {
        val base = cleanApiBase(apiBaseUrl)
        val res = httpClient.get(repoUrl(base, owner, repo))
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load repository (${res.status.value})")
        }
        val dto = res.body<ForgejoRepoDto>()
        val starred = runCatching {
            val s = httpClient.get("$base/user/starred/${owner.trim().encodeURLPathPart()}/${repo.trim().encodeURLPathPart()}")
            s.status == HttpStatusCode.NoContent || s.status.isSuccess()
        }.getOrDefault(false)
        val branches = runCatching { fetchBranches(base, owner, repo) }.getOrDefault(emptyList())
        val topics = runCatching {
            val tRes = httpClient.get("${repoUrl(base, owner, repo)}/topics")
            if (tRes.status.isSuccess()) {
                tRes.decodeOrDefault<TopicsEnvelope> { TopicsEnvelope() }.topics
            } else emptyList()
        }.getOrDefault(emptyList())
        return RepoDetail(
            summary = dto.toRepoSummary(viewerHasStarred = starred),
            homepageUrl = dto.website?.takeIf { it.isNotBlank() },
            topics = topics,
            watchersCount = dto.watchersCount,
            branches = branches
        )
    }

    @Serializable
    private data class TopicsEnvelope(val topics: List<String> = emptyList())

    suspend fun fetchBranches(apiBaseUrl: String, owner: String, repo: String): List<BranchRef> {
        val res = httpClient.get("${repoUrl(apiBaseUrl, owner, repo)}/branches") {
            parameter("limit", 50)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load branches (${res.status.value})")
        }
        return res.decodeOrDefault<List<ForgejoBranchDto>> { emptyList() }.map {
            BranchRef(name = it.name, sha = it.commit?.id.orEmpty())
        }
    }

    suspend fun fetchRepoIssuesOrPulls(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        pullRequests: Boolean,
        stateFilter: IssueStateFilter,
        limit: Int = 30
    ): List<IssueOrPullSummary> {
        if (stateFilter == IssueStateFilter.ALL) {
            val open = fetchRepoItemsForState(apiBaseUrl, owner, repo, pullRequests, "open", limit)
            val closed = fetchRepoItemsForState(apiBaseUrl, owner, repo, pullRequests, "closed", limit)
            return (open + closed).distinctBy { it.number }.sortedByDescending { it.updatedAt }
        }
        val state = if (stateFilter == IssueStateFilter.CLOSED) "closed" else "open"
        return fetchRepoItemsForState(apiBaseUrl, owner, repo, pullRequests, state, limit)
    }

    private suspend fun fetchRepoItemsForState(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        pullRequests: Boolean,
        state: String,
        limit: Int
    ): List<IssueOrPullSummary> {
        val rUrl = repoUrl(apiBaseUrl, owner, repo)
        return if (pullRequests) {
            val res = httpClient.get("$rUrl/pulls") {
                parameter("state", state)
                parameter("limit", limit)
            }
            if (!res.status.isSuccess()) {
                throw GitHubApiException(res.status.value, "Failed to load pull requests (${res.status.value})")
            }
            res.decodeOrDefault<List<ForgejoPullDto>> { emptyList() }.map { it.toSummary(owner, repo) }
        } else {
            val res = httpClient.get("$rUrl/issues") {
                parameter("state", state)
                parameter("type", "issues")
                parameter("limit", limit)
            }
            if (!res.status.isSuccess()) {
                throw GitHubApiException(res.status.value, "Failed to load issues (${res.status.value})")
            }
            res.decodeOrDefault<List<ForgejoIssueDto>> { emptyList() }
                .filter { it.pullRequest == null }
                .map { it.toSummary(fallbackOwner = owner, fallbackRepo = repo) }
        }
    }

    suspend fun fetchIssueOrPullDetail(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        number: Int
    ): IssueOrPullDetail {
        val rUrl = repoUrl(apiBaseUrl, owner, repo)
        val issueRes = httpClient.get("$rUrl/issues/$number")
        if (!issueRes.status.isSuccess()) {
            throw GitHubApiException(issueRes.status.value, "Failed to load #$number (${issueRes.status.value})")
        }
        val issueDto = issueRes.body<ForgejoIssueDto>()
        val isPr = issueDto.pullRequest != null
        val prDto = if (isPr) {
            runCatching {
                val prRes = httpClient.get("$rUrl/pulls/$number")
                if (prRes.status.isSuccess()) prRes.body<ForgejoPullDto>() else null
            }.getOrNull()
        } else null

        val comments = runCatching {
            val cRes = httpClient.get("$rUrl/issues/$number/comments")
            if (cRes.status.isSuccess()) {
                cRes.decodeOrDefault<List<ForgejoIssueCommentDto>> { emptyList() }
            } else emptyList()
        }.getOrDefault(emptyList())

        val reviews = if (isPr) {
            runCatching {
                val revRes = httpClient.get("$rUrl/pulls/$number/reviews")
                if (revRes.status.isSuccess()) {
                    revRes.decodeOrDefault<List<ForgejoReviewDto>> { emptyList() }
                } else emptyList()
            }.getOrDefault(emptyList())
        } else emptyList()

        val repoPerms = runCatching {
            val rRes = httpClient.get(rUrl)
            if (rRes.status.isSuccess()) rRes.body<ForgejoRepoDto>().permissions else null
        }.getOrNull()

        val timeline = buildList {
            comments.forEach { c ->
                add(
                    TimelineEvent.CommentEvent(
                        id = "comment_${c.id}",
                        createdAt = c.createdAt,
                        author = UserSummary(
                            login = c.user?.resolvedLogin() ?: "unknown",
                            avatarUrl = c.user?.avatarUrl.orEmpty(),
                            htmlUrl = c.user?.htmlUrl.orEmpty()
                        ),
                        body = c.body
                    )
                )
            }
            reviews.forEach { rev ->
                add(
                    TimelineEvent.ReviewEventItem(
                        id = "review_${rev.id}",
                        createdAt = rev.submittedAt,
                        author = UserSummary(
                            login = rev.user?.resolvedLogin() ?: "unknown",
                            avatarUrl = rev.user?.avatarUrl.orEmpty(),
                            htmlUrl = rev.user?.htmlUrl.orEmpty()
                        ),
                        state = rev.state.uppercase(),
                        body = rev.body
                    )
                )
            }
        }.sortedBy { it.createdAt }

        val merged = prDto?.merged ?: issueDto.pullRequest?.merged ?: false
        val state = when {
            merged -> IssueOrPullState.MERGED
            (prDto?.state ?: issueDto.state).equals("closed", ignoreCase = true) -> IssueOrPullState.CLOSED
            else -> IssueOrPullState.OPEN
        }
        val canWrite = repoPerms?.admin == true || repoPerms?.push == true
        return IssueOrPullDetail(
            id = (prDto?.id ?: issueDto.id).toString(),
            number = number,
            title = prDto?.title ?: issueDto.title,
            body = (prDto?.body ?: issueDto.body).orEmpty(),
            kind = if (isPr) IssueOrPullKind.PULL_REQUEST else IssueOrPullKind.ISSUE,
            state = state,
            isDraft = (prDto?.title ?: issueDto.title).startsWith("WIP:", ignoreCase = true),
            repoFullName = "$owner/$repo",
            repoOwner = owner,
            repoName = repo,
            author = UserSummary(
                login = (prDto?.user ?: issueDto.user)?.resolvedLogin() ?: "unknown",
                avatarUrl = (prDto?.user ?: issueDto.user)?.avatarUrl.orEmpty(),
                htmlUrl = (prDto?.user ?: issueDto.user)?.htmlUrl.orEmpty()
            ),
            createdAt = prDto?.createdAt ?: issueDto.createdAt,
            updatedAt = prDto?.updatedAt ?: issueDto.updatedAt,
            labels = (prDto?.labels ?: issueDto.labels).map { it.toLabelTag() },
            assignees = (prDto?.assignees ?: issueDto.assignees).map {
                UserSummary(it.resolvedLogin(), it.avatarUrl, it.htmlUrl)
            },
            milestone = (prDto?.milestone ?: issueDto.milestone)?.toMilestoneSummary(),
            headRefName = prDto?.head?.ref,
            baseRefName = prDto?.base?.ref,
            headRepoOwner = prDto?.head?.repo?.owner?.resolvedLogin(),
            headRepoName = prDto?.head?.repo?.name,
            additions = prDto?.additions ?: 0,
            deletions = prDto?.deletions ?: 0,
            changedFilesCount = prDto?.changedFiles ?: 0,
            mergeable = when {
                !isPr -> null
                prDto?.mergeable == true -> "MERGEABLE"
                prDto?.mergeable == false -> "CONFLICTING"
                else -> "UNKNOWN"
            },
            htmlUrl = prDto?.htmlUrl?.ifBlank { null } ?: issueDto.htmlUrl,
            timeline = timeline,
            viewerPermission = repoPerms?.highestRole(),
            viewerCanUpdate = canWrite,
            viewerCanCloseOrReopen = canWrite,
            viewerCanMerge = isPr && canWrite,
            viewerCanEditMetadata = canWrite,
            headRefOid = prDto?.head?.sha,
            requestedReviewers = prDto?.requestedReviewers.orEmpty().map {
                UserSummary(it.resolvedLogin(), it.avatarUrl, it.htmlUrl)
            },
            allowedMergeMethods = MergeMethod.entries
        )
    }

    suspend fun fetchPullFiles(apiBaseUrl: String, owner: String, repo: String, number: Int): List<PullFileChange> {
        val rUrl = repoUrl(apiBaseUrl, owner, repo)
        val res = httpClient.get("$rUrl/pulls/$number/files")
        if (res.status.isSuccess()) {
            val dtos = res.decodeOrDefault<List<ForgejoChangedFileDto>> { emptyList() }
            if (dtos.isNotEmpty()) {
                return dtos.map { dto ->
                    PullFileChange(
                        filename = dto.filename,
                        previousFilename = dto.previousFilename,
                        status = parseFileStatus(dto.status),
                        additions = dto.additions,
                        deletions = dto.deletions,
                        patch = dto.patch,
                        hunks = DiffParser.parse(dto.patch)
                    )
                }
            }
        }
        // Fallback to raw unified diff if `/files` is unavailable or omits patches
        val diffRes = httpClient.get("$rUrl/pulls/$number.diff")
        if (!diffRes.status.isSuccess()) {
            throw GitHubApiException(diffRes.status.value, "Failed to load PR diff (${diffRes.status.value})")
        }
        return parseUnifiedDiffToPullFiles(diffRes.bodyAsText())
    }

    private fun parseUnifiedDiffToPullFiles(rawDiff: String): List<PullFileChange> {
        if (rawDiff.isBlank()) return emptyList()
        val sections = rawDiff.split(Regex("(?=^diff --git )", RegexOption.MULTILINE))
            .filter { it.isNotBlank() }
        return sections.mapNotNull { section ->
            val lines = section.lines()
            val header = lines.firstOrNull { it.startsWith("diff --git ") } ?: return@mapNotNull null
            val parts = header.removePrefix("diff --git ").split(' ')
            val bPath = parts.lastOrNull()?.removePrefix("b/")?.trim().orEmpty()
            val aPath = parts.firstOrNull()?.removePrefix("a/")?.trim().orEmpty()
            val filename = bPath.ifBlank { aPath }.ifBlank { return@mapNotNull null }
            val hunkStartIdx = lines.indexOfFirst { it.startsWith("@@ ") }
            val patch = if (hunkStartIdx >= 0) lines.drop(hunkStartIdx).joinToString("\n") else null
            val status = when {
                lines.any { it.startsWith("new file mode") } -> FileChangeStatus.ADDED
                lines.any { it.startsWith("deleted file mode") } -> FileChangeStatus.REMOVED
                lines.any { it.startsWith("rename from") } -> FileChangeStatus.RENAMED
                else -> FileChangeStatus.MODIFIED
            }
            var additions = 0
            var deletions = 0
            patch?.lineSequence()?.forEach { line ->
                when {
                    line.startsWith("+") && !line.startsWith("+++") -> additions++
                    line.startsWith("-") && !line.startsWith("---") -> deletions++
                }
            }
            PullFileChange(
                filename = filename,
                previousFilename = if (status == FileChangeStatus.RENAMED && aPath != filename) aPath else null,
                status = status,
                additions = additions,
                deletions = deletions,
                patch = patch,
                hunks = DiffParser.parse(patch)
            )
        }
    }

    suspend fun fetchDirectoryContents(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        path: String = "",
        ref: String? = null
    ): List<TreeEntry> {
        val cleanPath = path.trimStart('/')
        val pathSegment = if (cleanPath.isEmpty()) "" else "/${cleanPath.encodeURLPath()}"
        val res = httpClient.get("${repoUrl(apiBaseUrl, owner, repo)}/contents$pathSegment") {
            ref?.takeIf { it.isNotBlank() }?.let { parameter("ref", it) }
        }
        if (res.status == HttpStatusCode.NotFound) return emptyList()
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load directory (${res.status.value})")
        }
        return res.decodeOrDefault<List<ForgejoContentsDto>> { emptyList() }
            .map {
                TreeEntry(
                    path = it.path,
                    name = it.name,
                    type = if (it.type == "dir") TreeEntryType.TREE else TreeEntryType.BLOB,
                    sha = it.sha,
                    size = it.size
                )
            }
            .sortedWith(compareBy<TreeEntry> { it.type != TreeEntryType.TREE }.thenBy { it.name.lowercase() })
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun fetchFileContent(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        path: String,
        ref: String? = null
    ): FileContent {
        val cleanPath = path.trimStart('/')
        val res = httpClient.get("${repoUrl(apiBaseUrl, owner, repo)}/contents/${cleanPath.encodeURLPath()}") {
            ref?.takeIf { it.isNotBlank() }?.let { parameter("ref", it) }
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load file (${res.status.value})")
        }
        val dto = res.body<ForgejoContentsDto>()
        val decoded = if (dto.encoding.equals("base64", ignoreCase = true) && !dto.content.isNullOrBlank()) {
            val cleaned = dto.content.replace("\n", "").replace("\r", "")
            runCatching { Base64.decode(cleaned).decodeToString() }.getOrNull()
        } else {
            dto.content
        }
        return FileContent(
            path = dto.path.ifBlank { cleanPath },
            name = dto.name.ifBlank { cleanPath.substringAfterLast('/') },
            size = dto.size,
            content = decoded.orEmpty(),
            isBinary = decoded == null && dto.size > 0L,
            htmlUrl = dto.htmlUrl.orEmpty(),
            sha = dto.sha
        )
    }

    suspend fun fetchReadme(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        ref: String? = null
    ): String? {
        val rootEntries = runCatching { fetchDirectoryContents(apiBaseUrl, owner, repo, "", ref) }
            .getOrDefault(emptyList())
        val readmeEntry = rootEntries.firstOrNull {
            it.type == TreeEntryType.BLOB && it.name.startsWith("readme", ignoreCase = true)
        }
        val targetPath = readmeEntry?.path ?: "README.md"
        return runCatching { fetchFileContent(apiBaseUrl, owner, repo, targetPath, ref).content }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    suspend fun fetchRepoCommits(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        ref: String? = null,
        path: String? = null,
        limit: Int = 30
    ): List<CommitSummary> {
        val res = httpClient.get("${repoUrl(apiBaseUrl, owner, repo)}/commits") {
            ref?.takeIf { it.isNotBlank() }?.let { parameter("sha", it) }
            path?.takeIf { it.isNotBlank() }?.let { parameter("path", it) }
            parameter("limit", limit)
        }
        if (res.status.value == 409 || res.status == HttpStatusCode.NotFound) return emptyList()
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load commits (${res.status.value})")
        }
        return res.decodeOrDefault<List<ForgejoCommitDto>> { emptyList() }.map { it.toCommitSummary() }
    }

    suspend fun fetchCommitDetail(apiBaseUrl: String, owner: String, repo: String, sha: String): CommitDetail {
        val res = httpClient.get("${repoUrl(apiBaseUrl, owner, repo)}/git/commits/${sha.trim().encodeURLPathPart()}")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load commit (${res.status.value})")
        }
        val dto = res.body<ForgejoCommitDto>()
        val files = dto.files.map {
            PullFileChange(
                filename = it.filename,
                status = parseFileStatus(it.status),
                additions = 0,
                deletions = 0
            )
        }
        return CommitDetail(
            summary = dto.toCommitSummary(),
            additions = dto.stats?.additions ?: 0,
            deletions = dto.stats?.deletions ?: 0,
            changedFilesCount = files.size,
            files = files,
            htmlUrl = dto.htmlUrl
        )
    }

    suspend fun fetchRepoTags(apiBaseUrl: String, owner: String, repo: String): List<TagRef> {
        val res = httpClient.get("${repoUrl(apiBaseUrl, owner, repo)}/tags") {
            parameter("limit", 30)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load tags (${res.status.value})")
        }
        return res.decodeOrDefault<List<ForgejoTagDto>> { emptyList() }.map {
            TagRef(name = it.name, commitSha = it.commit?.id ?: it.id)
        }
    }

    suspend fun fetchReleases(apiBaseUrl: String, owner: String, repo: String): List<ReleaseSummary> {
        val res = httpClient.get("${repoUrl(apiBaseUrl, owner, repo)}/releases") {
            parameter("limit", 30)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load releases (${res.status.value})")
        }
        return res.decodeOrDefault<List<ForgejoReleaseDto>> { emptyList() }.map { it.toReleaseSummary() }
    }

    suspend fun createRelease(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        tagName: String,
        name: String,
        body: String,
        targetCommitish: String?,
        isDraft: Boolean,
        isPrerelease: Boolean
    ): ReleaseSummary {
        @Serializable
        data class CreateReq(
            @SerialName("tag_name") val tagName: String,
            val name: String,
            val body: String,
            @SerialName("target_commitish") val targetCommitish: String? = null,
            val draft: Boolean = false,
            val prerelease: Boolean = false
        )
        val res = httpClient.post("${repoUrl(apiBaseUrl, owner, repo)}/releases") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateReq(
                    tagName = tagName,
                    name = name,
                    body = body,
                    targetCommitish = targetCommitish?.takeIf { it.isNotBlank() },
                    draft = isDraft,
                    prerelease = isPrerelease
                )
            )
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create release (${res.status.value})")
        }
        return res.body<ForgejoReleaseDto>().toReleaseSummary()
    }

    suspend fun fetchContributionCalendar(apiBaseUrl: String, login: String): ContributionCalendar {
        val res = httpClient.get("${cleanApiBase(apiBaseUrl)}/users/${login.trim().encodeURLPathPart()}/heatmap")
        if (!res.status.isSuccess()) {
            return ContributionCalendar(totalContributions = 0, weeks = emptyList())
        }
        val items = res.decodeOrDefault<List<ForgejoHeatmapItemDto>> { emptyList() }
        if (items.isEmpty()) return ContributionCalendar(totalContributions = 0, weeks = emptyList())
        val byDate = LinkedHashMap<String, Int>()
        for (item in items) {
            val dt = Instant.fromEpochSeconds(item.timestamp).toLocalDateTime(TimeZone.UTC).date
            val key = dt.toString()
            byDate[key] = (byDate[key] ?: 0) + item.contributions
        }
        val sortedDates = byDate.entries.sortedBy { it.key }
        val maxCount = (sortedDates.maxOfOrNull { it.value } ?: 1).coerceAtLeast(1)
        val days = sortedDates.map { (date, count) ->
            val level = when {
                count <= 0 -> 0
                count * 4 <= maxCount -> 1
                count * 2 <= maxCount -> 2
                count * 4 <= maxCount * 3 -> 3
                else -> 4
            }
            ContributionDay(date = date, count = count, level = level)
        }
        val weeks = days.chunked(7).map { ContributionWeek(it) }
        return ContributionCalendar(totalContributions = days.sumOf { it.count }, weeks = weeks)
    }

    suspend fun fetchUserActivityEvents(apiBaseUrl: String, login: String): List<ActivityEvent> {
        val res = httpClient.get("${cleanApiBase(apiBaseUrl)}/users/${login.trim().encodeURLPathPart()}/activities/feeds") {
            parameter("limit", 30)
        }
        if (!res.status.isSuccess()) return emptyList()
        return res.decodeOrDefault<List<ForgejoActivityFeedDto>> { emptyList() }.mapNotNull { dto ->
            val repoFull = dto.repo?.fullName ?: return@mapNotNull null
            val repoOwner = dto.repo.owner?.resolvedLogin()?.ifBlank { null } ?: repoFull.substringBefore('/')
            val repoName = dto.repo.name.ifBlank { repoFull.substringAfter('/') }
            val kind = when (dto.opType.lowercase()) {
                "commit_repo", "push_tag" -> ActivityEventKind.PUSH
                "create_issue" -> ActivityEventKind.ISSUES
                "create_pull_request", "merge_pull_request" -> ActivityEventKind.PULL_REQUEST
                "comment_issue", "comment_pull" -> ActivityEventKind.ISSUE_COMMENT
                "approve_pull_request", "reject_pull_request" -> ActivityEventKind.PULL_REQUEST_REVIEW
                "create_repo" -> ActivityEventKind.CREATE
                "publish_release" -> ActivityEventKind.RELEASE
                "fork_repo" -> ActivityEventKind.FORK
                else -> ActivityEventKind.OTHER
            }
            val summary = when (dto.opType.lowercase()) {
                "commit_repo" -> "pushed to ${dto.refName.removePrefix("refs/heads/")}"
                "create_issue" -> "opened issue"
                "create_pull_request" -> "opened pull request"
                "merge_pull_request" -> "merged pull request"
                "comment_issue", "comment_pull" -> "commented on thread"
                "create_repo" -> "created repository"
                "publish_release" -> "published release"
                "fork_repo" -> "forked repository"
                else -> dto.opType.replace('_', ' ')
            }
            ActivityEvent(
                id = dto.id.toString(),
                kind = kind,
                actor = UserSummary(
                    login = dto.actUser?.resolvedLogin() ?: login,
                    avatarUrl = dto.actUser?.avatarUrl.orEmpty(),
                    htmlUrl = dto.actUser?.htmlUrl.orEmpty()
                ),
                repoFullName = repoFull,
                repoOwner = repoOwner,
                repoName = repoName,
                summaryText = summary,
                detailTitle = dto.content.takeIf { it.isNotBlank() },
                branchName = dto.refName.removePrefix("refs/heads/").takeIf { it.isNotBlank() },
                createdAt = dto.created
            )
        }
    }

    suspend fun fetchUserProfile(apiBaseUrl: String, login: String): UserProfile {
        val base = cleanApiBase(apiBaseUrl)
        val cleanLogin = login.trim().encodeURLPathPart()
        val res = httpClient.get("$base/users/$cleanLogin")
        if (res.status.isSuccess()) {
            val dto = res.body<ForgejoUserDto>()
            val isFollowing = runCatching {
                val fRes = httpClient.get("$base/user/following/$cleanLogin")
                fRes.status == HttpStatusCode.NoContent || fRes.status.isSuccess()
            }.getOrDefault(false)
            return UserProfile(
                login = dto.resolvedLogin(),
                name = dto.fullName.takeIf { it.isNotBlank() },
                avatarUrl = dto.avatarUrl,
                bio = dto.description.takeIf { it.isNotBlank() },
                location = dto.location.takeIf { it.isNotBlank() },
                blogUrl = dto.website.takeIf { it.isNotBlank() },
                email = dto.email.takeIf { it.isNotBlank() },
                followersCount = dto.followersCount,
                followingCount = dto.followingCount,
                isOrganization = false,
                viewerIsFollowing = isFollowing,
                createdAt = dto.created,
                htmlUrl = dto.htmlUrl
            )
        }
        val orgRes = httpClient.get("$base/orgs/$cleanLogin")
        if (!orgRes.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load user (${res.status.value})")
        }
        val org = orgRes.body<ForgejoOrgDto>()
        return UserProfile(
            login = org.resolvedLogin(),
            name = org.fullName.takeIf { it.isNotBlank() },
            avatarUrl = org.avatarUrl,
            bio = org.description.takeIf { it.isNotBlank() },
            isOrganization = true
        )
    }

    suspend fun setFollowingUser(apiBaseUrl: String, login: String, follow: Boolean) {
        val url = "${cleanApiBase(apiBaseUrl)}/user/following/${login.trim().encodeURLPathPart()}"
        val res = if (follow) httpClient.put(url) else httpClient.delete(url)
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update follow state (${res.status.value})")
        }
    }

    suspend fun fetchUserRepositories(apiBaseUrl: String, login: String, isOrganization: Boolean): List<RepoSummary> {
        val base = cleanApiBase(apiBaseUrl)
        val path = if (isOrganization) {
            "$base/orgs/${login.trim().encodeURLPathPart()}/repos"
        } else {
            "$base/users/${login.trim().encodeURLPathPart()}/repos"
        }
        val res = httpClient.get(path) { parameter("limit", 50) }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load repositories (${res.status.value})")
        }
        return res.decodeOrDefault<List<ForgejoRepoDto>> { emptyList() }.map { it.toRepoSummary() }
    }

    suspend fun fetchUserStarredRepositories(apiBaseUrl: String, login: String): List<RepoSummary> {
        val res = httpClient.get("${cleanApiBase(apiBaseUrl)}/users/${login.trim().encodeURLPathPart()}/starred") {
            parameter("limit", 50)
        }
        if (!res.status.isSuccess()) return emptyList()
        return res.decodeOrDefault<List<ForgejoRepoDto>> { emptyList() }.map { it.toRepoSummary(viewerHasStarred = true) }
    }

    suspend fun fetchUserOrganizations(apiBaseUrl: String, login: String): List<OrganizationSummary> {
        val res = httpClient.get("${cleanApiBase(apiBaseUrl)}/users/${login.trim().encodeURLPathPart()}/orgs")
        if (!res.status.isSuccess()) return emptyList()
        return res.decodeOrDefault<List<ForgejoOrgDto>> { emptyList() }.map {
            OrganizationSummary(
                login = it.resolvedLogin(),
                avatarUrl = it.avatarUrl,
                description = it.description.takeIf { d -> d.isNotBlank() }
            )
        }
    }

    suspend fun fetchUserFollowers(apiBaseUrl: String, login: String): List<UserSummary> {
        val res = httpClient.get("${cleanApiBase(apiBaseUrl)}/users/${login.trim().encodeURLPathPart()}/followers")
        if (!res.status.isSuccess()) return emptyList()
        return res.decodeOrDefault<List<ForgejoUserDto>> { emptyList() }.map {
            UserSummary(it.resolvedLogin(), it.avatarUrl, it.htmlUrl)
        }
    }

    suspend fun fetchUserFollowing(apiBaseUrl: String, login: String): List<UserSummary> {
        val res = httpClient.get("${cleanApiBase(apiBaseUrl)}/users/${login.trim().encodeURLPathPart()}/following")
        if (!res.status.isSuccess()) return emptyList()
        return res.decodeOrDefault<List<ForgejoUserDto>> { emptyList() }.map {
            UserSummary(it.resolvedLogin(), it.avatarUrl, it.htmlUrl)
        }
    }

    suspend fun postComment(apiBaseUrl: String, owner: String, repo: String, number: Int, body: String) {
        @Serializable
        data class Req(val body: String)
        val res = httpClient.post("${repoUrl(apiBaseUrl, owner, repo)}/issues/$number/comments") {
            contentType(ContentType.Application.Json)
            setBody(Req(body))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to post comment (${res.status.value})")
        }
    }

    suspend fun setIssueOrPullState(apiBaseUrl: String, owner: String, repo: String, number: Int, open: Boolean) {
        @Serializable
        data class Req(val state: String)
        val res = httpClient.patch("${repoUrl(apiBaseUrl, owner, repo)}/issues/$number") {
            contentType(ContentType.Application.Json)
            setBody(Req(if (open) "open" else "closed"))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update state (${res.status.value})")
        }
    }

    suspend fun createIssue(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        title: String,
        body: String
    ): Int {
        @Serializable
        data class Req(val title: String, val body: String)
        @Serializable
        data class Resp(val number: Int)
        val res = httpClient.post("${repoUrl(apiBaseUrl, owner, repo)}/issues") {
            contentType(ContentType.Application.Json)
            setBody(Req(title, body))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create issue (${res.status.value})")
        }
        return res.body<Resp>().number
    }

    suspend fun submitPullReview(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        number: Int,
        event: ReviewEvent,
        body: String
    ) {
        @Serializable
        data class Req(val event: String, val body: String)
        val forgejoEvent = when (event) {
            ReviewEvent.APPROVE -> "APPROVED"
            ReviewEvent.REQUEST_CHANGES -> "REQUEST_CHANGES"
            ReviewEvent.COMMENT -> "COMMENT"
        }
        val res = httpClient.post("${repoUrl(apiBaseUrl, owner, repo)}/pulls/$number/reviews") {
            contentType(ContentType.Application.Json)
            setBody(Req(forgejoEvent, body))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to submit review (${res.status.value})")
        }
    }

    suspend fun mergePullRequest(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        number: Int,
        method: MergeMethod,
        commitTitle: String?
    ) {
        @Serializable
        data class Req(
            @SerialName("Do") val doMethod: String,
            @SerialName("MergeTitleField") val mergeTitle: String? = null
        )
        val res = httpClient.post("${repoUrl(apiBaseUrl, owner, repo)}/pulls/$number/merge") {
            contentType(ContentType.Application.Json)
            setBody(Req(doMethod = method.apiValue, mergeTitle = commitTitle?.takeIf { it.isNotBlank() }))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to merge pull request (${res.status.value})")
        }
    }

    suspend fun createPullRequest(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        title: String,
        body: String,
        head: String,
        base: String
    ): Int {
        @Serializable
        data class Req(val title: String, val body: String, val head: String, val base: String)
        @Serializable
        data class Resp(val number: Int)
        val res = httpClient.post("${repoUrl(apiBaseUrl, owner, repo)}/pulls") {
            contentType(ContentType.Application.Json)
            setBody(Req(title, body, head, base))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create pull request (${res.status.value})")
        }
        return res.body<Resp>().number
    }

    suspend fun createRepository(
        apiBaseUrl: String,
        name: String,
        description: String,
        isPrivate: Boolean,
        autoInit: Boolean
    ): RepoSummary {
        @Serializable
        data class Req(
            val name: String,
            val description: String = "",
            val private: Boolean = false,
            @SerialName("auto_init") val autoInit: Boolean = true
        )
        val res = httpClient.post("${cleanApiBase(apiBaseUrl)}/user/repos") {
            contentType(ContentType.Application.Json)
            setBody(Req(name, description, isPrivate, autoInit))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create repository (${res.status.value})")
        }
        return res.body<ForgejoRepoDto>().toRepoSummary()
    }

    suspend fun forkRepository(apiBaseUrl: String, owner: String, repo: String): RepoSummary {
        val res = httpClient.post("${repoUrl(apiBaseUrl, owner, repo)}/forks") {
            contentType(ContentType.Application.Json)
            setBody(emptyMap<String, String>())
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to fork repository (${res.status.value})")
        }
        return res.body<ForgejoRepoDto>().toRepoSummary()
    }

    suspend fun setStarred(apiBaseUrl: String, owner: String, repo: String, starred: Boolean) {
        val url = "${cleanApiBase(apiBaseUrl)}/user/starred/${owner.trim().encodeURLPathPart()}/${repo.trim().encodeURLPathPart()}"
        val res = if (starred) httpClient.put(url) else httpClient.delete(url)
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update star (${res.status.value})")
        }
    }

    suspend fun fetchRepoSubscription(apiBaseUrl: String, owner: String, repo: String): Boolean {
        val res = httpClient.get("${repoUrl(apiBaseUrl, owner, repo)}/subscription")
        return res.status.isSuccess()
    }

    suspend fun setRepoSubscription(apiBaseUrl: String, owner: String, repo: String, subscribed: Boolean) {
        val url = "${repoUrl(apiBaseUrl, owner, repo)}/subscription"
        val res = if (subscribed) httpClient.put(url) else httpClient.delete(url)
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update watch (${res.status.value})")
        }
    }

    suspend fun fetchRepoLabels(apiBaseUrl: String, owner: String, repo: String): List<LabelTag> {
        val res = httpClient.get("${repoUrl(apiBaseUrl, owner, repo)}/labels") { parameter("limit", 100) }
        if (!res.status.isSuccess()) return emptyList()
        return res.decodeOrDefault<List<ForgejoLabelDto>> { emptyList() }.map { it.toLabelTag() }
    }

    suspend fun fetchRepoMilestones(apiBaseUrl: String, owner: String, repo: String, state: String): List<MilestoneSummary> {
        val res = httpClient.get("${repoUrl(apiBaseUrl, owner, repo)}/milestones") {
            parameter("state", state)
            parameter("limit", 100)
        }
        if (!res.status.isSuccess()) return emptyList()
        return res.decodeOrDefault<List<ForgejoMilestoneDto>> { emptyList() }.map { it.toMilestoneSummary() }
    }

    suspend fun fetchRepoInsights(apiBaseUrl: String, owner: String, repo: String): RepoInsightsData {
        val res = httpClient.get("${repoUrl(apiBaseUrl, owner, repo)}/languages")
        val langMap = if (res.status.isSuccess()) {
            res.decodeOrDefault<Map<String, Long>> { emptyMap() }
        } else emptyMap()
        return RepoInsightsData(
            languages = computeLanguageShares(langMap),
            totalLanguageBytes = langMap.values.filter { it > 0L }.sum(),
            weeklyActivity = emptyList(),
            contributors = emptyList(),
            statsComputing = false
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun upsertRepoFile(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String?,
        existingSha: String?
    ): String {
        @Serializable
        data class Req(
            val content: String,
            val message: String,
            val branch: String? = null,
            val sha: String? = null
        )
        @Serializable
        data class RespCommit(val sha: String = "")
        @Serializable
        data class Resp(val commit: RespCommit? = null)

        val encodedContent = Base64.encode(content.encodeToByteArray())
        val url = "${repoUrl(apiBaseUrl, owner, repo)}/contents/${path.trimStart('/').encodeURLPath()}"
        val payload = Req(
            content = encodedContent,
            message = message,
            branch = branch?.takeIf { it.isNotBlank() },
            sha = existingSha?.takeIf { it.isNotBlank() }
        )
        val res = if (existingSha.isNullOrBlank()) {
            httpClient.post(url) { contentType(ContentType.Application.Json); setBody(payload) }
        } else {
            httpClient.put(url) { contentType(ContentType.Application.Json); setBody(payload) }
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to save file (${res.status.value})")
        }
        return runCatching { res.body<Resp>().commit?.sha.orEmpty() }.getOrDefault("")
    }

    suspend fun deleteRepoFile(
        apiBaseUrl: String,
        owner: String,
        repo: String,
        path: String,
        sha: String,
        message: String,
        branch: String?
    ): String {
        @Serializable
        data class Req(val message: String, val sha: String, val branch: String? = null)
        @Serializable
        data class RespCommit(val sha: String = "")
        @Serializable
        data class Resp(val commit: RespCommit? = null)

        val url = "${repoUrl(apiBaseUrl, owner, repo)}/contents/${path.trimStart('/').encodeURLPath()}"
        val res = httpClient.delete(url) {
            contentType(ContentType.Application.Json)
            setBody(Req(message = message, sha = sha, branch = branch?.takeIf { it.isNotBlank() }))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to delete file (${res.status.value})")
        }
        return runCatching { res.body<Resp>().commit?.sha.orEmpty() }.getOrDefault("")
    }

    private fun ForgejoRepoDto.toRepoSummary(viewerHasStarred: Boolean = false): RepoSummary {
        val resolvedOwner = owner?.resolvedLogin()?.ifBlank { null }
            ?: fullName.substringBefore('/', "")
        val resolvedName = name.ifBlank { fullName.substringAfter('/', "") }
        return RepoSummary(
            id = id.toString(),
            name = resolvedName,
            owner = resolvedOwner,
            fullName = fullName.ifBlank { "$resolvedOwner/$resolvedName" },
            description = description?.takeIf { it.isNotBlank() },
            language = language?.takeIf { it.isNotBlank() },
            stargazersCount = starsCount,
            forksCount = forksCount,
            openIssuesCount = openIssuesCount,
            openPullRequestsCount = openPrCounter,
            isPrivate = private,
            isFork = fork,
            defaultBranch = defaultBranch.ifBlank { "main" },
            updatedAt = updatedAt,
            ownerAvatarUrl = owner?.avatarUrl.orEmpty(),
            viewerHasStarred = viewerHasStarred,
            viewerPermission = permissions?.highestRole(),
            hasKnownPermission = permissions != null,
            isArchived = archived,
            hasIssuesEnabled = hasIssues,
            parent = parent?.let { p ->
                val pOwner = p.owner?.resolvedLogin().orEmpty().ifBlank { p.fullName.substringBefore('/', "") }
                val pName = p.name.ifBlank { p.fullName.substringAfter('/', "") }
                RepoParentSummary(
                    name = pName,
                    owner = pOwner,
                    fullName = p.fullName.ifBlank { "$pOwner/$pName" },
                    defaultBranch = p.defaultBranch.ifBlank { "main" },
                    ownerAvatarUrl = p.owner?.avatarUrl.orEmpty()
                )
            }
        )
    }

    private fun ForgejoIssueDto.toSummary(
        fallbackOwner: String = "",
        fallbackRepo: String = ""
    ): IssueOrPullSummary {
        val repoFull = repository?.fullName?.ifBlank { null }
            ?: if (fallbackOwner.isNotBlank() && fallbackRepo.isNotBlank()) "$fallbackOwner/$fallbackRepo" else ""
        val owner = repository?.owner?.resolvedLogin()?.ifBlank { null }
            ?: fallbackOwner.ifBlank { repoFull.substringBefore('/', "") }
        val repoName = repository?.name?.ifBlank { null }
            ?: fallbackRepo.ifBlank { repoFull.substringAfter('/', "") }
        val isPr = pullRequest != null
        val isMerged = pullRequest?.merged == true
        val resolvedState = when {
            isMerged -> IssueOrPullState.MERGED
            state.equals("closed", ignoreCase = true) -> IssueOrPullState.CLOSED
            else -> IssueOrPullState.OPEN
        }
        return IssueOrPullSummary(
            id = id.toString(),
            number = number,
            title = title,
            kind = if (isPr) IssueOrPullKind.PULL_REQUEST else IssueOrPullKind.ISSUE,
            state = resolvedState,
            isDraft = title.startsWith("WIP:", ignoreCase = true),
            repoFullName = repoFull.ifBlank { "$owner/$repoName" },
            repoOwner = owner,
            repoName = repoName,
            author = UserSummary(
                login = user?.resolvedLogin() ?: "unknown",
                avatarUrl = user?.avatarUrl.orEmpty(),
                htmlUrl = user?.htmlUrl.orEmpty()
            ),
            createdAt = createdAt,
            updatedAt = updatedAt,
            commentCount = comments,
            labels = labels.map { it.toLabelTag() }
        )
    }

    private fun ForgejoPullDto.toSummary(owner: String, repo: String): IssueOrPullSummary {
        val resolvedState = when {
            merged -> IssueOrPullState.MERGED
            state.equals("closed", ignoreCase = true) -> IssueOrPullState.CLOSED
            else -> IssueOrPullState.OPEN
        }
        return IssueOrPullSummary(
            id = id.toString(),
            number = number,
            title = title,
            kind = IssueOrPullKind.PULL_REQUEST,
            state = resolvedState,
            isDraft = title.startsWith("WIP:", ignoreCase = true),
            repoFullName = "$owner/$repo",
            repoOwner = owner,
            repoName = repo,
            author = UserSummary(
                login = user?.resolvedLogin() ?: "unknown",
                avatarUrl = user?.avatarUrl.orEmpty(),
                htmlUrl = user?.htmlUrl.orEmpty()
            ),
            createdAt = createdAt,
            updatedAt = updatedAt,
            commentCount = comments,
            labels = labels.map { it.toLabelTag() }
        )
    }

    private fun ForgejoLabelDto.toLabelTag(): LabelTag = LabelTag(
        name = name,
        colorHex = color.removePrefix("#").ifBlank { "6E40C9" },
        description = description
    )

    private fun ForgejoMilestoneDto.toMilestoneSummary(): MilestoneSummary = MilestoneSummary(
        number = id.toInt(),
        title = title,
        description = description,
        state = state,
        dueOn = dueOn,
        openIssues = openIssues,
        closedIssues = closedIssues
    )

    private fun ForgejoCommitDto.toCommitSummary(): CommitSummary {
        val message = commit?.message.orEmpty()
        val headline = message.lineSequence().firstOrNull().orEmpty()
        val body = message.substringAfter('\n', "").trim()
        return CommitSummary(
            sha = sha,
            shortSha = sha.take(7),
            headline = headline,
            body = body,
            authorName = commit?.author?.name?.ifBlank { null } ?: author?.resolvedLogin() ?: "unknown",
            authorLogin = author?.resolvedLogin()?.takeIf { it.isNotBlank() },
            authorAvatarUrl = author?.avatarUrl.orEmpty(),
            committedAt = commit?.author?.date.orEmpty(),
            parentShas = parents.map { it.sha }
        )
    }

    private fun ForgejoReleaseDto.toReleaseSummary(): ReleaseSummary = ReleaseSummary(
        id = id,
        tagName = tagName,
        name = name.ifBlank { tagName },
        body = body.orEmpty(),
        isDraft = draft,
        isPrerelease = prerelease,
        authorLogin = author?.resolvedLogin().orEmpty(),
        authorAvatarUrl = author?.avatarUrl.orEmpty(),
        publishedAt = publishedAt.orEmpty(),
        createdAt = createdAt,
        assets = assets.map {
            ReleaseAsset(
                id = it.id,
                name = it.name,
                sizeBytes = it.size,
                downloadCount = it.downloadCount,
                downloadUrl = it.browserDownloadUrl
            )
        },
        tarballUrl = tarballUrl.orEmpty(),
        zipballUrl = zipballUrl.orEmpty(),
        htmlUrl = htmlUrl
    )

    private fun parseFileStatus(raw: String): FileChangeStatus = when (raw.lowercase()) {
        "added", "new" -> FileChangeStatus.ADDED
        "removed", "deleted" -> FileChangeStatus.REMOVED
        "renamed" -> FileChangeStatus.RENAMED
        "copied" -> FileChangeStatus.COPIED
        "changed" -> FileChangeStatus.CHANGED
        else -> FileChangeStatus.MODIFIED
    }
}
