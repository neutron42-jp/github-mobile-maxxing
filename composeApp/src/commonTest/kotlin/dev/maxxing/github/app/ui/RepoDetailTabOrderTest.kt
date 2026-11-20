package dev.maxxing.github.app.ui

import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueOrPullSummary
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.RepoDetail
import dev.maxxing.github.model.RepoSummary
import dev.maxxing.github.model.UserSummary
import dev.maxxing.github.ui.i18n.EnglishStrings
import dev.maxxing.github.ui.i18n.JapaneseStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RepoDetailTabOrderTest {
    @Test
    fun `repo detail tabs start with Code Issues PRs and omit standalone Commits tab`() {
        val labels = RepoDetailTab.entries.map { it.localizedLabel(EnglishStrings) }
        assertEquals(
            listOf("Code", "Issues", "PRs", "Actions", "Releases", "Discussions", "Security", "Insights"),
            labels
        )
        assertFalse("Commits" in labels)
        assertEquals(
            listOf("コード", "Issue", "PR", "Actions", "リリース", "ディスカッション", "セキュリティ", "統計"),
            RepoDetailTab.entries.map { it.localizedLabel(JapaneseStrings) }
        )
    }

    @Test
    fun `formatLocalizedRepoDetailTabLabel appends open issue and PR counts in parentheses`() {
        fun en(tab: RepoDetailTab, issues: Int?, pulls: Int?) =
            formatLocalizedRepoDetailTabLabel(tab, openIssuesCount = issues, openPullRequestsCount = pulls, strings = EnglishStrings)
        fun ja(tab: RepoDetailTab, issues: Int?, pulls: Int?) =
            formatLocalizedRepoDetailTabLabel(tab, openIssuesCount = issues, openPullRequestsCount = pulls, strings = JapaneseStrings)

        assertEquals("Issues (14)", en(RepoDetailTab.ISSUES, 14, 3))
        assertEquals("PRs (3)", en(RepoDetailTab.PULLS, 14, 3))
        assertEquals("Issues", en(RepoDetailTab.ISSUES, null, 3))
        assertEquals("PRs", en(RepoDetailTab.PULLS, 14, null))
        assertEquals("Code", en(RepoDetailTab.CODE, 14, 3))

        assertEquals("Issue (14)", ja(RepoDetailTab.ISSUES, 14, 3))
        assertEquals("PR (3)", ja(RepoDetailTab.PULLS, 14, 3))
        assertEquals("Issue", ja(RepoDetailTab.ISSUES, null, 3))
        assertEquals("PR", ja(RepoDetailTab.PULLS, 14, null))
        assertEquals("コード", ja(RepoDetailTab.CODE, 14, 3))
    }

    @Test
    fun patchDetailOpenCountUpdatesTabCountsOnOpenListRefreshBelowPageLimit() {
        val initial = RepoDetail(
            summary = RepoSummary(
                id = "R_1",
                name = "app",
                owner = "octo",
                fullName = "octo/app",
                openIssuesCount = 5,
                openPullRequestsCount = 3
            )
        )
        fun fakeItem(number: Int, kind: IssueOrPullKind) = IssueOrPullSummary(
            id = "ID_$number",
            number = number,
            title = "Item $number",
            kind = kind,
            state = IssueOrPullState.OPEN,
            repoFullName = "octo/app",
            repoOwner = "octo",
            repoName = "app",
            author = UserSummary("octo", "", ""),
            createdAt = "",
            updatedAt = ""
        )

        val updatedIssues = patchDetailOpenCountFromList(
            currentDetail = initial,
            pullRequests = false,
            stateFilter = IssueStateFilter.OPEN,
            loaded = listOf(fakeItem(1, IssueOrPullKind.ISSUE), fakeItem(2, IssueOrPullKind.ISSUE))
        )
        assertEquals(2, updatedIssues?.summary?.openIssuesCount)
        assertEquals(3, updatedIssues?.summary?.openPullRequestsCount)

        val updatedPulls = patchDetailOpenCountFromList(
            currentDetail = updatedIssues,
            pullRequests = true,
            stateFilter = IssueStateFilter.OPEN,
            loaded = listOf(fakeItem(10, IssueOrPullKind.PULL_REQUEST))
        )
        assertEquals(2, updatedPulls?.summary?.openIssuesCount)
        assertEquals(1, updatedPulls?.summary?.openPullRequestsCount)

        val closedShouldNotOverwrite = patchDetailOpenCountFromList(
            currentDetail = updatedPulls,
            pullRequests = false,
            stateFilter = IssueStateFilter.CLOSED,
            loaded = emptyList()
        )
        assertEquals(2, closedShouldNotOverwrite?.summary?.openIssuesCount)

        val fullPageShouldWaitForDetailCount = patchDetailOpenCountFromList(
            currentDetail = initial,
            pullRequests = false,
            stateFilter = IssueStateFilter.OPEN,
            loaded = (1..30).map { fakeItem(it, IssueOrPullKind.ISSUE) }
        )
        assertEquals(5, fullPageShouldWaitForDetailCount?.summary?.openIssuesCount)
    }
}
