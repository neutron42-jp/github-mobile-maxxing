package dev.maxxing.github.app.ui

import dev.maxxing.github.model.FileChangeStatus
import dev.maxxing.github.model.PullFileChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PullFilesFilterTest {

    private fun file(name: String, previous: String? = null) = PullFileChange(
        filename = name,
        previousFilename = previous,
        status = if (previous != null) FileChangeStatus.MODIFIED else FileChangeStatus.ADDED,
        additions = 1,
        deletions = 0
    )

    private val files = listOf(
        file("composeApp/src/commonMain/App.kt"),
        file("core/network/GitHubRestService.kt"),
        file("docs/README.md"),
        file("core/ui/NewName.kt", previous = "core/ui/OldName.kt")
    )

    @Test
    fun blankQueryKeepsEveryFile() {
        assertSame(files, filterPullFiles(files, ""))
        assertSame(files, filterPullFiles(files, "   "))
        assertSame(files, filterPullFiles(files, "\t\n"))
    }

    @Test
    fun matchesSubstringIgnoringCase() {
        assertEquals(
            listOf("core/network/GitHubRestService.kt"),
            filterPullFiles(files, "restservice").map { it.filename }
        )
        assertEquals(
            listOf("docs/README.md"),
            filterPullFiles(files, "readme").map { it.filename }
        )
        assertEquals(
            listOf("core/network/GitHubRestService.kt", "core/ui/NewName.kt"),
            filterPullFiles(files, "CORE/").map { it.filename }
        )
    }

    @Test
    fun surroundingWhitespaceInQueryIsIgnored() {
        assertEquals(listOf("docs/README.md"), filterPullFiles(files, "  .md ").map { it.filename })
    }

    @Test
    fun matchesPreviousFilenameOfRenamedFiles() {
        assertEquals(listOf("core/ui/NewName.kt"), filterPullFiles(files, "oldname").map { it.filename })
    }

    @Test
    fun returnsEmptyWhenNothingMatchesAndKeepsOriginalOrder() {
        assertTrue(filterPullFiles(files, "does-not-exist").isEmpty())
        assertEquals(
            files.map { it.filename },
            filterPullFiles(files, ".").map { it.filename }
        )
    }
}
