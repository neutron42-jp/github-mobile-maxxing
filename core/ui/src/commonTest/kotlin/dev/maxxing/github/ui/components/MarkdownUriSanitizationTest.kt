package dev.maxxing.github.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownUriSanitizationTest {

    @Test
    fun sanitizeImageAndLinkUrisRejectUnsafeSchemes() {
        assertEquals("https://example.com/a.png", sanitizeMarkdownImageUri(" https://example.com/a.png "))
        assertNull(sanitizeMarkdownImageUri("data:image/png;base64,AAAA"))
        assertNull(sanitizeMarkdownImageUri("javascript:alert(1)"))
        assertNull(sanitizeMarkdownImageUri("file:///etc/passwd"))
        assertNull(sanitizeMarkdownImageUri("content://media/external/images/1"))
        assertNull(sanitizeMarkdownImageUri("https://example.com/a\nb.png"))

        assertEquals("https://github.com/octo/app", sanitizeMarkdownLinkUri("https://github.com/octo/app"))
        assertEquals("mailto:test@example.com", sanitizeMarkdownLinkUri("mailto:test@example.com"))
        assertNull(sanitizeMarkdownLinkUri("javascript:alert(1)"))
        assertNull(sanitizeMarkdownLinkUri("data:text/html,<script>"))
        assertNull(sanitizeMarkdownLinkUri("file:///tmp/x"))
    }

    @Test
    fun normalizeGitHubMarkdownSkipsCodeBlocksAndSanitizesHtmlImgTags() {
        val input = """
            ```html
            <img src="assets/inside-fence.png" alt="code" />
            ```
            Inline `<img src="data:image/png;base64,ABC" />` example.
            Real: <img src="docs/logo.png" alt="Logo [1]" />
            Unsafe: <img src="data:image/svg+xml,<svg/>" alt="xss" />
        """.trimIndent()

        val out = normalizeGitHubMarkdown(
            content = input,
            repoOwner = "octo",
            repoName = "app",
            branchRef = "main",
            documentPath = "README.md"
        )

        assertTrue(out.contains("""<img src="assets/inside-fence.png" alt="code" />"""))
        assertTrue(out.contains("""`<img src="data:image/png;base64,ABC" />`"""))
        assertTrue(out.contains("![Logo \\[1\\]](https://raw.githubusercontent.com/octo/app/main/docs/logo.png)"))
        assertFalse(out.contains("data:image/svg+xml"))
    }
}
