package dev.maxxing.github.model

import kotlinx.serialization.Serializable

@Serializable
data class RepoParentSummary(
    val name: String,
    val owner: String,
    val fullName: String,
    val defaultBranch: String = "main",
    val ownerAvatarUrl: String = ""
)

@Serializable
data class RepoSummary(
    val id: String,
    val name: String,
    val owner: String,
    val fullName: String,
    val description: String? = null,
    val language: String? = null,
    val languageColorHex: String? = null,
    val stargazersCount: Int = 0,
    val forksCount: Int = 0,
    val openIssuesCount: Int = 0,
    val openPullRequestsCount: Int? = null,
    val isPrivate: Boolean = false,
    val isFork: Boolean = false,
    val defaultBranch: String = "main",
    val updatedAt: String = "",
    val ownerAvatarUrl: String = "",
    val viewerHasStarred: Boolean = false,
    val viewerPermission: String? = null,
    val hasKnownPermission: Boolean = false,
    val isArchived: Boolean = false,
    val hasIssuesEnabled: Boolean = true,
    val parent: RepoParentSummary? = null
)

data class RepoCapabilities(
    val viewerPermission: String?,
    val permissionResolved: Boolean,
    val isArchived: Boolean,
    val canPush: Boolean,
    val canTriage: Boolean,
    val canCreateIssue: Boolean,
    val canCommentOrCreateDiscussion: Boolean
)

private val REPO_WRITE_PERMS = setOf("ADMIN", "MAINTAIN", "WRITE")
private val REPO_TRIAGE_PERMS = setOf("ADMIN", "MAINTAIN", "WRITE", "TRIAGE")

fun resolveRepoCapabilities(
    summary: RepoSummary,
    detail: RepoDetail? = null,
    viewerLogin: String? = null
): RepoCapabilities {
    val effective = detail?.summary ?: summary
    val perm = (detail?.summary?.viewerPermission ?: summary.viewerPermission)?.uppercase()
    val resolved = (detail?.summary?.hasKnownPermission == true) || summary.hasKnownPermission || perm != null
    val archived = detail?.summary?.isArchived ?: summary.isArchived
    val issuesEnabled = detail?.summary?.hasIssuesEnabled ?: summary.hasIssuesEnabled

    val hasWrite = if (resolved) {
        perm in REPO_WRITE_PERMS
    } else {
        !viewerLogin.isNullOrBlank() && viewerLogin.equals(effective.owner, ignoreCase = true)
    }
    val hasTriage = if (resolved) {
        perm in REPO_TRIAGE_PERMS
    } else {
        hasWrite
    }

    return RepoCapabilities(
        viewerPermission = perm,
        permissionResolved = resolved,
        isArchived = archived,
        canPush = !archived && hasWrite,
        canTriage = !archived && hasTriage,
        canCreateIssue = !archived && issuesEnabled,
        canCommentOrCreateDiscussion = !archived
    )
}

/** Resolves the canonical repository web URL from its owner, name, and instance [baseUrl]. */
fun resolveRepoWebUrl(
    owner: String,
    repo: String,
    baseUrl: String = AuthSession.GITHUB_WEB_BASE_URL
): String {
    val cleanBase = normalizeInstanceBaseUrl(baseUrl) ?: AuthSession.GITHUB_WEB_BASE_URL
    val cleanOwner = owner.trim().trim('/')
    val cleanRepo = repo.trim().trim('/').substringAfterLast('/')
    return if (cleanOwner.isNotBlank() && cleanRepo.isNotBlank()) {
        "$cleanBase/$cleanOwner/$cleanRepo"
    } else {
        cleanBase
    }
}

fun RepoSummary.webUrl(baseUrl: String = AuthSession.GITHUB_WEB_BASE_URL): String =
    resolveRepoWebUrl(owner, name, baseUrl)

@Serializable
data class RepoDetail(
    val summary: RepoSummary,
    val homepageUrl: String? = null,
    val licenseName: String? = null,
    val topics: List<String> = emptyList(),
    val watchersCount: Int = 0,
    val branches: List<BranchRef> = emptyList()
)

@Serializable
data class BranchRef(
    val name: String,
    val sha: String
)

@Serializable
enum class TreeEntryType {
    TREE,
    BLOB
}

@Serializable
data class TreeEntry(
    val path: String,
    val name: String,
    val type: TreeEntryType,
    val sha: String,
    val size: Long = 0L
)

@Serializable
data class FileContent(
    val path: String,
    val name: String,
    val size: Long,
    val content: String,
    val isBinary: Boolean = false,
    val htmlUrl: String = "",
    val sha: String = ""
)

@Serializable
data class CommitSummary(
    val sha: String,
    val shortSha: String,
    val headline: String,
    val body: String = "",
    val authorName: String,
    val authorLogin: String? = null,
    val authorAvatarUrl: String = "",
    val committedAt: String = "",
    val parentShas: List<String> = emptyList(),
    val verificationReason: String? = null,
    val isVerified: Boolean = false
)

@Serializable
data class CommitDetail(
    val summary: CommitSummary,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changedFilesCount: Int = 0,
    val files: List<PullFileChange> = emptyList(),
    val htmlUrl: String = ""
)

@Serializable
data class TagRef(
    val name: String,
    val commitSha: String
)

@Serializable
data class BranchCompareResult(
    val baseRef: String,
    val headRef: String,
    val status: String,
    val aheadBy: Int = 0,
    val behindBy: Int = 0,
    val totalCommits: Int = 0,
    val commits: List<CommitSummary> = emptyList(),
    val files: List<PullFileChange> = emptyList(),
    val htmlUrl: String = ""
)

@Serializable
data class ReleaseAsset(
    val id: Long,
    val name: String,
    val sizeBytes: Long = 0L,
    val downloadCount: Int = 0,
    val downloadUrl: String = "",
    val contentType: String = ""
)

@Serializable
data class ReleaseSummary(
    val id: Long,
    val tagName: String,
    val name: String,
    val body: String = "",
    val isDraft: Boolean = false,
    val isPrerelease: Boolean = false,
    val authorLogin: String = "",
    val authorAvatarUrl: String = "",
    val publishedAt: String = "",
    val createdAt: String = "",
    val assets: List<ReleaseAsset> = emptyList(),
    val tarballUrl: String = "",
    val zipballUrl: String = "",
    val htmlUrl: String = ""
)
