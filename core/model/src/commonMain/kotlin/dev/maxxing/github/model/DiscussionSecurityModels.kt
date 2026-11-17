package dev.maxxing.github.model

import kotlinx.serialization.Serializable

@Serializable
data class DiscussionCategory(
    val id: String,
    val name: String,
    val emoji: String = "",
    val description: String = "",
    val isAnswerable: Boolean = false
)

@Serializable
data class DiscussionReply(
    val id: String,
    val body: String,
    val authorLogin: String,
    val authorAvatarUrl: String = "",
    val createdAt: String = "",
    val isAnswer: Boolean = false
)

@Serializable
data class DiscussionComment(
    val id: String,
    val body: String,
    val authorLogin: String,
    val authorAvatarUrl: String = "",
    val createdAt: String = "",
    val isAnswer: Boolean = false,
    val upvoteCount: Int = 0,
    val viewerHasUpvoted: Boolean = false,
    val replies: List<DiscussionReply> = emptyList()
)

@Serializable
data class DiscussionSummary(
    val id: String,
    val number: Int,
    val title: String,
    val body: String = "",
    val category: DiscussionCategory,
    val authorLogin: String,
    val authorAvatarUrl: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val commentsCount: Int = 0,
    val upvoteCount: Int = 0,
    val viewerHasUpvoted: Boolean = false,
    val isAnswered: Boolean = false,
    val isClosed: Boolean = false,
    val htmlUrl: String = ""
)

@Serializable
data class DiscussionListData(
    val repositoryId: String = "",
    val categories: List<DiscussionCategory> = emptyList(),
    val discussions: List<DiscussionSummary> = emptyList()
)

@Serializable
data class DiscussionDetail(
    val summary: DiscussionSummary,
    val comments: List<DiscussionComment> = emptyList(),
    val categories: List<DiscussionCategory> = emptyList()
)

@Serializable
data class DependabotAlertSummary(
    val number: Int,
    val state: String,
    val severity: String,
    val packageName: String,
    val ecosystem: String = "",
    val summary: String,
    val vulnerableVersionRange: String = "",
    val patchedVersion: String? = null,
    val createdAt: String = "",
    val htmlUrl: String = ""
)

@Serializable
data class SecurityAdvisorySummary(
    val ghsaId: String,
    val summary: String,
    val severity: String,
    val state: String,
    val publishedAt: String = "",
    val htmlUrl: String = ""
)

@Serializable
data class RepoSecurityOverview(
    val alerts: List<DependabotAlertSummary> = emptyList(),
    val advisories: List<SecurityAdvisorySummary> = emptyList(),
    val alertsAccessNotice: String? = null,
    /** HTTP status of the failed Dependabot alerts request, so the UI can localize [alertsAccessNotice]. */
    val alertsAccessStatusCode: Int? = null
)
