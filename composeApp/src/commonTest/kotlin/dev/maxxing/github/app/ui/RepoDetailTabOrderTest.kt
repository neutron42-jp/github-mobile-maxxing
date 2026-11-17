package dev.maxxing.github.app.ui

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
}
