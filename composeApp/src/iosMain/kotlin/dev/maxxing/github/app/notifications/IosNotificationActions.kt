@file:OptIn(ExperimentalForeignApi::class)

package dev.maxxing.github.app.notifications

import dev.maxxing.github.app.IosAppGraph
import dev.maxxing.github.app.IosDeepLinkBridge
import dev.maxxing.github.app.NOTIFICATION_KEY_DEEP_LINK
import dev.maxxing.github.app.NOTIFICATION_KEY_OWNER
import dev.maxxing.github.app.NOTIFICATION_KEY_REPO
import dev.maxxing.github.app.NOTIFICATION_KEY_SUBJECT_NUMBER
import dev.maxxing.github.app.NOTIFICATION_KEY_THREAD_ID
import dev.maxxing.github.app.NotificationTapTarget
import dev.maxxing.github.app.parseNotificationTapTarget
import dev.maxxing.github.auth.SessionManager
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import dev.maxxing.github.app.iosLog

/**
 * Handles one notification action (Mark Read / Done) with a strict contract:
 * the completion callback runs exactly once, a duplicate action for the same
 * thread is dropped, and any failure leaves the delivered notification and the
 * in-app state untouched.
 */
internal class NotificationActionHandler(
    private val sessionManager: SessionManager,
    private val markRead: suspend (String) -> Result<Unit>,
    private val markDone: suspend (String) -> Result<Unit>,
    private val removeDelivered: suspend (String) -> Unit,
    private val onThreadRemoved: (String) -> Unit,
    private val scope: CoroutineScope,
    private val timeoutMillis: Long = DEFAULT_ACTION_TIMEOUT_MILLIS,
    private val ensureSessionLoaded: suspend () -> Unit = { sessionManager.initialize() },
    private val logger: (String) -> Unit = {},
) {
    private val inFlightMutex = Mutex()
    private val inFlight = mutableSetOf<String>()

    fun handle(actionId: String, threadId: String, completion: () -> Unit) {
        val operation: (suspend (String) -> Result<Unit>)? = when (actionId) {
            ACTION_MARK_READ -> markRead
            ACTION_MARK_DONE -> markDone
            else -> null
        }
        if (operation == null || threadId.isBlank()) {
            completion()
            return
        }

        scope.launch {
            if (!begin(threadId)) {
                // A request for the same thread is already running; nothing to
                // do, but the OS still needs its completion callback.
                completion()
                return@launch
            }
            try {
                ensureSessionLoaded()
                if (sessionManager.session.value == null) {
                    logger("Notification action ignored: no authenticated session")
                    return@launch
                }

                val result = withTimeoutOrNull(timeoutMillis) {
                    try {
                        operation(threadId)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                }

                when {
                    result == null -> logger(
                        "Notification action timed out after ${timeoutMillis}ms (thread=$threadId)"
                    )

                    result.isSuccess -> {
                        removeDelivered(threadId)
                        onThreadRemoved(threadId)
                    }

                    else -> {
                        // 401 / network failure / server error: keep both the
                        // notification and the in-app unread state.
                        logger(
                            "Notification action failed (thread=$threadId, " +
                                "error=${result.exceptionOrNull()?.message})"
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                logger("Notification action crashed (thread=$threadId): ${error.message}")
            } finally {
                end(threadId)
                completion()
            }
        }
    }

    private suspend fun begin(threadId: String): Boolean = inFlightMutex.withLock {
        if (threadId in inFlight) {
            false
        } else {
            inFlight += threadId
            true
        }
    }

    private suspend fun end(threadId: String) {
        inFlightMutex.withLock { inFlight -= threadId }
    }

    companion object {
        /** Short enough for the OS-provided action window. */
        const val DEFAULT_ACTION_TIMEOUT_MILLIS: Long = 15_000L
    }
}

/**
 * Swift-facing notification action entry point.
 *
 * `AppDelegate.userNotificationCenter(_:didReceive:withCompletionHandler:)`
 * calls [handle] for `ACTION_MARK_READ` / `ACTION_MARK_DONE`, and [handleTap]
 * (or its parsed result) for the default tap action.
 */
object IosNotificationActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val handler: NotificationActionHandler by lazy {
        val container = IosAppGraph.container
        NotificationActionHandler(
            sessionManager = container.sessionManager,
            markRead = { threadId -> container.repository.markNotificationRead(threadId) },
            markDone = { threadId -> container.repository.markNotificationDone(threadId) },
            removeDelivered = { threadId -> IosNotifier.engine.removeDeliveredThread(threadId) },
            onThreadRemoved = { threadId ->
                val current = container.unreadThreads.value
                if (current != null) {
                    container.updateUnreadThreads(current.filterNot { it.id == threadId })
                }
                // A foreground sync that was already fetching may still hold this thread and
                // write it back. syncNotifications() is mutex-serialized, so this follow-up
                // sync runs after it and converges on the server state.
                scope.launch { container.syncNotifications() }
            },
            scope = scope,
            ensureSessionLoaded = { IosAppGraph.ensureSessionLoaded() },
            logger = { iosLog(it) },
        )
    }

    fun handle(actionId: String, threadId: String, completion: () -> Unit) {
        if (!IosPushFeature.isApnsEnabled) {
            completion()
            return
        }
        handler.handle(actionId, threadId, completion)
    }

    /**
     * Parses the `userInfo` fields the Swift delegate extracted. A
     * `deep_link` wins; otherwise `owner` / `repo` / `subject_number` are used.
     * `subject_number == null` (CHECK_SUITE / Commit / …) means Inbox.
     */
    fun parseNotificationTapAction(
        deepLink: String?,
        owner: String?,
        repo: String?,
        subjectNumber: Int?,
        threadId: String?,
    ): NotificationTapTarget {
        val payload = buildMap<String, String?> {
            deepLink?.let { put(NOTIFICATION_KEY_DEEP_LINK, it) }
            owner?.let { put(NOTIFICATION_KEY_OWNER, it) }
            repo?.let { put(NOTIFICATION_KEY_REPO, it) }
            subjectNumber?.let { put(NOTIFICATION_KEY_SUBJECT_NUMBER, it.toString()) }
            threadId?.let { put(NOTIFICATION_KEY_THREAD_ID, it) }
        }
        return parseNotificationTapTarget(payload)
    }

    /** Parses an `NSDictionary`/`userInfo`-shaped payload directly. */
    fun parseNotificationTapAction(userInfo: Map<Any?, *>): NotificationTapTarget =
        parseNotificationTapTarget(
            userInfo.entries.associate { (key, value) ->
                key?.toString().orEmpty() to value?.toString()
            }
        )

    /** Routes a notification tap from a raw `userInfo` dictionary. */
    fun handleTap(userInfo: Map<Any?, *>): Boolean {
        if (!IosPushFeature.isApnsEnabled) return false
        return route(parseNotificationTapAction(userInfo))
    }

    /** Routes a notification tap. Returns true when a navigation was recorded. */
    fun handleTap(
        deepLink: String?,
        owner: String?,
        repo: String?,
        subjectNumber: Int?,
        threadId: String?,
    ): Boolean {
        if (!IosPushFeature.isApnsEnabled) return false
        return route(parseNotificationTapAction(deepLink, owner, repo, subjectNumber, threadId))
    }

    private fun route(target: NotificationTapTarget): Boolean = when (target) {
        is NotificationTapTarget.Thread -> IosDeepLinkBridge.handleTarget(target.target)
        NotificationTapTarget.Inbox -> {
            IosDeepLinkBridge.requestInbox()
            true
        }

        NotificationTapTarget.None -> false
    }

    /** Refreshes the cached OS notification permission on foreground return. */
    fun refreshAuthorizationStatus() {
        if (!IosPushFeature.isApnsEnabled) return
        IosNotifier.engine.refreshAuthorizationStatus()
    }
}
