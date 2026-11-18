package dev.maxxing.github.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.maxxing.github.model.ActivityEvent
import dev.maxxing.github.model.ActivityEventKind
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.DeviceCodeState
import dev.maxxing.github.model.DeviceFlowErrorKind
import dev.maxxing.github.model.DeviceFlowWaitReason
import dev.maxxing.github.model.FileChangeStatus
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import dev.maxxing.github.model.GlobalSearchKind
import dev.maxxing.github.model.HttpStatusError
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.MergeMethod
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.NotificationSubjectType
import dev.maxxing.github.model.RepoSecurityOverview
import dev.maxxing.github.model.ReviewEvent

/**
 * Localized UI strings resolved dynamically via [LocalAppStrings].
 *
 * Every text lives in `core/ui/i18n/strings.csv`; [GeneratedAppStrings] exposes one property per row.
 * This class only holds the logic that picks a row (enums, API values, plural forms, and the English
 * phrases built by the network layer). It contains no UI text of its own.
 */
@Immutable
abstract class AppStrings internal constructor(val language: AppLanguage) : GeneratedAppStrings(language.code) {

    /**
     * English is the language of the GitHub API values and of the messages and activity phrases that the
     * network layer builds, so the English UI shows them as is. Other languages translate them through the
     * rows of the "Errors", "Values from the GitHub API" and "Activity feed" sections of strings.csv.
     */
    private val showsSourceTextAsIs: Boolean get() = language == AppLanguage.DEFAULT

    // Keys whose text depends on a value: one CSV row per form.

    val gistFileCount: (Int) -> String
        get() = { count -> if (count == 1) gistFileCountOne(count) else gistFileCountOther(count) }

    val fileCommittedMessage: (String, String) -> String
        get() = { path, shortSha ->
            if (shortSha.isNotBlank()) fileCommittedMessageWithSha(path, shortSha) else fileCommittedMessageNoSha(path)
        }

    val fileDeletedMessage: (String, String) -> String
        get() = { name, shortSha ->
            if (shortSha.isNotBlank()) fileDeletedMessageWithSha(name, shortSha) else fileDeletedMessageNoSha(name)
        }

    fun fontSelectionLabel(selection: FontSelection): String = when (selection.kind) {
        FontSourceKind.SYSTEM_DEFAULT -> systemDefaultLabel
        FontSourceKind.GENERIC_SANS -> fontGenericSans
        FontSourceKind.GENERIC_SERIF -> fontGenericSerif
        FontSourceKind.GENERIC_MONOSPACE -> fontGenericMonospace
        FontSourceKind.SYSTEM_FAMILY -> selection.displayName.ifBlank { selection.value }.ifBlank { systemDefaultLabel }
        FontSourceKind.CUSTOM_FILE -> selection.displayName.ifBlank {
            selection.value.substringAfterLast('/').substringAfterLast('\\')
        }
    }

    /** Each language is always shown in its own name, whatever the current UI language is. */
    fun languageOptionLabel(language: AppLanguage): String = stringsFor(language).languageName

    fun authMethodLabel(method: AuthMethod): String = when (method) {
        AuthMethod.DEVICE_FLOW -> authMethodDeviceFlow
        AuthMethod.PERSONAL_ACCESS_TOKEN -> authMethodPat
    }

    fun issueFilterPresetLabel(preset: IssueFilterPreset): String = when (preset) {
        IssueFilterPreset.CREATED -> presetCreated
        IssueFilterPreset.ASSIGNED -> presetAssigned
        IssueFilterPreset.REVIEW_REQUESTED -> presetReviews
        IssueFilterPreset.MENTIONED -> presetMentioned
    }

    fun issueStateFilterLabel(filter: IssueStateFilter): String = when (filter) {
        IssueStateFilter.OPEN -> stateOpen
        IssueStateFilter.CLOSED -> stateClosed
        IssueStateFilter.ALL -> stateAll
    }

    fun searchKindLabel(kind: GlobalSearchKind): String = when (kind) {
        GlobalSearchKind.REPOSITORIES -> searchKindRepos
        GlobalSearchKind.ISSUES_PRS -> searchKindIssuesPrs
        GlobalSearchKind.CODE -> searchKindCode
        GlobalSearchKind.USERS -> searchKindUsers
    }

    fun mergeMethodLabel(method: MergeMethod): String = when (method) {
        MergeMethod.SQUASH -> mergeSquashLabel
        MergeMethod.MERGE -> mergeCommitLabel
        MergeMethod.REBASE -> mergeRebaseLabel
    }

    fun mergeMethodShortLabel(method: MergeMethod): String = when (method) {
        MergeMethod.SQUASH -> mergeSquashShortLabel
        MergeMethod.MERGE -> mergeCommitShortLabel
        MergeMethod.REBASE -> mergeRebaseShortLabel
    }

    fun reviewEventLabel(event: ReviewEvent): String = when (event) {
        ReviewEvent.APPROVE -> reviewApproveLabel
        ReviewEvent.REQUEST_CHANGES -> reviewRequestChangesLabel
        ReviewEvent.COMMENT -> reviewCommentLabel
    }

    fun notificationSubjectLabel(type: NotificationSubjectType): String = when (type) {
        NotificationSubjectType.ISSUE -> subjectIssue
        NotificationSubjectType.PULL_REQUEST -> subjectPullRequest
        NotificationSubjectType.COMMIT -> subjectCommit
        NotificationSubjectType.RELEASE -> subjectRelease
        NotificationSubjectType.DISCUSSION -> subjectDiscussion
        NotificationSubjectType.CHECK_SUITE -> subjectCheckSuite
        NotificationSubjectType.UNKNOWN -> subjectNotification
    }

    /** Header badge for an Issue/PR state. */
    fun issueStateBadgeLabel(state: IssueOrPullState, isDraft: Boolean): String = when {
        isDraft -> badgeDraft
        state == IssueOrPullState.OPEN -> badgeOpen
        state == IssueOrPullState.MERGED -> badgeMerged
        else -> badgeClosed
    }

    /** CI pill with a "CI:" prefix, e.g. "CI: SUCCESS". */
    fun formatCheckRollupLabel(state: String): String = checkRollupLabel(formatCheckRollupState(state))

    fun noIssuesOrPrsTitle(state: IssueStateFilter): String = when (state) {
        IssueStateFilter.OPEN -> noIssuesOrPrsTitleOpen
        IssueStateFilter.CLOSED -> noIssuesOrPrsTitleClosed
        IssueStateFilter.ALL -> noIssuesOrPrsTitleAll
    }

    fun noIssuesOrPrsBody(state: IssueStateFilter, preset: IssueFilterPreset): String {
        val presetText = when (preset) {
            IssueFilterPreset.CREATED -> noIssuesPresetCreated
            IssueFilterPreset.ASSIGNED -> noIssuesPresetAssigned
            IssueFilterPreset.REVIEW_REQUESTED -> noIssuesPresetReviewRequested
            IssueFilterPreset.MENTIONED -> noIssuesPresetMentioned
        }
        return when (state) {
            IssueStateFilter.OPEN -> noIssuesOrPrsBodyOpen(presetText)
            IssueStateFilter.CLOSED -> noIssuesOrPrsBodyClosed(presetText)
            IssueStateFilter.ALL -> noIssuesOrPrsBodyAll(presetText)
        }
    }

    fun repoItemsEmptyTitle(isPullRequests: Boolean, state: IssueStateFilter): String = when (state) {
        IssueStateFilter.OPEN -> if (isPullRequests) repoPullsEmptyTitleOpen else repoIssuesEmptyTitleOpen
        IssueStateFilter.CLOSED -> if (isPullRequests) repoPullsEmptyTitleClosed else repoIssuesEmptyTitleClosed
        IssueStateFilter.ALL -> if (isPullRequests) repoPullsEmptyTitleAll else repoIssuesEmptyTitleAll
    }

    fun repoItemsEmptyBody(isPullRequests: Boolean, state: IssueStateFilter): String = when (state) {
        IssueStateFilter.OPEN -> if (isPullRequests) repoPullsEmptyBodyOpen else repoIssuesEmptyBodyOpen
        IssueStateFilter.CLOSED -> if (isPullRequests) repoPullsEmptyBodyClosed else repoIssuesEmptyBodyClosed
        IssueStateFilter.ALL -> if (isPullRequests) repoPullsEmptyBodyAll else repoIssuesEmptyBodyAll
    }

    fun issueMergedMessage(number: Int, method: MergeMethod): String = when (method) {
        MergeMethod.SQUASH -> issueMergedSquash(number)
        MergeMethod.MERGE -> issueMergedMerge(number)
        MergeMethod.REBASE -> issueMergedRebase(number)
    }

    fun mergeConfirmQuestion(baseRef: String?, method: MergeMethod): String =
        if (baseRef != null) mergeConfirmQuestionInto(baseRef, mergeMethodLabel(method))
        else mergeConfirmQuestionIntoBase(mergeMethodLabel(method))

    fun stateChangeConfirmQuestion(willOpen: Boolean, isPullRequest: Boolean): String = when {
        willOpen && isPullRequest -> confirmReopenPullRequest
        willOpen -> confirmReopenIssue
        isPullRequest -> confirmClosePullRequest
        else -> confirmCloseIssue
    }

    fun fileStatusBadge(status: FileChangeStatus): String = when (status) {
        FileChangeStatus.ADDED -> fileStatusAdded
        FileChangeStatus.MODIFIED -> fileStatusModified
        FileChangeStatus.CHANGED -> fileStatusChanged
        FileChangeStatus.REMOVED -> fileStatusRemoved
        FileChangeStatus.RENAMED -> fileStatusRenamed
        FileChangeStatus.COPIED -> fileStatusCopied
    }

    fun formatEditorMetricsLabel(lines: Int, chars: Int): String = editorMetricsLabel(
        if (lines == 1) editorLineCountOne(lines) else editorLineCountOther(lines),
        if (chars == 1) editorCharCountOne(chars) else editorCharCountOther(chars)
    )

    fun notificationReasonLabel(reason: NotificationReason): String = when (reason) {
        NotificationReason.ASSIGN -> notificationReasonAssign
        NotificationReason.AUTHOR -> notificationReasonAuthor
        NotificationReason.COMMENT -> notificationReasonComment
        NotificationReason.CI_ACTIVITY -> notificationReasonCiActivity
        NotificationReason.INVITATION -> notificationReasonInvitation
        NotificationReason.MANUAL -> notificationReasonManual
        NotificationReason.MENTION -> notificationReasonMention
        NotificationReason.REVIEW_REQUESTED -> notificationReasonReviewRequested
        NotificationReason.SECURITY_ALERT -> notificationReasonSecurityAlert
        NotificationReason.STATE_CHANGE -> notificationReasonStateChange
        NotificationReason.SUBSCRIBED -> notificationReasonSubscribed
        NotificationReason.TEAM_MENTION -> notificationReasonTeamMention
        NotificationReason.OTHER -> notificationReasonOther
    }

    // ---------------------------------------------------------------------------------------------
    // Errors and sign-in status. The network layer builds English messages: English shows them as is,
    // other languages lead with their own description of what failed (plus the HTTP status code) and keep
    // the original English text as a detail line.
    // ---------------------------------------------------------------------------------------------

    /**
     * Text to show for a failed operation. [fallback] describes what failed; [notFoundHint] explains
     * a likely cause of an HTTP 404 (e.g. a missing token scope).
     */
    fun errorMessage(error: Throwable, fallback: String, notFoundHint: String? = null): String {
        if (showsSourceTextAsIs) return error.message ?: fallback
        val code = httpStatusOf(error)
        val head = buildString {
            append(if (code != null) errorWithHttpStatus(fallback, code) else fallback)
            when {
                code == 401 -> append(errorSignInAgainSuffix)
                code == 404 && notFoundHint != null -> append(errorHintSuffix(notFoundHint))
            }
        }
        return withDetail(head, error.message, fallback)
    }

    /** Failed job log download. */
    fun jobLogsErrorText(error: Throwable): String {
        if (showsSourceTextAsIs) return errorMessage(error, actionsLogsUnavailable)
        val code = httpStatusOf(error)
        return withDetail(if (code == null) jobLogsErrorNetwork else jobLogsErrorHttp(code), error.message)
    }

    /** Why Dependabot alerts are missing from [overview], or null when they loaded. */
    fun dependabotAlertsNoticeText(overview: RepoSecurityOverview): String? {
        val notice = overview.alertsAccessNotice ?: return null
        if (showsSourceTextAsIs) return notice
        return when (val code = overview.alertsAccessStatusCode) {
            403 -> dependabotNoticeForbidden
            404 -> dependabotNoticeDisabled
            null -> withDetail(dependabotNoticeFailed, notice)
            else -> withDetail(dependabotNoticeFailedHttp(code), notice)
        }
    }

    fun deviceFlowWaitingText(state: DeviceCodeState.AwaitingUser): String {
        if (showsSourceTextAsIs) return state.statusMessage ?: signInWaitingAuth
        return when (state.waitReason) {
            DeviceFlowWaitReason.CODE_ISSUED -> deviceFlowWaitCodeIssued(AppBranding.APP_NAME)
            DeviceFlowWaitReason.AUTHORIZATION_PENDING -> deviceFlowWaitPending
            DeviceFlowWaitReason.NETWORK_RETRY -> deviceFlowWaitNetworkRetry
            DeviceFlowWaitReason.SLOW_DOWN -> deviceFlowWaitSlowDown(state.intervalSeconds)
            null -> signInWaitingAuth
        }
    }

    fun deviceFlowVerifyingText(state: DeviceCodeState.Verifying): String =
        if (showsSourceTextAsIs) state.message else deviceFlowVerifying

    fun deviceFlowErrorText(state: DeviceCodeState.Error): String {
        if (showsSourceTextAsIs) return state.message
        return when (state.kind) {
            DeviceFlowErrorKind.MISSING_CLIENT_ID -> deviceFlowErrorMissingClientId
            DeviceFlowErrorKind.CODE_REQUEST_REJECTED ->
                state.httpStatusCode?.let { deviceFlowErrorRejectedHttp(it) } ?: deviceFlowErrorRejected
            DeviceFlowErrorKind.NETWORK -> withDetail(deviceFlowErrorNetwork, state.message)
            DeviceFlowErrorKind.EXPIRED -> deviceFlowErrorExpired
            DeviceFlowErrorKind.DENIED -> deviceFlowErrorDenied
            DeviceFlowErrorKind.TIMED_OUT -> deviceFlowErrorTimedOut
            DeviceFlowErrorKind.OTHER -> withDetail(
                when (val code = state.oauthErrorCode) {
                    null -> deviceFlowErrorFailed
                    "device_flow_disabled" -> deviceFlowErrorDisabled(code)
                    "incorrect_client_credentials" -> deviceFlowErrorBadClientId(code)
                    else -> deviceFlowErrorFailedWithCode(code)
                },
                state.message,
                state.oauthErrorCode.orEmpty()
            )
        }
    }

    /** Appends the original English message on its own line, unless it adds nothing. */
    private fun withDetail(head: String, detail: String?, vararg redundant: String): String {
        val text = detail?.trim()?.takeIf { it.isNotEmpty() && it !in redundant } ?: return head
        return errorWithDetail(head, text)
    }

    /** The real HTTP status of [error]; null for network failures and for statuses the client made up (GraphQL). */
    private fun httpStatusOf(error: Throwable): Int? =
        (error as? HttpStatusError)?.takeUnless { it.isSyntheticStatus }?.httpStatusCode

    // ---------------------------------------------------------------------------------------------
    // Values from the GitHub API. English shows the value as is (with the casing below); other languages
    // translate the known values and show unknown ones like English does.
    // ---------------------------------------------------------------------------------------------

    /** Bare CI rollup state for list pills (no prefix). */
    fun formatCheckRollupState(state: String): String {
        if (showsSourceTextAsIs) return state
        return when (state.uppercase()) {
            "SUCCESS" -> checkRollupSuccess
            "FAILURE", "ERROR" -> checkRollupFailure
            "PENDING" -> checkRollupPending
            "EXPECTED" -> checkRollupExpected
            else -> state
        }
    }

    fun formatCheckConclusionLabel(label: String): String {
        if (showsSourceTextAsIs) return label.uppercase()
        return when (label.uppercase()) {
            "SUCCESS" -> checkConclusionSuccess
            "FAILURE" -> checkConclusionFailure
            "TIMED_OUT" -> checkConclusionTimedOut
            "CANCELLED" -> checkConclusionCancelled
            "SKIPPED" -> checkConclusionSkipped
            "NEUTRAL" -> checkConclusionNeutral
            "ACTION_REQUIRED" -> checkConclusionActionRequired
            "STALE" -> checkConclusionStale
            "QUEUED" -> checkConclusionQueued
            "PENDING", "WAITING", "REQUESTED" -> checkConclusionPending
            "IN_PROGRESS" -> checkConclusionInProgress
            "COMPLETED" -> checkConclusionCompleted
            else -> label.uppercase()
        }
    }

    /** Check/run status as plain text (descriptions and accessibility labels). */
    fun formatCheckStatusText(status: String): String =
        if (showsSourceTextAsIs) status else formatCheckConclusionLabel(status)

    fun formatCompareStatusLabel(status: String): String {
        if (showsSourceTextAsIs) return status.uppercase()
        return when (status.lowercase()) {
            "ahead" -> compareStatusAhead
            "behind" -> compareStatusBehind
            "diverged" -> compareStatusDiverged
            "identical" -> compareStatusIdentical
            else -> status.uppercase()
        }
    }

    fun formatReviewStateLabel(state: String): String {
        if (showsSourceTextAsIs) return state.replace('_', ' ')
        return when (state.uppercase()) {
            "APPROVED" -> reviewStateApproved
            "CHANGES_REQUESTED" -> reviewStateChangesRequested
            "COMMENTED" -> reviewStateCommented
            "REVIEW_REQUIRED" -> reviewStateReviewRequired
            "DISMISSED" -> reviewStateDismissed
            "PENDING" -> reviewStatePending
            else -> state.replace('_', ' ')
        }
    }

    fun formatAuthorAssociationLabel(association: String): String {
        if (showsSourceTextAsIs) return association
        return when (association.uppercase()) {
            "OWNER" -> authorAssociationOwner
            "MEMBER" -> authorAssociationMember
            "COLLABORATOR" -> authorAssociationCollaborator
            "CONTRIBUTOR" -> authorAssociationContributor
            "FIRST_TIME_CONTRIBUTOR" -> authorAssociationFirstTimeContributor
            "FIRST_TIMER" -> authorAssociationFirstTimer
            "MANNEQUIN" -> authorAssociationMannequin
            else -> association
        }
    }

    fun formatReactionLabel(contentKey: String): String {
        if (showsSourceTextAsIs) return contentKey.lowercase().replace('_', ' ')
        return when (contentKey.uppercase()) {
            "THUMBS_UP" -> reactionThumbsUp
            "THUMBS_DOWN" -> reactionThumbsDown
            "LAUGH" -> reactionLaugh
            "HOORAY" -> reactionHooray
            "CONFUSED" -> reactionConfused
            "HEART" -> reactionHeart
            "ROCKET" -> reactionRocket
            "EYES" -> reactionEyes
            else -> contentKey.lowercase().replace('_', ' ')
        }
    }

    fun formatSeverityLabel(severity: String): String {
        if (showsSourceTextAsIs) return severity.uppercase()
        return when (severity.lowercase()) {
            "critical" -> severityCritical
            "high" -> severityHigh
            "medium", "moderate" -> severityMedium
            "low" -> severityLow
            else -> severity.uppercase()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Timeline and activity lines. The network layer builds English phrases (see GitHubGraphQlService
    // timeline parsing and GitHubRestService.parseActivityEvent). English shows them after the actor; other
    // languages parse them back so that the sentence can be ordered freely. ActivityFeedJapaneseContractTest
    // in composeApp pins every producer format to its Japanese line.
    // ---------------------------------------------------------------------------------------------

    /** Timeline line "actor + action" such as "@alice closed this"; a null actor is shown as the system. */
    fun formatTimelineLine(actorLogin: String?, actionLabel: String): String {
        if (showsSourceTextAsIs) return "@${actorLogin ?: systemActorFallback} $actionLabel"
        val subject = actorLogin?.let { actorSubject("@$it") } ?: systemActorSubject
        return when {
            actionLabel == "closed this" -> timelineClosed(subject)
            actionLabel == "reopened this" -> timelineReopened(subject)
            // GitHubGraphQlService writes "merged into base" when the merge ref name is unknown.
            actionLabel == "merged into base" -> timelineMergedIntoBase(subject)
            actionLabel.startsWith("merged into ") -> timelineMergedInto(subject, actionLabel.removePrefix("merged into "))
            else -> timelineUnrecognized(actorLogin?.let { "@$it" } ?: systemActorFallback, actionLabel)
        }
    }

    /** Activity feed line "actor + summary" such as "@alice pushed 2 commits to main". */
    fun formatActivityEventLine(event: ActivityEvent): String {
        val actor = "@${event.actor.login}"
        if (showsSourceTextAsIs) return "$actor ${event.summaryText}"
        translatedActivity(event, actorSubject(actor))?.let { return it }
        val detail = event.summaryText.takeIf { it.isNotBlank() && it != "activity" }
        return if (detail != null) activityUnknownWithDetail(actor, detail) else activityUnknown(actor)
    }

    /** The translated line for a recognized summary, or null when it is not recognized. */
    private fun translatedActivity(event: ActivityEvent, subject: String): String? {
        val summary = event.summaryText
        val num = event.targetIssueNumber
        fun numbered(withNumber: (String, Int) -> String, withoutNumber: (String) -> String): String =
            if (num != null) withNumber(subject, num) else withoutNumber(subject)
        return when (event.kind) {
            ActivityEventKind.PUSH -> {
                val count = PUSH_COUNT_REGEX.find(summary)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                val branch = event.branchName ?: summary.substringAfterLast(" to ", "").ifBlank { return null }
                activityPushed(subject, branch, count)
            }
            ActivityEventKind.PULL_REQUEST -> {
                if (!summary.contains(" pull request") && !summary.endsWith("pull request")) return null
                when (summary.substringBefore(" pull request")) {
                    "opened" -> numbered(activityPrOpened, activityPrOpenedNoNumber)
                    "merged" -> numbered(activityPrMerged, activityPrMergedNoNumber)
                    "closed" -> numbered(activityPrClosed, activityPrClosedNoNumber)
                    "reopened" -> numbered(activityPrReopened, activityPrReopenedNoNumber)
                    "edited" -> numbered(activityPrEdited, activityPrEditedNoNumber)
                    "ready_for_review" -> numbered(activityPrReadyForReview, activityPrReadyForReviewNoNumber)
                    "converted_to_draft" -> numbered(activityPrConvertedToDraft, activityPrConvertedToDraftNoNumber)
                    "review_requested" -> numbered(activityPrReviewRequested, activityPrReviewRequestedNoNumber)
                    "assigned" -> numbered(activityPrAssigned, activityPrAssignedNoNumber)
                    else -> numbered(activityPrUpdated, activityPrUpdatedNoNumber)
                }
            }
            ActivityEventKind.ISSUES -> {
                if (!summary.contains(" issue") && !summary.endsWith("issue")) return null
                when (summary.substringBefore(" issue")) {
                    "opened" -> numbered(activityIssueOpened, activityIssueOpenedNoNumber)
                    "closed" -> numbered(activityIssueClosed, activityIssueClosedNoNumber)
                    "reopened" -> numbered(activityIssueReopened, activityIssueReopenedNoNumber)
                    "edited" -> numbered(activityIssueEdited, activityIssueEditedNoNumber)
                    "assigned" -> numbered(activityIssueAssigned, activityIssueAssignedNoNumber)
                    else -> numbered(activityIssueUpdated, activityIssueUpdatedNoNumber)
                }
            }
            ActivityEventKind.ISSUE_COMMENT -> when {
                summary == "commented on thread" -> activityCommentedOnThread(subject)
                summary.startsWith("commented on") -> numbered(activityCommentedOn, activityCommentedOnThread)
                else -> null
            }
            ActivityEventKind.PULL_REQUEST_REVIEW -> when {
                summary.startsWith("approved") -> numbered(activityReviewApproved, activityReviewApprovedNoNumber)
                summary.startsWith("requested changes") ->
                    numbered(activityReviewChangesRequested, activityReviewChangesRequestedNoNumber)
                summary.startsWith("reviewed") -> numbered(activityReviewReviewed, activityReviewReviewedNoNumber)
                else -> null
            }
            ActivityEventKind.WATCH -> activityStarred(subject)
            ActivityEventKind.FORK -> activityForked(subject)
            ActivityEventKind.CREATE -> refActivity(summary, "created ", subject, created = true)
            ActivityEventKind.DELETE -> refActivity(summary, "deleted ", subject, created = false)
            ActivityEventKind.RELEASE -> {
                if (!summary.contains("release")) return null
                val tag = event.releaseTag?.takeIf { it.isNotBlank() }
                fun tagged(withTag: (String, String) -> String, withoutTag: (String) -> String): String =
                    if (tag != null) withTag(subject, tag) else withoutTag(subject)
                when (summary.substringBefore(" release")) {
                    "published", "released" -> tagged(activityReleasePublished, activityReleasePublishedNoTag)
                    "created" -> tagged(activityReleaseCreated, activityReleaseCreatedNoTag)
                    "prereleased" -> tagged(activityPrereleased, activityPrereleasedNoTag)
                    "edited" -> tagged(activityReleaseEdited, activityReleaseEditedNoTag)
                    "deleted" -> tagged(activityReleaseDeleted, activityReleaseDeletedNoTag)
                    "unpublished" -> tagged(activityReleaseUnpublished, activityReleaseUnpublishedNoTag)
                    else -> tagged(activityReleaseUpdated, activityReleaseUpdatedNoTag)
                }
            }
            ActivityEventKind.PUBLIC -> activityMadePublic(subject)
            ActivityEventKind.MEMBER -> {
                val member = summary.substringAfter(' ', "").takeIf { it.startsWith("@") }
                fun named(withName: (String, String) -> String, withoutName: (String) -> String): String =
                    if (member != null) withName(subject, member) else withoutName(subject)
                when (summary.substringBefore(' ')) {
                    "added" -> named(activityMemberAdded, activityMemberAddedNoName)
                    "removed" -> named(activityMemberRemoved, activityMemberRemovedNoName)
                    "edited" -> named(activityMemberEdited, activityMemberEditedNoName)
                    else -> null
                }
            }
            ActivityEventKind.OTHER -> when (summary) {
                "CommitComment" -> activityCommitComment(subject)
                "Gollum" -> activityWikiUpdated(subject)
                else -> null
            }
        }
    }

    /** "created branch main" / "deleted tag v1": the ref kind picks the row, the ref name is the argument. */
    private fun refActivity(summary: String, prefix: String, subject: String, created: Boolean): String? {
        if (!summary.startsWith(prefix.trim())) return null
        val rest = summary.removePrefix(prefix).trim()
        val refType = rest.substringBefore(' ')
        val ref = rest.substringAfter(' ', "").trim()
        val (withName, withoutName) = when (refType) {
            "branch" -> if (created) activityCreatedBranch to activityCreatedBranchNoName
                else activityDeletedBranch to activityDeletedBranchNoName
            "tag" -> if (created) activityCreatedTag to activityCreatedTagNoName
                else activityDeletedTag to activityDeletedTagNoName
            "repository" -> if (created) activityCreatedRepository to activityCreatedRepositoryNoName
                else activityDeletedRepository to activityDeletedRepositoryNoName
            "ref", "" -> if (created) activityCreatedRef to activityCreatedRefNoName
                else activityDeletedRef to activityDeletedRefNoName
            else -> return null
        }
        return if (ref.isNotEmpty()) withName(subject, ref) else withoutName(subject)
    }

    private companion object {
        val PUSH_COUNT_REGEX = Regex("""^pushed (\d+) commit""")
    }
}

/** English UI, which is also the source language of the network layer. */
object EnglishStrings : AppStrings(AppLanguage.DEFAULT)

object JapaneseStrings : AppStrings(AppLanguage.JAPANESE)

fun stringsFor(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.DEFAULT -> EnglishStrings
    AppLanguage.JAPANESE -> JapaneseStrings
}

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }

@Composable
fun appStrings(): AppStrings = LocalAppStrings.current
