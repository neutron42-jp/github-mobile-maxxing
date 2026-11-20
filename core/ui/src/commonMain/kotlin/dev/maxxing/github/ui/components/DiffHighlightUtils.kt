package dev.maxxing.github.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import dev.maxxing.github.model.DiffHunk
import dev.maxxing.github.model.DiffLineKind

private const val WORD_DIFF_MAX_LINE_CHARS = 1000
private const val WORD_DIFF_MAX_TOKENS = 180

private data class DiffToken(
    val text: String,
    val start: Int,
    val end: Int,
    val isWhitespace: Boolean
)

private fun tokenizeForWordDiff(text: String): List<DiffToken> {
    if (text.isEmpty()) return emptyList()
    val tokens = ArrayList<DiffToken>(minOf(text.length, 64))
    var i = 0
    val n = text.length
    while (i < n) {
        val ch = text[i]
        val start = i
        when {
            ch.isWhitespace() -> {
                while (i < n && text[i].isWhitespace()) i++
                tokens.add(DiffToken(text.substring(start, i), start, i, isWhitespace = true))
            }
            ch.isLetterOrDigit() || ch == '_' -> {
                while (i < n && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                tokens.add(DiffToken(text.substring(start, i), start, i, isWhitespace = false))
            }
            else -> {
                i++
                tokens.add(DiffToken(text.substring(start, i), start, i, isWhitespace = false))
            }
        }
        if (tokens.size > WORD_DIFF_MAX_TOKENS) return emptyList()
    }
    return tokens
}

private fun mergeAdjacentRanges(ranges: List<IntRange>): List<IntRange> {
    if (ranges.size <= 1) return ranges
    val merged = ArrayList<IntRange>(ranges.size)
    var current = ranges[0]
    for (idx in 1 until ranges.size) {
        val next = ranges[idx]
        if (next.first <= current.last + 1) {
            current = current.first..maxOf(current.last, next.last)
        } else {
            merged.add(current)
            current = next
        }
    }
    merged.add(current)
    return merged
}

/**
 * Computes changed character ranges between [oldText] and [newText] at token/word granularity.
 * Returns `(removedRangesInOld, addedRangesInNew)`. Bounded in length and token count so
 * minified/generated diffs never stall the UI thread.
 */
fun computeWordDiffRanges(
    oldText: String,
    newText: String
): Pair<List<IntRange>, List<IntRange>> {
    if (oldText == newText || oldText.isEmpty() || newText.isEmpty()) {
        return emptyList<IntRange>() to emptyList()
    }
    if (oldText.length > WORD_DIFF_MAX_LINE_CHARS || newText.length > WORD_DIFF_MAX_LINE_CHARS) {
        return emptyList<IntRange>() to emptyList()
    }

    val oldTokens = tokenizeForWordDiff(oldText)
    val newTokens = tokenizeForWordDiff(newText)
    if (oldTokens.isEmpty() || newTokens.isEmpty()) {
        return emptyList<IntRange>() to emptyList()
    }

    val m = oldTokens.size
    val n = newTokens.size
    val dp = Array(m + 1) { IntArray(n + 1) }
    for (i in m - 1 downTo 0) {
        for (j in n - 1 downTo 0) {
            dp[i][j] = if (oldTokens[i].text == newTokens[j].text) {
                dp[i + 1][j + 1] + 1
            } else {
                maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
    }

    val oldMatched = BooleanArray(m)
    val newMatched = BooleanArray(n)
    var i = 0
    var j = 0
    var commonNonWhitespaceTokens = 0
    while (i < m && j < n) {
        if (oldTokens[i].text == newTokens[j].text) {
            oldMatched[i] = true
            newMatched[j] = true
            if (!oldTokens[i].isWhitespace) commonNonWhitespaceTokens++
            i++
            j++
        } else if (dp[i + 1][j] >= dp[i][j + 1]) {
            i++
        } else {
            j++
        }
    }

    // Completely unrelated lines (e.g. a deleted import paired with an added function)
    // should not produce noisy highlights across the whole line.
    if (commonNonWhitespaceTokens == 0) {
        return emptyList<IntRange>() to emptyList()
    }

    val oldRanges = buildList {
        for (idx in 0 until m) {
            if (!oldMatched[idx]) {
                val tok = oldTokens[idx]
                if (!tok.isWhitespace || (idx > 0 && !oldMatched[idx - 1]) || (idx + 1 < m && !oldMatched[idx + 1])) {
                    add(tok.start until tok.end)
                }
            }
        }
    }
    val newRanges = buildList {
        for (idx in 0 until n) {
            if (!newMatched[idx]) {
                val tok = newTokens[idx]
                if (!tok.isWhitespace || (idx > 0 && !newMatched[idx - 1]) || (idx + 1 < n && !newMatched[idx + 1])) {
                    add(tok.start until tok.end)
                }
            }
        }
    }

    return mergeAdjacentRanges(oldRanges) to mergeAdjacentRanges(newRanges)
}

/**
 * Pairs adjacent REMOVED and ADDED blocks within a [DiffHunk] and computes per-line
 * word-level highlight ranges keyed by line index inside `hunk.lines`.
 */
fun computeHunkWordHighlights(hunk: DiffHunk): Map<Int, List<IntRange>> {
    val lines = hunk.lines
    if (lines.isEmpty()) return emptyMap()
    val result = HashMap<Int, List<IntRange>>()
    var idx = 0
    while (idx < lines.size) {
        if (lines[idx].kind != DiffLineKind.REMOVED) {
            idx++
            continue
        }
        val removedStart = idx
        while (idx < lines.size && lines[idx].kind == DiffLineKind.REMOVED) idx++
        val removedEnd = idx

        val addedStart = idx
        while (idx < lines.size && lines[idx].kind == DiffLineKind.ADDED) idx++
        val addedEnd = idx

        val removedCount = removedEnd - removedStart
        val addedCount = addedEnd - addedStart
        if (removedCount > 0 && addedCount > 0 && removedCount <= 12 && addedCount <= 12) {
            val pairCount = minOf(removedCount, addedCount)
            for (offset in 0 until pairCount) {
                val oldIdx = removedStart + offset
                val newIdx = addedStart + offset
                val (oldRanges, newRanges) = computeWordDiffRanges(lines[oldIdx].content, lines[newIdx].content)
                if (oldRanges.isNotEmpty()) result[oldIdx] = oldRanges
                if (newRanges.isNotEmpty()) result[newIdx] = newRanges
            }
        }
    }
    return result
}

fun buildHighlightedDiffLineText(
    content: String,
    highlightRanges: List<IntRange>,
    defaultColor: Color,
    highlightBackground: Color
): AnnotatedString {
    if (highlightRanges.isEmpty() || content.isEmpty()) {
        return AnnotatedString(content)
    }
    return buildAnnotatedString {
        var cursor = 0
        for (range in highlightRanges) {
            val start = range.first.coerceIn(cursor, content.length)
            val end = (range.last + 1).coerceIn(start, content.length)
            if (start > cursor) {
                withStyle(SpanStyle(color = defaultColor)) {
                    append(content, cursor, start)
                }
            }
            if (end > start) {
                withStyle(
                    SpanStyle(
                        color = defaultColor,
                        background = highlightBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(content, start, end)
                }
            }
            cursor = end
        }
        if (cursor < content.length) {
            withStyle(SpanStyle(color = defaultColor)) {
                append(content, cursor, content.length)
            }
        }
    }
}
