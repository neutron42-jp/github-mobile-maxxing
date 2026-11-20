package dev.maxxing.github.app.notifications

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidNotifierActionTest {

    @Test
    fun broadcastActionCancelsOnlyOnApiSuccess() = runTest {
        var cancelled = false
        performNotificationBroadcastAction(
            action = ACTION_MARK_READ,
            threadId = "t1",
            initializeSession = {},
            hasActiveSession = { true },
            markRead = { Result.failure(IllegalStateException("network down")) },
            markDone = { Result.success(Unit) },
            onSucceeded = { cancelled = true }
        )
        assertFalse(cancelled)

        performNotificationBroadcastAction(
            action = ACTION_MARK_DONE,
            threadId = "t1",
            initializeSession = {},
            hasActiveSession = { false },
            markRead = { Result.success(Unit) },
            markDone = { Result.success(Unit) },
            onSucceeded = { cancelled = true }
        )
        assertFalse(cancelled)

        performNotificationBroadcastAction(
            action = ACTION_MARK_READ,
            threadId = "t1",
            initializeSession = { error("storage failure") },
            hasActiveSession = { true },
            markRead = { Result.success(Unit) },
            markDone = { Result.success(Unit) },
            onSucceeded = { cancelled = true }
        )
        assertFalse(cancelled)

        performNotificationBroadcastAction(
            action = ACTION_MARK_READ,
            threadId = "t1",
            initializeSession = {},
            hasActiveSession = { true },
            markRead = {
                delay(15_000L)
                Result.success(Unit)
            },
            markDone = { Result.success(Unit) },
            onSucceeded = { cancelled = true }
        )
        assertFalse(cancelled)

        performNotificationBroadcastAction(
            action = ACTION_MARK_DONE,
            threadId = "t1",
            initializeSession = {},
            hasActiveSession = { true },
            markRead = { Result.success(Unit) },
            markDone = { Result.success(Unit) },
            onSucceeded = { cancelled = true }
        )
        assertTrue(cancelled)
    }
}
