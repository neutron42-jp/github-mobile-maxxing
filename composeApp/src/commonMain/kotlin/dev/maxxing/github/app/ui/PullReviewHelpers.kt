package dev.maxxing.github.app.ui

import dev.maxxing.github.model.DiffHunk
import dev.maxxing.github.model.DiffLine
import dev.maxxing.github.model.DiffLineKind
import dev.maxxing.github.model.FileChangeStatus
import dev.maxxing.github.model.FileViewedState
import dev.maxxing.github.model.PullFileChange
import dev.maxxing.github.model.PullReviewComment
import dev.maxxing.github.model.PullReviewSyncState

enum class ReviewFileCategory {
    PRIMARY,
    DELETION_ONLY,
    LOCK_OR_GENERATED
}

data class ReviewFileEntry(
    val file: PullFileChange,
    val category: ReviewFileCategory,
    val autoCollapsed: Boolean
)

private val EXACT_LOCK_FILENAMES = setOf(
    "package-lock.json",
    "yarn.lock",
    "pnpm-lock.yaml",
    "gemfile.lock",
    "podfile.lock",
    "cargo.lock",
    "poetry.lock",
    "composer.lock",
    "uv.lock",
    "flake.lock",
    "pubspec.lock",
    "gradle.lockfile",
    "mix.lock",
    "packages.resolved",
    "npm-shrinkwrap.json"
)

private val GENERATED_FILENAME_SUFFIXES = listOf(
    ".lock",
    ".lockb",
    ".lockfile",
    ".min.js",
    ".min.css",
    ".js.map",
    ".css.map",
    ".pb.go",
    ".pb.swift",
    ".pb.kt",
    ".g.dart",
    ".freezed.dart",
    ".gr.dart",
    ".snap"
)

private val GENERATED_PATH_SEGMENTS = listOf(
    "/generated/",
    "/__snapshots__/",
    "/node_modules/"
)

/**
 * Returns true for lockfiles, minified bundles, snapshot files, and generated artifacts
 * that add noise during mobile one-handed code review.
 */
fun isLockOrGeneratedFile(filename: String): Boolean {
    val normalized = filename.trim().replace('\\', '/').lowercase()
    if (normalized.isEmpty()) return false
    val base = normalized.substringAfterLast('/')
    if (base in EXACT_LOCK_FILENAMES) return true
    if (GENERATED_FILENAME_SUFFIXES.any { base.endsWith(it) }) return true
    if (base.contains(".generated.") || base.contains(".gen.")) return true
    if (base.startsWith("generated") && (base.endsWith(".kt") || base.endsWith(".java") || base.endsWith(".ts"))) {
        return true
    }
    if (GENERATED_PATH_SEGMENTS.any { segment -> normalized.contains(segment) || normalized.startsWith(segment.removePrefix("/")) }) {
        return true
    }
    return false
}

/**
 * Returns true when a file only deletes code (or is removed altogether) so it can be
 * folded and placed after active code additions/modifications.
 */
fun isDeletionOnlyFile(file: PullFileChange): Boolean {
    if (file.status == FileChangeStatus.REMOVED) return true
    if (file.additions > 0 || file.deletions == 0) return false
    if (file.hunks.isEmpty()) return true
    return file.hunks.all { hunk -> hunk.lines.none { it.kind == DiffLineKind.ADDED } }
}

/**
 * Orders changed files for mobile review:
 * 1. Primary code/content files (original order)
 * 2. Deletion-only files (original order, auto-collapsed)
 * 3. Lock/generated files (original order, auto-collapsed)
 */
fun organizeFilesForReview(files: List<PullFileChange>): List<ReviewFileEntry> {
    if (files.isEmpty()) return emptyList()
    val primary = mutableListOf<ReviewFileEntry>()
    val deletions = mutableListOf<ReviewFileEntry>()
    val generated = mutableListOf<ReviewFileEntry>()

    for (file in files) {
        when {
            isLockOrGeneratedFile(file.filename) -> {
                generated += ReviewFileEntry(
                    file = file,
                    category = ReviewFileCategory.LOCK_OR_GENERATED,
                    autoCollapsed = true
                )
            }
            isDeletionOnlyFile(file) -> {
                deletions += ReviewFileEntry(
                    file = file,
                    category = ReviewFileCategory.DELETION_ONLY,
                    autoCollapsed = true
                )
            }
            else -> {
                primary += ReviewFileEntry(
                    file = file,
                    category = ReviewFileCategory.PRIMARY,
                    autoCollapsed = file.patch == null
                )
            }
        }
    }
    return primary + deletions + generated
}

fun countViewedFiles(
    entries: List<ReviewFileEntry>,
    viewedStates: Map<String, FileViewedState>
): Int = entries.count { viewedStates[it.file.filename] == FileViewedState.VIEWED }

/**
 * Finds the next file not yet marked VIEWED after [currentIndex], wrapping around to earlier
 * files if everything after [currentIndex] is already viewed. Returns null when every file is viewed.
 */
fun findNextUnviewedFileIndex(
    entries: List<ReviewFileEntry>,
    viewedStates: Map<String, FileViewedState>,
    currentIndex: Int
): Int? {
    if (entries.isEmpty()) return null
    val start = if (currentIndex < 0) -1 else currentIndex.coerceAtMost(entries.lastIndex)
    for (offset in 1..entries.size) {
        val candidate = (start + offset) % entries.size
        if (viewedStates[entries[candidate].file.filename] != FileViewedState.VIEWED) {
            return candidate
        }
    }
    return null
}

/**
 * Extracts `(lineNumber, diffSide)` for GitHub review comments from a [DiffLine].
 * `REMOVED` lines target `"LEFT"` with `oldLineNumber`; `ADDED` and `CONTEXT` lines target `"RIGHT"` with `newLineNumber`.
 */
fun extractDiffLineNumberAndSide(line: DiffLine): Pair<Int, String>? {
    if (line.kind == DiffLineKind.META) return null
    return if (line.kind == DiffLineKind.REMOVED) {
        line.oldLineNumber?.let { it to "LEFT" }
    } else {
        line.newLineNumber?.let { it to "RIGHT" }
    }
}

/**
 * Collects up to [lineCount] contiguous lines ending at [endLineIndex] on the same diff side
 * (`RIGHT` for `ADDED`/`CONTEXT`, or single `LEFT` line for `REMOVED`) so Suggested Changes
 * pre-fills cleanly and supports multi-line suggestions.
 */
fun collectLineRangeForSuggestion(
    hunk: DiffHunk,
    endLineIndex: Int,
    lineCount: Int = 1
): Pair<Int?, List<DiffLine>> {
    val lines = hunk.lines
    if (endLineIndex !in lines.indices) return null to emptyList()
    val endLine = lines[endLineIndex]
    val targetSide = extractDiffLineNumberAndSide(endLine)?.second ?: return null to emptyList()
    if (targetSide == "LEFT") {
        // GitHub suggestions apply to the head (RIGHT) side; fall back to single-line content for reference.
        return endLine.oldLineNumber to listOf(endLine)
    }

    val clampedCount = lineCount.coerceAtLeast(1)
    val collected = mutableListOf<DiffLine>()
    var idx = endLineIndex
    while (idx >= 0 && collected.size < clampedCount) {
        val candidate = lines[idx]
        if (candidate.kind == DiffLineKind.ADDED || candidate.kind == DiffLineKind.CONTEXT) {
            if (candidate.newLineNumber != null) {
                collected.add(0, candidate)
            } else {
                break
            }
        } else {
            break
        }
        idx--
    }
    val startLineNum = collected.firstOrNull()?.newLineNumber
    return startLineNum to collected
}

/**
 * Formats a GitHub Suggested Change Markdown block (` ```suggestion `) with optional explanatory [note].
 * Uses a 4-backtick fence if [suggestedCode] itself contains triple backticks.
 */
fun buildSuggestedChangeComment(note: String, suggestedCode: String): String {
    val normalizedCode = suggestedCode.trimEnd('\r', '\n')
    val fence = if ("```" in normalizedCode) "````" else "```"
    val suggestionBlock = "$fence" + "suggestion\n$normalizedCode\n$fence"
    val cleanNote = note.trim()
    return if (cleanNote.isEmpty()) {
        suggestionBlock
    } else {
        "$cleanNote\n\n$suggestionBlock"
    }
}

/**
 * Indexes all pending + submitted comments for a given [filename] by target line number (`null` for file-level).
 */
fun collectReviewCommentsForFile(
    syncState: PullReviewSyncState?,
    filename: String
): Map<Int?, List<PullReviewComment>> {
    if (syncState == null) return emptyMap()
    val byId = linkedMapOf<String, PullReviewComment>()
    syncState.threads
        .filter { it.path == filename }
        .forEach { thread ->
            thread.comments.forEach { comment ->
                byId[comment.id] = comment.copy(
                    line = comment.line ?: thread.line,
                    originalLine = comment.originalLine ?: thread.originalLine,
                    diffSide = comment.diffSide ?: thread.diffSide
                )
            }
        }
    syncState.pendingReview?.comments
        .orEmpty()
        .filter { it.path == filename }
        .forEach { comment ->
            byId[comment.id] = comment
        }
    return byId.values.groupBy { comment ->
        if (comment.subjectType == "FILE") null else (comment.line ?: comment.originalLine)
    }
}
