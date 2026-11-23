package dev.maxxing.github.model

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationSubjectType {
    ISSUE,
    PULL_REQUEST,
    COMMIT,
    RELEASE,
    DISCUSSION,
    CHECK_SUITE,
    UNKNOWN
}

@Serializable
enum class NotificationReason {
    ASSIGN,
    AUTHOR,
    COMMENT,
    CI_ACTIVITY,
    INVITATION,
    MANUAL,
    MENTION,
    REVIEW_REQUESTED,
    SECURITY_ALERT,
    STATE_CHANGE,
    SUBSCRIBED,
    TEAM_MENTION,
    OTHER
}

@Serializable
data class NotificationThread(
    val id: String,
    val unread: Boolean,
    val reason: NotificationReason,
    val updatedAt: String,
    val subjectTitle: String,
    val subjectType: NotificationSubjectType,
    val subjectUrl: String?,
    val latestCommentUrl: String?,
    val repoFullName: String,
    val repoOwner: String,
    val repoName: String,
    val repoOwnerAvatarUrl: String = "",
    val isPrivate: Boolean = false
) {
    val subjectNumber: Int?
        get() = if (subjectType == NotificationSubjectType.ISSUE || subjectType == NotificationSubjectType.PULL_REQUEST) {
            subjectUrl?.substringAfterLast('/')?.toIntOrNull()
        } else {
            null
        }

    val checkSuiteId: Long?
        get() = if (subjectType == NotificationSubjectType.CHECK_SUITE) {
            subjectUrl?.substringAfterLast('/')?.toLongOrNull()
        } else {
            null
        }

    val commitSha: String?
        get() = if (subjectType == NotificationSubjectType.COMMIT) {
            subjectUrl?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    val htmlFallbackUrl: String
        get() = htmlFallbackUrlFor(AuthSession.GITHUB_WEB_BASE_URL)

    fun htmlFallbackUrlFor(baseUrl: String): String {
        val cleanBase = normalizeInstanceBaseUrl(baseUrl) ?: AuthSession.GITHUB_WEB_BASE_URL
        return when (subjectType) {
            NotificationSubjectType.ISSUE -> subjectNumber?.let { "$cleanBase/$repoFullName/issues/$it" }
                ?: "$cleanBase/$repoFullName/issues"
            NotificationSubjectType.PULL_REQUEST -> subjectNumber?.let { "$cleanBase/$repoFullName/pulls/$it" }
                ?.let { if (cleanBase == AuthSession.GITHUB_WEB_BASE_URL) "$cleanBase/$repoFullName/pull/$subjectNumber" else it }
                ?: "$cleanBase/$repoFullName/pulls"
            NotificationSubjectType.CHECK_SUITE -> "$cleanBase/$repoFullName/actions"
            NotificationSubjectType.COMMIT -> commitSha?.let { "$cleanBase/$repoFullName/commit/$it" }
                ?: "$cleanBase/$repoFullName/commits"
            NotificationSubjectType.RELEASE -> "$cleanBase/$repoFullName/releases"
            NotificationSubjectType.DISCUSSION -> "$cleanBase/$repoFullName/discussions"
            NotificationSubjectType.UNKNOWN -> "$cleanBase/$repoFullName"
        }
    }
}
