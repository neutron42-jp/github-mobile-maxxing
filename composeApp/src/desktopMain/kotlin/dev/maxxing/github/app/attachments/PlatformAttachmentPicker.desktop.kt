package dev.maxxing.github.app.attachments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import dev.maxxing.github.network.AttachmentValidation
import dev.maxxing.github.network.MAX_ATTACHMENT_READ_BYTES
import dev.maxxing.github.network.attachmentFormatFor
import dev.maxxing.github.network.validateAttachmentFile
import dev.maxxing.github.ui.i18n.appStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import javax.swing.JFileChooser

private val DESKTOP_EXT_MIME_TYPES = mapOf(
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "svg" to "image/svg+xml",
    "mp4" to "video/mp4",
    "mov" to "video/quicktime",
    "webm" to "video/webm"
)

@Composable
actual fun rememberMediaAttachmentPicker(
    onPicked: (AttachmentPickResult) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    val strings by rememberUpdatedState(appStrings())
    return remember(scope) {
        {
            scope.launch {
                val chosenFile: File? = withContext(Dispatchers.Swing) {
                    runCatching {
                        val chooser = JFileChooser().apply {
                            dialogTitle = strings.editorAttachChooserTitle
                            isAcceptAllFileFilterUsed = true
                            isFileHidingEnabled = false
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            chooser.selectedFile
                        } else {
                            null
                        }
                    }.getOrNull()
                }
                if (chosenFile == null) {
                    currentOnPicked(AttachmentPickResult.Cancelled)
                    return@launch
                }
                withContext(NonCancellable) {
                    val result = withContext(Dispatchers.IO) {
                        readDesktopFile(chosenFile)
                    }
                    currentOnPicked(result)
                }
            }
        }
    }
}

private fun readDesktopFile(file: File): AttachmentPickResult {
    if (!file.isFile || !file.canRead()) return AttachmentPickResult.Failed
    val name = file.name.ifBlank { "attachment" }
    val format = attachmentFormatFor(name) ?: return AttachmentPickResult.Unsupported
    val length = file.length()
    if (length > MAX_ATTACHMENT_READ_BYTES) return AttachmentPickResult.TooLarge(format.kind)
    when (val validation = validateAttachmentFile(name, length)) {
        AttachmentValidation.Empty -> return AttachmentPickResult.Empty
        is AttachmentValidation.UnsupportedType -> return AttachmentPickResult.Unsupported
        is AttachmentValidation.TooLarge -> return AttachmentPickResult.TooLarge(validation.format.kind)
        is AttachmentValidation.Valid -> Unit
    }
    val bytes = runCatching { file.readBytes() }.getOrNull() ?: return AttachmentPickResult.Failed
    if (bytes.isEmpty()) return AttachmentPickResult.Empty
    val ext = file.extension.lowercase()
    val mimeType = runCatching { Files.probeContentType(file.toPath()) }.getOrNull()
        ?: DESKTOP_EXT_MIME_TYPES[ext]
        ?: format.contentType
    return AttachmentPickResult.Selected(
        PickedMediaAttachment(
            fileName = name,
            mimeType = mimeType,
            bytes = bytes
        )
    )
}
