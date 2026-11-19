package dev.maxxing.github.app

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosDeepLinkBridgeTest {

    @BeforeTest
    fun clear() {
        clearBridge()
    }

    @Test
    fun rejectsInvalidUrlWithoutChangingPendingState() {
        assertFalse(IosDeepLinkBridge.handleUrl("https://example.com/o/r/1"))
        assertFalse(IosDeepLinkBridge.handleUrl("githubmaxxing://thread/o/r/null"))
        assertFalse(IosDeepLinkBridge.handleUrl(""))
        // Lenient-Android-only inputs are rejected at the iOS entry.
        assertFalse(IosDeepLinkBridge.handleUrl("githubmaxxing://thread/o/r/1/extra"))
        assertFalse(IosDeepLinkBridge.handleUrl("githubmaxxing://thread//o/r/1"))
        assertFalse(IosDeepLinkBridge.handleUrl("githubmaxxing://thread/o/r/0"))
        assertFalse(IosDeepLinkBridge.handleUrl("githubmaxxing://thread/o/r/+1"))
        assertNull(IosDeepLinkBridge.pending.value)
    }

    @Test
    fun acceptsValidUrlAndReturnsLatestOnly() {
        assertTrue(IosDeepLinkBridge.handleUrl("githubmaxxing://thread/o/r/1"))
        val first = assertNotNull(IosDeepLinkBridge.pending.value)
        assertEquals(IssuePullTarget("o", "r", 1), (first as PendingNavigation.Thread).target)

        assertTrue(IosDeepLinkBridge.handleUrl("githubmaxxing://thread/o/r/2"))
        val second = assertNotNull(IosDeepLinkBridge.pending.value)
        assertEquals(IssuePullTarget("o", "r", 2), (second as PendingNavigation.Thread).target)

        // Nonces make even the same URL a distinct pending value.
        assertTrue(IosDeepLinkBridge.handleUrl("githubmaxxing://thread/o/r/2"))
        val third = assertNotNull(IosDeepLinkBridge.pending.value)
        assertTrue(third.nonce > second.nonce)
    }

    @Test
    fun consumeOnlyClearsTheMatchingNonce() {
        assertTrue(IosDeepLinkBridge.handleUrl("githubmaxxing://thread/o/r/1"))
        val first = assertNotNull(IosDeepLinkBridge.pending.value)
        assertTrue(IosDeepLinkBridge.handleUrl("githubmaxxing://thread/o/r/2"))
        val second = assertNotNull(IosDeepLinkBridge.pending.value)

        // Consuming the older target must not clear the newer one.
        IosDeepLinkBridge.consume(first.nonce)
        assertEquals(second, IosDeepLinkBridge.pending.value)

        IosDeepLinkBridge.consume(second.nonce)
        assertNull(IosDeepLinkBridge.pending.value)
    }

    @Test
    fun inboxRequestUsesNonceCheckedConsume() {
        IosDeepLinkBridge.requestInbox()
        val first = assertNotNull(IosDeepLinkBridge.pending.value)
        assertTrue(first is PendingNavigation.Inbox)
        IosDeepLinkBridge.requestInbox()
        val second = assertNotNull(IosDeepLinkBridge.pending.value)

        IosDeepLinkBridge.consume(first.nonce)
        assertEquals(second, IosDeepLinkBridge.pending.value)

        IosDeepLinkBridge.consume(second.nonce)
        assertNull(IosDeepLinkBridge.pending.value)
    }

    @Test
    fun threadAndInboxRequestsReplaceEachOtherLatestOnly() {
        assertTrue(IosDeepLinkBridge.handleUrl("githubmaxxing://thread/o/r/1"))
        IosDeepLinkBridge.requestInbox()
        // A later Inbox request must cancel the older thread request (and not leave it behind).
        assertTrue(IosDeepLinkBridge.pending.value is PendingNavigation.Inbox)

        assertTrue(IosDeepLinkBridge.handleUrl("githubmaxxing://thread/o/r/2"))
        val thread = assertNotNull(IosDeepLinkBridge.pending.value)
        assertEquals(IssuePullTarget("o", "r", 2), (thread as PendingNavigation.Thread).target)
    }

    private fun clearBridge() {
        IosDeepLinkBridge.pending.value?.let { IosDeepLinkBridge.consume(it.nonce) }
    }
}
