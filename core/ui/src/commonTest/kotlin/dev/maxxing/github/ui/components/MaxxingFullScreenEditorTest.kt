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

    @Test
    fun appendAttachmentMarkdownPreservesDraftAndSeparatesSnippets() {
        val image = "![shot.png](https://github.com/user-attachments/assets/11111111-1111-1111-1111-111111111111)"
        val video = "https://github.com/user-attachments/assets/22222222-2222-2222-2222-222222222222"

        assertEquals(image, appendAttachmentMarkdown("", image))
        assertEquals(image, appendAttachmentMarkdown("   \n", image))
        assertEquals("Repro steps\n\n$image", appendAttachmentMarkdown("Repro steps", image))
        assertEquals("Repro steps\n\n$image", appendAttachmentMarkdown("Repro steps\n", image))
        assertEquals("Repro steps\n\n$image", appendAttachmentMarkdown("Repro steps\n\n", image))
        assertEquals("Repro\n\n$image\n\n$video", appendAttachmentMarkdown("Repro\n\n$image", video))
        assertEquals("Repro steps", appendAttachmentMarkdown("Repro steps", " \n "))
    }
}
