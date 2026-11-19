package dev.maxxing.github.app

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide iOS application graph.
 *
 * One process has exactly one [AppContainer]. The Compose UI, the notification
 * actions and the APNs bridge all use it, so `NotificationMonitor`'s marker
 * read-modify-write and the UI's `unreadThreads` stay consistent. The container
 * is created lazily: a notification action that launches the app in the
 * background gets the same instance the UI would have created.
 */
object IosAppGraph {
    val container: AppContainer by lazy { AppContainer() }

    private val sessionInitMutex = Mutex()
    private var sessionLoaded = false

    /**
     * Runs `sessionManager.initialize()` at most once. Mutex-guarded so the UI
     * `LaunchedEffect`, a notification action and the APNs bridge cannot
     * initialize the session concurrently.
     */
    suspend fun ensureSessionLoaded() {
        sessionInitMutex.withLock {
            if (sessionLoaded) return
            container.sessionManager.initialize()
            sessionLoaded = true
        }
    }
}
