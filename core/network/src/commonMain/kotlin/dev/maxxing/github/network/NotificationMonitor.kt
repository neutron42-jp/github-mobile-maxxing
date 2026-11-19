package dev.maxxing.github.network

import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.NotificationThread
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NotificationCheckResult(
    val allUnread: List<NotificationThread>,
    val newlyArrivedForOs: List<NotificationThread>
)

/**
 * Splits OS notifications into threads posted individually and an overflow that should be
 * represented by a single summary notification, so nothing is dropped silently.
 */
data class OsNotificationBatch(
    val individual: List<NotificationThread>,
    val overflow: List<NotificationThread>
)

fun splitForOsDisplay(
    threads: List<NotificationThread>,
    maxIndividual: Int = NotificationMonitor.MAX_INDIVIDUAL_OS_NOTIFICATIONS
): OsNotificationBatch = OsNotificationBatch(
    individual = threads.take(maxIndividual),
    overflow = threads.drop(maxIndividual)
)

class NotificationMonitor(
    private val sessionManager: SessionManager,
    private val repository: GitHubRepository,
    /**
     * Whether the platform can currently show OS notifications (e.g. Android permission granted
     * and app notifications enabled). When false, markers for threads that would have been
     * alerted are not advanced, so they are alerted once notifications become available.
     */
    private val canPostOsNotifications: () -> Boolean = { true }
) {
    private val checkMutex = Mutex()

    suspend fun markThreadsSeen(threads: List<NotificationThread>) = checkMutex.withLock {
        if (sessionManager.session.value == null || threads.isEmpty()) return@withLock
        val previousMarkers = sessionManager.loadSeenNotificationMarkers()
        val nextMarkers = previousMarkers.toMutableMap()
        var changed = previousMarkers[BASELINE_MARKER_KEY] != "1"
        for (thread in threads) {
            if (nextMarkers[thread.id] != thread.updatedAt) {
                nextMarkers[thread.id] = thread.updatedAt
                changed = true
            }
        }
        if (!changed) return@withLock
        nextMarkers[BASELINE_MARKER_KEY] = "1"
        val activeIds = threads.mapTo(HashSet()) { it.id }
        val trimmed = if (nextMarkers.size > 200) {
            nextMarkers.filterKeys { it in activeIds || it == BASELINE_MARKER_KEY }
        } else {
            nextMarkers
        }
        sessionManager.saveSeenNotificationMarkers(trimmed)
    }

    suspend fun checkForNewNotifications(
        suppressOsAlerts: Boolean = false
    ): Result<NotificationCheckResult> = checkMutex.withLock {
        if (sessionManager.session.value == null) {
            return@withLock Result.success(NotificationCheckResult(emptyList(), emptyList()))
        }

        val prefs = sessionManager.notificationPreferences.value
        val previousMarkers = sessionManager.loadSeenNotificationMarkers()

        repository.getNotifications(all = false, participating = false).map { unread ->
            val nextMarkers = previousMarkers.toMutableMap()
            val candidates = mutableListOf<NotificationThread>()

            val isFirstBaseline = previousMarkers.isEmpty()
            for (thread in unread) {
                val prevUpdated = previousMarkers[thread.id]
                if (!isFirstBaseline && (prevUpdated == null || prevUpdated != thread.updatedAt)) {
                    candidates.add(thread)
                }
                nextMarkers[thread.id] = thread.updatedAt
            }

            val forOs = if (suppressOsAlerts || !prefs.osNotificationsEnabled) {
                emptyList()
            } else if (prefs.urgentOnly) {
                candidates.filter { it.reason in URGENT_REASONS }
            } else {
                candidates
            }

            // If OS alerts are wanted but cannot be shown right now, keep the previous markers
            // for the would-be-alerted threads so they are not lost.
            val heldBack = if (!suppressOsAlerts && forOs.isNotEmpty() && !canPostOsNotifications()) {
                forOs
            } else {
                emptyList()
            }
            for (thread in heldBack) {
                val prev = previousMarkers[thread.id]
                if (prev == null) nextMarkers.remove(thread.id) else nextMarkers[thread.id] = prev
            }

            // Persist a sentinel marker so an initially-empty inbox (0 unread) does NOT
            // keep isFirstBaseline = true forever and swallow the first real notification.
            nextMarkers[BASELINE_MARKER_KEY] = "1"

            // Keep map bounded to active unread threads + baseline sentinel
            val activeIds = unread.mapTo(HashSet()) { it.id }
            val trimmedMarkers = if (nextMarkers.size > 200) {
                nextMarkers.filterKeys { it in activeIds || it == BASELINE_MARKER_KEY }
            } else {
                nextMarkers
            }
            sessionManager.saveSeenNotificationMarkers(trimmedMarkers)

            NotificationCheckResult(
                allUnread = unread,
                newlyArrivedForOs = if (suppressOsAlerts || heldBack.isNotEmpty()) emptyList() else forOs
            )
        }
    }

    companion object {
        internal const val BASELINE_MARKER_KEY = "__maxxing_baseline__"
        const val MAX_INDIVIDUAL_OS_NOTIFICATIONS = 6

        val URGENT_REASONS = setOf(
            NotificationReason.REVIEW_REQUESTED,
            NotificationReason.MENTION,
            NotificationReason.TEAM_MENTION,
            NotificationReason.ASSIGN,
            NotificationReason.SECURITY_ALERT,
            NotificationReason.AUTHOR
        )
    }
}
