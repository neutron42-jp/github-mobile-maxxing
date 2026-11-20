@file:OptIn(ExperimentalForeignApi::class)

package dev.maxxing.github.app.attachments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import dev.maxxing.github.auth.IosSharePresenter
import dev.maxxing.github.network.MAX_ATTACHMENT_BYTES
import dev.maxxing.github.ui.i18n.appStrings
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.getBytes
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerImageURL
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

@Composable
actual fun rememberMediaAttachmentPicker(
    onPicked: (AttachmentPickResult) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    val strings by rememberUpdatedState(appStrings())
    return remember(scope) {
        {
            presentIosAttachmentChooser(
                title = strings.editorAttachChooserTitle,
                photoVideoLabel = strings.editorAttachPickPhotoVideo,
                browseFilesLabel = strings.editorAttachPickFile,
                cancelLabel = strings.cancel
            ) { url ->
                if (url == null) {
                    currentOnPicked(AttachmentPickResult.Cancelled)
                } else {
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            readIosFileUrl(url)
                        }
                        currentOnPicked(result)
                    }
                }
            }
        }
    }
}

private var activePickerDelegate: NSObject? = null

private fun topMostPresenter(): UIViewController? {
    var top = IosSharePresenter.currentController() ?: return null
    while (true) {
        val next = top.presentedViewController ?: break
        top = next
    }
    if (top.isBeingDismissed()) return null
    if (top.viewIfLoaded?.window == null) return null
    return top
}

private fun presentIosAttachmentChooser(
    title: String,
    photoVideoLabel: String,
    browseFilesLabel: String,
    cancelLabel: String,
    onUrl: (NSURL?) -> Unit
) {
    val presenter = topMostPresenter()
    if (presenter == null) {
        onUrl(null)
        return
    }
    val sheet = UIAlertController.alertControllerWithTitle(
        title = title,
        message = null,
        preferredStyle = UIAlertControllerStyleActionSheet
    )
    sheet.addAction(
        UIAlertAction.actionWithTitle(photoVideoLabel, UIAlertActionStyleDefault) {
            presentIosMediaPicker(onUrl)
        }
    )
    sheet.addAction(
        UIAlertAction.actionWithTitle(browseFilesLabel, UIAlertActionStyleDefault) {
            presentIosDocumentPicker(onUrl)
        }
    )
    sheet.addAction(
        UIAlertAction.actionWithTitle(cancelLabel, UIAlertActionStyleCancel) {
            onUrl(null)
        }
    )
    sheet.popoverPresentationController?.let { popover ->
        val anchor = presenter.viewIfLoaded
        if (anchor != null) {
            popover.sourceView = anchor
        }
    }
    presenter.presentViewController(sheet, animated = true, completion = null)
}

private fun presentIosMediaPicker(onUrl: (NSURL?) -> Unit) {
    val presenter = topMostPresenter()
    if (presenter == null ||
        !UIImagePickerController.isSourceTypeAvailable(
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        )
    ) {
        presentIosDocumentPicker(onUrl)
        return
    }
    val delegate = object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
        override fun imagePickerController(
            picker: UIImagePickerController,
            didFinishPickingMediaWithInfo: Map<Any?, *>
        ) {
            val pickedUrl = (didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL)
                ?: (didFinishPickingMediaWithInfo[UIImagePickerControllerImageURL] as? NSURL)
            picker.dismissViewControllerAnimated(true) {
                activePickerDelegate = null
                onUrl(pickedUrl)
            }
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            picker.dismissViewControllerAnimated(true) {
                activePickerDelegate = null
                onUrl(null)
            }
        }
    }
    activePickerDelegate = delegate
    val controller = UIImagePickerController().apply {
        sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        mediaTypes = listOf("public.image", "public.movie")
        this.delegate = delegate
    }
    presenter.presentViewController(controller, animated = true, completion = null)
}

private fun presentIosDocumentPicker(onUrl: (NSURL?) -> Unit) {
    val presenter = topMostPresenter()
    if (presenter == null) {
        onUrl(null)
        return
    }
    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>
        ) {
            val first = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            activePickerDelegate = null
            onUrl(first)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            activePickerDelegate = null
            onUrl(null)
        }
    }
    activePickerDelegate = delegate
    val picker = UIDocumentPickerViewController(
        forOpeningContentTypes = listOf(UTTypeItem),
        asCopy = true
    ).apply {
        allowsMultipleSelection = false
        this.delegate = delegate
    }
    presenter.presentViewController(picker, animated = true, completion = null)
}

private fun readIosFileUrl(url: NSURL): AttachmentPickResult {
    val securityScoped = url.startAccessingSecurityScopedResource()
    try {
        val data = NSData.dataWithContentsOfURL(url) ?: return AttachmentPickResult.Failed
        val size = data.length.toLong()
        if (size <= 0L) return AttachmentPickResult.Failed
        if (size > MAX_ATTACHMENT_BYTES) return AttachmentPickResult.TooLarge
        val bytes = ByteArray(size.toInt())
        bytes.usePinned { pinned ->
            data.getBytes(pinned.addressOf(0), length = data.length)
        }
        val fileName = url.lastPathComponent?.takeIf { it.isNotBlank() } ?: "attachment"
        val mimeType = inferMimeFromExtension(fileName.substringAfterLast('.', "").lowercase())
        return AttachmentPickResult.Selected(
            PickedMediaAttachment(
                fileName = fileName,
                mimeType = mimeType,
                bytes = bytes
            )
        )
    } finally {
        if (securityScoped) {
            url.stopAccessingSecurityScopedResource()
        }
    }
}

private fun inferMimeFromExtension(ext: String): String = when (ext) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    "webm" -> "video/webm"
    "pdf" -> "application/pdf"
    "zip" -> "application/zip"
    "txt", "log" -> "text/plain"
    "json" -> "application/json"
    else -> "application/octet-stream"
}
