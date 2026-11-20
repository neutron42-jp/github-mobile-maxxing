package dev.maxxing.github.app.ui

import dev.maxxing.github.model.DiffHunk
import dev.maxxing.github.model.PullReviewComment

internal enum class ComposerMode {
    COMMENT,
    SUGGESTION
}

internal data class ReviewComposerTarget(
    val filePath: String,
    val hunk: DiffHunk? = null,
    val endLineIndex: Int? = null,
    val lineNumber: Int? = null,
    val side: String = "RIGHT",
    val editingPendingComment: PullReviewComment? = null
)

/**
 * Immutable snapshot of everything the user has typed in the PR review comment composer.
 *
 * Keeping the draft in one value means a failed submission can retain the exact text,
 * target line, and suggestion selection instead of reopening an empty composer.
 */
internal data class ReviewCommentDraft(
    val target: ReviewComposerTarget,
    val mode: ComposerMode = ComposerMode.COMMENT,
    val commentBody: String = "",
    val suggestionNote: String = "",
    val suggestedCode: String = "",
    val lineCount: Int = 1,
    val saveAsPending: Boolean = true
)

/**
 * PR Review Mode comment composer lifecycle.
 *
 * The draft survives a failed submit so the same text can be retried, and is cleared only
 * when the submit succeeds or the user explicitly cancels.
 */
internal data class ReviewCommentComposerState(
    val draft: ReviewCommentDraft? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
) {
    fun open(draft: ReviewCommentDraft): ReviewCommentComposerState =
        if (isSubmitting) this else ReviewCommentComposerState(draft = draft)

    fun updateDraft(transform: (ReviewCommentDraft) -> ReviewCommentDraft): ReviewCommentComposerState {
        val current = draft ?: return this
        if (isSubmitting) return this
        return copy(draft = transform(current), errorMessage = null)
    }

    fun beginSubmit(): ReviewCommentComposerState {
        if (draft == null || isSubmitting) return this
        return copy(isSubmitting = true, errorMessage = null)
    }

    fun failSubmit(message: String): ReviewCommentComposerState {
        if (draft == null) return this
        return copy(isSubmitting = false, errorMessage = message)
    }

    fun succeedSubmit(): ReviewCommentComposerState {
        if (draft == null) return this
        return ReviewCommentComposerState()
    }

    fun cancel(): ReviewCommentComposerState {
        if (draft == null || isSubmitting) return this
        return ReviewCommentComposerState()
    }
}
