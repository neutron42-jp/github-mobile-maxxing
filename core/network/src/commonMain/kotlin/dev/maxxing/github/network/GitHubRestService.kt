package dev.maxxing.github.network

import dev.maxxing.github.model.ActivityEvent
import dev.maxxing.github.model.ActivityEventKind
import dev.maxxing.github.model.BranchCompareResult
import dev.maxxing.github.model.BranchRef
import dev.maxxing.github.model.CheckRunSummary
import dev.maxxing.github.model.CodeSearchHit
import dev.maxxing.github.model.CommitDetail
import dev.maxxing.github.model.CommitSummary
import dev.maxxing.github.model.ContributorStat
import dev.maxxing.github.model.DependabotAlertSummary
import dev.maxxing.github.model.FileChangeStatus
import dev.maxxing.github.model.FileContent
import dev.maxxing.github.model.GistDetail
import dev.maxxing.github.model.GistFile
import dev.maxxing.github.model.GistSummary
import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueOrPullSummary
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
import dev.maxxing.github.model.RepoParentSummary
import dev.maxxing.github.model.RepoSummary
import dev.maxxing.github.model.ReviewEvent
import dev.maxxing.github.model.SecurityAdvisorySummary
import dev.maxxing.github.model.TagRef
import dev.maxxing.github.model.TreeEntry
import dev.maxxing.github.model.TreeEntryType
import dev.maxxing.github.model.UserProfile
import dev.maxxing.github.model.UserSummary
import dev.maxxing.github.model.WeeklyCommitActivity
import dev.maxxing.github.model.WorkflowJobStep
import dev.maxxing.github.model.WorkflowJobSummary
import dev.maxxing.github.model.WorkflowRunSummary
import dev.maxxing.github.model.WorkflowSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
private data class NotificationDto(
    val id: String,
    val unread: Boolean,
    val reason: String,
    @SerialName("updated_at") val updatedAt: String,
    val subject: NotificationSubjectDto,
    val repository: NotificationRepoDto
)

@Serializable
private data class NotificationSubjectDto(
    val title: String,
    val type: String,
    val url: String? = null,
    @SerialName("latest_comment_url") val latestCommentUrl: String? = null
)

@Serializable
private data class NotificationRepoDto(
    val name: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean = false,
    val owner: NotificationOwnerDto? = null
)

@Serializable
private data class NotificationOwnerDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
private data class PullFileDto(
    val filename: String,
    @SerialName("previous_filename") val previousFilename: String? = null,
    val status: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val patch: String? = null
)

@Serializable
private data class BranchDto(
    val name: String,
    val commit: BranchCommitDto
)

@Serializable
private data class BranchCommitDto(
    val sha: String
)

@Serializable
private data class ContentItemDto(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0L,
    val type: String,
    val content: String? = null,
    val encoding: String? = null,
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class CommentRequest(val body: String)

@Serializable
private data class IssueStateRequest(val state: String)

@Serializable
private data class CreateIssueResponse(val number: Int)

@Serializable
private data class SubmitReviewRequest(val event: String, val body: String = "")

@Serializable
private data class MergePullRequest(
    @SerialName("merge_method") val mergeMethod: String,
    @SerialName("commit_title") val commitTitle: String? = null
)

@Serializable
private data class RestRepoOwnerDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
private data class RestRepoPermissionsDto(
    val admin: Boolean = false,
    val maintain: Boolean = false,
    val push: Boolean = false,
    val triage: Boolean = false,
    val pull: Boolean = false
) {
    fun highestRole(): String? = when {
        admin -> "ADMIN"
        maintain -> "MAINTAIN"
        push -> "WRITE"
        triage -> "TRIAGE"
        pull -> "READ"
        else -> null
    }
}

@Serializable
private data class RestRepoParentDto(
    val name: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("default_branch") val defaultBranch: String = "main",
    val owner: RestRepoOwnerDto? = null
) {
    fun toRepoParentSummary(): RepoParentSummary? {
        val cleanFull = fullName.trim()
        if (cleanFull.isBlank()) return null
        val cleanOwner = owner?.login?.takeIf { it.isNotBlank() } ?: cleanFull.substringBefore('/').trim()
        val cleanName = name.takeIf { it.isNotBlank() } ?: cleanFull.substringAfter('/').trim()
        return RepoParentSummary(
            name = cleanName,
            owner = cleanOwner,
            fullName = cleanFull,
            defaultBranch = defaultBranch.ifBlank { "main" },
            ownerAvatarUrl = owner?.avatarUrl.orEmpty()
        )
    }
}

@Serializable
private data class RestRepoDto(
    @SerialName("node_id") val nodeId: String = "",
    val id: Long = 0L,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val description: String? = null,
    val language: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    @SerialName("forks_count") val forksCount: Int = 0,
    @SerialName("open_issues_count") val openIssuesCount: Int = 0,
    val private: Boolean = false,
    val fork: Boolean = false,
    val archived: Boolean = false,
    @SerialName("has_issues") val hasIssues: Boolean = true,
    val permissions: RestRepoPermissionsDto? = null,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("updated_at") val updatedAt: String = "",
    val owner: RestRepoOwnerDto? = null,
    val parent: RestRepoParentDto? = null
) {
    fun toRepoSummary(viewerHasStarred: Boolean = false): RepoSummary = RepoSummary(
        id = nodeId.ifBlank { "rest_$id" },
        name = name,
        owner = owner?.login ?: fullName.substringBefore('/'),
        fullName = fullName,
        description = description,
        language = language,
        languageColorHex = null,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        openIssuesCount = openIssuesCount,
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
        parent = parent?.toRepoParentSummary()
    )
}

@Serializable
private data class RestRepoSearchResponse(
    val items: List<RestRepoDto> = emptyList()
)

@Serializable
private data class RestEventActorDto(
    val login: String = "ghost",
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
private data class RestEventRepoDto(
    val name: String = ""
)

@Serializable
private data class RestEventDto(
    val id: String,
    val type: String = "",
    val actor: RestEventActorDto = RestEventActorDto(),
    val repo: RestEventRepoDto = RestEventRepoDto(),
    val payload: JsonObject? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
private data class RestCommitAuthorMetaDto(
    val name: String = "",
    val email: String = "",
    val date: String = ""
)

@Serializable
private data class RestCommitVerificationDto(
    val verified: Boolean = false,
    val reason: String? = null
)

@Serializable
private data class RestCommitMetaDto(
    val message: String = "",
    val author: RestCommitAuthorMetaDto? = null,
    val committer: RestCommitAuthorMetaDto? = null,
    val verification: RestCommitVerificationDto? = null
)

@Serializable
private data class RestCommitUserDto(
    val login: String? = null,
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
private data class RestCommitParentDto(
    val sha: String = ""
)

@Serializable
private data class RestCommitStatsDto(
    val additions: Int = 0,
    val deletions: Int = 0,
    val total: Int = 0
)

@Serializable
private data class RestCommitDto(
    val sha: String = "",
    val commit: RestCommitMetaDto = RestCommitMetaDto(),
    val author: RestCommitUserDto? = null,
    val committer: RestCommitUserDto? = null,
    val parents: List<RestCommitParentDto> = emptyList(),
    val stats: RestCommitStatsDto? = null,
    val files: List<PullFileDto> = emptyList(),
    @SerialName("html_url") val htmlUrl: String = ""
) {
    fun toCommitSummary(): CommitSummary {
        val normalized = commit.message.trimEnd()
        val splitIdx = normalized.indexOf('\n')
        val headline = if (splitIdx >= 0) normalized.substring(0, splitIdx).trim() else normalized
        val body = if (splitIdx >= 0) normalized.substring(splitIdx + 1).trim() else ""
        val nonBotCommitter = committer?.takeIf { !it.login.equals("web-flow", ignoreCase = true) }
        val noreplyLogin = commit.author?.email?.let(::extractGitHubLoginFromNoreplyEmail)
            ?: commit.committer?.email?.let(::extractGitHubLoginFromNoreplyEmail)
        val resolvedLogin = author?.login ?: noreplyLogin ?: nonBotCommitter?.login
        val resolvedAvatarUrl = author?.avatarUrl?.takeIf { it.isNotBlank() }
            ?: nonBotCommitter?.avatarUrl?.takeIf { it.isNotBlank() }
            ?: resolvedLogin?.let { "https://avatars.githubusercontent.com/$it?size=96" }
            ?: ""
        return CommitSummary(
            sha = sha,
            shortSha = sha.take(7),
            headline = headline.ifBlank { "(no commit message)" },
            body = body,
            authorName = commit.author?.name?.ifBlank { resolvedLogin ?: "unknown" }
                ?: resolvedLogin ?: "unknown",
            authorLogin = resolvedLogin,
            authorAvatarUrl = resolvedAvatarUrl,
            committedAt = commit.committer?.date?.ifBlank { commit.author?.date.orEmpty() }
                ?: commit.author?.date.orEmpty(),
            parentShas = parents.mapNotNull { it.sha.takeIf(String::isNotBlank) },
            verificationReason = commit.verification?.reason,
            isVerified = commit.verification?.verified == true
        )
    }
}

private fun extractGitHubLoginFromNoreplyEmail(email: String): String? {
    val trimmed = email.trim()
    val suffix = "@users.noreply.github.com"
    if (!trimmed.endsWith(suffix, ignoreCase = true)) return null
    val localPart = trimmed.substringBefore('@')
    val login = if ('+' in localPart) localPart.substringAfter('+') else localPart
    return login.takeIf { it.isNotBlank() }
}

@Serializable
private data class RestTagCommitDto(
    val sha: String = ""
)

@Serializable
private data class RestTagDto(
    val name: String = "",
    val commit: RestTagCommitDto = RestTagCommitDto()
)

@Serializable
private data class RestCompareDto(
    val status: String = "identical",
    @SerialName("ahead_by") val aheadBy: Int = 0,
    @SerialName("behind_by") val behindBy: Int = 0,
    @SerialName("total_commits") val totalCommits: Int = 0,
    val commits: List<RestCommitDto> = emptyList(),
    val files: List<PullFileDto> = emptyList(),
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestWorkflowDto(
    val id: Long,
    val name: String = "",
    val path: String = "",
    val state: String = "active",
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestWorkflowsResponse(
    val workflows: List<RestWorkflowDto> = emptyList()
)

@Serializable
private data class RestWorkflowRunDto(
    val id: Long,
    @SerialName("run_number") val runNumber: Int = 0,
    val name: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    @SerialName("head_branch") val headBranch: String? = null,
    @SerialName("head_sha") val headSha: String = "",
    val event: String = "",
    val status: String = "queued",
    val conclusion: String? = null,
    val actor: RestCommitUserDto? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestWorkflowRunsResponse(
    @SerialName("workflow_runs") val workflowRuns: List<RestWorkflowRunDto> = emptyList()
)

@Serializable
private data class RestWorkflowJobStepDto(
    val name: String = "",
    val number: Int = 0,
    val status: String = "queued",
    val conclusion: String? = null
)

@Serializable
private data class RestWorkflowJobDto(
    val id: Long,
    val name: String = "",
    val status: String = "queued",
    val conclusion: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val steps: List<RestWorkflowJobStepDto> = emptyList(),
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestWorkflowJobsResponse(
    val jobs: List<RestWorkflowJobDto> = emptyList()
)

@Serializable
private data class RestCheckAppDto(
    val name: String? = null
)

@Serializable
private data class RestCheckOutputDto(
    val title: String? = null,
    val summary: String? = null
)

@Serializable
private data class RestCheckRunDto(
    val id: Long,
    val name: String = "",
    val status: String = "queued",
    val conclusion: String? = null,
    val app: RestCheckAppDto? = null,
    val output: RestCheckOutputDto? = null,
    @SerialName("details_url") val detailsUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestCheckRunsResponse(
    @SerialName("check_runs") val checkRuns: List<RestCheckRunDto> = emptyList()
)

@Serializable
private data class RestReleaseAssetDto(
    val id: Long,
    val name: String = "",
    val size: Long = 0L,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("content_type") val contentType: String = ""
)

@Serializable
private data class RestReleaseDto(
    val id: Long,
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val author: RestCommitUserDto? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    val assets: List<RestReleaseAssetDto> = emptyList(),
    @SerialName("tarball_url") val tarballUrl: String? = null,
    @SerialName("zipball_url") val zipballUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String = ""
) {
    fun toReleaseSummary(): ReleaseSummary = ReleaseSummary(
        id = id,
        tagName = tagName,
        name = name?.takeIf { it.isNotBlank() } ?: tagName,
        body = body.orEmpty(),
        isDraft = draft,
        isPrerelease = prerelease,
        authorLogin = author?.login.orEmpty(),
        authorAvatarUrl = author?.avatarUrl.orEmpty(),
        publishedAt = publishedAt.orEmpty(),
        createdAt = createdAt,
        assets = assets.map {
            ReleaseAsset(
                id = it.id,
                name = it.name,
                sizeBytes = it.size,
                downloadCount = it.downloadCount,
                downloadUrl = it.browserDownloadUrl,
                contentType = it.contentType
            )
        },
        tarballUrl = tarballUrl.orEmpty(),
        zipballUrl = zipballUrl.orEmpty(),
        htmlUrl = htmlUrl
    )
}

@Serializable
private data class RestUserProfileDto(
    val login: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String = "",
    val bio: String? = null,
    val company: String? = null,
    val location: String? = null,
    val blog: String? = null,
    @SerialName("twitter_username") val twitterUsername: String? = null,
    val email: String? = null,
    val followers: Int = 0,
    val following: Int = 0,
    @SerialName("public_repos") val publicRepos: Int = 0,
    @SerialName("public_gists") val publicGists: Int = 0,
    val type: String = "User",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestOrgDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String = "",
    val description: String? = null
)

@Serializable
private data class RestUserFollowItemDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestCommitActivityDto(
    val week: Long = 0L,
    val total: Int = 0,
    val days: List<Int> = emptyList()
)

@Serializable
private data class RestContributorWeekDto(
    val w: Long = 0L,
    val a: Int = 0,
    val d: Int = 0,
    val c: Int = 0
)

@Serializable
private data class RestContributorStatsDto(
    val total: Int = 0,
    val weeks: List<RestContributorWeekDto> = emptyList(),
    val author: RestUserFollowItemDto? = null
)

@Serializable
private data class RestUserSearchResponse(
    val items: List<RestUserFollowItemDto> = emptyList()
)

@Serializable
private data class RestSearchLabelDto(
    val name: String,
    val color: String = "6750A4",
    val description: String? = null
)

@Serializable
private data class RestSearchIssueItemDto(
    @SerialName("node_id") val nodeId: String = "",
    val id: Long = 0L,
    val number: Int,
    val title: String,
    val state: String = "open",
    val draft: Boolean = false,
    @SerialName("repository_url") val repositoryUrl: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val comments: Int = 0,
    val user: RestEventActorDto? = null,
    val labels: List<RestSearchLabelDto> = emptyList(),
    @SerialName("pull_request") val pullRequest: JsonObject? = null
)

@Serializable
private data class RestIssueSearchResponse(
    val items: List<RestSearchIssueItemDto> = emptyList()
)

@Serializable
private data class RestCodeTextMatchDto(
    val fragment: String = ""
)

@Serializable
private data class RestCodeRepoDto(
    @SerialName("full_name") val fullName: String = "",
    val name: String = "",
    val owner: RestEventActorDto? = null
)

@Serializable
private data class RestCodeItemDto(
    val name: String,
    val path: String,
    val sha: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val repository: RestCodeRepoDto = RestCodeRepoDto(),
    @SerialName("text_matches") val textMatches: List<RestCodeTextMatchDto> = emptyList()
)

@Serializable
private data class RestCodeSearchResponse(
    val items: List<RestCodeItemDto> = emptyList()
)

@Serializable
private data class CreateRepoRequest(
    val name: String,
    val description: String = "",
    val private: Boolean = false,
    @SerialName("auto_init") val autoInit: Boolean = true
)

@Serializable
private data class SubscriptionRequest(
    val subscribed: Boolean,
    val ignored: Boolean = false
)

@Serializable
private data class SubscriptionResponse(
    val subscribed: Boolean = false,
    val ignored: Boolean = false
)

@Serializable
private data class CreateLabelRequest(
    val name: String,
    val color: String,
    val description: String = ""
)

@Serializable
private data class RestMilestoneDto(
    val number: Int,
    val title: String,
    val description: String? = null,
    val state: String = "open",
    @SerialName("due_on") val dueOn: String? = null,
    @SerialName("open_issues") val openIssues: Int = 0,
    @SerialName("closed_issues") val closedIssues: Int = 0
)

@Serializable
private data class UpsertFileContentRequest(
    val message: String,
    val content: String,
    val branch: String? = null,
    val sha: String? = null
)

@Serializable
private data class DeleteFileContentRequest(
    val message: String,
    val sha: String,
    val branch: String? = null
)

@Serializable
private data class FileMutationCommitDto(
    val sha: String = ""
)

@Serializable
private data class FileMutationContentDto(
    val path: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class FileMutationResultDto(
    val content: FileMutationContentDto? = null,
    val commit: FileMutationCommitDto? = null
)

@Serializable
private data class CreateGitBlobRequest(
    val content: String,
    val encoding: String = "base64"
)

@Serializable
private data class GitShaDto(
    val sha: String = ""
)

@Serializable
private data class GitTreeEntryRequest(
    val path: String,
    val mode: String = "100644",
    val type: String = "blob",
    val sha: String
)

@Serializable
private data class CreateGitTreeRequest(
    val tree: List<GitTreeEntryRequest>
)

@Serializable
private data class CreateGitCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String> = emptyList()
)

@Serializable
private data class CreateGitRefRequest(
    val ref: String,
    val sha: String
)

@Serializable
private data class RestViewerLoginDto(
    val login: String = ""
)

@Serializable
private data class RestGistOwnerDto(
    val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
private data class RestGistFileDto(
    val filename: String = "",
    val language: String? = null,
    @SerialName("raw_url") val rawUrl: String = "",
    val size: Long = 0L,
    val content: String? = null
)

@Serializable
private data class RestGistDto(
    val id: String,
    val description: String? = null,
    val public: Boolean = true,
    @SerialName("html_url") val htmlUrl: String = "",
    val owner: RestGistOwnerDto? = null,
    val files: Map<String, RestGistFileDto> = emptyMap(),
    val comments: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
private data class CreateOrUpdateGistFileRequest(
    val content: String
)

@Serializable
private data class CreateGistRequest(
    val description: String,
    val public: Boolean,
    val files: Map<String, CreateOrUpdateGistFileRequest>
)

@Serializable
private data class UpdateGistRequest(
    val description: String? = null,
    val files: Map<String, CreateOrUpdateGistFileRequest?>
)

private val LINK_ENTRY_REGEX = Regex("""<([^>]+)>\s*((?:;\s*[^<,]+)*)""")

/**
 * Extracts the `rel="next"` URL from one or more HTTP `Link` headers, accepting only URLs
 * whose origin (scheme, host, port) matches [baseUrl].
 */
internal fun parseNextLinkUrl(linkHeaders: List<String>, baseUrl: String): String? {
    val expected = runCatching { Url(baseUrl) }.getOrNull() ?: return null
    for (header in linkHeaders) {
        for (match in LINK_ENTRY_REGEX.findAll(header)) {
            val url = match.groupValues[1].trim()
            if (url.isBlank()) continue
            val params = match.groupValues[2].split(';').map { it.trim() }
            val relSegment = params.firstOrNull { it.startsWith("rel=", ignoreCase = true) } ?: continue
            val relValues = relSegment.substringAfter('=').trim().trim('"', '\'').split(Regex("\\s+"))
            if (relValues.none { it.equals("next", ignoreCase = true) }) continue
            val parsed = runCatching { Url(url) }.getOrNull() ?: continue
            if (
                parsed.protocol == expected.protocol &&
                parsed.host.equals(expected.host, ignoreCase = true) &&
                parsed.port == expected.port &&
                parsed.user == null &&
                parsed.password == null
            ) {
                return url
            }
        }
    }
    return null
}

/** Page size requested for `GET /repos/{owner}/{repo}/pulls/{number}/files` (the API maximum). */
internal const val PULL_FILES_PER_PAGE = 100

/**
 * Upper bound on pages fetched for a pull request's changed files. The GitHub REST API lists at most
 * 3000 files per pull request, i.e. 30 pages of [PULL_FILES_PER_PAGE], so a real response never needs more.
 */
internal const val MAX_PULL_FILES_PAGES = 30
private const val MAX_CONDITIONAL_CACHE_ENTRIES = 128

class GitHubRestService(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://api.github.com"
) {
    private val restJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private data class ConditionalCacheEntry(
        val etag: String?,
        val lastModified: String?,
        val value: Any?
    )

    private val conditionalMutex = Mutex()
    private val conditionalCache = LinkedHashMap<String, ConditionalCacheEntry>(64)

    internal suspend fun clearConditionalCache() {
        conditionalMutex.withLock {
            conditionalCache.clear()
        }
    }

    internal suspend fun invalidateConditionalCachePrefix(prefix: String) {
        conditionalMutex.withLock {
            val iter = conditionalCache.entries.iterator()
            while (iter.hasNext()) {
                if (iter.next().key.startsWith(prefix)) iter.remove()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> conditionalGet(
        cacheKey: String,
        url: String,
        buildRequest: HttpRequestBuilder.() -> Unit = {},
        onResponse: suspend (HttpResponse) -> T
    ): T {
        val cached = conditionalMutex.withLock { conditionalCache[cacheKey] }
        val response = httpClient.get(url) {
            buildRequest()
            cached?.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
            if (cached?.etag == null) {
                cached?.lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
            }
        }
        if (response.status == HttpStatusCode.NotModified && cached != null) {
            return cached.value as T
        }
        val parsed = onResponse(response)
        if (response.status.isSuccess()) {
            val etag = response.headers[HttpHeaders.ETag]?.takeIf { it.isNotBlank() }
            val lastModified = response.headers[HttpHeaders.LastModified]?.takeIf { it.isNotBlank() }
            if (etag != null || lastModified != null) {
                conditionalMutex.withLock {
                    conditionalCache.remove(cacheKey)
                    conditionalCache[cacheKey] = ConditionalCacheEntry(etag, lastModified, parsed)
                    while (conditionalCache.size > MAX_CONDITIONAL_CACHE_ENTRIES) {
                        val eldest = conditionalCache.keys.firstOrNull() ?: break
                        conditionalCache.remove(eldest)
                    }
                }
            }
        }
        return parsed
    }

    private suspend inline fun <reified T> HttpResponse.bodyOrDefaultOnEmpty(defaultValue: () -> T): T {
        if (status == HttpStatusCode.NoContent) return defaultValue()
        val raw = bodyAsText()
        if (raw.isBlank()) return defaultValue()
        return restJson.decodeFromString(raw)
    }

    suspend fun fetchNotifications(
        all: Boolean = false,
        participating: Boolean = false,
        page: Int = 1,
        perPage: Int = 40
    ): List<NotificationThread> = conditionalGet(
        cacheKey = "notifications:$all:$participating:$page:$perPage",
        url = "$baseUrl/notifications",
        buildRequest = {
            header(HttpHeaders.CacheControl, "no-cache")
            header(HttpHeaders.Pragma, "no-cache")
            parameter("all", all)
            parameter("participating", participating)
            parameter("page", page)
            parameter("per_page", perPage)
        }
    ) { response ->
        if (!response.status.isSuccess()) {
            throw GitHubApiException(response.status.value, "Failed to load notifications (${response.status.value})")
        }
        response.bodyOrDefaultOnEmpty<List<NotificationDto>> { emptyList() }.map { dto ->
            NotificationThread(
                id = dto.id,
                unread = dto.unread,
                reason = parseReason(dto.reason),
                updatedAt = dto.updatedAt,
                subjectTitle = dto.subject.title,
                subjectType = parseSubjectType(dto.subject.type),
                subjectUrl = dto.subject.url,
                latestCommentUrl = dto.subject.latestCommentUrl,
                repoFullName = dto.repository.fullName,
                repoOwner = dto.repository.owner?.login ?: dto.repository.fullName.substringBefore('/'),
                repoName = dto.repository.name,
                repoOwnerAvatarUrl = dto.repository.owner?.avatarUrl.orEmpty(),
                isPrivate = dto.repository.private
            )
        }
    }

    suspend fun markNotificationRead(threadId: String) {
        val res = httpClient.patch("$baseUrl/notifications/threads/$threadId")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to mark thread read")
        }
        invalidateConditionalCachePrefix("notifications:")
    }

    suspend fun markNotificationDone(threadId: String) {
        val res = httpClient.delete("$baseUrl/notifications/threads/$threadId")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to mark thread done")
        }
        invalidateConditionalCachePrefix("notifications:")
    }

    suspend fun markAllNotificationsRead() {
        val res = httpClient.put("$baseUrl/notifications") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("read" to true))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to mark all notifications read")
        }
        invalidateConditionalCachePrefix("notifications:")
    }

    suspend fun fetchPullFiles(owner: String, repo: String, number: Int): List<PullFileChange> {
        // Follow Link (rel="next") pagination until it runs out. The page cap and the revisit guard only
        // protect against a misbehaving server; GitHub itself never serves more than MAX_PULL_FILES_PAGES pages.
        val files = mutableListOf<PullFileChange>()
        val visitedUrls = mutableSetOf<String>()
        var nextUrl: String? = "$baseUrl/repos/$owner/$repo/pulls/$number/files"
        var fetchedPages = 0
        while (fetchedPages < MAX_PULL_FILES_PAGES) {
            val currentUrl = nextUrl ?: break
            if (!visitedUrls.add(currentUrl)) break
            val res = httpClient.get(currentUrl) {
                if (fetchedPages == 0) parameter("per_page", PULL_FILES_PER_PAGE)
            }
            if (!res.status.isSuccess()) {
                throw GitHubApiException(res.status.value, "Failed to load PR files (${res.status.value})")
            }
            files += res.bodyOrDefaultOnEmpty<List<PullFileDto>> { emptyList() }.map { it.toPullFileChange() }
            fetchedPages++
            nextUrl = parseNextLinkUrl(res.headers.getAll(HttpHeaders.Link).orEmpty(), baseUrl)
        }
        return files
    }

    private fun PullFileDto.toPullFileChange(): PullFileChange = PullFileChange(
        filename = filename,
        previousFilename = previousFilename,
        status = parseFileStatus(status),
        additions = additions,
        deletions = deletions,
        patch = patch,
        hunks = DiffParser.parse(patch)
    )

    suspend fun fetchRepoCommits(
        owner: String,
        repo: String,
        ref: String? = null,
        path: String? = null,
        perPage: Int = 30
    ): List<CommitSummary> = conditionalGet(
        cacheKey = "commits:${owner.lowercase()}/${repo.lowercase()}:${ref.orEmpty()}:${path.orEmpty()}:$perPage",
        url = "${repoPath(owner, repo)}/commits",
        buildRequest = {
            noCacheHeaders()
            ref?.takeIf { it.isNotBlank() }?.let { parameter("sha", it) }
            path?.takeIf { it.isNotBlank() }?.let { parameter("path", it) }
            parameter("per_page", perPage)
        }
    ) { res ->
        // GitHub returns 409 Conflict when a repository is empty or a newly created fork is still initializing.
        if (res.status.value == 409) {
            emptyList()
        } else if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load commits (${res.status.value})")
        } else {
            res.bodyOrDefaultOnEmpty<List<RestCommitDto>> { emptyList() }.map { it.toCommitSummary() }
        }
    }

    suspend fun fetchCommitDetail(owner: String, repo: String, sha: String): CommitDetail = conditionalGet(
        cacheKey = "commit:${owner.lowercase()}/${repo.lowercase()}:${sha.trim()}",
        url = "${repoPath(owner, repo)}/commits/${sha.trim().encodeURLPathPart()}",
        buildRequest = { noCacheHeaders() }
    ) { res ->
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load commit $sha (${res.status.value})")
        }
        val dto = res.body<RestCommitDto>()
        val files = dto.files.map { it.toPullFileChange() }
        CommitDetail(
            summary = dto.toCommitSummary(),
            additions = dto.stats?.additions ?: files.sumOf { it.additions },
            deletions = dto.stats?.deletions ?: files.sumOf { it.deletions },
            changedFilesCount = files.size,
            files = files,
            htmlUrl = dto.htmlUrl
        )
    }

    suspend fun fetchRepoTags(owner: String, repo: String, perPage: Int = 30): List<TagRef> = conditionalGet(
        cacheKey = "tags:${owner.lowercase()}/${repo.lowercase()}:$perPage",
        url = "${repoPath(owner, repo)}/tags",
        buildRequest = {
            noCacheHeaders()
            parameter("per_page", perPage)
        }
    ) { res ->
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load tags (${res.status.value})")
        }
        res.bodyOrDefaultOnEmpty<List<RestTagDto>> { emptyList() }.map { TagRef(name = it.name, commitSha = it.commit.sha) }
    }

    suspend fun compareRefs(
        owner: String,
        repo: String,
        base: String,
        head: String
    ): BranchCompareResult {
        val encodedBase = encodeCompareRef(base)
        val encodedHead = encodeCompareRef(head)
        val res = httpClient.get("${repoPath(owner, repo)}/compare/${encodedBase}...${encodedHead}") {
            noCacheHeaders()
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to compare $base...$head (${res.status.value})")
        }
        val dto = res.body<RestCompareDto>()
        return BranchCompareResult(
            baseRef = base,
            headRef = head,
            status = dto.status,
            aheadBy = dto.aheadBy,
            behindBy = dto.behindBy,
            totalCommits = dto.totalCommits,
            commits = dto.commits.map { it.toCommitSummary() },
            files = dto.files.map { it.toPullFileChange() },
            htmlUrl = dto.htmlUrl
        )
    }

    suspend fun fetchWorkflows(owner: String, repo: String): List<WorkflowSummary> = conditionalGet(
        cacheKey = "workflows:${owner.lowercase()}/${repo.lowercase()}",
        url = "$baseUrl/repos/$owner/$repo/actions/workflows"
    ) { res ->
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load workflows (${res.status.value})")
        }
        res.bodyOrDefaultOnEmpty<RestWorkflowsResponse> { RestWorkflowsResponse() }.workflows.map {
            WorkflowSummary(id = it.id, name = it.name, path = it.path, state = it.state, htmlUrl = it.htmlUrl)
        }
    }

    private fun RestWorkflowRunDto.toSummary(): WorkflowRunSummary = WorkflowRunSummary(
        id = id,
        runNumber = runNumber,
        name = name.orEmpty().ifBlank { displayTitle.orEmpty() },
        displayTitle = displayTitle.orEmpty().ifBlank { name.orEmpty() },
        headBranch = headBranch.orEmpty(),
        headSha = headSha,
        event = event,
        status = status,
        conclusion = conclusion,
        actorLogin = actor?.login.orEmpty(),
        actorAvatarUrl = actor?.avatarUrl.orEmpty(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        htmlUrl = htmlUrl
    )

    suspend fun fetchWorkflowRuns(
        owner: String,
        repo: String,
        workflowId: Long? = null,
        branch: String? = null,
        perPage: Int = 25
    ): List<WorkflowRunSummary> {
        val url = if (workflowId != null) {
            "$baseUrl/repos/$owner/$repo/actions/workflows/$workflowId/runs"
        } else {
            "$baseUrl/repos/$owner/$repo/actions/runs"
        }
        return conditionalGet(
            cacheKey = "runs:${owner.lowercase()}/${repo.lowercase()}:${workflowId ?: "all"}:${branch.orEmpty()}:$perPage",
            url = url,
            buildRequest = {
                branch?.takeIf { it.isNotBlank() }?.let { parameter("branch", it) }
                parameter("per_page", perPage)
            }
        ) { res ->
            if (!res.status.isSuccess()) {
                throw GitHubApiException(res.status.value, "Failed to load workflow runs (${res.status.value})")
            }
            res.bodyOrDefaultOnEmpty<RestWorkflowRunsResponse> { RestWorkflowRunsResponse() }
                .workflowRuns
                .map { it.toSummary() }
        }
    }

    suspend fun fetchWorkflowRun(owner: String, repo: String, runId: Long): WorkflowRunSummary {
        val res = httpClient.get("${repoPath(owner, repo)}/actions/runs/$runId")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load workflow run #$runId (${res.status.value})")
        }
        return res.body<RestWorkflowRunDto>().toSummary()
    }

    suspend fun fetchWorkflowRunJobs(owner: String, repo: String, runId: Long): List<WorkflowJobSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/actions/runs/$runId/jobs") {
            parameter("per_page", 50)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load jobs for run #$runId (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<RestWorkflowJobsResponse> { RestWorkflowJobsResponse() }.jobs.map { job ->
            WorkflowJobSummary(
                id = job.id,
                name = job.name,
                status = job.status,
                conclusion = job.conclusion,
                startedAt = job.startedAt,
                completedAt = job.completedAt,
                steps = job.steps.map { s ->
                    WorkflowJobStep(name = s.name, number = s.number, status = s.status, conclusion = s.conclusion)
                },
                htmlUrl = job.htmlUrl
            )
        }
    }

    suspend fun fetchJobLogs(owner: String, repo: String, jobId: Long): String {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/actions/jobs/$jobId/logs")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load job logs (${res.status.value})")
        }
        val full = res.bodyAsText()
        // Keep last 40KB if an action log is huge so mobile rendering stays fluid
        return if (full.length > 40_000) "... (truncated to latest 40 KB) ...\n" + full.takeLast(40_000) else full
    }

    suspend fun rerunWorkflow(owner: String, repo: String, runId: Long, failedOnly: Boolean = false) {
        val action = if (failedOnly) "rerun-failed-jobs" else "rerun"
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/actions/runs/$runId/$action")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to re-run workflow (${res.status.value})")
        }
    }

    suspend fun cancelWorkflowRun(owner: String, repo: String, runId: Long) {
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/actions/runs/$runId/cancel")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to cancel run (${res.status.value})")
        }
    }

    suspend fun dispatchWorkflow(
        owner: String,
        repo: String,
        workflowId: Long,
        ref: String,
        inputs: Map<String, String> = emptyMap()
    ) {
        val payload = buildJsonObject {
            put("ref", ref)
            if (inputs.isNotEmpty()) {
                put(
                    "inputs",
                    buildJsonObject {
                        inputs.forEach { (k, v) -> put(k, v) }
                    }
                )
            }
        }
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/actions/workflows/$workflowId/dispatches") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to dispatch workflow (${res.status.value})")
        }
    }

    suspend fun fetchCheckRunsForRef(owner: String, repo: String, ref: String): List<CheckRunSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/commits/${ref.encodeURLPathPart()}/check-runs") {
            parameter("per_page", 50)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load check runs (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<RestCheckRunsResponse> { RestCheckRunsResponse() }.toCheckRunSummaries()
    }

    suspend fun fetchCheckRunsForCheckSuite(owner: String, repo: String, checkSuiteId: Long): List<CheckRunSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/check-suites/$checkSuiteId/check-runs") {
            parameter("per_page", 50)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load check suite runs (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<RestCheckRunsResponse> { RestCheckRunsResponse() }.toCheckRunSummaries()
    }

    private fun RestCheckRunsResponse.toCheckRunSummaries(): List<CheckRunSummary> =
        checkRuns.map { cr ->
            CheckRunSummary(
                id = cr.id,
                name = cr.name,
                status = cr.status,
                conclusion = cr.conclusion,
                appName = cr.app?.name,
                title = cr.output?.title,
                summary = cr.output?.summary,
                detailsUrl = cr.detailsUrl?.ifBlank { cr.htmlUrl } ?: cr.htmlUrl
            )
        }

    suspend fun fetchReleases(owner: String, repo: String, perPage: Int = 20): List<ReleaseSummary> = conditionalGet(
        cacheKey = "releases:${owner.lowercase()}/${repo.lowercase()}:$perPage",
        url = "$baseUrl/repos/$owner/$repo/releases",
        buildRequest = { parameter("per_page", perPage) }
    ) { res ->
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load releases (${res.status.value})")
        }
        res.bodyOrDefaultOnEmpty<List<RestReleaseDto>> { emptyList() }.map { it.toReleaseSummary() }
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
    ): ReleaseSummary {
        val payload = buildJsonObject {
            put("tag_name", tagName)
            if (name.isNotBlank()) put("name", name)
            if (body.isNotBlank()) put("body", body)
            targetCommitish?.takeIf { it.isNotBlank() }?.let { put("target_commitish", it) }
            put("draft", isDraft)
            put("prerelease", isPrerelease)
            put("generate_release_notes", generateReleaseNotes)
        }
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/releases") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create release (${res.status.value})")
        }
        return res.body<RestReleaseDto>().toReleaseSummary()
    }

    suspend fun fetchDependabotAlerts(
        owner: String,
        repo: String
    ): List<DependabotAlertSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/dependabot/alerts") {
            parameter("per_page", 30)
            parameter("state", "open")
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load Dependabot alerts (${res.status.value})")
        }
        val items = res.bodyOrDefaultOnEmpty<JsonArray> { JsonArray(emptyList()) }
        return items.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val number = obj["number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val adv = obj["security_advisory"] as? JsonObject
            val vuln = obj["security_vulnerability"] as? JsonObject
            val dep = obj["dependency"] as? JsonObject
            val pkg = (dep?.get("package") as? JsonObject) ?: (vuln?.get("package") as? JsonObject)
            val firstPatched = (vuln?.get("first_patched_version") as? JsonObject)
                ?.get("identifier")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
            DependabotAlertSummary(
                number = number,
                state = obj["state"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "open",
                severity = vuln?.get("severity")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    ?: adv?.get("severity")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    ?: "medium",
                packageName = pkg?.get("name")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "unknown",
                ecosystem = pkg?.get("ecosystem")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "",
                summary = adv?.get("summary")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "Security alert #$number",
                vulnerableVersionRange = vuln?.get("vulnerable_version_range")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "",
                patchedVersion = firstPatched,
                createdAt = obj["created_at"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "",
                htmlUrl = obj["html_url"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: ""
            )
        }
    }

    suspend fun fetchSecurityAdvisories(
        owner: String,
        repo: String
    ): List<SecurityAdvisorySummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/security-advisories") {
            parameter("per_page", 20)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load security advisories (${res.status.value})")
        }
        val items = res.bodyOrDefaultOnEmpty<JsonArray> { JsonArray(emptyList()) }
        return items.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val ghsaId = obj["ghsa_id"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                ?: return@mapNotNull null
            SecurityAdvisorySummary(
                ghsaId = ghsaId,
                summary = obj["summary"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: ghsaId,
                severity = obj["severity"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "medium",
                state = obj["state"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "published",
                publishedAt = obj["published_at"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "",
                htmlUrl = obj["html_url"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: ""
            )
        }
    }

    suspend fun postComment(owner: String, repo: String, number: Int, body: String) {
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/issues/$number/comments") {
            contentType(ContentType.Application.Json)
            setBody(CommentRequest(body))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to post comment (${res.status.value})")
        }
    }

    suspend fun setIssueOrPullState(owner: String, repo: String, number: Int, open: Boolean) {
        val res = httpClient.patch("$baseUrl/repos/$owner/$repo/issues/$number") {
            contentType(ContentType.Application.Json)
            setBody(IssueStateRequest(if (open) "open" else "closed"))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update state (${res.status.value})")
        }
    }

    suspend fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String,
        labels: List<String> = emptyList()
    ): Int {
        val cleanLabels = labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val payload = buildJsonObject {
            put("title", title)
            put("body", body)
            if (cleanLabels.isNotEmpty()) {
                put("labels", JsonArray(cleanLabels.map { JsonPrimitive(it) }))
            }
        }
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/issues") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create issue (${res.status.value})")
        }
        return res.body<CreateIssueResponse>().number
    }

    suspend fun submitPullReview(
        owner: String,
        repo: String,
        number: Int,
        event: ReviewEvent,
        body: String
    ) {
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/pulls/$number/reviews") {
            contentType(ContentType.Application.Json)
            setBody(SubmitReviewRequest(event = event.apiValue, body = body))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to submit review (${res.status.value})")
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
    ): Int {
        val payload = buildJsonObject {
            put("title", title)
            put("body", body)
            put("head", head)
            put("base", base)
            put("draft", draft)
        }
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/pulls") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create pull request (${res.status.value})")
        }
        return res.body<CreateIssueResponse>().number
    }

    suspend fun requestPullReviewers(
        owner: String,
        repo: String,
        number: Int,
        reviewers: List<String>
    ) {
        val payload = buildJsonObject {
            put("reviewers", buildJsonArray { reviewers.distinct().forEach { add(it) } })
        }
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/pulls/$number/requested_reviewers") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to request reviewers (${res.status.value})")
        }
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
    ) {
        val payload = buildJsonObject {
            put("body", body)
            put("commit_id", commitId)
            put("path", path)
            if (line != null && line > 0) {
                put("line", line)
                put("side", side)
            } else {
                put("subject_type", "file")
            }
        }
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/pulls/$number/comments") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to add diff comment (${res.status.value})")
        }
    }

    suspend fun mergePullRequest(
        owner: String,
        repo: String,
        number: Int,
        method: MergeMethod,
        commitTitle: String? = null
    ) {
        val res = httpClient.put("$baseUrl/repos/$owner/$repo/pulls/$number/merge") {
            contentType(ContentType.Application.Json)
            setBody(MergePullRequest(mergeMethod = method.apiValue, commitTitle = commitTitle))
        }
        if (!res.status.isSuccess()) {
            val rawBody = runCatching { res.bodyAsText() }.getOrNull().orEmpty()
            val apiMsg = runCatching {
                restJson.parseToJsonElement(rawBody).let { el ->
                    (el as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }
            val message = if (apiMsg != null) {
                "$apiMsg (${res.status.value})"
            } else {
                "Merge failed (${res.status.value})"
            }
            throw GitHubApiException(res.status.value, message)
        }
    }

    suspend fun fetchBranches(owner: String, repo: String): List<BranchRef> = conditionalGet(
        cacheKey = "branches:${owner.lowercase()}/${repo.lowercase()}",
        url = "${repoPath(owner, repo)}/branches",
        buildRequest = {
            noCacheHeaders()
            parameter("per_page", 100)
        }
    ) { res ->
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load branches (${res.status.value})")
        }
        res.bodyOrDefaultOnEmpty<List<BranchDto>> { emptyList() }.map { BranchRef(name = it.name, sha = it.commit.sha) }
    }

    suspend fun fetchDirectoryContents(
        owner: String,
        repo: String,
        path: String = "",
        ref: String? = null
    ): List<TreeEntry> {
        val cleanPath = encodeRepoSubPath(path)
        val url = if (cleanPath.isEmpty()) {
            "${repoPath(owner, repo)}/contents"
        } else {
            "${repoPath(owner, repo)}/contents/$cleanPath"
        }
        return conditionalGet(
            cacheKey = "dir:${owner.lowercase()}/${repo.lowercase()}:${path.trimStart('/')}:${ref.orEmpty()}",
            url = url,
            buildRequest = {
                noCacheHeaders()
                if (!ref.isNullOrBlank()) parameter("ref", ref)
            }
        ) { res ->
            if (!res.status.isSuccess()) {
                throw GitHubApiException(res.status.value, "Failed to list directory (${res.status.value})")
            }
            res.bodyOrDefaultOnEmpty<List<ContentItemDto>> { emptyList() }
                .map { item ->
                    TreeEntry(
                        path = item.path,
                        name = item.name,
                        type = if (item.type == "dir") TreeEntryType.TREE else TreeEntryType.BLOB,
                        sha = item.sha,
                        size = item.size
                    )
                }
                .sortedWith(compareBy<TreeEntry> { it.type != TreeEntryType.TREE }.thenBy { it.name.lowercase() })
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun fetchFileContent(
        owner: String,
        repo: String,
        path: String,
        ref: String? = null
    ): FileContent {
        val cleanPath = encodeRepoSubPath(path)
        return conditionalGet(
            cacheKey = "file:${owner.lowercase()}/${repo.lowercase()}:${path.trimStart('/')}:${ref.orEmpty()}",
            url = "${repoPath(owner, repo)}/contents/$cleanPath",
            buildRequest = {
                noCacheHeaders()
                if (!ref.isNullOrBlank()) parameter("ref", ref)
            }
        ) { res ->
            if (!res.status.isSuccess()) {
                throw GitHubApiException(res.status.value, "Failed to fetch file (${res.status.value})")
            }
            val dto = res.body<ContentItemDto>()
            val raw = dto.content
            val decoded = if (raw != null && dto.encoding == "base64") {
                val bytes = Base64.Mime.decode(raw)
                val isLikelyBinary = bytes.take(512).any { it == 0.toByte() }
                if (isLikelyBinary) {
                    return@conditionalGet FileContent(
                        path = dto.path,
                        name = dto.name,
                        size = dto.size,
                        content = "",
                        isBinary = true,
                        htmlUrl = dto.htmlUrl,
                        sha = dto.sha
                    )
                }
                bytes.decodeToString()
            } else {
                raw.orEmpty()
            }
            FileContent(
                path = dto.path,
                name = dto.name,
                size = dto.size,
                content = decoded,
                isBinary = false,
                htmlUrl = dto.htmlUrl,
                sha = dto.sha
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun upsertRepoFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String? = null,
        existingSha: String? = null
    ): String {
        val cleanPath = path.trim().trimStart('/')
        require(cleanPath.isNotEmpty()) { "File path cannot be blank" }
        val encoded = Base64.Default.encode(content.encodeToByteArray())
        val res = httpClient.put("${repoPath(owner, repo)}/contents/${encodeRepoSubPath(cleanPath)}") {
            contentType(ContentType.Application.Json)
            setBody(
                UpsertFileContentRequest(
                    message = message.trim().ifBlank {
                        if (existingSha.isNullOrBlank()) "Create $cleanPath" else "Update $cleanPath"
                    },
                    content = encoded,
                    branch = branch?.trim()?.takeIf { it.isNotEmpty() },
                    sha = existingSha?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to commit file (${res.status.value})")
        }
        invalidateRepoTreeConditionalCaches(owner, repo)
        return res.body<FileMutationResultDto>().commit?.sha.orEmpty()
    }

    suspend fun deleteRepoFile(
        owner: String,
        repo: String,
        path: String,
        sha: String,
        message: String,
        branch: String? = null
    ): String {
        val cleanPath = path.trim().trimStart('/')
        require(cleanPath.isNotEmpty()) { "File path cannot be blank" }
        require(sha.isNotBlank()) { "Existing file SHA is required to delete" }
        val res = httpClient.delete("${repoPath(owner, repo)}/contents/${encodeRepoSubPath(cleanPath)}") {
            contentType(ContentType.Application.Json)
            setBody(
                DeleteFileContentRequest(
                    message = message.trim().ifBlank { "Delete $cleanPath" },
                    sha = sha,
                    branch = branch?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to delete file (${res.status.value})")
        }
        invalidateRepoTreeConditionalCaches(owner, repo)
        return res.body<FileMutationResultDto>().commit?.sha.orEmpty()
    }

    private suspend fun invalidateRepoTreeConditionalCaches(owner: String, repo: String) {
        val slug = "${owner.lowercase()}/${repo.lowercase()}:"
        invalidateConditionalCachePrefix("dir:$slug")
        invalidateConditionalCachePrefix("file:$slug")
        invalidateConditionalCachePrefix("readme:$slug")
        invalidateConditionalCachePrefix("commits:$slug")
    }

    suspend fun fetchGists(
        starred: Boolean = false,
        userLogin: String? = null,
        page: Int = 1,
        perPage: Int = 30
    ): List<GistSummary> {
        val url = when {
            starred -> "$baseUrl/gists/starred"
            !userLogin.isNullOrBlank() -> "$baseUrl/users/${userLogin.trim().encodeURLPathPart()}/gists"
            else -> "$baseUrl/gists"
        }
        val res = httpClient.get(url) {
            parameter("page", page)
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw gistApiException(res.status.value, "load gists")
        }
        return res.bodyOrDefaultOnEmpty<List<RestGistDto>> { emptyList() }.map { it.toGistSummary() }
    }

    /** Checks whether the viewer has starred [encodedId], propagating coroutine cancellation. */
    private suspend fun isGistStarred(encodedId: String): Boolean = try {
        httpClient.get("$baseUrl/gists/$encodedId/star").status.value == 204
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        false
    }

    suspend fun fetchGistDetail(gistId: String): GistDetail = coroutineScope {
        val encodedId = normalizeGistId(gistId).encodeURLPathPart()
        val detailDeferred = async {
            val res = httpClient.get("$baseUrl/gists/$encodedId")
            if (!res.status.isSuccess()) {
                throw gistApiException(res.status.value, "load gist")
            }
            res.body<RestGistDto>()
        }
        val starredDeferred = async { isGistStarred(encodedId) }
        val dto = detailDeferred.await()
        val isStarred = starredDeferred.await()
        dto.toGistDetail(viewerHasStarred = isStarred)
    }

    suspend fun createGist(
        description: String,
        isPublic: Boolean,
        files: Map<String, String>
    ): GistDetail {
        require(files.isNotEmpty()) { "At least one file is required" }
        val payloadFiles = files.entries.associate { (name, body) ->
            name.trim().ifBlank { "snippet.txt" } to CreateOrUpdateGistFileRequest(content = body)
        }
        val res = httpClient.post("$baseUrl/gists") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateGistRequest(
                    description = description.trim(),
                    public = isPublic,
                    files = payloadFiles
                )
            )
        }
        if (!res.status.isSuccess()) {
            throw gistApiException(res.status.value, "create gist")
        }
        return res.body<RestGistDto>().toGistDetail(viewerHasStarred = false)
    }

    suspend fun updateGist(
        gistId: String,
        description: String? = null,
        updatedFiles: Map<String, String?>
    ): GistDetail {
        val encodedId = normalizeGistId(gistId).encodeURLPathPart()
        val payloadFiles = updatedFiles.mapValues { (_, body) ->
            body?.let { CreateOrUpdateGistFileRequest(content = it) }
        }
        val res = httpClient.patch("$baseUrl/gists/$encodedId") {
            contentType(ContentType.Application.Json)
            setBody(
                UpdateGistRequest(
                    description = description?.trim(),
                    files = payloadFiles
                )
            )
        }
        if (!res.status.isSuccess()) {
            throw gistApiException(res.status.value, "update gist")
        }
        val dto = res.body<RestGistDto>()
        val isStarred = isGistStarred(encodedId)
        return dto.toGistDetail(viewerHasStarred = isStarred)
    }

    suspend fun setGistStarred(gistId: String, starred: Boolean) {
        val encodedId = normalizeGistId(gistId).encodeURLPathPart()
        val url = "$baseUrl/gists/$encodedId/star"
        val res = if (starred) {
            httpClient.put(url) {
                header(HttpHeaders.ContentLength, "0")
            }
        } else {
            httpClient.delete(url)
        }
        if (!res.status.isSuccess()) {
            throw gistApiException(res.status.value, "update gist star")
        }
    }

    suspend fun deleteGist(gistId: String) {
        val encodedId = normalizeGistId(gistId).encodeURLPathPart()
        val res = httpClient.delete("$baseUrl/gists/$encodedId")
        if (!res.status.isSuccess()) {
            throw gistApiException(res.status.value, "delete gist")
        }
    }

    private fun normalizeGistId(rawId: String): String =
        rawId.trim().trimEnd('/').substringAfterLast('/')

    private fun gistApiException(statusCode: Int, action: String): GitHubApiException {
        val message = if (statusCode == 404) {
            "Failed to $action (404). Your token may lack the 'gist' scope — sign out and sign in again (or add 'gist' scope)."
        } else {
            "Failed to $action ($statusCode)"
        }
        return GitHubApiException(statusCode, message)
    }

    private fun RestGistDto.toGistSummary(): GistSummary = GistSummary(
        id = id,
        description = description.orEmpty(),
        isPublic = public,
        htmlUrl = htmlUrl,
        ownerLogin = owner?.login.orEmpty(),
        ownerAvatarUrl = owner?.avatarUrl.orEmpty(),
        files = files.entries.map { (key, fileDto) ->
            GistFile(
                filename = fileDto.filename.ifBlank { key },
                language = fileDto.language,
                rawUrl = fileDto.rawUrl,
                size = fileDto.size,
                content = fileDto.content.orEmpty()
            )
        },
        commentsCount = comments,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun RestGistDto.toGistDetail(viewerHasStarred: Boolean): GistDetail {
        val summary = toGistSummary()
        return GistDetail(
            summary = summary,
            files = summary.files,
            viewerHasStarred = viewerHasStarred
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun fetchReadme(owner: String, repo: String, ref: String? = null): String? = conditionalGet(
        cacheKey = "readme:${owner.lowercase()}/${repo.lowercase()}:${ref.orEmpty()}",
        url = "${repoPath(owner, repo)}/readme",
        buildRequest = {
            noCacheHeaders()
            if (!ref.isNullOrBlank()) parameter("ref", ref)
        }
    ) { res ->
        if (!res.status.isSuccess()) null
        else {
            val dto = res.bodyOrDefaultOnEmpty<ContentItemDto?> { null }
            if (dto == null) null
            else if (dto.content != null && dto.encoding == "base64") {
                runCatching { Base64.Mime.decode(dto.content).decodeToString() }.getOrNull()
            } else {
                dto.content
            }
        }
    }

    suspend fun setStarred(owner: String, repo: String, starred: Boolean) {
        val url = "$baseUrl/user/starred/$owner/$repo"
        val res = if (starred) {
            httpClient.put(url) {
                header(HttpHeaders.ContentLength, "0")
            }
        } else {
            httpClient.delete(url)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update star (${res.status.value})")
        }
        invalidateConditionalCachePrefix("viewer_repos:")
        invalidateConditionalCachePrefix("starred_repos:")
    }

    suspend fun fetchViewerRepositoriesRest(perPage: Int = 40): List<RepoSummary> = conditionalGet(
        cacheKey = "viewer_repos:$perPage",
        url = "$baseUrl/user/repos",
        buildRequest = {
            parameter("sort", "updated")
            parameter("direction", "desc")
            parameter("per_page", perPage)
        }
    ) { res ->
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load repositories (${res.status.value})")
        }
        res.bodyOrDefaultOnEmpty<List<RestRepoDto>> { emptyList() }.map { it.toRepoSummary() }
    }

    suspend fun fetchStarredRepositoriesRest(perPage: Int = 40): List<RepoSummary> = conditionalGet(
        cacheKey = "starred_repos:$perPage",
        url = "$baseUrl/user/starred",
        buildRequest = {
            parameter("sort", "created")
            parameter("direction", "desc")
            parameter("per_page", perPage)
        }
    ) { res ->
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load starred repositories (${res.status.value})")
        }
        res.bodyOrDefaultOnEmpty<List<RestRepoDto>> { emptyList() }.map { it.toRepoSummary(viewerHasStarred = true) }
    }

    suspend fun searchRepositoriesRest(query: String, perPage: Int = 30): List<RepoSummary> {
        val res = httpClient.get("$baseUrl/search/repositories") {
            parameter("q", query)
            parameter("sort", "stars")
            parameter("order", "desc")
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Repository search failed (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<RestRepoSearchResponse> { RestRepoSearchResponse() }.items.map { it.toRepoSummary() }
    }

    suspend fun fetchReceivedEvents(login: String, page: Int = 1, perPage: Int = 30): List<ActivityEvent> = conditionalGet(
        cacheKey = "events:received:${login.lowercase()}:$page:$perPage",
        url = "$baseUrl/users/$login/received_events",
        buildRequest = {
            parameter("page", page)
            parameter("per_page", perPage)
        }
    ) { res ->
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load activity feed (${res.status.value})")
        }
        res.bodyOrDefaultOnEmpty<List<RestEventDto>> { emptyList() }.map(::parseActivityEvent)
    }

    suspend fun fetchUserEvents(login: String, page: Int = 1, perPage: Int = 30): List<ActivityEvent> = conditionalGet(
        cacheKey = "events:user:${login.lowercase()}:$page:$perPage",
        url = "$baseUrl/users/$login/events",
        buildRequest = {
            parameter("page", page)
            parameter("per_page", perPage)
        }
    ) { res ->
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load user events (${res.status.value})")
        }
        res.bodyOrDefaultOnEmpty<List<RestEventDto>> { emptyList() }.map(::parseActivityEvent)
    }

    suspend fun fetchUserProfileRest(login: String): UserProfile {
        val encodedLogin = login.trim().encodeURLPathPart()
        val res = httpClient.get("$baseUrl/users/$encodedLogin")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load user @$login (${res.status.value})")
        }
        val dto = res.body<RestUserProfileDto>()
        val isOrg = dto.type.equals("Organization", ignoreCase = true)
        val isFollowing = if (!isOrg) checkViewerFollowing(dto.login) else false
        return UserProfile(
            login = dto.login,
            name = dto.name,
            avatarUrl = dto.avatarUrl,
            bio = dto.bio,
            company = dto.company,
            location = dto.location,
            blogUrl = dto.blog?.takeIf { it.isNotBlank() },
            twitterUsername = dto.twitterUsername?.takeIf { it.isNotBlank() },
            email = dto.email?.takeIf { it.isNotBlank() },
            followersCount = dto.followers,
            followingCount = dto.following,
            publicReposCount = dto.publicRepos,
            publicGistsCount = dto.publicGists,
            isOrganization = isOrg,
            viewerIsFollowing = isFollowing,
            createdAt = dto.createdAt,
            htmlUrl = dto.htmlUrl
        )
    }

    suspend fun checkViewerFollowing(login: String): Boolean {
        val cleanLogin = login.trim().removePrefix("@")
        val encodedLogin = cleanLogin.encodeURLPathPart()
        val res = httpClient.get("$baseUrl/user/following/$encodedLogin")
        return res.status.value == 204
    }

    suspend fun setFollowingUser(login: String, follow: Boolean) {
        val cleanLogin = login.trim().removePrefix("@")
        val encodedLogin = cleanLogin.encodeURLPathPart()
        val url = "$baseUrl/user/following/$encodedLogin"
        val res = if (follow) {
            httpClient.put(url) {
                header(HttpHeaders.ContentLength, "0")
            }
        } else {
            httpClient.delete(url)
        }
        if (!res.status.isSuccess()) {
            val action = if (follow) "follow" else "unfollow"
            val msg = if (res.status.value == 404) {
                "Failed to $action @$cleanLogin (404). Your token may lack the 'user:follow' scope — sign out and sign in again (or add 'user:follow' scope)."
            } else {
                "Failed to update follow state (${res.status.value})"
            }
            throw GitHubApiException(res.status.value, msg)
        }
    }

    suspend fun fetchUserRepositories(login: String, isOrganization: Boolean = false, perPage: Int = 40): List<RepoSummary> {
        val encodedLogin = login.trim().encodeURLPathPart()
        val endpoint = if (isOrganization) {
            "$baseUrl/orgs/$encodedLogin/repos"
        } else {
            "$baseUrl/users/$encodedLogin/repos"
        }
        val res = httpClient.get(endpoint) {
            parameter("sort", "updated")
            parameter("direction", "desc")
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load repositories for @$login (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<List<RestRepoDto>> { emptyList() }.map { it.toRepoSummary() }
    }

    suspend fun fetchUserStarredRepositories(login: String, perPage: Int = 30): List<RepoSummary> {
        val encodedLogin = login.trim().encodeURLPathPart()
        val res = httpClient.get("$baseUrl/users/$encodedLogin/starred") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load starred repos for @$login (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<List<RestRepoDto>> { emptyList() }.map { it.toRepoSummary() }
    }

    suspend fun fetchUserOrganizations(login: String): List<OrganizationSummary> {
        val encodedLogin = login.trim().encodeURLPathPart()
        val res = httpClient.get("$baseUrl/users/$encodedLogin/orgs") {
            parameter("per_page", 30)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load organizations (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<List<RestOrgDto>> { emptyList() }.map {
            OrganizationSummary(login = it.login, avatarUrl = it.avatarUrl, description = it.description)
        }
    }

    suspend fun fetchUserFollowers(login: String, perPage: Int = 40): List<UserSummary> {
        val encodedLogin = login.trim().encodeURLPathPart()
        val res = httpClient.get("$baseUrl/users/$encodedLogin/followers") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load followers (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<List<RestUserFollowItemDto>> { emptyList() }.map {
            UserSummary(login = it.login, avatarUrl = it.avatarUrl, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun fetchUserFollowing(login: String, perPage: Int = 40): List<UserSummary> {
        val encodedLogin = login.trim().encodeURLPathPart()
        val res = httpClient.get("$baseUrl/users/$encodedLogin/following") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load following (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<List<RestUserFollowItemDto>> { emptyList() }.map {
            UserSummary(login = it.login, avatarUrl = it.avatarUrl, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun searchIssuesAndPullsRest(query: String, perPage: Int = 30): List<IssueOrPullSummary> {
        val res = httpClient.get("$baseUrl/search/issues") {
            parameter("q", query)
            parameter("sort", "updated")
            parameter("order", "desc")
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Issue search failed (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<RestIssueSearchResponse> { RestIssueSearchResponse() }.items.map { item ->
            val repoParts = item.repositoryUrl.removePrefix("$baseUrl/repos/").split('/')
            val repoOwner = repoParts.getOrNull(0).orEmpty()
            val repoName = repoParts.getOrNull(1).orEmpty()
            val repoFull = if (repoOwner.isNotBlank() && repoName.isNotBlank()) "$repoOwner/$repoName" else ""
            IssueOrPullSummary(
                id = item.nodeId.ifBlank { "issue_${item.id}_${item.number}" },
                number = item.number,
                title = item.title,
                kind = if (item.pullRequest != null) IssueOrPullKind.PULL_REQUEST else IssueOrPullKind.ISSUE,
                state = if (item.state.equals("closed", ignoreCase = true)) IssueOrPullState.CLOSED else IssueOrPullState.OPEN,
                isDraft = item.draft,
                repoFullName = repoFull,
                repoOwner = repoOwner,
                repoName = repoName,
                author = UserSummary(
                    login = item.user?.login ?: "ghost",
                    avatarUrl = item.user?.avatarUrl.orEmpty()
                ),
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
                commentCount = item.comments,
                labels = item.labels.map { LabelTag(it.name, it.color, it.description) }
            )
        }
    }

    suspend fun searchCodeRest(query: String, perPage: Int = 30): List<CodeSearchHit> {
        val res = httpClient.get("$baseUrl/search/code") {
            header(HttpHeaders.Accept, "application/vnd.github.text-match+json")
            parameter("q", query)
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Code search failed (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<RestCodeSearchResponse> { RestCodeSearchResponse() }.items.map { item ->
            val full = item.repository.fullName
            val owner = item.repository.owner?.login ?: full.substringBefore('/', "")
            val name = item.repository.name.ifBlank { full.substringAfter('/', "") }
            CodeSearchHit(
                name = item.name,
                path = item.path,
                sha = item.sha,
                repoFullName = full,
                repoOwner = owner,
                repoName = name,
                htmlUrl = item.htmlUrl,
                fragments = item.textMatches.mapNotNull { it.fragment.takeIf(String::isNotBlank) }
            )
        }
    }

    suspend fun searchUsersRest(query: String, perPage: Int = 30): List<UserSummary> {
        val res = httpClient.get("$baseUrl/search/users") {
            parameter("q", query)
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "User search failed (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<RestUserSearchResponse> { RestUserSearchResponse() }.items.map {
            UserSummary(login = it.login, avatarUrl = it.avatarUrl, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun fetchTrendingRepositories(language: String? = null, perPage: Int = 25): List<RepoSummary> {
        val langPart = language?.trim()?.takeIf { it.isNotEmpty() }?.let { " language:$it" } ?: ""
        return searchRepositoriesRest(query = "stars:>500$langPart", perPage = perPage)
    }

    suspend fun createRepositoryRest(
        name: String,
        description: String = "",
        isPrivate: Boolean = false,
        autoInit: Boolean = true
    ): RepoSummary {
        val res = httpClient.post("$baseUrl/user/repos") {
            contentType(ContentType.Application.Json)
            setBody(CreateRepoRequest(name = name.trim(), description = description.trim(), private = isPrivate, autoInit = autoInit))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create repository (${res.status.value})")
        }
        return res.body<RestRepoDto>().toRepoSummary()
    }

    suspend fun forkRepositoryRest(owner: String, repo: String): RepoSummary {
        val res = httpClient.post("${repoPath(owner, repo)}/forks") {
            header(HttpHeaders.ContentLength, "0")
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to fork repository (${res.status.value})")
        }
        return res.body<RestRepoDto>().toRepoSummary()
    }

    suspend fun fetchRepoSubscription(owner: String, repo: String): Boolean {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/subscription")
        if (res.status.value == 404) return false
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to read watch status (${res.status.value})")
        }
        val dto = res.body<SubscriptionResponse>()
        return dto.subscribed && !dto.ignored
    }

    suspend fun setRepoSubscription(owner: String, repo: String, subscribed: Boolean) {
        val url = "$baseUrl/repos/$owner/$repo/subscription"
        val res = if (subscribed) {
            httpClient.put(url) {
                contentType(ContentType.Application.Json)
                setBody(SubscriptionRequest(subscribed = true, ignored = false))
            }
        } else {
            httpClient.delete(url)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update watch status (${res.status.value})")
        }
    }

    suspend fun fetchRepoStargazers(owner: String, repo: String, perPage: Int = 40): List<UserSummary> {
        val (cleanOwner, cleanRepo) = normalizeOwnerRepo(owner, repo)
        val res = httpClient.get("$baseUrl/repos/${cleanOwner.encodeURLPathPart()}/${cleanRepo.encodeURLPathPart()}/stargazers") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load stargazers (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<List<RestUserFollowItemDto>> { emptyList() }.map {
            UserSummary(login = it.login, avatarUrl = it.avatarUrl, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun fetchRepoForks(
        owner: String,
        repo: String,
        sort: String = "stargazers",
        perPage: Int = 30
    ): List<RepoSummary> {
        val normalizedSort = when (sort.lowercase()) {
            "newest" -> "newest"
            "oldest" -> "oldest"
            "watchers" -> "watchers"
            else -> "stargazers"
        }
        val res = httpClient.get("${repoPath(owner, repo)}/forks") {
            noCacheHeaders()
            parameter("sort", normalizedSort)
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load forks (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<List<RestRepoDto>> { emptyList() }.map { it.toRepoSummary() }
    }

    suspend fun fetchRepoContributors(owner: String, repo: String, perPage: Int = 30): List<UserSummary> {
        val (cleanOwner, cleanRepo) = normalizeOwnerRepo(owner, repo)
        val res = httpClient.get("$baseUrl/repos/${cleanOwner.encodeURLPathPart()}/${cleanRepo.encodeURLPathPart()}/contributors") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load contributors (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<List<RestUserFollowItemDto>> { emptyList() }.map {
            UserSummary(login = it.login, avatarUrl = it.avatarUrl, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun fetchRepoLanguages(owner: String, repo: String): Map<String, Long> {
        val (cleanOwner, cleanRepo) = normalizeOwnerRepo(owner, repo)
        val res = httpClient.get("$baseUrl/repos/${cleanOwner.encodeURLPathPart()}/${cleanRepo.encodeURLPathPart()}/languages")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load languages (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<Map<String, Long>> { emptyMap() }
    }

    /**
     * Fetches weekly commit counts over the past year.
     * GitHub returns HTTP 202 while precomputing cached statistics; we surface `computing = true` instead of throwing.
     */
    suspend fun fetchRepoCommitActivity(owner: String, repo: String): Pair<List<WeeklyCommitActivity>, Boolean> {
        val (cleanOwner, cleanRepo) = normalizeOwnerRepo(owner, repo)
        val res = httpClient.get("$baseUrl/repos/${cleanOwner.encodeURLPathPart()}/${cleanRepo.encodeURLPathPart()}/stats/commit_activity")
        if (res.status.value == 202) return emptyList<WeeklyCommitActivity>() to true
        if (res.status.value == 204 || res.status.value == 404) return emptyList<WeeklyCommitActivity>() to false
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load commit activity (${res.status.value})")
        }
        val list = res.bodyOrDefaultOnEmpty<List<RestCommitActivityDto>> { emptyList() }.map {
            WeeklyCommitActivity(weekEpochSeconds = it.week, total = it.total, days = it.days)
        }
        return list to false
    }

    /**
     * Fetches contributor commit & line-change stats.
     * Handles HTTP 202 computing responses gracefully so Insights UI can show a warm-up banner.
     */
    suspend fun fetchRepoContributorStats(owner: String, repo: String): Pair<List<ContributorStat>, Boolean> {
        val (cleanOwner, cleanRepo) = normalizeOwnerRepo(owner, repo)
        val res = httpClient.get("$baseUrl/repos/${cleanOwner.encodeURLPathPart()}/${cleanRepo.encodeURLPathPart()}/stats/contributors")
        if (res.status.value == 202) return emptyList<ContributorStat>() to true
        if (res.status.value == 204 || res.status.value == 404) return emptyList<ContributorStat>() to false
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load contributor stats (${res.status.value})")
        }
        val stats = res.bodyOrDefaultOnEmpty<List<RestContributorStatsDto>> { emptyList() }
            .mapNotNull { dto ->
                val author = dto.author ?: return@mapNotNull null
                if (author.login.isBlank()) return@mapNotNull null
                ContributorStat(
                    login = author.login,
                    avatarUrl = author.avatarUrl,
                    htmlUrl = author.htmlUrl,
                    totalCommits = dto.total,
                    additions = dto.weeks.sumOf { it.a },
                    deletions = dto.weeks.sumOf { it.d }
                )
            }
            .sortedByDescending { it.totalCommits }
            .take(15)
        return stats to false
    }

    private fun repoPath(owner: String, repo: String): String {
        val (cleanOwner, cleanRepo) = normalizeOwnerRepo(owner, repo)
        return "$baseUrl/repos/${cleanOwner.encodeURLPathPart()}/${cleanRepo.encodeURLPathPart()}"
    }

    private fun encodeRepoSubPath(path: String): String =
        path.trimStart('/')
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { it.encodeURLPathPart() }

    private fun encodeCompareRef(ref: String): String {
        val trimmed = ref.trim()
        val colonIdx = trimmed.indexOf(':')
        return if (colonIdx > 0) {
            val refOwner = trimmed.substring(0, colonIdx).trim().encodeURLPathPart()
            val refBranch = trimmed.substring(colonIdx + 1).trim().encodeURLPathPart()
            "$refOwner:$refBranch"
        } else {
            trimmed.encodeURLPathPart()
        }
    }

    private fun HttpRequestBuilder.noCacheHeaders() {
        header(HttpHeaders.CacheControl, "no-cache")
        header(HttpHeaders.Pragma, "no-cache")
    }

    private fun normalizeOwnerRepo(owner: String, repo: String): Pair<String, String> {
        val trimmedOwner = owner.trim()
        val trimmedRepo = repo.trim()
        if (trimmedOwner.isBlank() && '/' in trimmedRepo) {
            return trimmedRepo.substringBefore('/').trim() to trimmedRepo.substringAfter('/').trim()
        }
        return trimmedOwner to trimmedRepo.substringAfterLast('/')
    }

    suspend fun fetchRepoLabels(owner: String, repo: String, perPage: Int = 100): List<LabelTag> {
        val res = httpClient.get("${repoPath(owner, repo)}/labels") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load labels (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<List<RestSearchLabelDto>> { emptyList() }.map {
            LabelTag(name = it.name, colorHex = it.color, description = it.description)
        }
    }

    suspend fun createRepoLabel(
        owner: String,
        repo: String,
        name: String,
        colorHex: String,
        description: String = ""
    ): LabelTag {
        val cleanColor = colorHex.removePrefix("#").trim().take(6).ifBlank { "6E40C9" }
        val res = httpClient.post("${repoPath(owner, repo)}/labels") {
            contentType(ContentType.Application.Json)
            setBody(CreateLabelRequest(name = name.trim(), color = cleanColor, description = description.trim()))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create label (${res.status.value})")
        }
        val dto = res.body<RestSearchLabelDto>()
        return LabelTag(name = dto.name, colorHex = dto.color, description = dto.description)
    }

    suspend fun updateRepoLabel(
        owner: String,
        repo: String,
        currentName: String,
        newName: String,
        colorHex: String,
        description: String = ""
    ): LabelTag {
        val cleanColor = colorHex.removePrefix("#").trim().take(6).ifBlank { "6E40C9" }
        val payload = buildJsonObject {
            put("new_name", newName.trim())
            put("color", cleanColor)
            put("description", description.trim())
        }
        val encodedName = currentName.trim().encodeURLPathPart()
        val res = httpClient.patch("${repoPath(owner, repo)}/labels/$encodedName") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update label (${res.status.value})")
        }
        val dto = res.body<RestSearchLabelDto>()
        return LabelTag(name = dto.name, colorHex = dto.color, description = dto.description)
    }

    suspend fun deleteRepoLabel(owner: String, repo: String, name: String) {
        val encodedName = name.trim().encodeURLPathPart()
        val res = httpClient.delete("${repoPath(owner, repo)}/labels/$encodedName")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to delete label (${res.status.value})")
        }
    }

    suspend fun fetchRepoMilestones(
        owner: String,
        repo: String,
        state: String = "open",
        perPage: Int = 50
    ): List<MilestoneSummary> {
        val res = httpClient.get("${repoPath(owner, repo)}/milestones") {
            parameter("state", state)
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load milestones (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<List<RestMilestoneDto>> { emptyList() }.map { it.toSummary() }
    }

    suspend fun createRepoMilestone(
        owner: String,
        repo: String,
        title: String,
        state: String = "open",
        description: String? = null,
        dueOn: String? = null
    ): MilestoneSummary {
        val payload = buildJsonObject {
            put("title", title.trim())
            put("state", state)
            if (!description.isNullOrBlank()) {
                put("description", description.trim())
            }
            if (!dueOn.isNullOrBlank()) {
                put("due_on", dueOn.trim())
            }
        }
        val res = httpClient.post("${repoPath(owner, repo)}/milestones") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create milestone (${res.status.value})")
        }
        return res.body<RestMilestoneDto>().toSummary()
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
    ): MilestoneSummary {
        val payload = buildJsonObject {
            if (title != null) put("title", title.trim())
            if (state != null) put("state", state)
            if (description != null) put("description", description.trim())
            if (clearDueOn) {
                put("due_on", JsonNull)
            } else if (!dueOn.isNullOrBlank()) {
                put("due_on", dueOn.trim())
            }
        }
        val res = httpClient.patch("${repoPath(owner, repo)}/milestones/$number") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update milestone (${res.status.value})")
        }
        return res.body<RestMilestoneDto>().toSummary()
    }

    suspend fun deleteRepoMilestone(owner: String, repo: String, number: Int) {
        val res = httpClient.delete("${repoPath(owner, repo)}/milestones/$number")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to delete milestone (${res.status.value})")
        }
    }

    private fun RestMilestoneDto.toSummary(): MilestoneSummary = MilestoneSummary(
        number = number,
        title = title,
        description = description,
        state = state,
        dueOn = dueOn,
        openIssues = openIssues,
        closedIssues = closedIssues
    )

    suspend fun fetchRepoAssignees(owner: String, repo: String, perPage: Int = 50): List<UserSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/assignees") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load assignees (${res.status.value})")
        }
        return res.bodyOrDefaultOnEmpty<List<RestUserFollowItemDto>> { emptyList() }.map {
            UserSummary(login = it.login, avatarUrl = it.avatarUrl, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun updateIssueMetadata(
        owner: String,
        repo: String,
        number: Int,
        labels: List<String>? = null,
        assignees: List<String>? = null,
        milestoneNumber: Int? = null,
        clearMilestone: Boolean = false
    ) {
        val payload = buildJsonObject {
            if (labels != null) {
                put("labels", JsonArray(labels.map { JsonPrimitive(it) }))
            }
            if (assignees != null) {
                put("assignees", JsonArray(assignees.map { JsonPrimitive(it) }))
            }
            if (clearMilestone) {
                put("milestone", JsonNull)
            } else if (milestoneNumber != null) {
                put("milestone", milestoneNumber)
            }
        }
        val res = httpClient.patch("$baseUrl/repos/$owner/$repo/issues/$number") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update metadata (${res.status.value})")
        }
    }

    private fun parseActivityEvent(dto: RestEventDto): ActivityEvent {
        val repoFull = dto.repo.name
        val repoOwner = repoFull.substringBefore('/', "")
        val repoName = repoFull.substringAfter('/', repoFull)
        val payload = dto.payload

        fun pStr(obj: JsonObject?, key: String): String? =
            obj?.get(key)?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.contentOrNull }

        fun pInt(obj: JsonObject?, key: String): Int? =
            obj?.get(key)?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.intOrNull }

        fun pObj(obj: JsonObject?, key: String): JsonObject? = obj?.get(key) as? JsonObject
        fun pArr(obj: JsonObject?, key: String): JsonArray? = obj?.get(key) as? JsonArray

        var detailTitle: String? = null
        var issueNum: Int? = null
        var commitSha: String? = null
        var branchName: String? = null
        var releaseTag: String? = null

        val kind: ActivityEventKind
        val summary: String

        when (dto.type) {
            "PushEvent" -> {
                kind = ActivityEventKind.PUSH
                val branch = pStr(payload, "ref")?.removePrefix("refs/heads/") ?: "main"
                branchName = branch
                val commits = pArr(payload, "commits")
                val size = pInt(payload, "size") ?: commits?.size ?: 1
                val firstCommit = commits?.firstOrNull() as? JsonObject
                commitSha = pStr(firstCommit, "sha") ?: pStr(payload, "head")
                detailTitle = pStr(firstCommit, "message")?.lineSequence()?.firstOrNull()
                summary = "pushed $size commit${if (size == 1) "" else "s"} to $branch"
            }
            "PullRequestEvent" -> {
                kind = ActivityEventKind.PULL_REQUEST
                val action = pStr(payload, "action") ?: "updated"
                val pr = pObj(payload, "pull_request")
                issueNum = pInt(pr, "number") ?: pInt(payload, "number")
                detailTitle = pStr(pr, "title")
                val merged = pStr(pr, "merged_at") != null
                val verb = if (action == "closed" && merged) "merged" else action
                summary = "$verb pull request${issueNum?.let { " #$it" } ?: ""}"
            }
            "IssuesEvent" -> {
                kind = ActivityEventKind.ISSUES
                val action = pStr(payload, "action") ?: "updated"
                val issue = pObj(payload, "issue")
                issueNum = pInt(issue, "number")
                detailTitle = pStr(issue, "title")
                summary = "$action issue${issueNum?.let { " #$it" } ?: ""}"
            }
            "IssueCommentEvent" -> {
                kind = ActivityEventKind.ISSUE_COMMENT
                val issue = pObj(payload, "issue")
                issueNum = pInt(issue, "number")
                detailTitle = pStr(issue, "title")
                summary = "commented on${issueNum?.let { " #$it" } ?: " thread"}"
            }
            "PullRequestReviewEvent", "PullRequestReviewCommentEvent" -> {
                kind = ActivityEventKind.PULL_REQUEST_REVIEW
                val pr = pObj(payload, "pull_request")
                issueNum = pInt(pr, "number")
                detailTitle = pStr(pr, "title")
                val state = pStr(pObj(payload, "review"), "state")
                summary = when (state?.lowercase()) {
                    "approved" -> "approved pull request${issueNum?.let { " #$it" } ?: ""}"
                    "changes_requested" -> "requested changes on${issueNum?.let { " #$it" } ?: ""}"
                    else -> "reviewed pull request${issueNum?.let { " #$it" } ?: ""}"
                }
            }
            "WatchEvent" -> {
                kind = ActivityEventKind.WATCH
                summary = "starred repository"
            }
            "ForkEvent" -> {
                kind = ActivityEventKind.FORK
                val forkee = pObj(payload, "forkee")
                detailTitle = pStr(forkee, "full_name")
                summary = "forked repository"
            }
            "CreateEvent" -> {
                kind = ActivityEventKind.CREATE
                val refType = pStr(payload, "ref_type") ?: "ref"
                val ref = pStr(payload, "ref")
                branchName = if (refType == "branch") ref else null
                summary = if (ref != null) "created $refType $ref" else "created repository"
            }
            "DeleteEvent" -> {
                kind = ActivityEventKind.DELETE
                val refType = pStr(payload, "ref_type") ?: "ref"
                val ref = pStr(payload, "ref")
                summary = "deleted $refType ${ref.orEmpty()}".trim()
            }
            "ReleaseEvent" -> {
                kind = ActivityEventKind.RELEASE
                val release = pObj(payload, "release")
                releaseTag = pStr(release, "tag_name")
                detailTitle = pStr(release, "name") ?: releaseTag
                val action = pStr(payload, "action") ?: "published"
                summary = "$action release ${releaseTag.orEmpty()}".trim()
            }
            "PublicEvent" -> {
                kind = ActivityEventKind.PUBLIC
                summary = "made repository public"
            }
            "MemberEvent" -> {
                kind = ActivityEventKind.MEMBER
                val memberLogin = pStr(pObj(payload, "member"), "login")
                val action = pStr(payload, "action") ?: "added"
                summary = "$action ${memberLogin?.let { "@$it" } ?: "collaborator"}"
            }
            else -> {
                kind = ActivityEventKind.OTHER
                summary = dto.type.removeSuffix("Event").ifBlank { "activity" }
            }
        }

        return ActivityEvent(
            id = dto.id,
            kind = kind,
            actor = UserSummary(
                login = dto.actor.login,
                avatarUrl = dto.actor.avatarUrl,
                htmlUrl = "https://github.com/${dto.actor.login}"
            ),
            repoFullName = repoFull,
            repoOwner = repoOwner,
            repoName = repoName,
            summaryText = summary,
            detailTitle = detailTitle,
            targetIssueNumber = issueNum,
            commitSha = commitSha,
            branchName = branchName,
            releaseTag = releaseTag,
            createdAt = dto.createdAt
        )
    }

    private fun parseReason(raw: String): NotificationReason = when (raw.lowercase()) {
        "assign" -> NotificationReason.ASSIGN
        "author" -> NotificationReason.AUTHOR
        "comment" -> NotificationReason.COMMENT
        "ci_activity" -> NotificationReason.CI_ACTIVITY
        "invitation" -> NotificationReason.INVITATION
        "manual" -> NotificationReason.MANUAL
        "mention" -> NotificationReason.MENTION
        "review_requested" -> NotificationReason.REVIEW_REQUESTED
        "security_alert" -> NotificationReason.SECURITY_ALERT
        "state_change" -> NotificationReason.STATE_CHANGE
        "subscribed" -> NotificationReason.SUBSCRIBED
        "team_mention" -> NotificationReason.TEAM_MENTION
        else -> NotificationReason.OTHER
    }

    private fun parseSubjectType(raw: String): NotificationSubjectType = when (raw) {
        "Issue" -> NotificationSubjectType.ISSUE
        "PullRequest" -> NotificationSubjectType.PULL_REQUEST
        "Commit" -> NotificationSubjectType.COMMIT
        "Release" -> NotificationSubjectType.RELEASE
        "Discussion" -> NotificationSubjectType.DISCUSSION
        "CheckSuite" -> NotificationSubjectType.CHECK_SUITE
        else -> NotificationSubjectType.UNKNOWN
    }

    private fun parseFileStatus(raw: String): FileChangeStatus = when (raw.lowercase()) {
        "added" -> FileChangeStatus.ADDED
        "removed" -> FileChangeStatus.REMOVED
        "renamed" -> FileChangeStatus.RENAMED
        "copied" -> FileChangeStatus.COPIED
        "changed" -> FileChangeStatus.CHANGED
        else -> FileChangeStatus.MODIFIED
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadMediaAttachment(
        targetOwner: String,
        targetRepo: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        viewerLoginHint: String? = null
    ): UploadedAttachment {
        if (bytes.isEmpty()) throw AttachmentException.Empty()
        if (bytes.size > MAX_ATTACHMENT_BYTES) throw AttachmentException.TooLarge(bytes.size.toLong())

        val cleanOwner = targetOwner.trim()
        val cleanRepo = targetRepo.trim()
        require(cleanOwner.isNotEmpty() && cleanRepo.isNotEmpty()) { "Target repository is required" }

        val targetRepoRes = httpClient.get(repoPath(cleanOwner, cleanRepo))
        if (!targetRepoRes.status.isSuccess()) {
            throw GitHubApiException(
                targetRepoRes.status.value,
                "Failed to load target repository (${targetRepoRes.status.value})"
            )
        }
        val targetDto = targetRepoRes.body<RestRepoDto>()
        val encodedBytes = Base64.Default.encode(bytes)
        val attachmentPath = buildAttachmentRepoPath(cleanOwner, cleanRepo, fileName, mimeType)
        val commitMessage = "Upload attachment ${sanitizeAttachmentFileName(fileName, mimeType)}"

        val resolvedUrl = if (targetDto.private) {
            val perms = targetDto.permissions
            if (perms != null && !perms.admin && !perms.maintain && !perms.push) {
                throw AttachmentException.PrivateRepoPermissionDenied()
            }
            uploadToOrphanBranch(
                owner = cleanOwner,
                repo = cleanRepo,
                path = attachmentPath,
                encodedBase64 = encodedBytes,
                message = commitMessage
            )
            "https://github.com/$cleanOwner/$cleanRepo/blob/$ATTACHMENT_ORPHAN_BRANCH/${encodeRepoSubPath(attachmentPath)}?raw=true"
        } else {
            val viewerLogin = viewerLoginHint?.trim()?.takeIf { it.isNotEmpty() } ?: fetchViewerLoginRest()
            val hostRepo = ensurePublicAttachmentsHostRepo(viewerLogin)
            val result = upsertBinaryFile(
                owner = hostRepo.owner,
                repo = hostRepo.name,
                path = attachmentPath,
                encodedBase64 = encodedBytes,
                message = commitMessage,
                branch = hostRepo.defaultBranch
            )
            result.content?.downloadUrl?.takeIf { it.isNotBlank() }
                ?: "https://raw.githubusercontent.com/${hostRepo.owner}/${hostRepo.name}/${encodeRepoSubPath(hostRepo.defaultBranch)}/${encodeRepoSubPath(attachmentPath)}"
        }

        val displayName = fileName.trim().ifEmpty { sanitizeAttachmentFileName(fileName, mimeType) }
        return UploadedAttachment(
            fileName = displayName,
            mimeType = mimeType,
            downloadUrl = resolvedUrl,
            markdownSnippet = formatAttachmentMarkdown(displayName, mimeType, resolvedUrl)
        )
    }

    private suspend fun fetchViewerLoginRest(): String {
        val res = httpClient.get("$baseUrl/user")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to resolve signed-in user (${res.status.value})")
        }
        return res.body<RestViewerLoginDto>().login.trim().also {
            if (it.isEmpty()) throw IllegalStateException("Signed-in user login is empty")
        }
    }

    private suspend fun ensurePublicAttachmentsHostRepo(viewerLogin: String): RepoSummary {
        val getRes = httpClient.get(repoPath(viewerLogin, ATTACHMENT_HOST_REPO_NAME))
        if (getRes.status.isSuccess()) {
            return getRes.body<RestRepoDto>().toRepoSummary()
        }
        if (getRes.status != HttpStatusCode.NotFound) {
            throw GitHubApiException(
                getRes.status.value,
                "Failed to access attachment host repository (${getRes.status.value})"
            )
        }
        return createRepositoryRest(
            name = ATTACHMENT_HOST_REPO_NAME,
            description = "Media and file attachments uploaded from GitHub Maxxing",
            isPrivate = false,
            autoInit = true
        )
    }

    private suspend fun uploadToOrphanBranch(
        owner: String,
        repo: String,
        path: String,
        encodedBase64: String,
        message: String
    ) {
        val refRes = httpClient.get("${repoPath(owner, repo)}/git/ref/heads/$ATTACHMENT_ORPHAN_BRANCH")
        if (refRes.status.isSuccess()) {
            upsertBinaryFile(owner, repo, path, encodedBase64, message, ATTACHMENT_ORPHAN_BRANCH)
            return
        }
        if (refRes.status == HttpStatusCode.Forbidden) {
            throw AttachmentException.PrivateRepoPermissionDenied()
        }
        if (refRes.status != HttpStatusCode.NotFound) {
            throw GitHubApiException(refRes.status.value, "Failed to inspect attachment branch (${refRes.status.value})")
        }

        val blobRes = httpClient.post("${repoPath(owner, repo)}/git/blobs") {
            contentType(ContentType.Application.Json)
            setBody(CreateGitBlobRequest(content = encodedBase64))
        }
        if (blobRes.status == HttpStatusCode.Forbidden) throw AttachmentException.PrivateRepoPermissionDenied()
        if (!blobRes.status.isSuccess()) {
            throw GitHubApiException(blobRes.status.value, "Failed to upload attachment blob (${blobRes.status.value})")
        }
        val blobSha = blobRes.body<GitShaDto>().sha

        val treeRes = httpClient.post("${repoPath(owner, repo)}/git/trees") {
            contentType(ContentType.Application.Json)
            setBody(CreateGitTreeRequest(tree = listOf(GitTreeEntryRequest(path = path, sha = blobSha))))
        }
        if (!treeRes.status.isSuccess()) {
            throw GitHubApiException(treeRes.status.value, "Failed to create attachment tree (${treeRes.status.value})")
        }
        val treeSha = treeRes.body<GitShaDto>().sha

        val commitRes = httpClient.post("${repoPath(owner, repo)}/git/commits") {
            contentType(ContentType.Application.Json)
            setBody(CreateGitCommitRequest(message = message, tree = treeSha, parents = emptyList()))
        }
        if (!commitRes.status.isSuccess()) {
            throw GitHubApiException(commitRes.status.value, "Failed to commit attachment (${commitRes.status.value})")
        }
        val commitSha = commitRes.body<GitShaDto>().sha

        val createRefRes = httpClient.post("${repoPath(owner, repo)}/git/refs") {
            contentType(ContentType.Application.Json)
            setBody(CreateGitRefRequest(ref = "refs/heads/$ATTACHMENT_ORPHAN_BRANCH", sha = commitSha))
        }
        if (createRefRes.status == HttpStatusCode.UnprocessableEntity) {
            // Concurrent creation: the branch already exists now, so upload normally onto it.
            upsertBinaryFile(owner, repo, path, encodedBase64, message, ATTACHMENT_ORPHAN_BRANCH)
            return
        }
        if (createRefRes.status == HttpStatusCode.Forbidden) throw AttachmentException.PrivateRepoPermissionDenied()
        if (!createRefRes.status.isSuccess()) {
            throw GitHubApiException(
                createRefRes.status.value,
                "Failed to create attachment branch (${createRefRes.status.value})"
            )
        }
    }

    private suspend fun upsertBinaryFile(
        owner: String,
        repo: String,
        path: String,
        encodedBase64: String,
        message: String,
        branch: String
    ): FileMutationResultDto {
        val res = httpClient.put("${repoPath(owner, repo)}/contents/${encodeRepoSubPath(path)}") {
            contentType(ContentType.Application.Json)
            setBody(
                UpsertFileContentRequest(
                    message = message,
                    content = encodedBase64,
                    branch = branch
                )
            )
        }
        if (res.status == HttpStatusCode.Forbidden) {
            throw AttachmentException.PrivateRepoPermissionDenied()
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to upload attachment (${res.status.value})")
        }
        return res.body<FileMutationResultDto>()
    }
}
