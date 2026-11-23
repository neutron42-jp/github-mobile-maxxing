@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package dev.maxxing.github.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.ui.components.GitHubMarkdown
import dev.maxxing.github.ui.components.GitHubMarkdownPreNormalized
import dev.maxxing.github.ui.components.rememberNormalizedMarkdownSections
import dev.maxxing.github.ui.theme.MaxxingTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class MarkdownRenderRegressionTest {

    @Test
    fun gitHubMarkdownAndReadmeSectionsRenderWithoutRuntimeOrLayoutErrors() {
        val sampleReadme = """
            # github-mobile-maxxing

            A fast **GitHub** client with [links](https://github.com/neutron42-jp/github-mobile-maxxing).

            | Platform | Status |
            | --- | --- |
            | Linux | Ready |
            | Android | Ready |

            ```kotlin
            println("hello")
            ```
        """.trimIndent()
        val largeReadme = buildString {
            appendLine(sampleReadme)
            appendLine()
            repeat(60) { index ->
                appendLine("- Item $index with **bold** and `code`")
            }
        }

        val scene = ImageComposeScene(
            width = 480,
            height = 640,
            density = Density(1f)
        ) {
            MaxxingTheme(
                useDynamicColor = false,
                darkThemeOverride = false,
                language = AppLanguage.DEFAULT
            ) {
                val sections = rememberNormalizedMarkdownSections(
                    content = largeReadme,
                    repoOwner = "neutron42-jp",
                    repoName = "github-mobile-maxxing",
                    branchRef = "main",
                    documentPath = "README.md"
                )
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            GitHubMarkdown(content = "Inline **markdown** check")
                        }
                    }
                    itemsIndexed(sections) { _, section ->
                        GitHubMarkdownPreNormalized(normalizedContent = section)
                    }
                }
            }
        }
        try {
            val renderedTexts = mutableListOf<String>()
            fun collectTexts() {
                renderedTexts.clear()
                fun walk(node: androidx.compose.ui.semantics.SemanticsNode) {
                    node.config.getOrNull(SemanticsProperties.Text)?.let { list ->
                        renderedTexts += list.joinToString("") { it.text }
                    }
                    node.children.forEach { walk(it) }
                }
                scene.semanticsOwners.forEach { walk(it.rootSemanticsNode) }
            }

            for (frame in 0 until 40) {
                scene.render(frame * 16_666_667L)
                collectTexts()
                if (renderedTexts.any { it.contains("github-mobile-maxxing") } &&
                    renderedTexts.any { it.contains("Inline markdown check") }
                ) {
                    break
                }
                Thread.sleep(25)
            }

            assertTrue(
                renderedTexts.any { it.contains("github-mobile-maxxing") },
                "Expected rendered README heading in semantics tree, got: $renderedTexts"
            )
            assertTrue(
                renderedTexts.any { it.contains("Inline markdown check") },
                "Expected rendered GitHubMarkdown text in semantics tree, got: $renderedTexts"
            )
        } finally {
            scene.close()
        }
    }
}
