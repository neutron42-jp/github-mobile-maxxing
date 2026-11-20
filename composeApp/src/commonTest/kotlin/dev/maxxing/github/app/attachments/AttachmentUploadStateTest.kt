package dev.maxxing.github.app.attachments

import dev.maxxing.github.network.AttachmentException
import dev.maxxing.github.network.AttachmentKind
import dev.maxxing.github.network.GitHubApiException
import dev.maxxing.github.ui.i18n.EnglishStrings
import dev.maxxing.github.ui.i18n.JapaneseStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class AttachmentUploadStateTest {

    @Test
    fun mapsLocalValidationAndFileKindsToLocalizedMessages() {
        assertEquals(
            EnglishStrings.editorAttachImageTooLarge,
            tooLargeMessage(AttachmentKind.IMAGE, EnglishStrings)
        )
        assertEquals(
            EnglishStrings.editorAttachVideoTooLarge,
            tooLargeMessage(AttachmentKind.VIDEO, EnglishStrings)
        )
        assertEquals(
            JapaneseStrings.editorAttachVideoTooLarge,
            tooLargeMessage(AttachmentKind.VIDEO, JapaneseStrings)
        )
        assertEquals(
            EnglishStrings.editorAttachEmpty,
            attachmentFailureMessage(AttachmentException.Empty(), EnglishStrings)
        )
        assertEquals(
            EnglishStrings.editorAttachUnsupportedType,
            attachmentFailureMessage(AttachmentException.UnsupportedType("notes.pdf"), EnglishStrings)
        )
    }

    @Test
    fun mapsPermissionCredentialAndServerErrorsToActionableWebFallback() {
        assertEquals(
            EnglishStrings.editorAttachWritePermission,
            attachmentFailureMessage(AttachmentException.WriteAccessRequired(), EnglishStrings)
        )
        assertEquals(
            EnglishStrings.editorAttachCredentialUnsupported,
            attachmentFailureMessage(AttachmentException.UnsupportedCredential(), EnglishStrings)
        )
        assertEquals(
            JapaneseStrings.editorAttachRejected,
            attachmentFailureMessage(AttachmentException.Rejected(422, "bad"), JapaneseStrings)
        )
        assertEquals(
            EnglishStrings.editorAttachRedirected,
            attachmentFailureMessage(AttachmentException.Redirected(302), EnglishStrings)
        )
        assertEquals(
            EnglishStrings.editorAttachUnexpectedResponse,
            attachmentFailureMessage(AttachmentException.InvalidResponse(), EnglishStrings)
        )

        // 404 from the user-attachments endpoint also means the credential cannot write.
        assertEquals(
            EnglishStrings.editorAttachWritePermission,
            attachmentFailureMessage(GitHubApiException(404, "Not Found"), EnglishStrings)
        )
        assertEquals(
            JapaneseStrings.editorAttachCredentialUnsupported,
            attachmentFailureMessage(GitHubApiException(401, "Bad credentials"), JapaneseStrings)
        )
    }

    @Test
    fun knownMessagesStayLocalizedAndMentionGitHubWeb() {
        val messages = listOf(
            EnglishStrings.editorAttachUnsupportedType,
            EnglishStrings.editorAttachWritePermission,
            EnglishStrings.editorAttachCredentialUnsupported,
            EnglishStrings.editorAttachRejected
        )
        for (message in messages) {
            assertEquals(true, message.contains("GitHub Web"), message)
        }
        assertEquals(false, JapaneseStrings.editorAttachWritePermission == EnglishStrings.editorAttachWritePermission)
        assertEquals(false, JapaneseStrings.editorAttachUnsupportedType == EnglishStrings.editorAttachUnsupportedType)
    }
}
