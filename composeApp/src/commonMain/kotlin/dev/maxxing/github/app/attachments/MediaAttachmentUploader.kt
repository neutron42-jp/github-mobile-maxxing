package dev.maxxing.github.app.attachments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.network.AttachmentException
import dev.maxxing.github.network.AttachmentKind
import dev.maxxing.github.network.AttachmentValidation
import dev.maxxing.github.network.GitHubApiException
import dev.maxxing.github.network.UploadedAttachment
import dev.maxxing.github.network.validateAttachmentFile
import dev.maxxing.github.ui.i18n.AppStrings
import dev.maxxing.github.ui.i18n.appStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class PendingAttachment(
    val id: Int,
    val accountKey: String,
    val onSnippetReady: (String) -> Unit
)

/**
 * Owns one editor's attachment pick/upload cycle.
 *
 * The controller deliberately exposes only a [attach] callback: the caller decides which draft
 * receives the Markdown. A pick or upload in flight blocks a second one, and a result is delivered
 * only while the same destination repository and signed-in account are still current.
 */
@Stable
internal class EditorAttachmentController(
    private val scope: CoroutineScope,
    private val requestPick: (onPicked: (AttachmentPickResult) -> Unit) -> Boolean,
    private val upload: suspend (fileName: String, bytes: ByteArray) -> Result<UploadedAttachment>,
    private val onMessage: (String) -> Unit,
    private val strings: () -> AppStrings,
    private val isCurrentDestination: () -> Boolean,
    private val accountKey: () -> String
) {
    var isUploading by mutableStateOf(false)
        private set

    private var nextRequestId = 0
    private var pending: PendingAttachment? = null
    private var disposed = false

    fun attach(onSnippetReady: (String) -> Unit) {
        if (disposed || pending != null || isUploading) return
        val request = PendingAttachment(
            id = ++nextRequestId,
            accountKey = accountKey(),
            onSnippetReady = onSnippetReady
        )
        pending = request
        val launched = requestPick { result -> onPicked(request, result) }
        if (!launched && pending?.id == request.id) {
            pending = null
        }
    }

    fun dispose() {
        disposed = true
        pending = null
        isUploading = false
    }

    private fun onPicked(request: PendingAttachment, result: AttachmentPickResult) {
        if (disposed || pending?.id != request.id) return
        pending = null
        when (result) {
            AttachmentPickResult.Cancelled -> Unit
            AttachmentPickResult.Empty -> onMessage(strings().editorAttachEmpty)
            AttachmentPickResult.Unsupported -> onMessage(strings().editorAttachUnsupportedType)
            is AttachmentPickResult.TooLarge -> onMessage(tooLargeMessage(result.kind, strings()))
            AttachmentPickResult.Failed -> onMessage(strings().editorAttachFailed)
            is AttachmentPickResult.Selected -> startUpload(request, result.file)
        }
    }

    private fun startUpload(request: PendingAttachment, file: PickedMediaAttachment) {
        if (!isCurrentDestination() || accountKey() != request.accountKey) {
            onMessage(strings().editorAttachAccountChanged)
            return
        }
        when (val validation = validateAttachmentFile(file.fileName, file.bytes.size.toLong())) {
            AttachmentValidation.Empty -> {
                onMessage(strings().editorAttachEmpty)
                return
            }
            is AttachmentValidation.UnsupportedType -> {
                onMessage(strings().editorAttachUnsupportedType)
                return
            }
            is AttachmentValidation.TooLarge -> {
                onMessage(tooLargeMessage(validation.format.kind, strings()))
                return
            }
            is AttachmentValidation.Valid -> Unit
        }

        isUploading = true
        scope.launch {
            try {
                if (!isCurrentDestination() || accountKey() != request.accountKey) {
                    onMessage(strings().editorAttachAccountChanged)
                    return@launch
                }
                upload(file.fileName, file.bytes).fold(
                    onSuccess = { uploaded ->
                        if (!disposed && isCurrentDestination() && accountKey() == request.accountKey) {
                            request.onSnippetReady(uploaded.markdownSnippet)
                        } else if (!disposed) {
                            onMessage(strings().editorAttachAccountChanged)
                        }
                    },
                    onFailure = { error ->
                        if (!disposed) onMessage(attachmentFailureMessage(error, strings()))
                    }
                )
            } finally {
                isUploading = false
            }
        }
    }
}

internal fun tooLargeMessage(kind: AttachmentKind, strings: AppStrings): String = when (kind) {
    AttachmentKind.IMAGE -> strings.editorAttachImageTooLarge
    AttachmentKind.VIDEO -> strings.editorAttachVideoTooLarge
}

/**
 * Maps attachment failures to actionable localized text. GitHub answers 404 rather than 403 when a
 * token cannot write, so both are presented as a write-access/credential problem with the GitHub
 * Web fallback instead of the generic API error.
 */
internal fun attachmentFailureMessage(error: Throwable, strings: AppStrings): String = when (error) {
    is AttachmentException.Empty -> strings.editorAttachEmpty
    is AttachmentException.UnsupportedType -> strings.editorAttachUnsupportedType
    is AttachmentException.TooLarge -> tooLargeMessage(error.kind, strings)
    is AttachmentException.WriteAccessRequired -> strings.editorAttachWritePermission
    is AttachmentException.UnsupportedCredential -> strings.editorAttachCredentialUnsupported
    is AttachmentException.Rejected -> strings.editorAttachRejected
    is AttachmentException.Redirected -> strings.editorAttachRedirected
    is AttachmentException.InvalidResponse -> strings.editorAttachUnexpectedResponse
    is GitHubApiException -> when (error.statusCode) {
        401 -> strings.editorAttachCredentialUnsupported
        403, 404 -> strings.editorAttachWritePermission
        else -> strings.errorMessage(error, strings.editorAttachFailed)
    }
    else -> strings.errorMessage(error, strings.editorAttachFailed)
}

private class PickerCallbackHolder {
    var current: ((AttachmentPickResult) -> Unit)? = null

    fun begin(callback: (AttachmentPickResult) -> Unit): Boolean {
        if (current != null) return false
        current = callback
        return true
    }

    fun take(): ((AttachmentPickResult) -> Unit)? {
        val callback = current
        current = null
        return callback
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
    val currentOnMessage by rememberUpdatedState(onMessage)
    val currentOwner by rememberUpdatedState(owner)
    val currentRepo by rememberUpdatedState(repo)
    val callbackHolder = remember { PickerCallbackHolder() }
    val picker = rememberMediaAttachmentPicker { result ->
        callbackHolder.take()?.invoke(result)
    }

    val controller = remember(owner, repo, picker) {
        EditorAttachmentController(
            scope = scope,
            requestPick = { onPicked ->
                if (!callbackHolder.begin(onPicked)) {
                    false
                } else {
                    runCatching { picker() }.isSuccess.also { launched ->
                        if (!launched) callbackHolder.take()
                    }
                }
            },
            upload = { fileName, bytes ->
                container.repository.uploadAttachment(
                    targetOwner = owner,
                    targetRepo = repo,
                    fileName = fileName,
                    bytes = bytes
                )
            },
            onMessage = { message -> currentOnMessage(message) },
            strings = { strings },
            isCurrentDestination = { currentOwner == owner && currentRepo == repo },
            accountKey = {
                container.sessionManager.session.value?.userLogin?.trim()?.lowercase().orEmpty()
            }
        )
    }
    DisposableEffect(controller) {
        onDispose {
            controller.dispose()
            callbackHolder.take()
        }
    }
    return controller
}
