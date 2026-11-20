package dev.maxxing.github.app.attachments

import androidx.compose.runtime.Composable

data class PickedMediaAttachment(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

sealed interface AttachmentPickResult {
    data class Selected(val file: PickedMediaAttachment) : AttachmentPickResult
    data object Cancelled : AttachmentPickResult
    data object TooLarge : AttachmentPickResult
    data object Failed : AttachmentPickResult
}

@Composable
expect fun rememberMediaAttachmentPicker(
    onPicked: (AttachmentPickResult) -> Unit
): () -> Unit
