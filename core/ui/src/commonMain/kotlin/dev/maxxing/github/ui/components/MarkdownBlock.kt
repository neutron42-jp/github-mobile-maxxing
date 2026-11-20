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
private val HTML_ATTR_REGEX = Regex("""([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")
private val HTML_PICTURE_SOURCE_REGEX = Regex("""</?(?:picture|source)\b[^>]*>""", RegexOption.IGNORE_CASE)
private val HTML_BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val HTML_ALIGN_WRAPPER_REGEX = Regex("""</?(?:p|div|center)\b[^>]*>""", RegexOption.IGNORE_CASE)
private val MARKDOWN_IMAGE_REGEX = Regex("""!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
private val INLINE_CODE_SPAN_REGEX = Regex("""(`+)([^`]|[^`].*?[^`])\1""", RegexOption.DOT_MATCHES_ALL)
private val EXPLICIT_SCHEME_REGEX = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*):(.*)$""")
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
                val attrsRaw = match.groupValues[1]
                val attrs = mutableMapOf<String, String>()
                for (attr in HTML_ATTR_REGEX.findAll(attrsRaw)) {
                    val key = attr.groupValues[1].lowercase()
                    val value = attr.groupValues[2].ifEmpty {
                        attr.groupValues[3].ifEmpty { attr.groupValues[4] }
                    }
                    attrs[key] = value
                }
                val src = attrs["src"].orEmpty().trim()
                if (src.isEmpty()) {
                    ""
                } else {
                    val alt = escapeMarkdownAltText(attrs["alt"].orEmpty())
                    val resolved = resolveGitHubImageUrl(
                        rawUrl = src,
                        repoOwner = repoOwner,
                        repoName = repoName,
                        branchRef = branchRef,
                        documentPath = documentPath
                    )
                    if (resolved.isEmpty()) alt else "![$alt]($resolved)"
                }
            }
        }
    }

    if (result.contains("![")) {
        result = MARKDOWN_IMAGE_REGEX.replace(result) { match ->
            val alt = match.groupValues[1]
            val url = match.groupValues[2]
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
    var activeFence: String? = null
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
        val trimmed = line.trimStart()
        val fenceMarker = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> null
        }
        if (activeFence == null) {
            if (fenceMarker != null) {
                flushPlainBuffer()
                activeFence = fenceMarker
                appendOutLine(line)
            } else {
                if (plainBuffer.isNotEmpty()) plainBuffer.append('\n')
                plainBuffer.append(line)
            }
        } else {
            appendOutLine(line)
            if (fenceMarker == activeFence) {
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
    var activeFence: String? = null

    fun flushCurrent() {
        val text = current.toString().trim()
        if (text.isNotEmpty()) sections += text
        current.setLength(0)
        currentLineCount = 0
    }

    for (line in lines) {
        val trimmed = line.trimStart()
        val fenceMarker = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> null
        }

        if (activeFence == null) {
            val isHeading = trimmed.startsWith("#")
            val shouldSplitBefore =
                (isHeading && currentLineCount >= 8) ||
                    (trimmed.isEmpty() && currentLineCount >= maxLinesPerSection) ||
                    (currentLineCount >= maxLinesPerSection * 3)
            if (shouldSplitBefore) {
                flushCurrent()
            }
            if (fenceMarker != null) {
                activeFence = fenceMarker
            }
        } else if (fenceMarker == activeFence) {
            activeFence = null
        }

        if (currentLineCount > 0) current.append('\n')
        current.append(line)
        currentLineCount++
    }
    flushCurrent()
    return if (sections.isEmpty()) listOf(markdown) else sections
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
