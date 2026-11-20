package dev.maxxing.github.app.ui

import dev.maxxing.github.model.IssueOrPullState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrPrimaryActionTest {
    private fun action(
        isPr: Boolean = true,
        state: IssueOrPullState = IssueOrPullState.OPEN,
        isDraft: Boolean = false,
        canMerge: Boolean = true,
        canUpdate: Boolean = true,
        hasConflicts: Boolean = false
    ) = prPrimaryAction(isPr, state, isDraft, canMerge, canUpdate, hasConflicts)

    @Test
    fun openNonDraftPrWithMergePermissionShowsMerge() {
        assertEquals(PrPrimaryAction.MERGE, action())
        assertEquals(PrPrimaryAction.MERGE, action(canUpdate = false))
    }

    @Test
    fun openNonDraftConflictingPrWithMergePermissionShowsConflicting() {
        assertEquals(PrPrimaryAction.CONFLICTING, action(hasConflicts = true))
        assertEquals(PrPrimaryAction.CONFLICTING, action(canUpdate = false, hasConflicts = true))
        assertEquals(PrPrimaryAction.MARK_READY, action(isDraft = true, hasConflicts = true))
        assertEquals(PrPrimaryAction.NONE, action(canMerge = false, hasConflicts = true))
        assertEquals(PrPrimaryAction.NONE, action(state = IssueOrPullState.CLOSED, hasConflicts = true))
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

    @Test
    fun isPullConflictingDetectsConflictingAndDirtyStates() {
        assertTrue(isPullConflicting("CONFLICTING", null))
        assertTrue(isPullConflicting("conflicting", "CLEAN"))
        assertTrue(isPullConflicting("UNKNOWN", "DIRTY"))
        assertTrue(isPullConflicting(null, "dirty"))
        assertFalse(isPullConflicting("MERGEABLE", "CLEAN"))
        assertFalse(isPullConflicting("UNKNOWN", "BLOCKED"))
        assertFalse(isPullConflicting(null, null))
    }
}
