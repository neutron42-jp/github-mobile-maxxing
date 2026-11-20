package dev.maxxing.github.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HTML_IMG_REGEX = Regex("""<img\b([^>]*)/?>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_ANCHOR_IMG_REGEX = Regex(
    """<a\b([^>]*)>\s*(?:</?(?:picture|source|p|div|center|span)\b[^>]*>\s*)*(<img\b[^>]*>)(?:\s*</?(?:picture|source|p|div|center|span)\b[^>]*>)*\s*</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val HTML_ATTR_REGEX = Regex("""([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")
private val HTML_PICTURE_SOURCE_REGEX = Regex("""</?(?:picture|source)\b[^>]*>""", RegexOption.IGNORE_CASE)
private val HTML_BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val HTML_ALIGN_WRAPPER_REGEX = Regex("""</?(?:p|div|center)\b[^>]*>""", RegexOption.IGNORE_CASE)
private val INLINE_CODE_SPAN_REGEX = Regex("""(`+)([^`]|[^`].*?[^`])\1""", RegexOption.DOT_MATCHES_ALL)
private val EXPLICIT_SCHEME_REGEX = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*):(.*)$""")
private val REFERENCE_DEF_LINE_REGEX = Regex("""^[ ]{0,3}\[([^\]]+)\]:[ \t]*(.+)$""")
private val REFERENCE_USAGE_REGEX = Regex("""\[([^\[\]]*)\]\[([^\[\]]+)\]""")
private val COLLAPSED_OR_SHORTCUT_REF_REGEX = Regex("""\[([^\[\]]+)\](?:\[\])?""")
private val GITHUB_BLOB_OR_RAW_REGEX = Regex(
    """^https?://github\.com/([^/]+)/([^/]+)/(?:blob|raw)/(.+)$""",
    RegexOption.IGNORE_CASE
)

private val ALLOWED_IMAGE_SCHEMES = setOf("http", "https")
private val ALLOWED_LINK_SCHEMES = setOf("http", "https", "mailto")

private fun hasControlChars(text: String): Boolean =
    text.any { it.code < 0x20 || it.code == 0x7F }

/**
 * Validates that a Markdown image URI uses an explicit `http://` or `https://` scheme
 * with a non-blank host and no control characters, rejecting `data:`, `file:`, `content:`,
 * `javascript:`, and unresolved relative paths.
 */
fun sanitizeMarkdownImageUri(rawUrl: String): String? {
    val trimmed = rawUrl.trim().removePrefix("<").removeSuffix(">").trim()
    if (trimmed.isEmpty() || hasControlChars(trimmed)) return null
    val schemeMatch = EXPLICIT_SCHEME_REGEX.matchEntire(trimmed) ?: return null
    val scheme = schemeMatch.groupValues[1].lowercase()
    if (scheme !in ALLOWED_IMAGE_SCHEMES) return null
    val rest = schemeMatch.groupValues[2]
    if (!rest.startsWith("//")) return null
    val authority = rest.removePrefix("//").substringBefore('/').substringBefore('?').substringBefore('#')
    if (authority.isBlank() || '@' in authority) return null
    return trimmed.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
}

/**
 * Validates that a Markdown hyperlink URI uses a safe external scheme (`http`, `https`, or `mailto`)
 * with no control characters before forwarding to the platform [UriHandler].
 */
fun sanitizeMarkdownLinkUri(rawUrl: String): String? {
    val trimmed = rawUrl.trim().removePrefix("<").removeSuffix(">").trim()
    if (trimmed.isEmpty() || hasControlChars(trimmed)) return null
    val schemeMatch = EXPLICIT_SCHEME_REGEX.matchEntire(trimmed) ?: return null
    val scheme = schemeMatch.groupValues[1].lowercase()
    if (scheme !in ALLOWED_LINK_SCHEMES) return null
    if (scheme == "mailto") {
        val address = schemeMatch.groupValues[2].substringBefore('?')
        return if ('@' in address && address.isNotBlank()) trimmed else null
    }
    val rest = schemeMatch.groupValues[2]
    if (!rest.startsWith("//")) return null
    val authority = rest.removePrefix("//").substringBefore('/').substringBefore('?').substringBefore('#')
    if (authority.isBlank() || '@' in authority) return null
    return trimmed
}

private fun escapeMarkdownAltText(alt: String): String =
    alt.replace("\r", " ")
        .replace("\n", " ")
        .trim()
        .replace("\\", "\\\\")
        .replace("[", "\\[")
        .replace("]", "\\]")

private fun decodeHtmlEntities(text: String): String {
    if ('&' !in text) return text
    return text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&#x2F;", "/", ignoreCase = true)
}

private fun parseHtmlAttributes(attrsRaw: String): Map<String, String> {
    val attrs = mutableMapOf<String, String>()
    for (attr in HTML_ATTR_REGEX.findAll(attrsRaw)) {
        val key = attr.groupValues[1].lowercase()
        val rawValue = attr.groupValues[2].ifEmpty {
            attr.groupValues[3].ifEmpty { attr.groupValues[4] }
        }
        attrs[key] = decodeHtmlEntities(rawValue)
    }
    return attrs
}

private fun convertHtmlImgToMarkdown(
    attrsRaw: String,
    repoOwner: String?,
    repoName: String?,
    branchRef: String?,
    documentPath: String
): String {
    val attrs = parseHtmlAttributes(attrsRaw)
    val src = attrs["src"].orEmpty().ifBlank { attrs["data-canonical-src"].orEmpty() }.trim()
    if (src.isEmpty()) return ""
    val alt = escapeMarkdownAltText(attrs["alt"].orEmpty())
    val resolved = resolveGitHubImageUrl(
        rawUrl = src,
        repoOwner = repoOwner,
        repoName = repoName,
        branchRef = branchRef,
        documentPath = documentPath
    )
    return if (resolved.isEmpty()) alt else "![$alt]($resolved)"
}

internal fun resolveGitHubImageUrl(
    rawUrl: String,
    repoOwner: String? = null,
    repoName: String? = null,
    branchRef: String? = null,
    documentPath: String = ""
): String {
    val trimmed = rawUrl.trim().removePrefix("<").removeSuffix(">").trim()
    if (trimmed.isBlank() || hasControlChars(trimmed)) return ""
    if (trimmed.startsWith("#")) return ""

    if (trimmed.startsWith("//")) {
        return sanitizeMarkdownImageUri("https:$trimmed").orEmpty()
    }

    GITHUB_BLOB_OR_RAW_REGEX.matchEntire(trimmed)?.let { match ->
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val rest = match.groupValues[3]
        return sanitizeMarkdownImageUri("https://raw.githubusercontent.com/$owner/$repo/$rest").orEmpty()
    }

    if (EXPLICIT_SCHEME_REGEX.matches(trimmed)) {
        return sanitizeMarkdownImageUri(trimmed).orEmpty()
    }

    val owner = repoOwner?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
    val repo = repoName?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
    val ref = branchRef?.trim()?.takeIf { it.isNotEmpty() } ?: "HEAD"

    val resolvedPath = if (trimmed.startsWith("/")) {
        trimmed.trimStart('/')
    } else {
        val baseDir = documentPath.trim('/').substringBeforeLast('/', "")
        val rawSegments = buildList {
            if (baseDir.isNotEmpty()) addAll(baseDir.split('/'))
            addAll(trimmed.split('/'))
        }
        val normalized = mutableListOf<String>()
        for (segment in rawSegments) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (normalized.isNotEmpty()) normalized.removeAt(normalized.lastIndex)
                else -> normalized += segment
            }
        }
        normalized.joinToString("/")
    }

    return sanitizeMarkdownImageUri("https://raw.githubusercontent.com/$owner/$repo/$ref/$resolvedPath").orEmpty()
}

private fun normalizeOutsideInlineCode(
    text: String,
    transform: (String) -> String
): String {
    if ('`' !in text) return transform(text)
    val sb = StringBuilder(text.length)
    var cursor = 0
    for (match in INLINE_CODE_SPAN_REGEX.findAll(text)) {
        if (match.range.first > cursor) {
            sb.append(transform(text.substring(cursor, match.range.first)))
        }
        sb.append(match.value)
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        sb.append(transform(text.substring(cursor)))
    }
    return sb.toString()
}

private fun normalizeMarkdownSegment(
    segment: String,
    repoOwner: String?,
    repoName: String?,
    branchRef: String?,
    documentPath: String
): String {
    var result = segment
    if ('<' in result) {
        if (result.contains("<a", ignoreCase = true) && result.contains("<img", ignoreCase = true)) {
            result = HTML_ANCHOR_IMG_REGEX.replace(result) { match ->
                val anchorAttrs = parseHtmlAttributes(match.groupValues[1])
                val imgTag = match.groupValues[2]
                val imgAttrsRaw = HTML_IMG_REGEX.find(imgTag)?.groupValues?.get(1).orEmpty()
                val imgMarkdown = convertHtmlImgToMarkdown(imgAttrsRaw, repoOwner, repoName, branchRef, documentPath)
                val href = sanitizeMarkdownLinkUri(anchorAttrs["href"].orEmpty())
                when {
                    imgMarkdown.isEmpty() -> ""
                    href != null && imgMarkdown.startsWith("![") -> "[$imgMarkdown]($href)"
                    else -> imgMarkdown
                }
            }
        }
        if (result.contains("<picture", ignoreCase = true) || result.contains("<source", ignoreCase = true)) {
            result = result.replace(HTML_PICTURE_SOURCE_REGEX, "")
        }
        if (result.contains("<br", ignoreCase = true)) {
            result = result.replace(HTML_BR_REGEX, "\n")
        }
        if (result.contains("<p", ignoreCase = true) ||
            result.contains("<div", ignoreCase = true) ||
            result.contains("<center", ignoreCase = true)
        ) {
            result = result.replace(HTML_ALIGN_WRAPPER_REGEX, "\n")
        }
        if (result.contains("<img", ignoreCase = true)) {
            result = HTML_IMG_REGEX.replace(result) { match ->
                convertHtmlImgToMarkdown(match.groupValues[1], repoOwner, repoName, branchRef, documentPath)
            }
        }
    }

    if (result.contains("![")) {
        result = rewriteMarkdownImages(result) { alt, url ->
            val resolved = resolveGitHubImageUrl(
                rawUrl = url,
                repoOwner = repoOwner,
                repoName = repoName,
                branchRef = branchRef,
                documentPath = documentPath
            )
            if (resolved.isEmpty()) escapeMarkdownAltText(alt) else "![$alt]($resolved)"
        }
    }
    return result
}

private fun rewriteMarkdownImages(
    text: String,
    transform: (alt: String, url: String) -> String
): String {
    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val start = text.indexOf("![", i)
        if (start < 0) {
            sb.append(text, i, text.length)
            break
        }
        sb.append(text, i, start)
        val parsed = parseInlineMarkdownImageAt(text, start)
        if (parsed != null) {
            sb.append(transform(parsed.alt, parsed.url))
            i = parsed.endExclusive
        } else {
            sb.append("![")
            i = start + 2
        }
    }
    return sb.toString()
}

private data class ParsedInlineMarkdownImage(
    val alt: String,
    val url: String,
    val endExclusive: Int
)

private fun parseInlineMarkdownImageAt(text: String, start: Int): ParsedInlineMarkdownImage? {
    var cursor = start + 2
    var altEnd = -1
    var bracketDepth = 1
    while (cursor < text.length) {
        val ch = text[cursor]
        if (ch == '\\' && cursor + 1 < text.length) {
            cursor += 2
            continue
        }
        if (ch == '[') {
            bracketDepth++
        } else if (ch == ']') {
            bracketDepth--
            if (bracketDepth == 0) {
                altEnd = cursor
                break
            }
        } else if (ch == '\n' && cursor + 1 < text.length && text[cursor + 1] == '\n') {
            return null
        }
        cursor++
    }
    if (altEnd < 0 || altEnd + 1 >= text.length || text[altEnd + 1] != '(') return null

    var p = altEnd + 2
    while (p < text.length && text[p].isWhitespace()) p++
    if (p >= text.length) return null

    val url: String
    if (text[p] == '<') {
        val closeAngle = text.indexOf('>', p + 1)
        if (closeAngle < 0) return null
        url = text.substring(p + 1, closeAngle)
        p = closeAngle + 1
    } else {
        val urlStart = p
        var parenDepth = 0
        while (p < text.length) {
            val c = text[p]
            if (c == '\\' && p + 1 < text.length) {
                p += 2
                continue
            }
            if (c == '(') {
                parenDepth++
            } else if (c == ')') {
                if (parenDepth == 0) break
                parenDepth--
            } else if (c.isWhitespace()) {
                break
            }
            p++
        }
        url = text.substring(urlStart, p)
    }
    if (url.isBlank()) return null

    while (p < text.length && text[p].isWhitespace()) p++
    if (p < text.length && (text[p] == '"' || text[p] == '\'')) {
        val quote = text[p]
        p++
        while (p < text.length && text[p] != quote) {
            if (text[p] == '\\' && p + 1 < text.length) p += 2 else p++
        }
        if (p >= text.length) return null
        p++
        while (p < text.length && text[p].isWhitespace()) p++
    }
    if (p >= text.length || text[p] != ')') return null

    return ParsedInlineMarkdownImage(
        alt = text.substring(start + 2, altEnd),
        url = url,
        endExclusive = p + 1
    )
}

/**
 * A CommonMark fenced-code delimiter. The closing fence must repeat the same character at
 * least as many times as the opening fence, so a three-backtick line inside a four-backtick
 * fence is literal content instead of the end of the block.
 */
private data class MarkdownFence(val marker: Char, val length: Int)

/** Reads a fence delimiter after any leading whitespace, as the previous trimStart-based check did. */
private fun markdownFenceAt(line: String): MarkdownFence? {
    val indent = line.indexOfFirst { it != ' ' && it != '\t' }.let { if (it < 0) line.length else it }
    val marker = line.getOrNull(indent) ?: return null
    if (marker != '`' && marker != '~') return null
    var length = 0
    while (indent + length < line.length && line[indent + length] == marker) length++
    return if (length >= 3) MarkdownFence(marker, length) else null
}

/**
 * A closing fence repeats the opening character at least as many times and carries no info
 * string. A shorter run or a run followed by text stays inside the fenced block.
 */
private fun isMarkdownFenceClosing(line: String, fence: MarkdownFence): Boolean {
    val indent = line.indexOfFirst { it != ' ' && it != '\t' }.let { if (it < 0) line.length else it }
    var length = 0
    while (indent + length < line.length && line[indent + length] == fence.marker) length++
    if (length < fence.length) return false
    return line.substring(indent + length).all { it == ' ' || it == '\t' || it == '\r' }
}

fun normalizeGitHubMarkdown(
    content: String,
    repoOwner: String? = null,
    repoName: String? = null,
    branchRef: String? = null,
    documentPath: String = ""
): String {
    if (content.isBlank()) return content
    val lines = content.split('\n')
    val out = StringBuilder(content.length)
    val plainBuffer = StringBuilder()
    var activeFence: MarkdownFence? = null
    var hasEmittedLine = false

    fun appendOutLine(lineText: String) {
        if (hasEmittedLine) out.append('\n')
        out.append(lineText)
        hasEmittedLine = true
    }

    fun flushPlainBuffer() {
        if (plainBuffer.isEmpty()) return
        val normalized = normalizeOutsideInlineCode(plainBuffer.toString()) { segment ->
            normalizeMarkdownSegment(segment, repoOwner, repoName, branchRef, documentPath)
        }
        appendOutLine(normalized)
        plainBuffer.setLength(0)
    }

    for (line in lines) {
        if (activeFence == null) {
            val openingFence = markdownFenceAt(line)
            if (openingFence != null) {
                flushPlainBuffer()
                activeFence = openingFence
                appendOutLine(line)
            } else {
                if (plainBuffer.isNotEmpty()) plainBuffer.append('\n')
                plainBuffer.append(line)
            }
        } else {
            appendOutLine(line)
            if (isMarkdownFenceClosing(line, activeFence)) {
                activeFence = null
            }
        }
    }
    flushPlainBuffer()
    return out.toString()
}

/**
 * Splits large markdown documents into independent sections outside fenced code blocks
 * so Compose LazyColumn/LazyList only composes and measures visible chunks.
 */
fun splitMarkdownIntoSections(
    markdown: String,
    maxLinesPerSection: Int = 40,
    minCharsToSplit: Int = 2400
): List<String> {
    if (markdown.isBlank()) return emptyList()
    if (markdown.length < minCharsToSplit) return listOf(markdown)

    val lines = markdown.lines()
    if (lines.size <= maxLinesPerSection) return listOf(markdown)

    val sections = mutableListOf<String>()
    val current = StringBuilder()
    var currentLineCount = 0
    var activeFence: MarkdownFence? = null

    fun flushCurrent() {
        val text = current.toString().trim()
        if (text.isNotEmpty()) sections += text
        current.setLength(0)
        currentLineCount = 0
    }

    for (line in lines) {
        val trimmed = line.trimStart()
        val openingFence = markdownFenceAt(line)

        if (activeFence == null) {
            val isHeading = trimmed.startsWith("#")
            val shouldSplitBefore =
                (isHeading && currentLineCount >= 8) ||
                    (trimmed.isEmpty() && currentLineCount >= maxLinesPerSection) ||
                    (currentLineCount >= maxLinesPerSection * 3)
            if (shouldSplitBefore) {
                flushCurrent()
            }
            if (openingFence != null) {
                activeFence = openingFence
            }
        } else if (isMarkdownFenceClosing(line, activeFence)) {
            activeFence = null
        }

        if (currentLineCount > 0) current.append('\n')
        current.append(line)
        currentLineCount++
    }
    flushCurrent()
    if (sections.isEmpty()) return listOf(markdown)
    if (sections.size == 1) return sections

    // Preserve markdown reference link/image definitions across split sections.
    val refDefs = linkedMapOf<String, String>()
    var defFence: MarkdownFence? = null
    for (line in lines) {
        if (defFence == null) {
            val opening = markdownFenceAt(line)
            if (opening != null) {
                defFence = opening
            } else {
                REFERENCE_DEF_LINE_REGEX.matchEntire(line)?.let { match ->
                    val key = match.groupValues[1].lowercase()
                    if (key !in refDefs) refDefs[key] = line
                }
            }
        } else if (isMarkdownFenceClosing(line, defFence)) {
            defFence = null
        }
    }
    if (refDefs.isEmpty()) return sections

    return sections.map { section ->
        val localDefs = section.lines()
            .mapNotNull { REFERENCE_DEF_LINE_REGEX.matchEntire(it)?.groupValues?.get(1)?.lowercase() }
            .toSet()
        val needed = buildSet {
            REFERENCE_USAGE_REGEX.findAll(section).forEach { add(it.groupValues[2].lowercase()) }
            COLLAPSED_OR_SHORTCUT_REF_REGEX.findAll(section).forEach { add(it.groupValues[1].lowercase()) }
        }.filter { it in refDefs && it !in localDefs }
        if (needed.isEmpty()) {
            section
        } else {
            buildString {
                append(section)
                append("\n\n")
                needed.forEachIndexed { idx, key ->
                    if (idx > 0) append('\n')
                    append(refDefs.getValue(key))
                }
            }
        }
    }
}

@Composable
fun rememberNormalizedMarkdownSections(
    content: String,
    repoOwner: String? = null,
    repoName: String? = null,
    branchRef: String? = null,
    documentPath: String = ""
): List<String> {
    if (content.isBlank()) return emptyList()
    val smallSync = remember(content, repoOwner, repoName, branchRef, documentPath) {
        if (content.length <= 2400) {
            val norm = normalizeGitHubMarkdown(content, repoOwner, repoName, branchRef, documentPath)
            splitMarkdownIntoSections(norm)
        } else null
    }
    if (smallSync != null) return smallSync

    val sections by produceState(
        initialValue = remember(content) {
            splitMarkdownIntoSections(content.take(4000), maxLinesPerSection = 35, minCharsToSplit = 1000)
        },
        content,
        repoOwner,
        repoName,
        branchRef,
        documentPath
    ) {
        value = withContext(Dispatchers.Default) {
            val norm = normalizeGitHubMarkdown(content, repoOwner, repoName, branchRef, documentPath)
            splitMarkdownIntoSections(norm)
        }
    }
    return sections
}

@Composable
fun GitHubMarkdownPreNormalized(
    normalizedContent: String,
    modifier: Modifier = Modifier
) {
    if (normalizedContent.isBlank()) return
    val baseUriHandler = LocalUriHandler.current
    val safeUriHandler = remember(baseUriHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                sanitizeMarkdownLinkUri(uri)?.let { baseUriHandler.openUri(it) }
            }
        }
    }
    val markdownState = rememberMarkdownState(
        content = normalizedContent,
        retainState = true
    )
    CompositionLocalProvider(LocalUriHandler provides safeUriHandler) {
        Markdown(
            markdownState = markdownState,
            colors = markdownColor(
                text = MaterialTheme.colorScheme.onSurface,
                codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
                inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
                dividerColor = MaterialTheme.colorScheme.outlineVariant
            ),
            typography = markdownTypography(
                h1 = MaterialTheme.typography.headlineSmall,
                h2 = MaterialTheme.typography.titleLarge,
                h3 = MaterialTheme.typography.titleMedium,
                paragraph = MaterialTheme.typography.bodyMedium,
                code = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            ),
            imageTransformer = GitHubImageTransformer,
            modifier = modifier.fillMaxWidth()
        )
    }
}

@Composable
fun GitHubMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    repoOwner: String? = null,
    repoName: String? = null,
    branchRef: String? = null,
    documentPath: String = ""
) {
    if (content.isBlank()) return
    val syncNormalized = remember(content, repoOwner, repoName, branchRef, documentPath) {
        if (content.length <= 3500) {
            normalizeGitHubMarkdown(
                content = content,
                repoOwner = repoOwner,
                repoName = repoName,
                branchRef = branchRef,
                documentPath = documentPath
            )
        } else null
    }
    val asyncNormalized by produceState(
        initialValue = syncNormalized ?: content,
        content,
        repoOwner,
        repoName,
        branchRef,
        documentPath
    ) {
        if (syncNormalized == null) {
            value = withContext(Dispatchers.Default) {
                normalizeGitHubMarkdown(
                    content = content,
                    repoOwner = repoOwner,
                    repoName = repoName,
                    branchRef = branchRef,
                    documentPath = documentPath
                )
            }
        }
    }
    val textToRender = syncNormalized ?: asyncNormalized
    val sections = remember(textToRender) {
        if (textToRender.length > 6000) splitMarkdownIntoSections(textToRender) else listOf(textToRender)
    }
    if (sections.size == 1) {
        GitHubMarkdownPreNormalized(normalizedContent = sections.first(), modifier = modifier)
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            sections.forEach { section ->
                GitHubMarkdownPreNormalized(normalizedContent = section)
            }
        }
    }
}
