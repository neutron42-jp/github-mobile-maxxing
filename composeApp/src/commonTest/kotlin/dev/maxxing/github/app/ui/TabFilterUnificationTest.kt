package dev.maxxing.github.app.ui

import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.ui.i18n.EnglishStrings
import dev.maxxing.github.ui.i18n.JapaneseStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class TabFilterUnificationTest {
    @Test
    fun inboxReasonFiltersPreserveTabOrderAndLabels() {
        assertEquals(
            listOf(
                ReasonFilter.ALL,
                ReasonFilter.REVIEWS,
                ReasonFilter.MENTIONS,
                ReasonFilter.ASSIGNED,
                ReasonFilter.CI
            ),
            ReasonFilter.entries
        )
        assertEquals(
            listOf("All Reasons", "Review Req", "Mentions", "Assigned", "CI"),
            ReasonFilter.entries.map { it.localizedLabel(EnglishStrings) }
        )
        assertEquals(
            listOf("すべての理由", "レビュー依頼", "メンション", "アサイン", "CI"),
            ReasonFilter.entries.map { it.localizedLabel(JapaneseStrings) }
        )
    }

    @Test
    fun issueStateFiltersPreserveTabOrderAndLabels() {
        assertEquals(
            listOf(
                IssueStateFilter.OPEN,
                IssueStateFilter.CLOSED,
                IssueStateFilter.ALL
            ),
            IssueStateFilter.entries
        )
        assertEquals(
            listOf("Open", "Closed", "All"),
            IssueStateFilter.entries.map { EnglishStrings.issueStateFilterLabel(it) }
        )
        assertEquals(
            listOf("オープン", "クローズ", "すべて"),
            IssueStateFilter.entries.map { JapaneseStrings.issueStateFilterLabel(it) }
        )
    }
}
