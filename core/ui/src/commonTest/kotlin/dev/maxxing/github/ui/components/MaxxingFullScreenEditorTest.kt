package dev.maxxing.github.ui.components

import dev.maxxing.github.ui.i18n.EnglishStrings
import dev.maxxing.github.ui.i18n.JapaneseStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class MaxxingFullScreenEditorTest {
    @Test
    fun measureEditorTextHandlesEmptySingleAndMultiLineText() {
        assertEquals(EditorTextMetrics(0, 0), measureEditorText(""))
        assertEquals("0 lines · 0 chars", formatEditorMetrics("", EnglishStrings))
        assertEquals("0 行 · 0 文字", formatEditorMetrics("", JapaneseStrings))

        assertEquals(EditorTextMetrics(1, 5), measureEditorText("hello"))
        assertEquals("1 line · 5 chars", formatEditorMetrics("hello", EnglishStrings))
        assertEquals("1 行 · 5 文字", formatEditorMetrics("hello", JapaneseStrings))

        assertEquals(EditorTextMetrics(2, 11), measureEditorText("line1\nline2"))
        assertEquals(EditorTextMetrics(3, 9), measureEditorText("a\nb\ncdefg"))
        assertEquals("3 lines · 9 chars", formatEditorMetrics("a\nb\ncdefg", EnglishStrings))
        assertEquals("3 行 · 9 文字", formatEditorMetrics("a\nb\ncdefg", JapaneseStrings))
    }
}
