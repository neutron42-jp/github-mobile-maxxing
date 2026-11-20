package dev.maxxing.github.model

import kotlinx.serialization.Serializable

@Serializable
enum class IssueOrPullKind {
    ISSUE,
    PULL_REQUEST
}

@Serializable
enum class IssueOrPullState {
    OPEN,
    CLOSED,
    MERGED
}

@Serializable
enum class IssueStateFilter {
    OPEN,
    CLOSED,
    ALL
}

@Serializable
enum class IssueFilterPreset {
    CREATED,
    ASSIGNED,
    REVIEW_REQUESTED,
    MENTIONED
}

@Serializable
enum class MergeMethod(val apiValue: String) {
    SQUASH("squash"),
    MERGE("merge"),
    REBASE("rebase")
}

@Serializable
enum class ReviewEvent(val apiValue: String) {
    APPROVE("APPROVE"),
    REQUEST_CHANGES("REQUEST_CHANGES"),
    COMMENT("COMMENT")
}

@Serializable
data class LabelTag(
    val name: String,
    val colorHex: String,
    val description: String? = null
)

@Serializable
data class MilestoneSummary(
    val number: Int,
    val title: String,
    val description: String? = null,
    val state: String = "open",
    val dueOn: String? = null,
    val openIssues: Int = 0,
    val closedIssues: Int = 0
)

@Serializable
data class CheckRollupSummary(
    val state: String, // SUCCESS, FAILURE, PENDING, EXPECTED, ERROR
    val totalCount: Int = 0,
    val passedCount: Int = 0,
    val failedCount: Int = 0,
    val pendingCount: Int = 0
)

@Serializable
data class IssueOrPullSummary(
    val id: String,
    val number: Int,
    val title: String,
    val kind: IssueOrPullKind,
    val state: IssueOrPullState,
    val isDraft: Boolean = false,
    val repoFullName: String,
    val repoOwner: String,
    val repoName: String,
    val author: UserSummary,
    val createdAt: String,
    val updatedAt: String,
    val commentCount: Int = 0,
    val labels: List<LabelTag> = emptyList(),
    val checkRollup: CheckRollupSummary? = null,
    val reviewDecision: String? = null
)

@Serializable
data class ReactionSummary(
    val content: String, // THUMBS_UP, THUMBS_DOWN, LAUGH, HOORAY, CONFUSED, HEART, ROCKET, EYES
    val count: Int,
    val viewerHasReacted: Boolean
)

sealed interface TimelineEvent {
    val id: String
    val createdAt: String

    data class CommentEvent(
        override val id: String,
        override val createdAt: String,
        val author: UserSummary,
        val body: String,
        val authorAssociation: String? = null,
        val reactions: List<ReactionSummary> = emptyList(),
        val viewerCanUpdate: Boolean = false,
        val viewerCanDelete: Boolean = false
    ) : TimelineEvent

    data class ReviewEventItem(
        override val id: String,
        override val createdAt: String,
        val author: UserSummary,
        val state: String, // APPROVED, CHANGES_REQUESTED, COMMENTED, DISMISSED
        val body: String
    ) : TimelineEvent

    data class CommitEvent(
        override val id: String,
        override val createdAt: String,
        val oidShort: String,
        val messageHeadline: String,
        val authorName: String
    ) : TimelineEvent

    data class StateChangeEvent(
        override val id: String,
        override val createdAt: String,
        val actor: UserSummary?,
        val actionLabel: String // e.g., "closed this", "reopened this", "merged commit ..."
    ) : TimelineEvent
}

@Serializable
data class IssueOrPullDetail(
    val id: String,
    val number: Int,
    val title: String,
    val body: String,
    val kind: IssueOrPullKind,
    val state: IssueOrPullState,
    val isDraft: Boolean = false,
    val repoFullName: String,
    val repoOwner: String,
    val repoName: String,
    val author: UserSummary,
    val createdAt: String,
    val updatedAt: String,
    val labels: List<LabelTag> = emptyList(),
    val assignees: List<UserSummary> = emptyList(),
    val milestone: MilestoneSummary? = null,
    val headRefName: String? = null,
    val baseRefName: String? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changedFilesCount: Int = 0,
    val mergeable: String? = null, // MERGEABLE, CONFLICTING, UNKNOWN
    val mergeStateStatus: String? = null, // CLEAN, BLOCKED, BEHIND, DIRTY, DRAFT, HAS_HOOKS, UNKNOWN, UNSTABLE
    val reviewDecision: String? = null,
    val checkRollup: CheckRollupSummary? = null,
    val htmlUrl: String = "",
    val timeline: List<TimelineEvent> = emptyList(),
    val viewerPermission: String? = null,
    val viewerCanUpdate: Boolean = false,
    val viewerCanCloseOrReopen: Boolean = false,
    val viewerCanMerge: Boolean = false,
    val viewerCanEditMetadata: Boolean = false,
    val headRefOid: String? = null,
    val reactions: List<ReactionSummary> = emptyList(),
    val requestedReviewers: List<UserSummary> = emptyList()
)

@Serializable
enum class FileChangeStatus {
    ADDED,
    MODIFIED,
    REMOVED,
    RENAMED,
    COPIED,
    CHANGED
}

@Serializable
enum class DiffLineKind {
    CONTEXT,
    ADDED,
    REMOVED,
    META
}

@Serializable
data class DiffLine(
    val kind: DiffLineKind,
    val content: String,
    val oldLineNumber: Int?,
    val newLineNumber: Int?
)

@Serializable
data class DiffHunk(
    val header: String,
    val oldStart: Int,
    val oldCount: Int,
    val newStart: Int,
    val newCount: Int,
    val lines: List<DiffLine>
)

@Serializable
data class PullFileChange(
    val filename: String,
    val previousFilename: String? = null,
    val status: FileChangeStatus,
    val additions: Int,
    val deletions: Int,
    val patch: String? = null,
    val hunks: List<DiffHunk> = emptyList()
)

@Serializable
enum class FileViewedState {
    VIEWED,
    UNVIEWED,
    DISMISSED
}

@Serializable
data class PullReviewComment(
    val id: String,
    val path: String,
    val line: Int? = null,
    val originalLine: Int? = null,
    val startLine: Int? = null,
    val diffSide: String? = null,
    val subjectType: String = "LINE",
    val body: String,
    val author: UserSummary,
    val createdAt: String,
    val isPending: Boolean = false,
    val viewerCanUpdate: Boolean = false,
    val viewerCanDelete: Boolean = false
)

@Serializable
data class PullReviewThread(
    val id: String,
    val path: String,
    val line: Int? = null,
    val originalLine: Int? = null,
    val startLine: Int? = null,
    val diffSide: String? = null,
    val subjectType: String = "LINE",
    val isResolved: Boolean = false,
    val isOutdated: Boolean = false,
    val comments: List<PullReviewComment> = emptyList()
)

@Serializable
data class PendingPullReview(
    val id: String,
    val body: String = "",
    val comments: List<PullReviewComment> = emptyList()
)

@Serializable
data class PullReviewSyncState(
    val viewedStates: Map<String, FileViewedState> = emptyMap(),
    val pendingReview: PendingPullReview? = null,
    val threads: List<PullReviewThread> = emptyList()
)
