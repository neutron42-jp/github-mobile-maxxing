package dev.maxxing.github.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GistFileUpdateHelpersTest {

    @Test
    fun rejectsRenameOntoExistingFilename() {
        val result = buildGistFileUpdates(
            originalName = "a.txt",
            originalContent = "hello",
            newFilename = "b.txt",
            newContent = "hello",
            existingFilenames = setOf("a.txt", "b.txt"),
            isTruncated = false
        )
        assertTrue(result.isFailure)
        val err = assertIs<DuplicateGistFilenameException>(result.exceptionOrNull())
        assertEquals("b.txt", err.filename)
    }

    @Test
    fun truncatedFileProducesEmptyUpdatesAndRenameDeletesOldKey() {
        val truncatedResult = buildGistFileUpdates(
            originalName = "big.txt",
            originalContent = "partial",
            newFilename = "big-renamed.txt",
            newContent = "modified",
            existingFilenames = setOf("big.txt"),
            isTruncated = true
        )
        assertTrue(truncatedResult.getOrThrow().isEmpty())

        val renameResult = buildGistFileUpdates(
            originalName = "a.txt",
            originalContent = "old",
            newFilename = "c.txt",
            newContent = "new",
            existingFilenames = setOf("a.txt", "b.txt"),
            isTruncated = false
        ).getOrThrow()
        assertEquals(2, renameResult.size)
        assertNull(renameResult["a.txt"])
        assertEquals("new", renameResult["c.txt"])
    }

    @Test
    fun truncatedFileListRejectsRenameToAnUnreturnedName() {
        val result = buildGistFileUpdates(
            originalName = "a.txt",
            originalContent = "old",
            newFilename = "not-in-the-returned-list.txt",
            newContent = "old",
            existingFilenames = setOf("a.txt"),
            isTruncated = false,
            isFileListTruncated = true
        )
        assertTrue(result.isFailure)
        assertIs<GistFileListIncompleteException>(result.exceptionOrNull())
    }

    @Test
    fun truncatedFileListRejectsRenameEvenWhenTheNameWasReturned() {
        val result = buildGistFileUpdates(
            originalName = "a.txt",
            originalContent = "old",
            newFilename = "b.txt",
            newContent = "old",
            existingFilenames = setOf("a.txt", "b.txt"),
            isTruncated = false,
            isFileListTruncated = true
        )
        assertTrue(result.isFailure)
        assertIs<GistFileListIncompleteException>(result.exceptionOrNull())
    }

    @Test
    fun truncatedFileListRejectsAddingANewFile() {
        val result = buildGistFileUpdates(
            originalName = null,
            originalContent = "",
            newFilename = "new.txt",
            newContent = "body",
            existingFilenames = emptySet(),
            isTruncated = false,
            isFileListTruncated = true
        )
        assertTrue(result.isFailure)
        assertIs<GistFileListIncompleteException>(result.exceptionOrNull())
    }

    @Test
    fun truncatedFileListAllowsSameNameContentUpdate() {
        val result = buildGistFileUpdates(
            originalName = "a.txt",
            originalContent = "old",
            newFilename = "a.txt",
            newContent = "new body",
            existingFilenames = setOf("a.txt"),
            isTruncated = false,
            isFileListTruncated = true
        ).getOrThrow()
        assertEquals(mapOf("a.txt" to "new body"), result)
    }

    @Test
    fun truncatedFileListAllowsDescriptionOnlySave() {
        val result = buildGistFileUpdates(
            originalName = "a.txt",
            originalContent = "old",
            newFilename = "a.txt",
            newContent = "old",
            existingFilenames = setOf("a.txt"),
            isTruncated = false,
            isFileListTruncated = true
        ).getOrThrow()
        assertTrue(result.isEmpty())
    }

    @Test
    fun bodyTruncationStillAllowsOnlyADescriptionChange() {
        val result = buildGistFileUpdates(
            originalName = "a.txt",
            originalContent = "partial",
            newFilename = "renamed.txt",
            newContent = "edited partial body",
            existingFilenames = setOf("a.txt"),
            isTruncated = true,
            isFileListTruncated = true
        ).getOrThrow()
        assertTrue(result.isEmpty())
    }

    @Test
    fun completeFileListWithDistinctNameCanStillRename() {
        val result = buildGistFileUpdates(
            originalName = "a.txt",
            originalContent = "old",
            newFilename = "renamed.txt",
            newContent = "old",
            existingFilenames = setOf("a.txt", "b.txt"),
            isTruncated = false,
            isFileListTruncated = false
        ).getOrThrow()
        assertEquals(mapOf("a.txt" to null, "renamed.txt" to "old"), result)
    }

    @Test
    fun canSaveCompletesListAllowsAValidEditAndRejectsADuplicateRename() {
        assertTrue(
            canSaveGistEdit(
                isCreating = false,
                isBodyTruncated = false,
                isFileListTruncated = false,
                initialDescription = "desc",
                description = "desc",
                initialFilename = "a.txt",
                filename = "a.txt",
                initialContent = "old",
                content = "new",
                existingFilenames = setOf("a.txt")
            )
        )
        assertFalse(
            canSaveGistEdit(
                isCreating = false,
                isBodyTruncated = false,
                isFileListTruncated = false,
                initialDescription = "desc",
                description = "desc",
                initialFilename = "a.txt",
                filename = "b.txt",
                initialContent = "old",
                content = "old",
                existingFilenames = setOf("a.txt", "b.txt")
            )
        )
    }

    @Test
    fun canSaveTruncatedBodyAllowsOnlyADescriptionChange() {
        val base = CanSaveDefaults(isBodyTruncated = true)
        assertFalse(base(description = "desc", content = "new"))
        assertTrue(base(description = "new desc", content = "partial"))
    }

    @Test
    fun canSaveTruncatedFileListAllowsDescriptionAndContentButNeverARename() {
        val base = CanSaveDefaults(isFileListTruncated = true)
        assertTrue(base(description = "new desc", content = "old"))
        assertTrue(base(description = "desc", content = "new content"))
        assertFalse(base(description = "desc", content = "old"))
        assertFalse(base(description = "new desc", content = "old", filename = "renamed.txt"))
        assertFalse(base(description = "new desc", content = "   "))
    }

    private class CanSaveDefaults(
        private val isBodyTruncated: Boolean = false,
        private val isFileListTruncated: Boolean = false
    ) {
        operator fun invoke(
            description: String,
            content: String,
            filename: String = "a.txt"
        ): Boolean = canSaveGistEdit(
            isCreating = false,
            isBodyTruncated = isBodyTruncated,
            isFileListTruncated = isFileListTruncated,
            initialDescription = "desc",
            description = description,
            initialFilename = "a.txt",
            filename = filename,
            initialContent = "old",
            content = content,
            existingFilenames = setOf("a.txt", "b.txt")
        )
    }
}
