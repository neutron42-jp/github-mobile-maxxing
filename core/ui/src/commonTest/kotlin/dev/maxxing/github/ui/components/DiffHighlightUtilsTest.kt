package dev.maxxing.github.ui.components

import dev.maxxing.github.model.DiffHunk
import dev.maxxing.github.model.DiffLine
import dev.maxxing.github.model.DiffLineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiffHighlightUtilsTest {

    @Test
    fun computesWordLevelDiffRangesForModifiedLine() {
        val oldLine = "val totalCount = items.size + 1"
        val newLine = "val activeCount = items.size + 2"
        val (oldRanges, newRanges) = computeWordDiffRanges(oldLine, newLine)

        assertEquals(listOf("totalCount", "1"), oldRanges.map { oldLine.substring(it) })
        assertEquals(listOf("activeCount", "2"), newRanges.map { newLine.substring(it) })
    }

    @Test
    fun returnsEmptyWhenLinesAreIdenticalOrCompletelyUnrelated() {
        assertEquals(emptyList<IntRange>() to emptyList(), computeWordDiffRanges("same", "same"))
        assertEquals(emptyList<IntRange>() to emptyList(), computeWordDiffRanges("", "added"))
        assertEquals(
            emptyList<IntRange>() to emptyList(),
            computeWordDiffRanges("import foo.bar.Baz", "return computeSomething()")
        )
    }

    @Test
    fun computesHunkWordHighlightsForPairedRemovedAddedBlocks() {
        val hunk = DiffHunk(
            header = "@@ -1,3 +1,3 @@",
            oldStart = 1,
            oldCount = 3,
            newStart = 1,
            newCount = 3,
            lines = listOf(
                DiffLine(DiffLineKind.CONTEXT, "fun greet() {", 1, 1),
                DiffLine(DiffLineKind.REMOVED, "    println(\"hi\")", 2, null),
                DiffLine(DiffLineKind.ADDED, "    println(\"hello\")", null, 2),
                DiffLine(DiffLineKind.CONTEXT, "}", 3, 3)
            )
        )

        val highlights = computeHunkWordHighlights(hunk)
        assertTrue(highlights.containsKey(1))
        assertTrue(highlights.containsKey(2))
        assertEquals("hi", hunk.lines[1].content.substring(highlights[1]!!.single()))
        assertEquals("hello", hunk.lines[2].content.substring(highlights[2]!!.single()))
    }
}
