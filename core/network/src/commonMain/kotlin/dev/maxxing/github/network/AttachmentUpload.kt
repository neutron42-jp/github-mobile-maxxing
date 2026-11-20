package dev.maxxing.github.network

import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/** Largest image GitHub accepts for a user attachment (the official gh CLI bound). */
const val MAX_IMAGE_ATTACHMENT_BYTES: Long = 10L * 1024 * 1024

/**
 * Largest video the GitHub CLI attempts to upload. The real limit depends on the account plan,
 * so the server can still refuse a smaller file; this only bounds the local read.
 */
const val MAX_VIDEO_ATTACHMENT_BYTES: Long = 100L * 1024 * 1024

/** Upper bound on any attachment read from the platform picker, so an unsupported file is not buffered whole. */
const val MAX_ATTACHMENT_READ_BYTES: Long = MAX_VIDEO_ATTACHMENT_BYTES

/** The only origin and path the user-attachment upload credential is authorized for. */
internal const val USER_ATTACHMENT_UPLOAD_URL: String = "https://uploads.github.com/user-attachments/assets"
internal const val USER_ATTACHMENT_UPLOAD_HOST: String = "uploads.github.com"
internal const val USER_ATTACHMENT_UPLOAD_PATH: String = "/user-attachments/assets"
internal const val USER_ATTACHMENT_PUBLIC_HOST: String = "github.com"
internal const val USER_ATTACHMENT_PUBLIC_PATH: String = "/user-attachments/assets/"

enum class AttachmentKind { IMAGE, VIDEO }

data class AttachmentFormat(
    val extension: String,
    val contentType: String,
    val kind: AttachmentKind
)

/**
 * Extensions accepted by GitHub user attachments, mapped to the content type the endpoint expects.
 * The extension is authoritative: the picker-provided MIME type is never used to disguise the bytes.
 */
internal val SUPPORTED_ATTACHMENT_FORMATS: Map<String, AttachmentFormat> = listOf(
    AttachmentFormat("png", "image/png", AttachmentKind.IMAGE),
    AttachmentFormat("jpg", "image/jpeg", AttachmentKind.IMAGE),
    AttachmentFormat("jpeg", "image/jpeg", AttachmentKind.IMAGE),
    AttachmentFormat("gif", "image/gif", AttachmentKind.IMAGE),
    AttachmentFormat("webp", "image/webp", AttachmentKind.IMAGE),
    AttachmentFormat("svg", "image/svg+xml", AttachmentKind.IMAGE),
    AttachmentFormat("mp4", "video/mp4", AttachmentKind.VIDEO),
    AttachmentFormat("mov", "video/quicktime", AttachmentKind.VIDEO),
    AttachmentFormat("webm", "video/webm", AttachmentKind.VIDEO)
).associateBy { it.extension }

sealed interface AttachmentValidation {
    data class Valid(val format: AttachmentFormat) : AttachmentValidation
    data object Empty : AttachmentValidation
    data class UnsupportedType(val extension: String?) : AttachmentValidation
    data class TooLarge(val format: AttachmentFormat, val sizeBytes: Long) : AttachmentValidation
}

data class UploadedAttachment(
    val fileName: String,
    val contentType: String,
    val url: String,
    val markdownSnippet: String
)

sealed class AttachmentException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Empty : AttachmentException("The selected file is empty.")

    class UnsupportedType(val fileName: String) :
        AttachmentException("The file type of $fileName cannot be attached through the GitHub user-attachments API.")

    class TooLarge(val fileName: String, val kind: AttachmentKind, val sizeBytes: Long) :
        AttachmentException("$fileName is too large to attach.")

    class WriteAccessRequired :
        AttachmentException("Attaching files requires write access to the repository.")

    class UnsupportedCredential :
        AttachmentException("The current sign-in method cannot attach files.")

    class Rejected(val statusCode: Int, val detail: String?) :
        AttachmentException("GitHub rejected the attachment (${statusCode}).")

    class Redirected(val statusCode: Int) :
        AttachmentException("GitHub answered the attachment request with an unexpected redirect (${statusCode}).")

    class InvalidResponse :
        AttachmentException("GitHub did not return a valid user-attachments URL.")

    class Network(cause: Throwable) : AttachmentException("The attachment upload failed.", cause)
}

/** Returns the normalized extension of [fileName], or null when it has none. */
internal fun attachmentExtension(fileName: String): String? {
    val base = fileName.trim().substringAfterLast('/').substringAfterLast('\\')
    val dot = base.lastIndexOf('.')
    if (dot < 0 || dot == base.lastIndex) return null
    return base.substring(dot + 1).lowercase()
}

fun attachmentFormatFor(fileName: String): AttachmentFormat? =
    attachmentExtension(fileName)?.let { SUPPORTED_ATTACHMENT_FORMATS[it] }

/**
 * Validates a picked file before any bytes are uploaded.
 *
 * The size limits are inclusive, matching the GitHub CLI and GitHub's documented per-kind bounds.
 * General files (PDF, ZIP, text, ...) are deliberately rejected even though the GitHub web editor
 * can attach some of them: the user-attachments endpoint accepts only the formats listed in
 * [SUPPORTED_ATTACHMENT_FORMATS].
 */
fun validateAttachmentFile(fileName: String, sizeBytes: Long): AttachmentValidation {
    val format = attachmentFormatFor(fileName)
        ?: return AttachmentValidation.UnsupportedType(attachmentExtension(fileName))
    if (sizeBytes <= 0L) return AttachmentValidation.Empty
    val maxBytes = when (format.kind) {
        AttachmentKind.IMAGE -> MAX_IMAGE_ATTACHMENT_BYTES
        AttachmentKind.VIDEO -> MAX_VIDEO_ATTACHMENT_BYTES
    }
    if (sizeBytes > maxBytes) return AttachmentValidation.TooLarge(format, sizeBytes)
    return AttachmentValidation.Valid(format)
}

/**
 * Validates the canonical asset URL GitHub returns. Only an exact `https://github.com/user-attachments/assets/<uuid>`
 * URL may be interpolated into Markdown, so a compromised or unexpected response cannot inject another origin.
 */
internal fun validateUserAttachmentUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val url = runCatching { Url(raw) }.getOrNull() ?: return null
    if (url.protocol != URLProtocol.HTTPS) return null
    if (url.host != USER_ATTACHMENT_PUBLIC_HOST) return null
    if (url.port != DEFAULT_PORT && url.port != 443) return null
    if (!url.user.isNullOrEmpty() || !url.password.isNullOrEmpty()) return null
    if (url.parameters.names().isNotEmpty()) return null
    if (!url.fragment.isNullOrEmpty()) return null
    val uuid = url.encodedPath.removePrefix(USER_ATTACHMENT_PUBLIC_PATH)
    if (url.encodedPath != USER_ATTACHMENT_PUBLIC_PATH + uuid) return null
    if (!USER_ASSET_UUID_REGEX.matches(uuid)) return null
    return raw
}

private val USER_ASSET_UUID_REGEX =
    Regex("""^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$""")

/**
 * Builds the Markdown for an uploaded asset. Images get escaped alt text; videos are a bare URL,
 * which is the syntax GitHub renders as a player.
 */
fun formatAttachmentMarkdown(fileName: String, contentType: String, url: String): String {
    val cleanUrl = url.trim()
    val kind = SUPPORTED_ATTACHMENT_FORMATS.values
        .firstOrNull { it.contentType.equals(contentType.substringBefore(';').trim(), ignoreCase = true) }
        ?.kind
    return if (kind == AttachmentKind.IMAGE) {
        "![${escapeAttachmentAlt(fileName)}]($cleanUrl)"
    } else {
        cleanUrl
    }
}

internal fun escapeAttachmentAlt(fileName: String): String {
    val base = fileName.trim().substringAfterLast('/').substringAfterLast('\\')
    val withoutExtension = base.substringBeforeLast('.', base).replace('.', ' ')
    val alt = withoutExtension.ifBlank { "attachment" }
    return buildString(alt.length) {
        for (ch in alt) {
            when (ch) {
                '\\' -> append("\\\\")
                '[' -> append("\\[")
                ']' -> append("\\]")
                '\n', '\r' -> append(' ')
                else -> append(ch)
            }
        }
    }
}
