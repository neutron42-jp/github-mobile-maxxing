package dev.maxxing.github.app.ui

import dev.maxxing.github.model.DiffHunk
import dev.maxxing.github.model.DiffLine
import dev.maxxing.github.model.DiffLineKind
import dev.maxxing.github.model.FileChangeStatus
import dev.maxxing.github.model.FileViewedState
import dev.maxxing.github.model.PendingPullReview
import dev.maxxing.github.model.PullFileChange
import dev.maxxing.github.model.PullReviewComment
import dev.maxxing.github.model.PullReviewSyncState
import dev.maxxing.github.model.PullReviewThread
import dev.maxxing.github.model.UserSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PullReviewHelpersTest {

    private fun file(
        name: String,
        status: FileChangeStatus = FileChangeStatus.MODIFIED,
        additions: Int = 5,
        deletions: Int = 2,
        patch: String? = "@@ -1 +1 @@\n-a\n+b"
    ) = PullFileChange(
        filename = name,
        status = status,
        additions = additions,
        deletions = deletions,
        patch = patch
    )

    @Test
    fun detectsLockAndGeneratedFiles() {
        assertTrue(isLockOrGeneratedFile("package-lock.json"))
        assertTrue(isLockOrGeneratedFile("iosApp/Podfile.lock"))
        assertTrue(isLockOrGeneratedFile("web/bundle.min.js"))
        assertTrue(isLockOrGeneratedFile("proto/service.pb.kt"))
        assertTrue(isLockOrGeneratedFile("src/generated/Schema.kt"))
        assertFalse(isLockOrGeneratedFile("composeApp/src/commonMain/App.kt"))
    }

    @Test
    fun organizesFilesWithDeletionsAndGeneratedAtTailAndCollapsed() {
        val lock = file("yarn.lock", additions = 200, deletions = 150)
        val deleted = file("old/Unused.kt", status = FileChangeStatus.REMOVED, additions = 0, deletions = 40)
        val codeA = file("src/A.kt")
        val codeB = file("src/B.kt")

        val organized = organizeFilesForReview(listOf(lock, deleted, codeA, codeB))
        assertEquals(
            listOf("src/A.kt", "src/B.kt", "old/Unused.kt", "yarn.lock"),
            organized.map { it.file.filename }
        )
        assertEquals(
            listOf(
                ReviewFileCategory.PRIMARY,
                ReviewFileCategory.PRIMARY,
                ReviewFileCategory.DELETION_ONLY,
                ReviewFileCategory.LOCK_OR_GENERATED
            ),
            organized.map { it.category }
        )
        assertEquals(listOf(false, false, true, true), organized.map { it.autoCollapsed })
    }

    @Test
    fun findsNextUnviewedFileWithWrapAround() {
        val entries = organizeFilesForReview(
            listOf(file("a.kt"), file("b.kt"), file("c.kt"))
        )
        val states = mapOf(
            "a.kt" to FileViewedState.UNVIEWED,
            "b.kt" to FileViewedState.VIEWED,
            "c.kt" to FileViewedState.VIEWED
        )
        assertEquals(0, findNextUnviewedFileIndex(entries, states, currentIndex = 1))
        assertEquals(3, countViewedFiles(entries, mapOf("a.kt" to FileViewedState.VIEWED, "b.kt" to FileViewedState.VIEWED, "c.kt" to FileViewedState.VIEWED)))
        assertNull(findNextUnviewedFileIndex(entries, mapOf("a.kt" to FileViewedState.VIEWED, "b.kt" to FileViewedState.VIEWED, "c.kt" to FileViewedState.VIEWED), 0))
    }

    @Test
    fun buildsSuggestedChangeMarkdownAndHandlesBackticks() {
        assertEquals(
            "```suggestion\nval x = 2\n```",
            buildSuggestedChangeComment("", "val x = 2\n")
        )
        assertEquals(
            "Use constant\n\n````suggestion\nval md = \"```\"\n````",
            buildSuggestedChangeComment("  Use constant ", "val md = \"```\"")
        )
    }

    @Test
    fun collectsLineRangeForSuggestionStoppingAtRemovedLine() {
        val hunk = DiffHunk(
            header = "@@ -1,4 +1,4 @@",
            oldStart = 1,
            oldCount = 4,
            newStart = 1,
            newCount = 4,
            lines = listOf(
                DiffLine(DiffLineKind.CONTEXT, "line1", 1, 1),
                DiffLine(DiffLineKind.REMOVED, "old2", 2, null),
                DiffLine(DiffLineKind.ADDED, "new2", null, 2),
                DiffLine(DiffLineKind.ADDED, "new3", null, 3)
            )
        )

        val (startLine, collected) = collectLineRangeForSuggestion(hunk, endLineIndex = 3, lineCount = 3)
        assertEquals(2, startLine)
        assertEquals(listOf("new2", "new3"), collected.map { it.content })
    }

    @Test
    fun mergesThreadsAndPendingCommentsForFileByLine() {
        val user = UserSummary("alice", "")
        val pendingComment = PullReviewComment(
            id = "c1",
            path = "src/A.kt",
            line = 10,
            diffSide = "RIGHT",
            body = "draft",
            author = user,
            createdAt = "",
            isPending = true
        )
        val fileComment = PullReviewComment(
            id = "c2",
            path = "src/A.kt",
            line = null,
            subjectType = "FILE",
            body = "file note",
            author = user,
            createdAt = "",
            isPending = false
        )
        val syncState = PullReviewSyncState(
            pendingReview = PendingPullReview(id = "r1", comments = listOf(pendingComment)),
            threads = listOf(
                PullReviewThread(id = "t1", path = "src/A.kt", line = 10, diffSide = "RIGHT", comments = listOf(pendingComment)),
                PullReviewThread(id = "t2", path = "src/A.kt", line = null, subjectType = "FILE", comments = listOf(fileComment))
            )
        )

        val grouped = collectReviewCommentsForFile(syncState, "src/A.kt")
        assertEquals(1, grouped[10]?.size)
        assertEquals(1, grouped[null]?.size)
    }
}
