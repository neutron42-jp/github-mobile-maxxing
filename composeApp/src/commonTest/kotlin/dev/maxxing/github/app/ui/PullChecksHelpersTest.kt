package dev.maxxing.github.app.ui

import dev.maxxing.github.model.CheckRunSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PullChecksHelpersTest {
    @Test
    fun resolveChecksRefPrefersHeadShaOverBranchName() {
        // Fork PRs: the head branch does not exist in the base repo, but the SHA resolves.
        assertEquals("abc123", resolveChecksRef(headRefOid = "abc123", headRefName = "feat/fork-branch"))
    }

    @Test
    fun resolveChecksRefFallsBackToBranchNameThenNull() {
        assertEquals("feat/ci", resolveChecksRef(headRefOid = null, headRefName = "feat/ci"))
        assertEquals("feat/ci", resolveChecksRef(headRefOid = "  ", headRefName = "feat/ci"))
        assertNull(resolveChecksRef(headRefOid = "", headRefName = " "))
        assertNull(resolveChecksRef(headRefOid = null, headRefName = null))
    }

    @Test
    fun hasPendingCheckRunsIsTrueOnlyWhileARunIsNotCompleted() {
        val done = CheckRunSummary(id = 1, name = "build", status = "completed", conclusion = "success")
        val skipped = CheckRunSummary(id = 2, name = "deploy", status = "completed", conclusion = "skipped")
        val running = CheckRunSummary(id = 3, name = "test", status = "in_progress")
        val queued = CheckRunSummary(id = 4, name = "lint", status = "queued")

        assertFalse(hasPendingCheckRuns(null))
        assertFalse(hasPendingCheckRuns(emptyList()))
        assertFalse(hasPendingCheckRuns(listOf(done, skipped)))
        assertTrue(hasPendingCheckRuns(listOf(done, running)))
        assertTrue(hasPendingCheckRuns(listOf(queued)))
    }

    @Test
    fun checksViewStateKeepsShownRunsWhileRefreshingOrAfterFailure() {
        val runs = listOf(CheckRunSummary(id = 1, name = "build", status = "in_progress"))

        assertEquals(ChecksViewState.LIST, checksViewState(loading = false, error = null, runs = runs))
        assertEquals(ChecksViewState.LIST, checksViewState(loading = true, error = null, runs = runs))
        assertEquals(ChecksViewState.LIST, checksViewState(loading = false, error = "HTTP 502", runs = runs))
    }

    @Test
    fun checksViewStateSeparatesLoadingErrorAndEmpty() {
        assertEquals(ChecksViewState.LOADING, checksViewState(loading = true, error = null, runs = null))
        assertEquals(ChecksViewState.LOADING, checksViewState(loading = true, error = "HTTP 422", runs = emptyList()))
        // A failed load must not look like "no checks reported".
        assertEquals(ChecksViewState.ERROR, checksViewState(loading = false, error = "HTTP 422", runs = null))
        assertEquals(ChecksViewState.ERROR, checksViewState(loading = false, error = "HTTP 422", runs = emptyList()))
        assertEquals(ChecksViewState.EMPTY, checksViewState(loading = false, error = null, runs = emptyList()))
        assertEquals(ChecksViewState.EMPTY, checksViewState(loading = false, error = null, runs = null))
    }

    @Test
    fun checksFailureActionShowsErrorOnlyWithoutRunsAndHidesPollFailures() {
        assertEquals(ChecksFailureAction.SHOW_ERROR, checksFailureAction(hasRuns = false, silent = false))
        assertEquals(ChecksFailureAction.SHOW_ERROR, checksFailureAction(hasRuns = false, silent = true))
        assertEquals(ChecksFailureAction.SNACKBAR, checksFailureAction(hasRuns = true, silent = false))
        assertEquals(ChecksFailureAction.IGNORE, checksFailureAction(hasRuns = true, silent = true))
    }

    @Test
    fun jobLogActionDownloadsOnlyCompletedJobs() {
        val url = "https://github.com/octocat/app/actions/runs/1/job/2"
        assertEquals(JobLogAction.DOWNLOAD, jobLogAction(status = "completed", htmlUrl = url))
        assertEquals(JobLogAction.DOWNLOAD, jobLogAction(status = "completed", htmlUrl = ""))
        assertEquals(JobLogAction.OPEN_IN_BROWSER, jobLogAction(status = "in_progress", htmlUrl = url))
        assertEquals(JobLogAction.OPEN_IN_BROWSER, jobLogAction(status = "queued", htmlUrl = url))
        assertEquals(JobLogAction.UNAVAILABLE, jobLogAction(status = "in_progress", htmlUrl = " "))
    }

    @Test
    fun parseActionsRunUrlExtractsRunAndOptionalJobId() {
        assertEquals(
            ActionsRunTarget(owner = "octocat", repo = "app", runId = 12345L, jobId = 67890L),
            parseActionsRunUrl("https://github.com/octocat/app/actions/runs/12345/job/67890?check_suite_focus=true")
        )
        assertEquals(
            ActionsRunTarget(owner = "octocat", repo = "app", runId = 12345L, jobId = 67890L),
            parseActionsRunUrl("https://github.com/octocat/app/actions/runs/12345/jobs/67890")
        )
        assertEquals(
            ActionsRunTarget(owner = "octocat", repo = "app", runId = 12345L, jobId = null),
            parseActionsRunUrl("https://github.com/octocat/app/actions/runs/12345")
        )
        assertNull(parseActionsRunUrl("https://example.com/status"))
    }
}
