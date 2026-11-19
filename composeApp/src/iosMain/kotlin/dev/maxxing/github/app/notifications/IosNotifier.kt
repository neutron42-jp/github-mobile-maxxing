@file:OptIn(ExperimentalForeignApi::class)

package dev.maxxing.github.app.notifications

import dev.maxxing.github.app.NOTIFICATION_KEY_DEEP_LINK
import dev.maxxing.github.app.NOTIFICATION_KEY_THREAD_ID
import dev.maxxing.github.app.buildThreadDeepLinkUrl
import dev.maxxing.github.app.openIosAppSettings
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.NotificationThread
import kotlinx.cinterop.ExperimentalForeignApi
import dev.maxxing.github.app.iosLog
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionAuthenticationRequired
import platform.UserNotifications.UNNotificationActionOptionDestructive
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

const val MAXXING_GITHUB_THREAD: String = "MAXXING_GITHUB_THREAD"
const val ACTION_MARK_READ: String = "ACTION_MARK_READ"
const val ACTION_MARK_DONE: String = "ACTION_MARK_DONE"

internal const val MAX_LOCAL_GITHUB_NOTIFICATIONS = 6
internal const val GITHUB_NOTIFICATION_ID_PREFIX = "gh_notif_"
internal const val TEST_NOTIFICATION_ID = "gh_notif_test"

internal fun notificationIdForThread(threadId: String): String =
    "$GITHUB_NOTIFICATION_ID_PREFIX$threadId"

/** A local notification request, separate from UIKit so it is unit-testable. */
internal data class IosLocalNotification(
    val identifier: String,
    val title: String,
    val body: String,
    val categoryIdentifier: String,
    val threadIdentifier: String,
    val userInfo: Map<String, String>,
    val interruptionLevelActive: Boolean = true,
)

internal enum class IosNotificationAuthorization {
    UNKNOWN,
    NOT_DETERMINED,
    DENIED,
    AUTHORIZED,
    PROVISIONAL,
    EPHEMERAL,
}

internal val IosNotificationAuthorization.isAllowed: Boolean
    get() = this == IosNotificationAuthorization.AUTHORIZED ||
        this == IosNotificationAuthorization.PROVISIONAL ||
        this == IosNotificationAuthorization.EPHEMERAL

/**
 * Builds the local-notification payloads for a batch of unread threads.
 *
 * Max 6 notifications, title `"${repoFullName} • ${reasonLabel}"`, body
 * `subjectTitle`, `threadIdentifier` per repo, `deep_link` only when
 * `subjectNumber != null && > 0` (never `.../null`).
 */
internal fun buildGitHubNotificationPayloads(
    threads: List<NotificationThread>,
): List<IosLocalNotification> = threads.take(MAX_LOCAL_GITHUB_NOTIFICATIONS).map { thread ->
    val userInfo = buildMap {
        put(NOTIFICATION_KEY_THREAD_ID, thread.id)
        buildThreadDeepLinkUrl(
            owner = thread.repoOwner,
            repo = thread.repoName,
            number = thread.subjectNumber,
        )?.let { deepLink -> put(NOTIFICATION_KEY_DEEP_LINK, deepLink) }
    }
    IosLocalNotification(
        identifier = notificationIdForThread(thread.id),
        title = "${thread.repoFullName} • ${formatReason(thread.reason)}",
        body = thread.subjectTitle,
        categoryIdentifier = MAXXING_GITHUB_THREAD,
        threadIdentifier = thread.repoFullName,
        userInfo = userInfo,
    )
}

internal fun buildTestNotificationPayload(): IosLocalNotification = IosLocalNotification(
    identifier = TEST_NOTIFICATION_ID,
    title = "${AppBranding.APP_NAME} Notifications Ready",
    body = "New mentions & review requests will appear here with Mark Read / Done actions.",
    categoryIdentifier = MAXXING_GITHUB_THREAD,
    threadIdentifier = "test",
    userInfo = emptyMap(),
)

private fun formatReason(reason: NotificationReason): String =
    reason.name.lowercase().replace('_', ' ')

/**
 * Thin seam over `UNUserNotificationCenter` so the notifier state machine can
 * be unit-tested on the simulator without touching the real notification
 * database or presenting a permission prompt.
 */
internal interface IosNotificationGateway {
    fun registerCategories()
    fun refreshAuthorization(onResult: (IosNotificationAuthorization) -> Unit)
    fun requestAuthorization(onResult: (Boolean) -> Unit)
    fun schedule(payload: IosLocalNotification, onResult: (Boolean) -> Unit)
    fun removeDelivered(identifier: String)

    /**
     * Removes the delivered notification(s) of [threadId]. An APNs alert's request
     * identifier is `apns-collapse-id` or system generated, so it cannot be derived from the
     * thread id; implementations should match on the payload's `thread_id` as well as the
     * local-notification identifier [fallbackIdentifier].
     */
    fun removeDeliveredForThread(threadId: String, fallbackIdentifier: String, onDone: () -> Unit) {
        removeDelivered(fallbackIdentifier)
        onDone()
    }
}

internal class IosNotifierEngine(
    private val gateway: IosNotificationGateway,
    /**
     * APNs alert pushes are the delivery path by default. While this
     * is true, `showGitHubNotifications` must not add local requests, otherwise
     * the same thread is displayed twice. The `AppContainer` still updates
     * `unreadThreads`, so the in-app badge keeps working.
     */
    var pushDeliveryActive: Boolean = true,
    /** When set, evaluated on every use instead of [pushDeliveryActive] (the push server can be installed later). */
    private val pushDeliveryActiveProvider: (() -> Boolean)? = null,
    private val logger: (String) -> Unit = { iosLog(it) },
    private val onAuthorizationResult: (IosNotificationAuthorization) -> Unit = {},
) {
    var authorization: IosNotificationAuthorization = IosNotificationAuthorization.UNKNOWN

    fun ensureInitialized() {
        gateway.registerCategories()
        refreshAuthorizationStatus()
    }

    /** Refreshes the cached status; called on foreground return. */
    fun refreshAuthorizationStatus() {
        gateway.refreshAuthorization { status ->
            authorization = status
        }
    }

    fun showGitHubNotifications(threads: List<NotificationThread>) {
        if (threads.isEmpty()) return
        if (pushDeliveryActiveProvider?.invoke() ?: pushDeliveryActive) {
            // Intentionally no local request: the APNs alert is the display path.
            return
        }
        if (!authorization.isAllowed) return
        for (payload in buildGitHubNotificationPayloads(threads)) {
            gateway.schedule(payload) { scheduled ->
                if (!scheduled) logger("Failed to schedule local notification ${payload.identifier}")
            }
        }
    }

    /**
     * Returns true only when the local test notification request was
     * accepted by the OS. NotDetermined starts the permission request and
     * returns false; Denied/unknown returns false after refreshing the cache.
     */
    fun showTestNotification(): Boolean = when (authorization) {
        IosNotificationAuthorization.AUTHORIZED,
        IosNotificationAuthorization.PROVISIONAL,
        IosNotificationAuthorization.EPHEMERAL,
        -> {
            gateway.schedule(buildTestNotificationPayload()) { scheduled ->
                if (!scheduled) logger("Failed to schedule the test notification")
            }
            true
        }

        IosNotificationAuthorization.NOT_DETERMINED -> {
            gateway.requestAuthorization { granted ->
                val status = if (granted) {
                    IosNotificationAuthorization.AUTHORIZED
                } else {
                    IosNotificationAuthorization.DENIED
                }
                authorization = status
                onAuthorizationResult(status)
            }
            false
        }

        IosNotificationAuthorization.DENIED,
        IosNotificationAuthorization.UNKNOWN,
        -> {
            refreshAuthorizationStatus()
            false
        }
    }

    /** Suspends until the delivered-notification lookup and removal were submitted (bounded by the caller). */
    suspend fun removeDeliveredThread(threadId: String) = suspendCoroutine<Unit> { continuation ->
        gateway.removeDeliveredForThread(threadId, notificationIdForThread(threadId)) {
            continuation.resume(Unit)
        }
    }
}

/**
 * Real gateway. Nothing here requests permission: `ensureInitialized()` is
 * called on startup before sign-in, so it only registers categories and reads
 * the current status.
 */
internal class UNUserNotificationGateway : IosNotificationGateway {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override fun registerCategories() {
        val markRead = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_MARK_READ,
            title = "Mark Read",
            options = UNNotificationActionOptionAuthenticationRequired,
        )
        val markDone = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_MARK_DONE,
            title = "Done",
            options = UNNotificationActionOptionAuthenticationRequired or
                UNNotificationActionOptionDestructive,
        )
        val category = UNNotificationCategory.categoryWithIdentifier(
            identifier = MAXXING_GITHUB_THREAD,
            actions = listOf(markRead, markDone),
            intentIdentifiers = emptyList<String>(),
            options = 0uL,
        )
        center.setNotificationCategories(setOf(category))
    }

    override fun refreshAuthorization(onResult: (IosNotificationAuthorization) -> Unit) {
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized -> IosNotificationAuthorization.AUTHORIZED
                UNAuthorizationStatusProvisional -> IosNotificationAuthorization.PROVISIONAL
                UNAuthorizationStatusEphemeral -> IosNotificationAuthorization.EPHEMERAL
                UNAuthorizationStatusDenied -> IosNotificationAuthorization.DENIED
                UNAuthorizationStatusNotDetermined -> IosNotificationAuthorization.NOT_DETERMINED
                else -> IosNotificationAuthorization.UNKNOWN
            }
            onResult(status)
        }
    }

    override fun requestAuthorization(onResult: (Boolean) -> Unit) {
        val options = UNAuthorizationOptionAlert or
            UNAuthorizationOptionBadge or
            UNAuthorizationOptionSound
        center.requestAuthorizationWithOptions(options) { granted, _ ->
            onResult(granted)
        }
    }

    override fun schedule(payload: IosLocalNotification, onResult: (Boolean) -> Unit) {
        val content = UNMutableNotificationContent().apply {
            setTitle(payload.title)
            setBody(payload.body)
            setCategoryIdentifier(payload.categoryIdentifier)
            setThreadIdentifier(payload.threadIdentifier)
            setUserInfo(payload.userInfo.mapKeys { it.key as Any? })
            setSound(UNNotificationSound.defaultSound())
            if (payload.interruptionLevelActive) {
                setInterruptionLevel(UNNotificationInterruptionLevel.UNNotificationInterruptionLevelActive)
            }
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = payload.identifier,
            content = content,
            trigger = null,
        )
        center.addNotificationRequest(request) { error ->
            if (error != null) {
                iosLog("Failed to add notification request: ${error.localizedDescription}")
            }
            onResult(error == null)
        }
    }

    override fun removeDelivered(identifier: String) {
        center.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
    }

    override fun removeDeliveredForThread(threadId: String, fallbackIdentifier: String, onDone: () -> Unit) {
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val identifiers = (delivered ?: emptyList<Any?>())
                .filterIsInstance<platform.UserNotifications.UNNotification>()
                .filter { notification ->
                    val request = notification.request
                    request.identifier == fallbackIdentifier ||
                        request.content.userInfo[NOTIFICATION_KEY_THREAD_ID]?.toString() == threadId
                }
                .map { it.request.identifier }
                .plus(fallbackIdentifier)
                .distinct()
            center.removeDeliveredNotificationsWithIdentifiers(identifiers)
            onDone()
        }
    }
}

/** Process-wide notifier used by the common `PlatformNotifier` actual. */
internal object IosNotifier {
    @kotlin.concurrent.Volatile
    var language: AppLanguage = AppLanguage.DEFAULT

    val engine: IosNotifierEngine by lazy {
        IosNotifierEngine(
            gateway = UNUserNotificationGateway(),
            // APNs is the display path only when a push server is configured;
            // otherwise local notifications are the only way a new thread is shown.
            pushDeliveryActiveProvider = { IosPushFeature.isApnsEnabled && IosPushBridge.isServerConfigured },
            onAuthorizationResult = {
                // Permission just changed; let the APNs bridge converge.
                IosPushBridge.onNotificationAuthorizationChanged()
            },
        )
    }
}

actual object PlatformNotifier {
    actual fun ensureInitialized() {
        if (!IosPushFeature.isApnsEnabled) return
        IosNotifier.engine.ensureInitialized()
    }

    actual fun showGitHubNotifications(threads: List<NotificationThread>) {
        if (!IosPushFeature.isApnsEnabled) return
        IosNotifier.engine.showGitHubNotifications(threads)
    }

    actual fun showTestNotification(): Boolean {
        if (!IosPushFeature.isApnsEnabled) return false
        return IosNotifier.engine.showTestNotification()
    }

    actual fun updateLanguage(language: AppLanguage) {
        // Local notifications (test notification, foreground fallback) are English-only for now; APNs
        // alerts are built by the central push server. The language is recorded for when that changes.
        IosNotifier.language = language
    }

    /** True when iOS will not show this app's notifications (permission denied or not yet granted). */
    actual fun areOsNotificationsBlocked(): Boolean {
        if (!IosPushFeature.isApnsEnabled) return false
        return when (IosNotifier.engine.authorization) {
            IosNotificationAuthorization.DENIED,
            IosNotificationAuthorization.NOT_DETERMINED,
            -> true

            else -> false
        }
    }

    actual fun openOsNotificationSettings(): Boolean {
        if (!IosPushFeature.isApnsEnabled) return false
        return openIosAppSettings()
    }

    actual fun syncBackgroundSchedule(enabled: Boolean) {
        if (!IosPushFeature.isApnsEnabled) return
        // No BGTask / BGAppRefreshTask is used. Only the APNs registration state
        // follows the notification preference.
        IosNotifier.engine.refreshAuthorizationStatus()
        IosPushBridge.onNotificationPreferenceChanged(enabled)
    }
}
