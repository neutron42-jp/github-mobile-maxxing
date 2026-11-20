package dev.maxxing.github.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownBlockTest {
    @Test
    fun resolvesRelativeHtmlAndGithubBlobImagesInReadmeAndSubdirs() {
        val raw = """
            <p align="center">
              <picture>
                <source srcset="docs/dark.png" media="(prefers-color-scheme: dark)">
                <img src="./docs/banner.png" alt="Project Banner" width="400" />
              </picture>
            </p>
            ![Logo](/assets/logo.svg)
            ![Demo](https://github.com/octocat/hello/blob/main/media/demo.gif)
            ![Ext](https://img.shields.io/badge/build-passing-brightgreen.svg)
        """.trimIndent()

        val normalizedRoot = normalizeGitHubMarkdown(
            content = raw,
            repoOwner = "octocat",
            repoName = "hello",
            branchRef = "main",
            documentPath = "README.md"
        )

        assertTrue("![Project Banner](https://raw.githubusercontent.com/octocat/hello/main/docs/banner.png)" in normalizedRoot)
        assertTrue("![Logo](https://raw.githubusercontent.com/octocat/hello/main/assets/logo.svg)" in normalizedRoot)
        assertTrue("![Demo](https://raw.githubusercontent.com/octocat/hello/main/media/demo.gif)" in normalizedRoot)
        assertTrue("![Ext](https://img.shields.io/badge/build-passing-brightgreen.svg)" in normalizedRoot)

        val subDoc = "![Diagram](../img/arch.png)"
        val normalizedSub = normalizeGitHubMarkdown(
            content = subDoc,
            repoOwner = "octocat",
            repoName = "hello",
            branchRef = "dev",
            documentPath = "docs/guide/intro.md"
        )
        assertEquals(
            "![Diagram](https://raw.githubusercontent.com/octocat/hello/dev/docs/img/arch.png)",
            normalizedSub
        )
    }

    @Test
    fun `splitMarkdownIntoSections splits large readmes while preserving fenced code blocks`() {
        val codeBlock = buildString {
            appendLine("# Section One")
            repeat(15) { appendLine("Paragraph line $it explaining usage.") }
            appendLine("```kotlin")
            appendLine("# Not a heading inside fence")
            repeat(55) { appendLine("val x$it = $it") }
            appendLine("```")
            appendLine("")
            appendLine("## Section Two")
            repeat(20) { appendLine("More docs $it.") }
        }
        val sections = splitMarkdownIntoSections(
            markdown = codeBlock,
            maxLinesPerSection = 25,
            minCharsToSplit = 200
        )
        assertTrue(sections.size >= 2, "Expected large markdown to split into multiple sections")
        val blockWithFence = sections.first { "val x0 = 0" in it }
        assertTrue("val x54 = 54" in blockWithFence, "Fenced code block must remain intact inside one section")
    }

    @Test
    fun normalizesAnchorWrappedImgEntitiesParenthesizedUrlsAndReferenceImages() {
        val raw = """
            <a href="https://github.com/octo/app"><img src="https://example.com/img.png?a=1&amp;b=2" alt="Badge" /></a>
            ![Screen](https://example.com/screen_(1).png)
            ![Space](<https://example.com/my shot.png> 'title')
            ![Banner][banner-ref]
            ${"\n".repeat(45)}
            [banner-ref]: https://example.com/banner.png
        """.trimIndent()

        val normalized = normalizeGitHubMarkdown(
            content = raw,
            repoOwner = "octo",
            repoName = "app",
            branchRef = "main"
        )
        assertTrue("[![Badge](https://example.com/img.png?a=1&b=2)](https://github.com/octo/app)" in normalized)
        assertTrue("![Screen](https://example.com/screen_%281%29.png)" in normalized)
        assertTrue("![Space](https://example.com/my%20shot.png)" in normalized)

        val padded = buildString {
            appendLine("![Banner][banner-ref]")
            repeat(20) { appendLine("Paragraph line $it explaining usage in detail with lots of text padding.") }
            appendLine()
            appendLine("## Section Two")
            repeat(15) { appendLine("Second section line $it explaining more usage details.") }
            appendLine("[banner-ref]: https://example.com/banner.png")
        }
        val sections = splitMarkdownIntoSections(padded, maxLinesPerSection = 15, minCharsToSplit = 200)
        assertTrue(sections.size >= 2)
        val firstSection = sections.first { "![Banner][banner-ref]" in it }
        assertTrue("[banner-ref]: https://example.com/banner.png" in firstSection)
    }
}
