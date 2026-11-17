package dev.maxxing.github.app.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class PinnedBookmarksVisibilityTest {
    private val names = (1..10).map { "owner/repo$it" }

    @Test
    fun collapsedShowsOnlyFirstLimitItems() {
        assertEquals(names.take(6), pinnedBookmarksVisibleItems(names, limit = 6, expanded = false))
    }

    @Test
    fun expandedShowsAllItems() {
        assertEquals(names, pinnedBookmarksVisibleItems(names, limit = 6, expanded = true))
    }

    @Test
    fun fewerThanLimitShowsAllItemsWhenCollapsed() {
        val few = names.take(3)
        assertEquals(few, pinnedBookmarksVisibleItems(few, limit = 6, expanded = false))
    }

    @Test
    fun exactlyLimitShowsAllItemsWhenCollapsed() {
        val exact = names.take(6)
        assertEquals(exact, pinnedBookmarksVisibleItems(exact, limit = 6, expanded = false))
    }

    @Test
    fun collapsedLimitDependsOnAvailableWidth() {
        assertEquals(6, pinnedBookmarksCollapsedLimit(360.dp))
        assertEquals(6, pinnedBookmarksCollapsedLimit(599.dp))
        assertEquals(12, pinnedBookmarksCollapsedLimit(600.dp))
        assertEquals(12, pinnedBookmarksCollapsedLimit(1200.dp))
    }
}
