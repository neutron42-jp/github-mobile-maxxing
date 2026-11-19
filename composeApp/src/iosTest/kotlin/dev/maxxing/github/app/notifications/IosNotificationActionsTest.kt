package dev.maxxing.github.app.notifications

import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.NotificationSubjectType
import dev.maxxing.github.model.NotificationThread
import dev.maxxing.github.network.GitHubApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosNotificationActionsTest {

    @Test
    fun markReadSuccessRemovesNotificationAndUnreadState() = runTest {
        val sessionManager = authenticatedSessionManager()
        val container = AppContainer(sessionManager = sessionManager, showOsNotifications = {})
        container.updateUnreadThreads(listOf(thread("t1"), thread("t2")))

        val markReadCalls = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val handler = handler(
            sessionManager = sessionManager,
            markRead = { id -> markReadCalls += id; Result.success(Unit) },
            removeDelivered = { removed += it },
            onThreadRemoved = { id -> container.removeUnreadThread(id) },
        )

        var completions = 0
        handler.handle(ACTION_MARK_READ, "t1") { completions++ }
        advanceUntilIdle()

        assertEquals(1, completions)
        assertEquals(listOf("t1"), markReadCalls)
        assertEquals(listOf("t1"), removed)
        assertEquals(listOf("t2"), container.unreadThreads.value?.map { it.id })
        assertEquals(1, container.unreadNotificationCount.value)
    }

    @Test
    fun markDoneSuccessUsesDoneEndpointAndRemovesNotification() = runTest {
        val sessionManager = authenticatedSessionManager()
        val container = AppContainer(sessionManager = sessionManager, showOsNotifications = {})
        container.updateUnreadThreads(listOf(thread("t1")))

        val markReadCalls = mutableListOf<String>()
        val markDoneCalls = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val handler = handler(
            sessionManager = sessionManager,
            markRead = { id -> markReadCalls += id; Result.success(Unit) },
            markDone = { id -> markDoneCalls += id; Result.success(Unit) },
            removeDelivered = { removed += it },
            onThreadRemoved = { id -> container.removeUnreadThread(id) },
        )

        var completions = 0
        handler.handle(ACTION_MARK_DONE, "t1") { completions++ }
        advanceUntilIdle()

        assertEquals(1, completions)
        assertTrue(markReadCalls.isEmpty())
        assertEquals(listOf("t1"), markDoneCalls)
        assertEquals(listOf("t1"), removed)
        assertTrue(container.unreadThreads.value.isNullOrEmpty())
    }

    @Test
    fun unauthenticatedSessionDoesNothingButCompletesOnce() = runTest {
        val sessionManager = signedOutSessionManager()
        var markReadCalls = 0
        var removed = 0
        var removedStateUpdates = 0
        val handler = handler(
            sessionManager = sessionManager,
            markRead = { markReadCalls++; Result.success(Unit) },
            removeDelivered = { removed++ },
            onThreadRemoved = { removedStateUpdates++ },
        )

        var completions = 0
        handler.handle(ACTION_MARK_READ, "t1") { completions++ }
        advanceUntilIdle()

        assertEquals(1, completions)
        assertEquals(0, markReadCalls)
        assertEquals(0, removed)
        assertEquals(0, removedStateUpdates)
    }

    @Test
    fun unauthorized401KeepsNotificationAndCompletesOnce() = runTest {
        val sessionManager = authenticatedSessionManager()
        var removed = 0
        var stateUpdates = 0
        val handler = handler(
            sessionManager = sessionManager,
            markRead = { Result.failure(GitHubApiException(401, "Unauthorized")) },
            removeDelivered = { removed++ },
            onThreadRemoved = { stateUpdates++ },
        )

        var completions = 0
        handler.handle(ACTION_MARK_READ, "t1") { completions++ }
        advanceUntilIdle()

        assertEquals(1, completions)
        assertEquals(0, removed)
        assertEquals(0, stateUpdates)
    }

    @Test
    fun apiFailureKeepsNotificationAndCompletesOnce() = runTest {
        val sessionManager = authenticatedSessionManager()
        var removed = 0
        var stateUpdates = 0
        val handler = handler(
            sessionManager = sessionManager,
            markDone = { Result.failure(GitHubApiException(500, "Server error")) },
            removeDelivered = { removed++ },
            onThreadRemoved = { stateUpdates++ },
        )

        var completions = 0
        handler.handle(ACTION_MARK_DONE, "t1") { completions++ }
        advanceUntilIdle()

        assertEquals(1, completions)
        assertEquals(0, removed)
        assertEquals(0, stateUpdates)
    }

    @Test
    fun thrownNetworkFailureKeepsNotificationAndCompletesOnce() = runTest {
        val sessionManager = authenticatedSessionManager()
        var removed = 0
        var stateUpdates = 0
        val handler = handler(
            sessionManager = sessionManager,
            markRead = { throw IllegalStateException("network unavailable") },
            removeDelivered = { removed++ },
            onThreadRemoved = { stateUpdates++ },
        )

        var completions = 0
        handler.handle(ACTION_MARK_READ, "t1") { completions++ }
        advanceUntilIdle()

        assertEquals(1, completions)
        assertEquals(0, removed)
        assertEquals(0, stateUpdates)
    }

    @Test
    fun apiTimeoutKeepsNotificationAndCompletesOnce() = runTest {
        val sessionManager = authenticatedSessionManager()
        var removed = 0
        var stateUpdates = 0
        val handler = handler(
            sessionManager = sessionManager,
            markRead = { delay(60_000); Result.success(Unit) },
            removeDelivered = { removed++ },
            onThreadRemoved = { stateUpdates++ },
            timeoutMillis = 1_000,
        )

        var completions = 0
        handler.handle(ACTION_MARK_READ, "t1") { completions++ }
        advanceUntilIdle()

        assertEquals(1, completions)
        assertEquals(0, removed)
        assertEquals(0, stateUpdates)
    }

    @Test
    fun duplicateConcurrentActionForSameThreadRunsOnce() = runTest {
        val sessionManager = authenticatedSessionManager()
        val gate = CompletableDeferred<Unit>()
        var markReadCalls = 0
        var removed = 0
        val handler = handler(
            sessionManager = sessionManager,
            markRead = {
                markReadCalls++
                gate.await()
                Result.success(Unit)
            },
            removeDelivered = { removed++ },
        )

        var firstCompletions = 0
        var secondCompletions = 0
        handler.handle(ACTION_MARK_READ, "t1") { firstCompletions++ }
        runCurrent()
        handler.handle(ACTION_MARK_READ, "t1") { secondCompletions++ }
        runCurrent()

        // The duplicate finishes immediately without touching the API.
        assertEquals(1, secondCompletions)
        assertEquals(0, firstCompletions)
        assertEquals(1, markReadCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, firstCompletions)
        assertEquals(1, secondCompletions)
        assertEquals(1, markReadCalls)
        assertEquals(1, removed)
    }

    @Test
    fun unknownActionCompletesWithoutNetworkCall() = runTest {
        val sessionManager = authenticatedSessionManager()
        var markReadCalls = 0
        val handler = handler(
            sessionManager = sessionManager,
            markRead = { markReadCalls++; Result.success(Unit) },
        )

        var completions = 0
        handler.handle("ACTION_UNKNOWN", "t1") { completions++ }
        advanceUntilIdle()

        assertEquals(1, completions)
        assertEquals(0, markReadCalls)
    }

    @Test
    fun blankThreadIdCompletesWithoutNetworkCall() = runTest {
        val sessionManager = authenticatedSessionManager()
        var markReadCalls = 0
        val handler = handler(
            sessionManager = sessionManager,
            markRead = { markReadCalls++; Result.success(Unit) },
        )

        var completions = 0
        handler.handle(ACTION_MARK_READ, "  ") { completions++ }
        advanceUntilIdle()

        assertEquals(1, completions)
        assertEquals(0, markReadCalls)
    }

    // ---- tap parsing -----------------------------------------------------

    @Test
    fun tapParsingRoutesDeepLinkStructuredAndInbox() {
        assertEquals(
            dev.maxxing.github.app.NotificationTapTarget.Thread(
                dev.maxxing.github.app.IssuePullTarget("o", "r", 7)
            ),
            IosNotificationActions.parseNotificationTapAction(
                deepLink = "githubmaxxing://thread/o/r/7",
                owner = null,
                repo = null,
                subjectNumber = null,
                threadId = "t1",
            ),
        )
        assertEquals(
            dev.maxxing.github.app.NotificationTapTarget.Thread(
                dev.maxxing.github.app.IssuePullTarget("o", "r", 7)
            ),
            IosNotificationActions.parseNotificationTapAction(
                deepLink = null,
                owner = "o",
                repo = "r",
                subjectNumber = 7,
                threadId = "t1",
            ),
        )
        assertEquals(
            dev.maxxing.github.app.NotificationTapTarget.Inbox,
            IosNotificationActions.parseNotificationTapAction(
                deepLink = null,
                owner = "o",
                repo = "r",
                subjectNumber = null,
                threadId = "t1",
            ),
        )
        assertEquals(
            dev.maxxing.github.app.NotificationTapTarget.None,
            IosNotificationActions.parseNotificationTapAction(
                deepLink = null,
                owner = null,
                repo = null,
                subjectNumber = null,
                threadId = null,
            ),
        )
    }

    @Test
    fun handleTapRecordsThreadAndInboxRequestsWhenEnabled() {
        clearDeepLinkBridge()
        IosPushFeature.enabledOverrideForTesting = true
        try {
            assertTrue(
                IosNotificationActions.handleTap(
                    deepLink = "githubmaxxing://thread/o/r/7",
                    owner = null,
                    repo = null,
                    subjectNumber = null,
                    threadId = "t1",
                )
            )
            val thread = dev.maxxing.github.app.IosDeepLinkBridge.pending.value
                as dev.maxxing.github.app.PendingNavigation.Thread
            assertEquals("o", thread.target.owner)
            assertEquals(7, thread.target.number)

            clearDeepLinkBridge()
            assertTrue(
                IosNotificationActions.handleTap(
                    deepLink = null,
                    owner = null,
                    repo = null,
                    subjectNumber = null,
                    threadId = "t1",
                )
            )
            assertTrue(dev.maxxing.github.app.IosDeepLinkBridge.pending.value is dev.maxxing.github.app.PendingNavigation.Inbox)
        } finally {
            IosPushFeature.enabledOverrideForTesting = null
            clearDeepLinkBridge()
        }
    }

    @Test
    fun disabledApnsSuppressesTapRoutingAndActions() {
        clearDeepLinkBridge()
        kotlin.test.assertFalse(IosPushFeature.isApnsEnabled)
        kotlin.test.assertFalse(
            IosNotificationActions.handleTap(
                deepLink = "githubmaxxing://thread/o/r/7",
                owner = null,
                repo = null,
                subjectNumber = null,
                threadId = "t1",
            )
        )
        assertNull(dev.maxxing.github.app.IosDeepLinkBridge.pending.value)

        var completions = 0
        IosNotificationActions.handle(ACTION_MARK_READ, "t1") { completions++ }
        assertEquals(1, completions)
    }

    // ---- helpers ---------------------------------------------------------

    private fun kotlinx.coroutines.test.TestScope.handler(
        sessionManager: SessionManager,
        markRead: suspend (String) -> Result<Unit> = { Result.success(Unit) },
        markDone: suspend (String) -> Result<Unit> = { Result.success(Unit) },
        removeDelivered: (String) -> Unit = {},
        onThreadRemoved: (String) -> Unit = {},
        timeoutMillis: Long = NotificationActionHandler.DEFAULT_ACTION_TIMEOUT_MILLIS,
    ): NotificationActionHandler = NotificationActionHandler(
        sessionManager = sessionManager,
        markRead = markRead,
        markDone = markDone,
        removeDelivered = removeDelivered,
        onThreadRemoved = onThreadRemoved,
        scope = this,
        timeoutMillis = timeoutMillis,
        ensureSessionLoaded = { sessionManager.initialize() },
    )

    private fun authenticatedSessionManager(): SessionManager =
        SessionManager(
            tokenStore = InMemoryTokenStore(
                AuthSession(accessToken = "token", userLogin = "octo")
            )
        )

    private fun signedOutSessionManager(): SessionManager =
        SessionManager(tokenStore = InMemoryTokenStore(null))

    private fun thread(id: String): NotificationThread = NotificationThread(
        id = id,
        unread = true,
        reason = NotificationReason.COMMENT,
        updatedAt = "v1",
        subjectTitle = "Title $id",
        subjectType = NotificationSubjectType.ISSUE,
        subjectUrl = "https://api.github.com/repos/octo/repo/issues/1",
        latestCommentUrl = null,
        repoFullName = "octo/repo",
        repoOwner = "octo",
        repoName = "repo",
    )
}

private fun AppContainer.removeUnreadThread(threadId: String) {
    val current = unreadThreads.value ?: return
    updateUnreadThreads(current.filterNot { it.id == threadId })
}

private fun clearDeepLinkBridge() {
    dev.maxxing.github.app.IosDeepLinkBridge.pending.value?.let {
        dev.maxxing.github.app.IosDeepLinkBridge.consume(it.nonce)
    }
}

private class InMemoryTokenStore(initialSession: AuthSession?) : TokenStore {
    private var session: AuthSession? = initialSession
    private var theme = ThemePreferences()
    private var notifications = NotificationPreferences()
    private var markers: Map<String, String> = emptyMap()

    override suspend fun saveSession(session: AuthSession) {
        this.session = session
    }

    override suspend fun loadSession(): AuthSession? = session

    override suspend fun clearSession() {
        session = null
    }

    override suspend fun saveClientId(clientId: String) = Unit

    override suspend fun loadClientId(): String? = null

    override suspend fun saveThemePreferences(prefs: ThemePreferences) {
        theme = prefs
    }

    override suspend fun loadThemePreferences(): ThemePreferences = theme

    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {
        notifications = prefs
    }

    override suspend fun loadNotificationPreferences(): NotificationPreferences = notifications

    override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) {
        this.markers = markers
    }

    override suspend fun loadSeenNotificationMarkers(): Map<String, String> = markers
}
