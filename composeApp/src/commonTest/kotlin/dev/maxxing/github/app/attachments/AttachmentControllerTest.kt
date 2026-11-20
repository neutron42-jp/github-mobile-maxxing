package dev.maxxing.github.app.attachments

import dev.maxxing.github.network.AttachmentException
import dev.maxxing.github.network.UploadedAttachment
import dev.maxxing.github.ui.i18n.EnglishStrings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentControllerTest {

    private fun TestScope.controller(
        account: () -> String = { "octocat" },
        currentDestination: () -> Boolean = { true },
        onPick: ((AttachmentPickResult) -> Unit) -> Boolean = { true },
        upload: suspend (String, ByteArray) -> Result<UploadedAttachment> = { name, _ ->
            Result.success(
                UploadedAttachment(
                    fileName = name,
                    contentType = "image/png",
                    url = "https://github.com/user-attachments/assets/11111111-1111-1111-1111-111111111111",
                    markdownSnippet = "![$name](https://github.com/user-attachments/assets/11111111-1111-1111-1111-111111111111)"
                )
            )
        },
        messages: MutableList<String> = mutableListOf()
    ): EditorAttachmentController = EditorAttachmentController(
        scope = this,
        requestPick = onPick,
        upload = upload,
        onMessage = { messages += it },
        strings = { EnglishStrings },
        isCurrentDestination = currentDestination,
        accountKey = account
    )

    @Test
    fun attachIgnoresDoubleClicksAndAllowsRetryAfterCancel() = runTest {
        var picks = 0
        var callback: ((AttachmentPickResult) -> Unit)? = null
        val controller = controller(
            onPick = { onPicked ->
                picks++
                callback = onPicked
                true
            }
        )

        controller.attach { }
        controller.attach { }
        assertEquals(1, picks)
        assertTrue(controller.isUploading.not())

        requireNotNull(callback).invoke(AttachmentPickResult.Cancelled)
        controller.attach { }
        assertEquals(2, picks)
    }

    @Test
    fun successfulUploadAppendsOnceAndFailureLeavesDraftAlone() = runTest {
        val appended = mutableListOf<String>()
        var callback: ((AttachmentPickResult) -> Unit)? = null
        var fail = false
        val messages = mutableListOf<String>()
        val controller = controller(
            onPick = { onPicked ->
                callback = onPicked
                true
            },
            upload = { name, _ ->
                if (fail) {
                    Result.failure(AttachmentException.WriteAccessRequired())
                } else {
                    Result.success(
                        UploadedAttachment(
                            fileName = name,
                            contentType = "image/png",
                            url = "https://github.com/user-attachments/assets/11111111-1111-1111-1111-111111111111",
                            markdownSnippet = "![$name](https://github.com/user-attachments/assets/11111111-1111-1111-1111-111111111111)"
                        )
                    )
                }
            },
            messages = messages
        )

        controller.attach { appended += it }
        requireNotNull(callback).invoke(
            AttachmentPickResult.Selected(
                PickedMediaAttachment("shot.png", "image/png", byteArrayOf(1, 2, 3))
            )
        )
        advanceUntilIdle()
        assertEquals(1, appended.size)
        assertTrue(controller.isUploading.not())

        fail = true
        controller.attach { appended += it }
        requireNotNull(callback).invoke(
            AttachmentPickResult.Selected(
                PickedMediaAttachment("shot.png", "image/png", byteArrayOf(1, 2, 3))
            )
        )
        advanceUntilIdle()
        assertEquals(1, appended.size)
        assertEquals(EnglishStrings.editorAttachWritePermission, messages.last())
    }

    @Test
    fun accountChangeBeforeOrDuringUploadDropsTheSnippet() = runTest {
        var account = "octocat"
        var callback: ((AttachmentPickResult) -> Unit)? = null
        var uploads = 0
        val appended = mutableListOf<String>()
        val messages = mutableListOf<String>()
        val controller = controller(
            account = { account },
            onPick = { onPicked ->
                callback = onPicked
                true
            },
            upload = { name, _ ->
                uploads++
                Result.success(
                    UploadedAttachment(
                        fileName = name,
                        contentType = "image/png",
                        url = "https://github.com/user-attachments/assets/11111111-1111-1111-1111-111111111111",
                        markdownSnippet = "![$name](https://github.com/user-attachments/assets/11111111-1111-1111-1111-111111111111)"
                    )
                )
            },
            messages = messages
        )

        controller.attach { appended += it }
        account = "someone-else"
        requireNotNull(callback).invoke(
            AttachmentPickResult.Selected(
                PickedMediaAttachment("shot.png", "image/png", byteArrayOf(1))
            )
        )
        advanceUntilIdle()

        assertEquals(0, uploads)
        assertTrue(appended.isEmpty())
        assertEquals(EnglishStrings.editorAttachAccountChanged, messages.last())
    }

    @Test
    fun disposedControllerDropsLatePickerResults() = runTest {
        var callback: ((AttachmentPickResult) -> Unit)? = null
        val appended = mutableListOf<String>()
        val controller = controller(onPick = { onPicked ->
            callback = onPicked
            true
        })

        controller.attach { appended += it }
        controller.dispose()
        requireNotNull(callback).invoke(
            AttachmentPickResult.Selected(
                PickedMediaAttachment("shot.png", "image/png", byteArrayOf(1))
            )
        )
        advanceUntilIdle()

        assertTrue(appended.isEmpty())
    }
}
