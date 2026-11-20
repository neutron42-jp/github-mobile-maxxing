package dev.maxxing.github.app.attachments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.network.AttachmentException
import dev.maxxing.github.ui.i18n.appStrings
import kotlinx.coroutines.launch

@Stable
internal class EditorAttachmentController(
    val isUploading: Boolean,
    private val onLaunch: (onSnippetReady: (String) -> Unit) -> Unit
) {
    fun attach(onSnippetReady: (String) -> Unit) {
        onLaunch(onSnippetReady)
    }
}

@Composable
internal fun rememberEditorAttachmentController(
    container: AppContainer,
    owner: String,
    repo: String,
    onMessage: (String) -> Unit
): EditorAttachmentController {
    val scope = rememberCoroutineScope()
    val strings by rememberUpdatedState(appStrings())
    val currentOwner by rememberUpdatedState(owner)
    val currentRepo by rememberUpdatedState(repo)
    val currentOnMessage by rememberUpdatedState(onMessage)
    var isUploading by remember { mutableStateOf(false) }
    var pendingOnSnippet by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val launchPicker = rememberMediaAttachmentPicker { pickResult ->
        val callback = pendingOnSnippet
        pendingOnSnippet = null
        when (pickResult) {
            AttachmentPickResult.Cancelled -> Unit
            AttachmentPickResult.TooLarge -> currentOnMessage(strings.editorAttachTooLarge)
            AttachmentPickResult.Failed -> currentOnMessage(strings.editorAttachFailed)
            is AttachmentPickResult.Selected -> {
                val picked = pickResult.file
                scope.launch {
                    isUploading = true
                    val viewerLogin = container.viewerProfile.value?.login
                    container.repository.uploadMediaAttachment(
                        targetOwner = currentOwner,
                        targetRepo = currentRepo,
                        fileName = picked.fileName,
                        mimeType = picked.mimeType,
                        bytes = picked.bytes,
                        viewerLoginHint = viewerLogin
                    ).onSuccess { uploaded ->
                        callback?.invoke(uploaded.markdownSnippet)
                    }.onFailure { err ->
                        val msg = when (err) {
                            is AttachmentException.TooLarge -> strings.editorAttachTooLarge
                            is AttachmentException.PrivateRepoPermissionDenied ->
                                strings.editorAttachUnsupportedPrivateRepo
                            else -> strings.errorMessage(err, strings.editorAttachFailed)
                        }
                        currentOnMessage(msg)
                    }
                    isUploading = false
                }
            }
        }
    }

    return remember(isUploading, launchPicker) {
        EditorAttachmentController(
            isUploading = isUploading,
            onLaunch = { onSnippetReady ->
                if (!isUploading) {
                    pendingOnSnippet = onSnippetReady
                    launchPicker()
                }
            }
        )
    }
}
