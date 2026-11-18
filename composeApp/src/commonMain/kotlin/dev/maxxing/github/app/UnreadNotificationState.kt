package dev.maxxing.github.app

import dev.maxxing.github.model.NotificationThread

internal enum class SuppressedNotificationKind {
    READ,
    DONE
}

internal data class SuppressedNotificationEntry(
    val kind: SuppressedNotificationKind,
    val atMs: Long
)

/**
 * Immutable state and pure transitions for optimistic notification read/done tracking.
 * Used inside `MutableStateFlow.update` so concurrent UI actions and background syncs stay race-free.
 */
internal data class UnreadNotificationTrackerState(
    val currentThreads: List<NotificationThread>? = null,
    val unreadCount: Int = 0,
    val suppressed: Map<String, SuppressedNotificationEntry> = emptyMap(),
    val markAllReadAtMs: Long? = null,
    val suppressionWindowMs: Long = 30_000L
) {
    fun withDirectCount(count: Int): UnreadNotificationTrackerState =
        copy(unreadCount = count.coerceAtLeast(0))

    fun markReadLocally(threadId: String, nowMs: Long): UnreadNotificationTrackerState {
        val nextSuppressed = suppressed + (threadId to SuppressedNotificationEntry(SuppressedNotificationKind.READ, nowMs))
        val nextThreads = currentThreads?.map { thread ->
            if (thread.id == threadId) thread.copy(unread = false) else thread
        }
        return copy(
            currentThreads = nextThreads,
            unreadCount = nextThreads?.count { it.unread } ?: 0,
            suppressed = nextSuppressed
        )
    }

    fun markDoneLocally(threadId: String, nowMs: Long): UnreadNotificationTrackerState {
        val nextSuppressed = suppressed + (threadId to SuppressedNotificationEntry(SuppressedNotificationKind.DONE, nowMs))
        val nextThreads = currentThreads?.filterNot { it.id == threadId }
        return copy(
            currentThreads = nextThreads,
            unreadCount = nextThreads?.count { it.unread } ?: 0,
            suppressed = nextSuppressed
        )
    }

    fun markAllReadLocally(nowMs: Long): UnreadNotificationTrackerState {
        val nextSuppressed = suppressed.toMutableMap()
        val nextThreads = currentThreads?.map { thread ->
            nextSuppressed[thread.id] = SuppressedNotificationEntry(SuppressedNotificationKind.READ, nowMs)
            thread.copy(unread = false)
        }
        return copy(
            currentThreads = nextThreads,
            unreadCount = 0,
            suppressed = nextSuppressed,
            markAllReadAtMs = nowMs
        )
    }

    fun rollbackSuppression(threadId: String): UnreadNotificationTrackerState =
        copy(suppressed = suppressed - threadId)

    fun rollbackMarkAll(): UnreadNotificationTrackerState =
        copy(suppressed = emptyMap(), markAllReadAtMs = null)

    fun reconcileFromServer(
        fetchedUnread: List<NotificationThread>,
        syncStartedAtMs: Long,
        nowMs: Long
    ): UnreadNotificationTrackerState {
        val fetchedIds = fetchedUnread.mapTo(HashSet()) { it.id }

        // Expire old suppressions and drop IDs the server no longer returns as unread.
        val nextSuppressed = suppressed.filter { (id, entry) ->
            nowMs - entry.atMs <= suppressionWindowMs && id in fetchedIds
        }

        val activeMarkAllMs = markAllReadAtMs?.takeIf { nowMs - it <= suppressionWindowMs }
        val existingReadById = currentThreads.orEmpty()
            .filter { !it.unread }
            .associateBy { it.id }

        val result = ArrayList<NotificationThread>(fetchedUnread.size)
        for (thread in fetchedUnread) {
            val supp = nextSuppressed[thread.id]
            val coveredByMarkAll = activeMarkAllMs != null && syncStartedAtMs <= activeMarkAllMs
            when {
                supp?.kind == SuppressedNotificationKind.DONE -> Unit
                supp?.kind == SuppressedNotificationKind.READ || coveredByMarkAll -> {
                    if (thread.id in existingReadById) {
                        result += thread.copy(unread = false)
                    }
                }
                else -> result += thread
            }
        }

        return copy(
            currentThreads = result,
            unreadCount = result.count { it.unread },
            suppressed = nextSuppressed,
            markAllReadAtMs = activeMarkAllMs
        )
    }
}
