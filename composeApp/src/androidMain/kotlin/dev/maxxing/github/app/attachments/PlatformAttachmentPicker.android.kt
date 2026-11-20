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
import dev.maxxing.github.network.MAX_ATTACHMENT_BYTES
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
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) reportedSize = cursor.getLong(sizeIdx)
        }
    }
    if (reportedSize != null && reportedSize!! > MAX_ATTACHMENT_BYTES) {
        return AttachmentPickResult.TooLarge
    }

    val mimeType = resolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
    val resolvedName = displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
        ?: "attachment"

    val bytes = try {
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16 * 1024)
            val out = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_ATTACHMENT_BYTES) {
                    return AttachmentPickResult.TooLarge
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: return AttachmentPickResult.Failed
    } catch (_: Throwable) {
        return AttachmentPickResult.Failed
    }

    if (bytes.isEmpty()) return AttachmentPickResult.Failed
    return AttachmentPickResult.Selected(
        PickedMediaAttachment(
            fileName = resolvedName,
            mimeType = mimeType,
            bytes = bytes
        )
    )
}
