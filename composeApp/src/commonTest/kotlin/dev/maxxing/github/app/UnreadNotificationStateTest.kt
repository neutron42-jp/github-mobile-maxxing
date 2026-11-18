package dev.maxxing.github.app

import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.NotificationSubjectType
import dev.maxxing.github.model.NotificationThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnreadNotificationStateTest {
    private fun thread(id: String, unread: Boolean = true) = NotificationThread(
        id = id,
        unread = unread,
        reason = NotificationReason.MENTION,
        updatedAt = "2026-11-18T00:00:00Z",
        subjectTitle = "Issue $id",
        subjectType = NotificationSubjectType.ISSUE,
        subjectUrl = "https://api.github.com/repos/octo/app/issues/$id",
        latestCommentUrl = null,
        repoFullName = "octo/app",
        repoOwner = "octo",
        repoName = "app"
    )

    @Test
    fun `in-flight sync does not resurrect unread badge after local markRead`() {
        var state = UnreadNotificationTrackerState(suppressionWindowMs = 30_000L)
        val t1 = thread("1")
        val t2 = thread("2")

        // Initial sync discovers 2 unread notifications.
        state = state.reconcileFromServer(listOf(t1, t2), syncStartedAtMs = 1_000L, nowMs = 1_050L)
        assertEquals(2, state.unreadCount)

        // A background sync starts at t=2000 while user taps notification #1 at t=2010.
        state = state.markReadLocally("1", nowMs = 2_010L)
        assertEquals(1, state.unreadCount)
        assertFalse(state.currentThreads!!.first { it.id == "1" }.unread)

        // User also taps notification #2 at t=2020.
        state = state.markReadLocally("2", nowMs = 2_020L)
        assertEquals(0, state.unreadCount)

        // Stale in-flight sync finishes at t=2100 returning [t1, t2] unread from server.
        state = state.reconcileFromServer(listOf(t1, t2), syncStartedAtMs = 2_000L, nowMs = 2_100L)
        assertEquals(0, state.unreadCount)
        assertTrue(state.currentThreads!!.all { !it.unread })

        // Later sync after server catches up returns empty unread list and clears suppressions.
        state = state.reconcileFromServer(emptyList(), syncStartedAtMs = 3_000L, nowMs = 3_050L)
        assertEquals(0, state.unreadCount)
        assertTrue(state.currentThreads!!.isEmpty())
    }

    @Test
    fun `markDone removes thread and markAllRead suppresses stale in-flight sync`() {
        var state = UnreadNotificationTrackerState(suppressionWindowMs = 30_000L)
        val t1 = thread("1")
        val t2 = thread("2")
        val t3 = thread("3")

        state = state.reconcileFromServer(listOf(t1, t2), syncStartedAtMs = 1_000L, nowMs = 1_050L)
        assertEquals(2, state.unreadCount)

        state = state.markDoneLocally("1", nowMs = 1_500L)
        assertEquals(1, state.unreadCount)
        assertEquals(listOf("2"), state.currentThreads?.map { it.id })

        // Mark all read while a sync started at t=2000 is still in flight.
        state = state.markAllReadLocally(nowMs = 2_050L)
        assertEquals(0, state.unreadCount)

        // In-flight sync from t=2000 returns t1, t2. Must stay 0.
        state = state.reconcileFromServer(listOf(t1, t2), syncStartedAtMs = 2_000L, nowMs = 2_100L)
        assertEquals(0, state.unreadCount)

        // A brand-new sync started AFTER markAllRead brings in newly arrived t3.
        state = state.reconcileFromServer(listOf(t3), syncStartedAtMs = 2_200L, nowMs = 2_250L)
        assertEquals(1, state.unreadCount)
        assertEquals(listOf("3"), state.currentThreads?.map { it.id })
    }
}
