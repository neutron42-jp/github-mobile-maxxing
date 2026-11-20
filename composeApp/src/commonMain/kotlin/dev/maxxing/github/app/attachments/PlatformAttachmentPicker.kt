package dev.maxxing.github.app.attachments

import androidx.compose.runtime.Composable
import dev.maxxing.github.network.AttachmentKind

data class PickedMediaAttachment(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

sealed interface AttachmentPickResult {
    data class Selected(val file: PickedMediaAttachment) : AttachmentPickResult
    data object Cancelled : AttachmentPickResult
    data object Empty : AttachmentPickResult
    data object Unsupported : AttachmentPickResult
    data class TooLarge(val kind: AttachmentKind) : AttachmentPickResult
    data object Failed : AttachmentPickResult
}

@Composable
expect fun rememberMediaAttachmentPicker(
    onPicked: (AttachmentPickResult) -> Unit
): () -> Unit
