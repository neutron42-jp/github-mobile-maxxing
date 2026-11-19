package dev.maxxing.github.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DeepLinkParserTest {

    // ---- valid URLs -------------------------------------------------------

    @Test
    fun strictParserAgreesWithTheShellParserOnCanonicalUrls() {
        for (url in listOf(
            "githubmaxxing://thread/octocat/hello-world/123",
            "githubmaxxing://thread/o/r/5?x=1#f",
            "githubmaxxing://thread/octo/%E3%83%AA/2",
            "githubmaxxing://thread/o/r/12/",
        )) {
            // The shell (upstream `parseAppDeepLink`) re-parses what the iOS entry accepted.
            val shell = assertIs<AppDeepLink.IssueOrPull>(parseAppDeepLink(url), url)
            assertEquals(true, shell.fromNotification, url)
            assertEquals(shell.target, parseStrictDeepLink(url), url)
        }
    }

    @Test
    fun strictParserAcceptsCaseInsensitiveSchemeAndHost() {
        assertEquals(
            IssuePullTarget("Octo", "Repo", 7),
            parseStrictDeepLink("GitHubMaxxing://THREAD/Octo/Repo/7"),
        )
    }

    @Test
    fun strictParserRejectsLooseUrls() {
        for (url in listOf(
            "githubmaxxing://thread/o/r/1/extra",
            "githubmaxxing://thread/o/r/1/2",
            "githubmaxxing://thread//o//r/1//",
            "githubmaxxing://thread//r/1",
            "githubmaxxing://thread/o//1",
            "githubmaxxing://thread/o/r/0",
            "githubmaxxing://thread/o/r/-1",
            "githubmaxxing://thread/o/r/+1",
            "githubmaxxing://thread:8080/o/r/1",
            "githubmaxxing://user@thread/o/r/1",
            "  githubmaxxing://thread/o/r/3  ",
            "githubmaxxing://thread/o/r%ZZ/1",
            "githubmaxxing://thread/o/100%/1",
            "githubmaxxing://thread/o%2Fr/r/1",
            "githubmaxxing://thread/o/r/%E3%81",
            "githubmaxxing://thread/%20/r/1",
        )) {
            assertNull(parseStrictDeepLink(url), url)
        }
    }

    @Test
    fun strictParserRejectsMalformedShapes() {
        for (url in listOf(
            null, "", "   ", "https://thread/o/r/1", "githubmaxxing:/thread/o/r/1",
            "githubmaxxing://other/o/r/1", "githubmaxxing:///o/r/1", "githubmaxxing://thread",
            "githubmaxxing://thread/o/r", "githubmaxxing://thread/o/r/", "githubmaxxing://thread/o/r/abc",
            "githubmaxxing://thread/o/r/1.5", "githubmaxxing://thread/o/r/999999999999999999",
            "githubmaxxing://thread/o/r/1//", "not a url",
        )) {
            assertNull(parseStrictDeepLink(url), url.toString())
        }
    }

    @Test
    fun strictParserDecodesPercentEncodedSegments() {
        assertEquals(
            IssuePullTarget("o", "r", 42),
            parseStrictDeepLink("githubmaxxing://thread/o/r/%34%32"),
        )
    }

    @Test
    fun notificationTapRejectsLenientOnlyDeepLinks() {
        // The lenient Android parser would open /o/r/1/extra; the iOS tap path must not.
        assertEquals(
            NotificationTapTarget.Inbox,
            parseNotificationTapTarget(
                mapOf(
                    NOTIFICATION_KEY_THREAD_ID to "t",
                    NOTIFICATION_KEY_DEEP_LINK to "githubmaxxing://thread/o/r/1/extra",
                )
            ),
        )
    }

    // ---- building deep links for notification payloads -------------------

    @Test
    fun buildThreadDeepLinkUrlOnlyForPositiveNumbers() {
        assertEquals(
            "githubmaxxing://thread/octo/repo/42",
            buildThreadDeepLinkUrl("octo", "repo", 42),
        )
        assertNull(buildThreadDeepLinkUrl("octo", "repo", null))
        assertNull(buildThreadDeepLinkUrl("octo", "repo", 0))
        assertNull(buildThreadDeepLinkUrl("octo", "repo", -3))
        assertNull(buildThreadDeepLinkUrl("", "repo", 1))
        assertNull(buildThreadDeepLinkUrl("octo", "  ", 1))
        assertNull(buildThreadDeepLinkUrl("oc/to", "repo", 1))
    }

    @Test
    fun buildNeverProducesNullNumberSegment() {
        val url = buildThreadDeepLinkUrl("octo", "repo", null)
        assertNull(url)
        assertTrue(url?.contains("/null") != true)
    }

    // ---- notification tap parsing ----------------------------------------

    @Test
    fun parsesDeepLinkPayload() {
        val payload = mapOf(
            NOTIFICATION_KEY_THREAD_ID to "thread-1",
            NOTIFICATION_KEY_DEEP_LINK to "githubmaxxing://thread/o/r/12",
        )
        assertEquals(
            NotificationTapTarget.Thread(IssuePullTarget("o", "r", 12)),
            parseNotificationTapTarget(payload),
        )
    }

    @Test
    fun parsesStructuredPayloadWhenDeepLinkIsMissing() {
        val payload = mapOf(
            NOTIFICATION_KEY_THREAD_ID to "thread-1",
            NOTIFICATION_KEY_OWNER to "o",
            NOTIFICATION_KEY_REPO to "r",
            NOTIFICATION_KEY_SUBJECT_NUMBER to "12",
        )
        assertEquals(
            NotificationTapTarget.Thread(IssuePullTarget("o", "r", 12)),
            parseNotificationTapTarget(payload),
        )
    }

    @Test
    fun subjectNumberNullFallsBackToInbox() {
        assertEquals(
            NotificationTapTarget.Inbox,
            parseNotificationTapTarget(mapOf(NOTIFICATION_KEY_THREAD_ID to "thread-1")),
        )
        assertEquals(
            NotificationTapTarget.Inbox,
            parseNotificationTapTarget(
                mapOf(
                    NOTIFICATION_KEY_THREAD_ID to "thread-1",
                    NOTIFICATION_KEY_OWNER to "o",
                    NOTIFICATION_KEY_REPO to "r",
                    NOTIFICATION_KEY_SUBJECT_NUMBER to null,
                )
            ),
        )
        assertEquals(
            NotificationTapTarget.Inbox,
            parseNotificationTapTarget(
                mapOf(
                    NOTIFICATION_KEY_THREAD_ID to "thread-1",
                    NOTIFICATION_KEY_OWNER to "o",
                    NOTIFICATION_KEY_REPO to "r",
                    NOTIFICATION_KEY_SUBJECT_NUMBER to "0",
                )
            ),
        )
    }

    @Test
    fun invalidDeepLinkStillLooksLikeNotificationAndOpensInbox() {
        assertEquals(
            NotificationTapTarget.Inbox,
            parseNotificationTapTarget(
                mapOf(
                    NOTIFICATION_KEY_THREAD_ID to "thread-1",
                    NOTIFICATION_KEY_DEEP_LINK to "githubmaxxing://thread/o/r/null",
                )
            ),
        )
        assertEquals(
            NotificationTapTarget.Inbox,
            parseNotificationTapTarget(
                mapOf(NOTIFICATION_KEY_DEEP_LINK to "https://example.com/o/r/1")
            ),
        )
    }

    @Test
    fun nonPositiveDeepLinkNumberOpensInbox() {
        assertEquals(
            NotificationTapTarget.Inbox,
            parseNotificationTapTarget(
                mapOf(
                    NOTIFICATION_KEY_THREAD_ID to "t",
                    NOTIFICATION_KEY_DEEP_LINK to "githubmaxxing://thread/o/r/0",
                )
            ),
        )
    }

    @Test
    fun unknownPayloadIsNone() {
        assertSame(NotificationTapTarget.None, parseNotificationTapTarget(emptyMap()))
        assertSame(
            NotificationTapTarget.None,
            parseNotificationTapTarget(
                mapOf(
                    NOTIFICATION_KEY_DEEP_LINK to "   ",
                    NOTIFICATION_KEY_THREAD_ID to "",
                    NOTIFICATION_KEY_OWNER to null,
                )
            ),
        )
    }

    @Test
    fun emptyDeepLinkDoesNotMatchNullNumberUrl() {
        // A missing deep_link must not be turned into `.../null`.
        val payload = mapOf(
            NOTIFICATION_KEY_THREAD_ID to "t",
            NOTIFICATION_KEY_DEEP_LINK to "",
        )
        assertEquals(NotificationTapTarget.Inbox, parseNotificationTapTarget(payload))
        assertTrue(
            parseNotificationTapTarget(payload) !is NotificationTapTarget.Thread
        )
    }
}
