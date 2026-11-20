package dev.maxxing.github.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

    @Test
    fun highlightedSpansUseTheProvidedForegroundAndBackground() {
        val content = "val totalCount = 1"
        val ranges = listOf(4..13, 17..17)
        val defaultColor = Color(0xFF111111)
        val highlightBackground = Color(0xFF22C55E)
        val highlightText = Color(0xFFF9F9F9)

        val text = buildHighlightedDiffLineText(
            content = content,
            highlightRanges = ranges,
            defaultColor = defaultColor,
            highlightBackground = highlightBackground,
            highlightTextColor = highlightText
        )

        assertEquals(content, text.text)
        val highlights = text.spanStyles.filter { it.item.background == highlightBackground }
        assertEquals(listOf(4 to 14, 17 to 18), highlights.map { it.start to it.end })
        assertTrue(highlights.all { it.item.color == highlightText })
        assertTrue(highlights.all { it.item.fontWeight == FontWeight.SemiBold })
        assertTrue(
            text.spanStyles
                .filter { it.item.background == Color.Unspecified }
                .all { it.item.color == defaultColor }
        )
    }

    @Test
    fun highlightedSpansDefaultToTheBodyColorAndIncludeWhitespace() {
        val content = "a b c"
        val highlightBackground = Color(0xFF166534)

        val text = buildHighlightedDiffLineText(
            content = content,
            highlightRanges = listOf(1..3),
            defaultColor = Color(0xFF1B1B1B),
            highlightBackground = highlightBackground
        )

        assertEquals(content, text.text)
        val highlight = text.spanStyles.single { it.item.background == highlightBackground }
        assertEquals(" b ", content.substring(highlight.start, highlight.end))
        assertEquals(Color(0xFF1B1B1B), highlight.item.color)
        assertEquals(FontWeight.SemiBold, highlight.item.fontWeight)
    }

    @Test
    fun highlightedSpansStayEmptyForEmptyContentAndClampRanges() {
        val defaultColor = Color(0xFF1B1B1B)
        val highlightBackground = Color(0xFF166534)
        val highlightText = Color(0xFFF9F9F9)

        val empty = buildHighlightedDiffLineText(
            content = "",
            highlightRanges = listOf(0..5),
            defaultColor = defaultColor,
            highlightBackground = highlightBackground,
            highlightTextColor = highlightText
        )
        assertEquals("", empty.text)
        assertTrue(empty.spanStyles.isEmpty())

        val clamped = buildHighlightedDiffLineText(
            content = "abc",
            highlightRanges = listOf(-2..1, 2..99),
            defaultColor = defaultColor,
            highlightBackground = highlightBackground,
            highlightTextColor = highlightText
        )
        assertEquals("abc", clamped.text)
        val spans = clamped.spanStyles.filter { it.item.background == highlightBackground }
        assertEquals(listOf(0 to 2, 2 to 3), spans.map { it.start to it.end })
        assertTrue(spans.all { it.item.color == highlightText })
    }
}
