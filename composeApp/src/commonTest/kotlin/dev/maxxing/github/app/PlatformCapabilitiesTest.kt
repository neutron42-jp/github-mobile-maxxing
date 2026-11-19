package dev.maxxing.github.app

import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.NotificationSubjectType
import dev.maxxing.github.model.NotificationThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformCapabilitiesTest {
    @Test
    fun defaultsAreTheAndroidAndDesktopBehaviour() {
        val defaults = PlatformCapabilities()
        assertFalse(defaults.usesIosCopy)
        assertTrue(defaults.supportsDynamicColorToggle)
        assertTrue(defaults.supportsCustomFonts)
        assertFalse(defaults.avoidsKeyboardOnSignIn)
    }

    @Test
    fun sampleThreadHasExpectedNumberForPayloadTests() {
        assertEquals(42, sampleThread().subjectNumber)
        assertEquals(null, sampleThread(subjectType = NotificationSubjectType.CHECK_SUITE).subjectNumber)
    }

    private fun sampleThread(
        subjectType: NotificationSubjectType = NotificationSubjectType.ISSUE,
    ): NotificationThread = NotificationThread(
        id = "t1",
        unread = true,
        reason = NotificationReason.COMMENT,
        updatedAt = "v1",
        subjectTitle = "Title",
        subjectType = subjectType,
        subjectUrl = if (subjectType == NotificationSubjectType.ISSUE) {
            "https://api.github.com/repos/o/r/issues/42"
        } else {
            "https://api.github.com/repos/o/r/check-suites/9"
        },
        latestCommentUrl = null,
        repoFullName = "o/r",
        repoOwner = "o",
        repoName = "r",
    )
}
