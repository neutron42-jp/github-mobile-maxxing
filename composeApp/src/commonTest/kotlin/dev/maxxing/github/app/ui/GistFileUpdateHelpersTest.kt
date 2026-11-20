package dev.maxxing.github.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
