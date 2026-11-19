package dev.maxxing.github.app.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PinnedBookmarksVisibilityTest {
    private val names = (1..10).map { "owner/repo$it" }

    @Test
    fun previewShowsOnlyFirstLimitItems() {
        assertEquals(names.take(6), pinnedBookmarksPreviewItems(names, limit = 6))
    }

    @Test
    fun fewerThanLimitPreviewsAllItems() {
        val few = names.take(3)
        assertEquals(few, pinnedBookmarksPreviewItems(few, limit = 6))
    }

    @Test
    fun exactlyLimitPreviewsAllItems() {
        val exact = names.take(6)
        assertEquals(exact, pinnedBookmarksPreviewItems(exact, limit = 6))
    }

    @Test
    fun previewOfEmptyListIsEmpty() {
        assertEquals(emptyList(), pinnedBookmarksPreviewItems(emptyList<String>(), limit = 6))
    }

    @Test
    fun collapsedLimitDependsOnAvailableWidth() {
        assertEquals(3, pinnedBookmarksCollapsedLimit(360.dp))
        assertEquals(3, pinnedBookmarksCollapsedLimit(599.dp))
        assertEquals(6, pinnedBookmarksCollapsedLimit(600.dp))
        assertEquals(6, pinnedBookmarksCollapsedLimit(1200.dp))
    }

    @Test
    fun ownerAndNameAreSplitAtFirstSlash() {
        assertEquals("owner" to "repo", pinnedBookmarkOwnerAndName("owner/repo"))
    }

    @Test
    fun missingOwnerCannotBeOpened() {
        assertNull(pinnedBookmarkOwnerAndName("repo-without-owner"))
        assertNull(pinnedBookmarkOwnerAndName("/repo"))
        assertNull(pinnedBookmarkOwnerAndName(" /repo"))
    }
}
