package dev.maxxing.github.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S06: the Markdown pre-processor must remember the opening fence's character and length.
 * A shorter fence or a fence-like line with an info string is code content, not a close.
 */
class MarkdownFencePreservationTest {

    private fun normalize(input: String): String = normalizeGitHubMarkdown(
        content = input,
        repoOwner = "octo",
        repoName = "app",
        branchRef = "main",
        documentPath = "README.md"
    )

    @Test
    fun `four-backtick fence keeps a three-backtick block and image example literal`() {
        val input = """
            ````markdown
            ```html
            <img src="assets/inside.png" alt="Inside [1]" />
            ```
            ![Inside](assets/inside.png)
            ````
        """.trimIndent()

        assertEquals(input, normalize(input))
    }

    @Test
    fun `a fence-like line with an info string does not close a four-backtick fence`() {
        val input = """
            ````
            ```still-code
            ![Inside](assets/inside.png)
            ````
            ![Outside](docs/outside.png)
        """.trimIndent()

        val out = normalize(input)

        assertTrue("```still-code" in out)
        assertTrue("![Inside](assets/inside.png)" in out)
        assertFalse("raw.githubusercontent.com/octo/app/main/assets/inside.png" in out)
        assertTrue("![Outside](https://raw.githubusercontent.com/octo/app/main/docs/outside.png)" in out)
    }

    @Test
    fun `tilde fence keeps its own length`() {
        val input = """
            ~~~~markdown
            ~~~js
            ![Inside](assets/inside.png)
            ~~~
            ~~~~
            ![Outside](docs/outside.png)
        """.trimIndent()

        val out = normalize(input)

        assertTrue("~~~~markdown" in out)
        assertTrue("~~~js" in out)
        assertTrue("![Inside](assets/inside.png)" in out)
        assertFalse("raw.githubusercontent.com/octo/app/main/assets/inside.png" in out)
        assertTrue("![Outside](https://raw.githubusercontent.com/octo/app/main/docs/outside.png)" in out)
    }

    @Test
    fun `a longer closing fence still closes the block`() {
        val input = """
            ```
            ![Inside](assets/inside.png)
            `````
            ![Outside](docs/outside.png)
        """.trimIndent()

        val out = normalize(input)

        assertTrue("![Inside](assets/inside.png)" in out)
        assertFalse("raw.githubusercontent.com/octo/app/main/assets/inside.png" in out)
        assertTrue("![Outside](https://raw.githubusercontent.com/octo/app/main/docs/outside.png)" in out)
    }

    @Test
    fun `CRLF fences close only on a matching run`() {
        val input = "````\r\n```\r\n![Inside](assets/inside.png)\r\n````\r\n" +
            "![Outside](docs/outside.png)"

        val out = normalize(input)

        assertTrue("![Inside](assets/inside.png)" in out)
        assertFalse("raw.githubusercontent.com/octo/app/main/assets/inside.png" in out)
        assertTrue("![Outside](https://raw.githubusercontent.com/octo/app/main/docs/outside.png)" in out)
    }

    @Test
    fun `splitMarkdownIntoSections keeps a four-backtick block in one section`() {
        val input = buildString {
            appendLine("# Intro")
            repeat(12) { appendLine("intro $it") }
            appendLine("````markdown")
            appendLine("```kotlin")
            repeat(40) { appendLine("val x$it = $it") }
            appendLine("```")
            appendLine("![Inside](assets/inside.png)")
            appendLine("````")
            appendLine("")
            appendLine("## After")
            repeat(12) { appendLine("after $it") }
        }

        val sections = splitMarkdownIntoSections(
            markdown = input,
            maxLinesPerSection = 10,
            minCharsToSplit = 100
        )

        assertTrue(sections.size >= 2, "Expected the document to split outside the fence")
        val fencedSection = sections.first { "val x0 = 0" in it }
        assertTrue("val x39 = 39" in fencedSection, "The whole fenced block must stay in one section")
        assertTrue("![Inside](assets/inside.png)" in fencedSection)
    }
}
