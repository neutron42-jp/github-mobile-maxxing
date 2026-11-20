package dev.maxxing.github.app.ui

import dev.maxxing.github.model.PullReviewComment
import dev.maxxing.github.model.UserSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PullReviewComposerStateTest {

    private fun lineTarget() = ReviewComposerTarget(
        filePath = "composeApp/src/commonMain/App.kt",
        lineNumber = 42,
        side = "RIGHT"
    )

    private fun comment() = PullReviewComment(
        id = "pending-1",
        path = "composeApp/src/commonMain/App.kt",
        line = 42,
        diffSide = "RIGHT",
        body = "Old pending body",
        author = UserSummary(login = "reviewer"),
        createdAt = "2026-11-20T00:00:00Z",
        isPending = true
    )

    @Test
    fun failedDirectDiffCommentSubmissionKeepsDraftForRetry() {
        val draft = ReviewCommentDraft(
            target = lineTarget(),
            commentBody = "Please rename this.\nSecond line stays intact.",
            saveAsPending = false
        )

        val submitting = ReviewCommentComposerState().open(draft).beginSubmit()
        assertTrue(submitting.isSubmitting)
        assertEquals(draft, submitting.draft)

        val failed = submitting.failSubmit("Failed to post diff comment")
        assertFalse(failed.isSubmitting)
        assertEquals("Failed to post diff comment", failed.errorMessage)
        assertEquals(draft, failed.draft, "A failed submission must retain the exact draft")

        val retrying = failed.beginSubmit()
        assertTrue(retrying.isSubmitting)
        assertNull(retrying.errorMessage, "Retry starts without the previous error")
        assertEquals(draft, retrying.draft)

        val succeeded = retrying.succeedSubmit()
        assertNull(succeeded.draft, "A successful submission clears the draft")
        assertFalse(succeeded.isSubmitting)
        assertNull(succeeded.errorMessage)
    }

    @Test
    fun failedPendingReviewCommentSubmissionKeepsBodyAndSelection() {
        val draft = ReviewCommentDraft(
            target = lineTarget(),
            commentBody = "Draft pending review comment",
            saveAsPending = true
        )

        val failed = ReviewCommentComposerState()
            .open(draft)
            .beginSubmit()
            .failSubmit("Failed to save pending comment")

        assertEquals(draft, failed.draft)
        assertEquals(true, failed.draft?.saveAsPending)
        assertEquals("Draft pending review comment", failed.draft?.commentBody)
    }

    @Test
    fun failedSuggestedChangeSubmissionKeepsNoteCodeAndLineRange() {
        val draft = ReviewCommentDraft(
            target = lineTarget(),
            mode = ComposerMode.SUGGESTION,
            suggestionNote = "Use the helper",
            suggestedCode = "val value = helper()",
            lineCount = 3,
            saveAsPending = false
        )

        val failed = ReviewCommentComposerState()
            .open(draft)
            .beginSubmit()
            .failSubmit("Failed to post diff comment")

        assertEquals(draft, failed.draft)
        assertEquals(ComposerMode.SUGGESTION, failed.draft?.mode)
        assertEquals("Use the helper", failed.draft?.suggestionNote)
        assertEquals("val value = helper()", failed.draft?.suggestedCode)
        assertEquals(3, failed.draft?.lineCount)
    }

    @Test
    fun failedEditOfPendingCommentKeepsOriginalTargetAndBody() {
        val pending = comment()
        val draft = ReviewCommentDraft(
            target = ReviewComposerTarget(
                filePath = pending.path,
                lineNumber = pending.line,
                side = pending.diffSide ?: "RIGHT",
                editingPendingComment = pending
            ),
            commentBody = "Edited body that must survive"
        )

        val failed = ReviewCommentComposerState()
            .open(draft)
            .beginSubmit()
            .failSubmit("Failed to save pending comment")

        assertEquals(draft, failed.draft)
        assertEquals(pending, failed.draft?.target?.editingPendingComment)
        assertEquals("Edited body that must survive", failed.draft?.commentBody)
    }

    @Test
    fun explicitCancelClearsDraftButIsIgnoredWhileSubmitting() {
        val draft = ReviewCommentDraft(target = lineTarget(), commentBody = "Cancel me")
        val open = ReviewCommentComposerState().open(draft)

        val cancelled = open.cancel()
        assertNull(cancelled.draft, "Explicit cancel clears the draft")

        val submitting = open.beginSubmit()
        val cancelDuringSubmit = submitting.cancel()
        assertEquals(submitting, cancelDuringSubmit, "The composer cannot be cancelled mid-submit")
        assertEquals(draft, cancelDuringSubmit.draft)
    }

    @Test
    fun submittingIgnoresEditsAndReplacementUntilTheResultArrives() {
        val draft = ReviewCommentDraft(target = lineTarget(), commentBody = "Submitted body")
        val submitting = ReviewCommentComposerState().open(draft).beginSubmit()

        assertEquals(submitting, submitting.updateDraft { it.copy(commentBody = "Changed body") })
        assertEquals(submitting, submitting.open(draft.copy(commentBody = "Replacement draft")))

        val failed = submitting.failSubmit("Network error")
        assertEquals("Changed body", failed.updateDraft { it.copy(commentBody = "Changed body") }.draft?.commentBody)
    }

    @Test
    fun editingAfterFailureClearsErrorButKeepsUpdatedDraft() {
        val draft = ReviewCommentDraft(target = lineTarget(), commentBody = "First attempt")
        val failed = ReviewCommentComposerState()
            .open(draft)
            .beginSubmit()
            .failSubmit("Network error")

        val edited = failed.updateDraft { it.copy(commentBody = "First attempt, revised") }

        assertNull(edited.errorMessage)
        assertEquals("First attempt, revised", edited.draft?.commentBody)
        assertEquals(draft.target, edited.draft?.target)
    }
}
