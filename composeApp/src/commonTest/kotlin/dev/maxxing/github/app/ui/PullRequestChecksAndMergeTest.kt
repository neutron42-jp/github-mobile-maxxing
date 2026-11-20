package dev.maxxing.github.app.ui

import dev.maxxing.github.model.CheckRollupSummary
import dev.maxxing.github.model.CheckRunSummary
import dev.maxxing.github.model.IssueOrPullDetail
import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.MergeMethod
import dev.maxxing.github.model.UserSummary
import dev.maxxing.github.network.GitHubApiException
import dev.maxxing.github.ui.i18n.EnglishStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PullRequestChecksAndMergeTest {

    private fun samplePull(
        mergeable: String? = "MERGEABLE",
        mergeStateStatus: String? = "CLEAN",
        reviewDecision: String? = null,
        rollupState: String? = null
    ) = IssueOrPullDetail(
        id = "PR_1",
        number = 10,
        kind = IssueOrPullKind.PULL_REQUEST,
        title = "Sample PR",
        body = "",
        state = IssueOrPullState.OPEN,
        repoFullName = "octo/app",
        repoOwner = "octo",
        repoName = "app",
        author = UserSummary("alice", "", ""),
        createdAt = "2026-11-19T00:00:00Z",
        updatedAt = "2026-11-19T00:00:00Z",
        mergeable = mergeable,
        mergeStateStatus = mergeStateStatus,
        reviewDecision = reviewDecision,
        checkRollup = rollupState?.let { CheckRollupSummary(state = it) }
    )

    @Test
    fun parseActionsRunUrlExtractsOwnerRepoRunAndOptionalJob() {
        assertEquals(
            ActionsRunTarget("neutron42-jp", "github-mobile-maxxing", 44490500895L, 135444593382L),
            parseActionsRunUrl("https://github.com/neutron42-jp/github-mobile-maxxing/actions/runs/44490500895/job/135444593382")
        )
        assertEquals(
            ActionsRunTarget("octo", "app", 12345L, null),
            parseActionsRunUrl("https://github.com/octo/app/actions/runs/12345?check_suite_focus=true")
        )
        assertNull(parseActionsRunUrl("https://vercel.com/team/app/deployments/xyz"))
        assertNull(parseActionsRunUrl(""))
    }

    @Test
    fun resolvePullMergeBlockReasonExplainsPendingChecksConflictsAndProtection() {
        val pendingRun = CheckRunSummary(id = 1L, name = "CI", status = "in_progress")
        assertEquals(
            EnglishStrings.issueMergeBlockedPendingChecks,
            resolvePullMergeBlockReason(
                samplePull(mergeStateStatus = "BLOCKED", rollupState = "PENDING"),
                listOf(pendingRun),
                EnglishStrings
            )
        )
        assertEquals(
            EnglishStrings.issueMergeBlockedConflicts,
            resolvePullMergeBlockReason(
                samplePull(mergeable = "CONFLICTING", mergeStateStatus = "DIRTY"),
                emptyList(),
                EnglishStrings
            )
        )
        assertEquals(
            EnglishStrings.issueMergeBlockedBehind,
            resolvePullMergeBlockReason(
                samplePull(mergeStateStatus = "BEHIND"),
                emptyList(),
                EnglishStrings
            )
        )
        assertEquals(
            EnglishStrings.issueMergeBlockedReview,
            resolvePullMergeBlockReason(
                samplePull(mergeStateStatus = "BLOCKED", reviewDecision = "REVIEW_REQUIRED"),
                emptyList(),
                EnglishStrings
            )
        )
        assertNull(
            resolvePullMergeBlockReason(
                samplePull(mergeable = "MERGEABLE", mergeStateStatus = "CLEAN"),
                listOf(CheckRunSummary(id = 2L, name = "CI", status = "completed", conclusion = "success")),
                EnglishStrings
            )
        )
    }

    @Test
    fun formatPullMergeFailureMessageTranslatesHttp405() {
        assertEquals(
            EnglishStrings.issueMergeBlockedPendingChecks,
            formatPullMergeFailureMessage(
                GitHubApiException(405, "Required status check \"CI\" is in progress (405)"),
                EnglishStrings
            )
        )
        assertEquals(
            EnglishStrings.issueMergeBlockedConflicts,
            formatPullMergeFailureMessage(
                GitHubApiException(405, "Pull Request is not mergeable (405)"),
                EnglishStrings
            )
        )
    }

    @Test
    fun unstableAndUnknownWithPendingChecksAllowMergingAndEffectiveMethodRespectsRepoSettings() {
        val pendingRun = CheckRunSummary(id = 1L, name = "optional", status = "in_progress")
        assertNull(
            resolvePullMergeBlockReason(
                samplePull(mergeStateStatus = "UNSTABLE", rollupState = "PENDING"),
                listOf(pendingRun),
                EnglishStrings
            )
        )
        assertNull(
            resolvePullMergeBlockReason(
                samplePull(mergeable = "UNKNOWN", mergeStateStatus = "UNKNOWN", rollupState = "PENDING"),
                listOf(pendingRun),
                EnglishStrings
            )
        )

        assertTrue(canConfirmPullMerge(samplePull(mergeable = "MERGEABLE", mergeStateStatus = "UNSTABLE")))
        assertTrue(canConfirmPullMerge(samplePull(mergeable = "UNKNOWN", mergeStateStatus = "BLOCKED")))
        assertFalse(canConfirmPullMerge(samplePull(mergeable = "CONFLICTING", mergeStateStatus = "DIRTY")))

        assertEquals(
            MergeMethod.MERGE,
            resolveEffectiveMergeMethod(MergeMethod.SQUASH, listOf(MergeMethod.MERGE, MergeMethod.REBASE))
        )
        assertEquals(
            MergeMethod.SQUASH,
            resolveEffectiveMergeMethod(MergeMethod.SQUASH, listOf(MergeMethod.SQUASH, MergeMethod.MERGE))
        )
    }
}
