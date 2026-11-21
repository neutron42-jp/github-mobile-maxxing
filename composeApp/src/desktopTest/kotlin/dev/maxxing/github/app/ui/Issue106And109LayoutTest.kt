@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package dev.maxxing.github.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.IssueOrPullDetail
import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.UserSummary
import dev.maxxing.github.model.WorkflowRunSummary
import dev.maxxing.github.ui.components.ScrollableMarkdownTableFrame
import dev.maxxing.github.ui.theme.MaxxingTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Issue106And109LayoutTest {

    @Test
    fun allocateBranchChipWidthsPreservesShortBaseBranchAndClampsLongHeadBranch() {
        val (headW, baseW) = allocateBranchChipWidths(
            headIntrinsicPx = 320,
            baseIntrinsicPx = 56,
            availablePx = 280
        )
        assertEquals(56, baseW)
        assertEquals(224, headW)
    }

    @Test
    fun allocateBranchChipWidthsKeepsBothWhenTheyFit() {
        val (headW, baseW) = allocateBranchChipWidths(
            headIntrinsicPx = 100,
            baseIntrinsicPx = 60,
            availablePx = 280
        )
        assertEquals(100, headW)
        assertEquals(60, baseW)
    }

    @Test
    fun longPrBranchKeepsDiffStatsOnSingleLineRowBelowAt360dp() {
        val detail = IssueOrPullDetail(
            id = "pr_109",
            number = 109,
            title = "PR layout with a very long branch name",
            body = "",
            kind = IssueOrPullKind.PULL_REQUEST,
            state = IssueOrPullState.OPEN,
            repoFullName = "neutron42-jp/github-mobile-maxxing",
            repoOwner = "neutron42-jp",
            repoName = "github-mobile-maxxing",
            author = UserSummary("neutron42-jp", ""),
            createdAt = "2026-11-21T07:00:00Z",
            updatedAt = "2026-11-21T07:00:00Z",
            headRefName = "fix/guard-gist-renames-with-incomplete-list",
            baseRefName = "main",
            headRepoOwner = "fork-user",
            headRepoName = "github-mobile-maxxing",
            additions = 295,
            deletions = 34
        )
        val openedBranches = mutableListOf<Triple<String, String, String>>()
        var branchBounds: Rect? = null

        val scene = ImageComposeScene(
            width = 360,
            height = 640,
            density = Density(1f)
        ) {
            MaxxingTheme(
                useDynamicColor = false,
                darkThemeOverride = false,
                language = AppLanguage.DEFAULT
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    DetailHeaderCard(
                        item = detail,
                        onOpenBranch = { owner, repo, branch ->
                            openedBranches += Triple(owner, repo, branch)
                        },
                        onEditLabels = {},
                        onEditAssignees = {},
                        onEditMilestone = {},
                        onRequestReviewers = {},
                        onToggleDraft = {}
                    )
                }
            }
        }
        try {
            scene.render()
            scene.render()
            // Walk semantics tree to verify both branch texts and single-line diff stats exist without vertical wrapping.
            val textNodes = mutableListOf<Pair<String, Rect>>()
            fun walk(node: androidx.compose.ui.semantics.SemanticsNode) {
                val texts = node.config.getOrNull(SemanticsProperties.Text)
                if (!texts.isNullOrEmpty()) {
                    textNodes += texts.joinToString("") { it.text } to node.boundsInRoot
                }
                node.children.forEach { walk(it) }
            }
            scene.semanticsOwners.forEach { walk(it.rootSemanticsNode) }

            val headNode = textNodes.firstOrNull { it.first == detail.headRefName }
            val baseNode = textNodes.firstOrNull { it.first == detail.baseRefName }
            val diffNode = textNodes.firstOrNull { it.first == "+295 / -34" }

            assertTrue(headNode != null, "Expected head branch chip in semantics tree")
            assertTrue(baseNode != null, "Expected base branch chip in semantics tree")
            assertTrue(diffNode != null, "Expected diff stats in semantics tree")

            // Single-line chip & single-line diff stats height must remain compact (< 28px at 1x density).
            assertTrue(headNode.second.height <= 28f, "Head branch wrapped unexpectedly: ${headNode.second}")
            assertTrue(baseNode.second.height <= 28f, "Base branch wrapped unexpectedly: ${baseNode.second}")
            assertTrue(diffNode.second.height <= 28f, "Diff stats wrapped vertically: ${diffNode.second}")

            // Diff stats must sit strictly below the branch row.
            branchBounds = headNode.second
            assertTrue(
                diffNode.second.top >= branchBounds.bottom,
                "Expected diff stats (${diffNode.second}) below branch row ($branchBounds)"
            )
            // Must fit within the 360dp viewport.
            assertTrue(baseNode.second.right <= 360f, "Base chip overflowed viewport: ${baseNode.second}")
            assertTrue(diffNode.second.right <= 360f, "Diff stats overflowed viewport: ${diffNode.second}")

            // Invoke branch navigation clicks via semantics.
            fun findAndClick(label: String) {
                var clicked = false
                fun findNode(node: androidx.compose.ui.semantics.SemanticsNode) {
                    if (clicked) return
                    val hasText = node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
                    val onClick = node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)
                    if (hasText && onClick != null) {
                        onClick.action?.invoke()
                        clicked = true
                        return
                    }
                    node.children.forEach { findNode(it) }
                }
                scene.semanticsOwners.forEach { findNode(it.rootSemanticsNode) }
                assertTrue(clicked, "Expected clickable node for '$label'")
            }

            findAndClick(detail.headRefName!!)
            findAndClick(detail.baseRefName!!)
            assertEquals(
                listOf(
                    Triple("fork-user", "github-mobile-maxxing", "fix/guard-gist-renames-with-incomplete-list"),
                    Triple("neutron42-jp", "github-mobile-maxxing", "main")
                ),
                openedBranches
            )
        } finally {
            scene.close()
        }
    }

    @Test
    fun workflowRunCardClampsLongBranchNameWithoutOverflowingViewport() {
        val run = WorkflowRunSummary(
            id = 1L,
            runNumber = 42,
            name = "CI Build and Test",
            displayTitle = "Verify branch chip",
            headBranch = "fix/very-long-branch-name-that-previously-pushed-event-label-offscreen",
            headSha = "abc1234",
            event = "pull_request",
            status = "completed",
            conclusion = "success",
            createdAt = ""
        )
        val scene = ImageComposeScene(width = 360, height = 160, density = Density(1f)) {
            MaxxingTheme(useDynamicColor = false, darkThemeOverride = false, language = AppLanguage.DEFAULT) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    WorkflowRunSummaryCard(run = run, onClick = {}, onOpenBranch = {})
                }
            }
        }
        try {
            scene.render()
            scene.render()
            val textNodes = mutableListOf<Pair<String, Rect>>()
            fun walk(node: androidx.compose.ui.semantics.SemanticsNode) {
                val texts = node.config.getOrNull(SemanticsProperties.Text)
                if (!texts.isNullOrEmpty()) {
                    textNodes += texts.joinToString("") { it.text } to node.boundsInRoot
                }
                node.children.forEach { walk(it) }
            }
            scene.semanticsOwners.forEach { walk(it.unmergedRootSemanticsNode) }

            val branchNode = textNodes.firstOrNull { it.first == run.headBranch }
            val eventNode = textNodes.firstOrNull { it.first == "pull_request" }
            assertTrue(branchNode != null)
            assertTrue(eventNode != null)
            assertTrue(branchNode.second.height <= 28f, "Branch chip wrapped: ${branchNode.second}")
            assertTrue(eventNode.second.right <= 360f, "Event label overflowed 360dp viewport: ${eventNode.second}")
        } finally {
            scene.close()
        }
    }

    @Test
    fun scrollableMarkdownTableFrameSupportsHorizontalScrollAndMultiLineCells() {
        val scrollState = ScrollState(initial = 0)
        val headers = listOf("Col 1", "Col 2", "Col 3", "Col 4", "Col 5")
        val rows = listOf(
            listOf("Alpha", "Beta", "Gamma", "Delta", "Epsilon"),
            listOf("Long cell text that wraps cleanly", "Two", "Three", "Four", "Five")
        )
        var longCellLines = 0
        var longCellOverflow = true

        val scene = ImageComposeScene(width = 360, height = 400, density = Density(1f)) {
            MaxxingTheme(useDynamicColor = false, darkThemeOverride = false, language = AppLanguage.DEFAULT) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    ScrollableMarkdownTableFrame(
                        columnCount = 5,
                        rowCount = rows.size,
                        hasHeader = true,
                        naturalColumnWidthsDp = listOf(180f, 110f, 110f, 110f, 110f),
                        horizontalScrollState = scrollState
                    ) { rowIdx, colIdx, isHeader ->
                        val text = if (isHeader) headers[colIdx] else rows[rowIdx][colIdx]
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = Int.MAX_VALUE,
                            overflow = TextOverflow.Clip,
                            onTextLayout = { result ->
                                if (!isHeader && rowIdx == 1 && colIdx == 0) {
                                    longCellLines = result.lineCount
                                    longCellOverflow = result.hasVisualOverflow
                                }
                            }
                        )
                    }
                }
            }
        }
        try {
            scene.render()
            scene.render()
            assertTrue(
                scrollState.maxValue > 0,
                "Expected wide 5-column table to be horizontally scrollable (maxValue=${scrollState.maxValue})"
            )
            assertTrue(longCellLines >= 1, "Expected wrapped text layout to render, got $longCellLines lines")
            assertFalse(longCellOverflow, "Expected no ellipsis/visual overflow on wrapped table cell")
        } finally {
            scene.close()
        }
    }
}
