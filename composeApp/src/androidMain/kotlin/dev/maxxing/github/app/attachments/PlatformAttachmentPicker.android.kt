package dev.maxxing.github.app.attachments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.maxxing.github.network.AttachmentKind
import dev.maxxing.github.network.AttachmentValidation
import dev.maxxing.github.network.MAX_ATTACHMENT_READ_BYTES
import dev.maxxing.github.network.MAX_IMAGE_ATTACHMENT_BYTES
import dev.maxxing.github.network.MAX_VIDEO_ATTACHMENT_BYTES
import dev.maxxing.github.network.attachmentFormatFor
import dev.maxxing.github.network.validateAttachmentFile
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
actual fun rememberMediaAttachmentPicker(
    onPicked: (AttachmentPickResult) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            currentOnPicked(AttachmentPickResult.Cancelled)
            return@rememberLauncherForActivityResult
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                val result = withContext(Dispatchers.IO) {
                    readUriAttachment(context, uri)
                }
                currentOnPicked(result)
            }
        }
    }
    return remember(launcher) {
        { launcher.launch(arrayOf("*/*")) }
    }
}

private fun readUriAttachment(context: Context, uri: Uri): AttachmentPickResult {
    val resolver = context.contentResolver
    var displayName: String? = null
    var reportedSize: Long? = null
    runCatching {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) reportedSize = cursor.getLong(sizeIdx)
            }
        }
    }

    val resolvedName = displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
        ?: "attachment"
    val format = attachmentFormatFor(resolvedName)
        ?: return AttachmentPickResult.Unsupported
    val knownSize = reportedSize
    if (knownSize != null && knownSize > 0L) {
        when (val validation = validateAttachmentFile(resolvedName, knownSize)) {
            AttachmentValidation.Empty -> return AttachmentPickResult.Empty
            is AttachmentValidation.UnsupportedType -> return AttachmentPickResult.Unsupported
            is AttachmentValidation.TooLarge -> return AttachmentPickResult.TooLarge(validation.format.kind)
            is AttachmentValidation.Valid -> Unit
        }
    }

    val maxBytes = when (format.kind) {
        AttachmentKind.IMAGE -> MAX_IMAGE_ATTACHMENT_BYTES
        AttachmentKind.VIDEO -> MAX_VIDEO_ATTACHMENT_BYTES
    }
    val readLimit = minOf(maxBytes, MAX_ATTACHMENT_READ_BYTES)
    val mimeType = resolver.getType(uri).orEmpty().ifBlank { format.contentType }
    val bytes = try {
        resolver.openInputStream(uri)?.use { input ->
            val initialCapacity = reportedSize
                ?.coerceIn(0L, readLimit)
                ?.toInt()
                ?: 16 * 1024
            val out = ByteArrayOutputStream(initialCapacity)
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > readLimit) return AttachmentPickResult.TooLarge(format.kind)
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: return AttachmentPickResult.Failed
    } catch (_: Throwable) {
        return AttachmentPickResult.Failed
    }
    if (bytes.isEmpty()) return AttachmentPickResult.Empty
    return AttachmentPickResult.Selected(
        PickedMediaAttachment(
            fileName = resolvedName,
            mimeType = mimeType,
            bytes = bytes
        )
    )
}
