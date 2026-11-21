package dev.maxxing.github.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTableUtilsTest {

    @Test
    fun narrowTableColumnsExpandEvenlyToFillAvailableWidth() {
        val widths = computeMarkdownTableColumnWidthsDp(
            naturalColumnWidthsDp = listOf(80f, 100f),
            availableWidthDp = 360f
        )
        assertEquals(listOf(170f, 190f), widths)
        assertEquals(360f, widths.sum())
    }

    @Test
    fun wideMultiColumnTableExceedsViewportSoHorizontalScrollActivates() {
        val widths = computeMarkdownTableColumnWidthsDp(
            naturalColumnWidthsDp = listOf(110f, 130f, 140f, 120f),
            availableWidthDp = 360f
        )
        assertEquals(listOf(110f, 130f, 140f, 120f), widths)
        assertTrue(widths.sum() > 360f)
    }

    @Test
    fun veryWideCellColumnClampsAtMaxWidthSoTextWrapsInsteadOfScrollingForever() {
        val widths = computeMarkdownTableColumnWidthsDp(
            naturalColumnWidthsDp = listOf(70f, 900f),
            availableWidthDp = 360f,
            minColWidthDp = 64f,
            maxColWidthDp = 260f
        )
        assertEquals(listOf(85f, 275f), widths)
    }

    @Test
    fun splitMarkdownIntoSectionsDoesNotSplitInsideLongTable() {
        val tableHeader = "| Col A | Col B |\n| --- | --- |"
        val tableRows = (1..140).joinToString("\n") { "| item_$it | value_$it |" }
        val markdown = "$tableHeader\n$tableRows\n\nTrailing paragraph ${"x".repeat(2500)}"
        val sections = splitMarkdownIntoSections(markdown, maxLinesPerSection = 40, minCharsToSplit = 100)
        // The blank line after the 140-row table splits cleanly after the table, never mid-table.
        assertEquals(2, sections.size)
        assertTrue(sections[0].contains("| item_140 | value_140 |"))
        assertTrue(sections[0].startsWith("| Col A | Col B |"))
    }
}
