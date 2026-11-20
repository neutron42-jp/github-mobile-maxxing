package dev.maxxing.github.network

import kotlin.random.Random

const val MAX_ATTACHMENT_BYTES: Int = 25 * 1024 * 1024
const val ATTACHMENT_HOST_REPO_NAME: String = "github-mobile-attachments"
const val ATTACHMENT_ORPHAN_BRANCH: String = "github-mobile-attachments"

private val IMAGE_EXTENSIONS = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "avif"
)

private val MIME_TO_EXTENSION = mapOf(
    "image/png" to "png",
    "image/jpeg" to "jpg",
    "image/jpg" to "jpg",
    "image/gif" to "gif",
    "image/webp" to "webp",
    "image/svg+xml" to "svg",
    "image/heic" to "heic",
    "image/heif" to "heif",
    "video/mp4" to "mp4",
    "video/quicktime" to "mov",
    "video/webm" to "webm",
    "video/x-matroska" to "mkv",
    "audio/mpeg" to "mp3",
    "audio/mp4" to "m4a",
    "application/pdf" to "pdf",
    "application/zip" to "zip",
    "text/plain" to "txt",
    "application/json" to "json"
)

data class UploadedAttachment(
    val fileName: String,
    val mimeType: String,
    val downloadUrl: String,
    val markdownSnippet: String
)

sealed class AttachmentException(message: String) : IllegalStateException(message) {
    class Empty : AttachmentException("Selected file is empty.")
    class TooLarge(val sizeBytes: Long) :
        AttachmentException("Selected file exceeds the 25 MB limit (${sizeBytes / (1024 * 1024)} MB).")
    class PrivateRepoPermissionDenied :
        AttachmentException("Push permission is required to attach files to a private repository.")
}

internal fun sanitizeAttachmentFileName(fileName: String, mimeType: String = ""): String {
    val base = fileName.trim().substringAfterLast('/').substringAfterLast('\\')
    val dotIdx = base.lastIndexOf('.')
    val rawStem = when {
        dotIdx > 0 -> base.substring(0, dotIdx)
        dotIdx == 0 -> ""
        else -> base
    }
    val rawExt = if (dotIdx >= 0 && dotIdx + 1 < base.length) {
        base.substring(dotIdx + 1).lowercase()
    } else {
        ""
    }
    val cleanExt = rawExt.filter { it.isLetterOrDigit() && it.code < 128 }
        .ifEmpty { MIME_TO_EXTENSION[mimeType.lowercase().substringBefore(';').trim()].orEmpty() }
    val cleanStem = buildString {
        for (ch in rawStem) {
            when {
                ch.isLetterOrDigit() && ch.code < 128 -> append(ch)
                ch == '.' || ch == '_' || ch == '-' -> append(ch)
                ch == ' ' -> append('-')
            }
        }
    }.trim('-', '.').take(80).ifEmpty { "attachment" }

    return if (cleanExt.isNotEmpty()) "$cleanStem.$cleanExt" else "$cleanStem.bin"
}

internal fun isImageAttachment(fileName: String, mimeType: String): Boolean {
    val cleanMime = mimeType.lowercase().substringBefore(';').trim()
    if (cleanMime.startsWith("image/")) return true
    val ext = fileName.trim().substringAfterLast('.', "").lowercase()
    return ext in IMAGE_EXTENSIONS
}

fun formatAttachmentMarkdown(fileName: String, mimeType: String, url: String): String {
    val displayName = fileName.trim().ifEmpty { "attachment" }
        .replace("[", "(")
        .replace("]", ")")
    val cleanUrl = url.trim().replace(" ", "%20")
    return if (isImageAttachment(displayName, mimeType)) {
        "![$displayName]($cleanUrl)"
    } else {
        "[$displayName]($cleanUrl)"
    }
}

internal fun buildAttachmentRepoPath(
    targetOwner: String,
    targetRepo: String,
    fileName: String,
    mimeType: String = "",
    randomHex: String = nextAttachmentRandomHex()
): String {
    val safeOwner = targetOwner.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "-")
    val safeRepo = targetRepo.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "-")
    val safeFile = sanitizeAttachmentFileName(fileName, mimeType)
    return "attachments/$safeOwner/$safeRepo/$randomHex-$safeFile"
}

private fun nextAttachmentRandomHex(): String {
    val bytes = Random.nextBytes(6)
    return buildString(12) {
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            append(HEX_CHARS[v ushr 4])
            append(HEX_CHARS[v and 0x0F])
        }
    }
}

private const val HEX_CHARS = "0123456789abcdef"
