package dev.maxxing.github.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * S06: drives the shared Markdown transformation with a local fixture that mixes a
 * four-backtick fence, shorter fences and image examples inside and outside the block.
 */
class MarkdownFenceFixtureTest {

    @Test
    fun `local Markdown fixture keeps fenced examples literal and converts outside examples`() {
        val fixture = javaClass.getResource("/markdown/s06-fence-preservation.md")?.readText()
            ?: fail("missing fixture src/desktopTest/resources/markdown/s06-fence-preservation.md")

        val normalized = normalizeGitHubMarkdown(
            content = fixture,
            repoOwner = "octo",
            repoName = "app",
            branchRef = "main",
            documentPath = "README.md"
        )

        assertTrue("````markdown" in normalized)
        assertTrue("```html" in normalized)
        assertTrue("""<img src="assets/inside.png" alt="Inside [1]" />""" in normalized)
        assertTrue("![Inside screenshot](assets/inside.png)" in normalized)
        assertFalse("raw.githubusercontent.com/octo/app/main/assets/inside.png" in normalized)

        assertTrue("![Outside](https://raw.githubusercontent.com/octo/app/main/docs/outside.png)" in normalized)
        assertTrue(
            "![Outside screenshot](https://raw.githubusercontent.com/octo/app/main/docs/outside.png)" in normalized
        )
    }
}
