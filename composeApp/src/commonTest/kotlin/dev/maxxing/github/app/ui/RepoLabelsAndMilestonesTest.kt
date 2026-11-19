package dev.maxxing.github.app.ui

import dev.maxxing.github.model.MilestoneSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepoLabelsAndMilestonesTest {

    @Test
    fun hexColorValidationAndNormalizationWork() {
        assertTrue(isValidHexColor("d73a4a"))
        assertTrue(isValidHexColor("#6E40C9"))
        assertFalse(isValidHexColor("12345"))
        assertFalse(isValidHexColor("zzzzzz"))

        assertEquals("6e40c9", normalizeHexColor("#6E40C9"))
        assertEquals("6E40C9", normalizeHexColor("bad"))
    }

    @Test
    fun milestoneDueDateFormattingAndParsingHandleValidAndInvalidInputs() {
        assertNull(formatMilestoneDueDate(null))
        assertNull(formatMilestoneDueDate("   "))
        assertEquals("2026-12-31", formatMilestoneDueDate("2026-12-31T23:59:59Z"))

        assertEquals(MilestoneDueParseResult(null, true), parseMilestoneDueInput(""))
        assertEquals(
            MilestoneDueParseResult("2026-12-31T23:59:59Z", true),
            parseMilestoneDueInput(" 2026-12-31 ")
        )
        assertFalse(parseMilestoneDueInput("2026-13-01").isValid)
        assertFalse(parseMilestoneDueInput("2026/12/31").isValid)
    }

    @Test
    fun milestoneProgressAndSortingBehaveAsExpected() {
        val closedMs = MilestoneSummary(number = 1, title = "v1.0", state = "closed", openIssues = 0, closedIssues = 5)
        val openLater = MilestoneSummary(number = 2, title = "v1.2", state = "open", dueOn = "2027-02-01T00:00:00Z", openIssues = 3, closedIssues = 1)
        val openSoon = MilestoneSummary(number = 3, title = "v1.1", state = "open", dueOn = "2027-01-15T00:00:00Z", openIssues = 1, closedIssues = 3)
        val openNoDue = MilestoneSummary(number = 4, title = "Backlog", state = "open", openIssues = 0, closedIssues = 0)

        assertEquals(100 to 5, computeMilestoneProgress(closedMs))
        assertEquals(25 to 4, computeMilestoneProgress(openLater))
        assertEquals(75 to 4, computeMilestoneProgress(openSoon))
        assertEquals(0 to 0, computeMilestoneProgress(openNoDue))

        assertEquals(
            listOf(3, 2, 4, 1),
            sortMilestonesForManagement(listOf(closedMs, openNoDue, openLater, openSoon)).map { it.number }
        )
    }
}
