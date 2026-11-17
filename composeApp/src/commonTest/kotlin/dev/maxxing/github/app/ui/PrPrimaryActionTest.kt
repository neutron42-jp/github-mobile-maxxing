package dev.maxxing.github.app.ui

import dev.maxxing.github.model.IssueOrPullState
import kotlin.test.Test
import kotlin.test.assertEquals

class PrPrimaryActionTest {
    private fun action(
        isPr: Boolean = true,
        state: IssueOrPullState = IssueOrPullState.OPEN,
        isDraft: Boolean = false,
        canMerge: Boolean = true,
        canUpdate: Boolean = true
    ) = prPrimaryAction(isPr, state, isDraft, canMerge, canUpdate)

    @Test
    fun openNonDraftPrWithMergePermissionShowsMerge() {
        assertEquals(PrPrimaryAction.MERGE, action())
        assertEquals(PrPrimaryAction.MERGE, action(canUpdate = false))
    }

    @Test
    fun openNonDraftPrWithoutMergePermissionShowsNothing() {
        assertEquals(PrPrimaryAction.NONE, action(canMerge = false))
    }

    @Test
    fun draftPrOffersReadyForReviewInsteadOfMerge() {
        assertEquals(PrPrimaryAction.MARK_READY, action(isDraft = true))
        assertEquals(PrPrimaryAction.MARK_READY, action(isDraft = true, canMerge = false))
    }

    @Test
    fun draftPrWithoutUpdatePermissionShowsNothing() {
        assertEquals(PrPrimaryAction.NONE, action(isDraft = true, canUpdate = false))
    }

    @Test
    fun closedMergedOrIssueShowsNothing() {
        assertEquals(PrPrimaryAction.NONE, action(state = IssueOrPullState.CLOSED))
        assertEquals(PrPrimaryAction.NONE, action(state = IssueOrPullState.MERGED))
        assertEquals(PrPrimaryAction.NONE, action(state = IssueOrPullState.CLOSED, isDraft = true))
        assertEquals(PrPrimaryAction.NONE, action(isPr = false))
    }
}
