package dev.maxxing.github.app.notifications

import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.app.NOTIFICATION_KEY_DEEP_LINK
import dev.maxxing.github.app.NOTIFICATION_KEY_THREAD_ID
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.NotificationSubjectType
import dev.maxxing.github.model.NotificationThread
import dev.maxxing.github.network.GitHubNetworkConfig
import dev.maxxing.github.network.GitHubRepository
import dev.maxxing.github.network.NotificationMonitor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosNotifierTest {

    @Test
    fun apnsPushModeSuppressesLocalRequestsButContainerStillUpdatesUnreadState() = runTest {
        val gateway = FakeNotificationGateway()
        val engine = IosNotifierEngine(gateway)
        assertTrue(engine.pushDeliveryActive)

        val sessionManager = SessionManager(tokenStore = FakeTokenStore())
        sessionManager.initialize()

        var requestIndex = 0
        val engineHttp = MockEngine {
            val id = if (requestIndex++ == 0) "t1" else "t2"
            respond(
                content = notificationJson(id),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = GitHubRepository(
            sessionManager = sessionManager,
            networkConfig = GitHubNetworkConfig(sessionManager, baseUrl = "https://example.test"),
            httpClient = HttpClient(engineHttp),
        )
        val container = AppContainer(
            sessionManager = sessionManager,
            repository = repository,
            notificationMonitor = NotificationMonitor(sessionManager, repository),
            showOsNotifications = { threads -> engine.showGitHubNotifications(threads) },
        )

        // First sync records the baseline, second has a genuinely new thread.
        assertTrue(container.syncNotifications().isSuccess)
        assertTrue(container.syncNotifications().isSuccess)

        assertTrue(gateway.scheduled.isEmpty(), "APNs mode must not add local requests")
        assertEquals(listOf("t2"), container.unreadThreads.value?.map { it.id })
        assertEquals(1, container.unreadNotificationCount.value)
    }

    @Test
    fun localFallbackSchedulesUpToSixNotifications() {
        val gateway = FakeNotificationGateway()
        val engine = IosNotifierEngine(gateway, pushDeliveryActive = false)
        engine.authorization = IosNotificationAuthorization.AUTHORIZED

        engine.showGitHubNotifications((1..8).map { thread("t$it", number = it) })

        assertEquals(6, gateway.scheduled.size)
        assertEquals("gh_notif_t1", gateway.scheduled.first().identifier)
        assertEquals("gh_notif_t6", gateway.scheduled.last().identifier)
    }

    @Test
    fun payloadIncludesDeepLinkOnlyForPositiveSubjectNumber() {
        val withNumber = buildGitHubNotificationPayloads(listOf(thread("t1", number = 42))).single()
        assertEquals("MAXXING_GITHUB_THREAD", withNumber.categoryIdentifier)
        assertEquals("octo/repo", withNumber.threadIdentifier)
        assertEquals("octo/repo • comment", withNumber.title)
        assertEquals("Title t1", withNumber.body)
        assertEquals("t1", withNumber.userInfo[NOTIFICATION_KEY_THREAD_ID])
        assertEquals(
            "githubmaxxing://thread/octo/repo/42",
            withNumber.userInfo[NOTIFICATION_KEY_DEEP_LINK],
        )

        val withoutNumber = buildGitHubNotificationPayloads(
            listOf(thread("t2", subjectType = NotificationSubjectType.CHECK_SUITE))
        ).single()
        assertEquals("t2", withoutNumber.userInfo[NOTIFICATION_KEY_THREAD_ID])
        assertFalse(NOTIFICATION_KEY_DEEP_LINK in withoutNumber.userInfo)
        assertNull(withoutNumber.userInfo[NOTIFICATION_KEY_DEEP_LINK])
    }

    @Test
    fun localFallbackSkipsWhenNotAuthorized() {
        val gateway = FakeNotificationGateway()
        val engine = IosNotifierEngine(gateway, pushDeliveryActive = false)
        engine.authorization = IosNotificationAuthorization.DENIED

        engine.showGitHubNotifications(listOf(thread("t1", number = 1)))

        assertTrue(gateway.scheduled.isEmpty())
    }

    @Test
    fun ensureInitializedRegistersCategoriesAndReadsStatus() {
        val gateway = FakeNotificationGateway().apply {
            authorizationResult = IosNotificationAuthorization.AUTHORIZED
        }
        val engine = IosNotifierEngine(gateway)

        engine.ensureInitialized()

        assertEquals(1, gateway.registerCategoriesCalls)
        assertEquals(1, gateway.refreshCalls)
        assertEquals(IosNotificationAuthorization.AUTHORIZED, engine.authorization)
    }

    @Test
    fun testNotificationAcceptedWhenAuthorized() {
        val gateway = FakeNotificationGateway()
        val engine = IosNotifierEngine(gateway)
        engine.authorization = IosNotificationAuthorization.AUTHORIZED

        assertTrue(engine.showTestNotification())
        assertEquals(1, gateway.scheduled.size)
        assertEquals("gh_notif_test", gateway.scheduled.single().identifier)
        assertEquals(0, gateway.requestAuthorizationCalls)
    }

    @Test
    fun testNotificationStartsPermissionRequestWhenNotDetermined() {
        val gateway = FakeNotificationGateway().apply { grantOnRequest = true }
        var authorizationCallback: IosNotificationAuthorization? = null
        val engine = IosNotifierEngine(
            gateway = gateway,
            onAuthorizationResult = { authorizationCallback = it },
        )
        engine.authorization = IosNotificationAuthorization.NOT_DETERMINED

        assertFalse(engine.showTestNotification())

        assertEquals(1, gateway.requestAuthorizationCalls)
        assertEquals(IosNotificationAuthorization.AUTHORIZED, engine.authorization)
        assertEquals(IosNotificationAuthorization.AUTHORIZED, authorizationCallback)
        assertTrue(gateway.scheduled.isEmpty())
    }

    @Test
    fun testNotificationReturnsFalseWhenDenied() {
        val gateway = FakeNotificationGateway().apply {
            authorizationResult = IosNotificationAuthorization.DENIED
        }
        val engine = IosNotifierEngine(gateway)
        engine.authorization = IosNotificationAuthorization.DENIED

        assertFalse(engine.showTestNotification())
        assertEquals(0, gateway.requestAuthorizationCalls)
        // Denied refreshes the cached status but never prompts again.
        assertEquals(1, gateway.refreshCalls)
        assertTrue(gateway.scheduled.isEmpty())
    }

    @Test
    fun testNotificationRefreshesWhenStatusUnknown() {
        val gateway = FakeNotificationGateway().apply {
            authorizationResult = IosNotificationAuthorization.DENIED
        }
        val engine = IosNotifierEngine(gateway)
        engine.authorization = IosNotificationAuthorization.UNKNOWN

        assertFalse(engine.showTestNotification())

        assertEquals(1, gateway.refreshCalls)
        assertEquals(IosNotificationAuthorization.DENIED, engine.authorization)
        assertTrue(gateway.scheduled.isEmpty())
    }

    @Test
    fun pushDeliveryProviderIsEvaluatedOnEveryUse() {
        val gateway = FakeNotificationGateway()
        var configured = false
        val engine = IosNotifierEngine(gateway, pushDeliveryActiveProvider = { configured })
        engine.authorization = IosNotificationAuthorization.AUTHORIZED

        engine.showGitHubNotifications(listOf(thread("t1")))
        assertEquals(1, gateway.scheduled.size)

        configured = true // a push server is installed later: local display stops immediately
        engine.showGitHubNotifications(listOf(thread("t2")))
        assertEquals(1, gateway.scheduled.size)
    }

    @Test
    fun deliveredThreadIsRemovedByIdentifier() = kotlinx.coroutines.test.runTest {
        val gateway = FakeNotificationGateway()
        val engine = IosNotifierEngine(gateway)

        engine.removeDeliveredThread("t1")

        assertEquals(listOf("gh_notif_t1"), gateway.removed)
    }

    @Test
    fun disabledApnsFeatureSuppressesBlockedStatusAndNotificationsWhileInboxSyncKeepsWorking() = runTest {
        assertFalse(IosPushFeature.APNS_ENABLED)
        assertFalse(IosPushFeature.isApnsEnabled)

        // Even before OS notification permission is determined, blocked-state warning and
        // NotificationMonitor marker hold-back are suppressed while APNS_ENABLED is false.
        assertFalse(PlatformNotifier.areOsNotificationsBlocked())
        assertFalse(PlatformNotifier.showTestNotification())
        assertFalse(PlatformNotifier.openOsNotificationSettings())
        PlatformNotifier.ensureInitialized()
        PlatformNotifier.syncBackgroundSchedule(true)
        PlatformNotifier.showGitHubNotifications(listOf(thread("t0", number = 1)))

        val sessionManager = SessionManager(tokenStore = FakeTokenStore())
        sessionManager.initialize()
        var requestIndex = 0
        val engineHttp = MockEngine {
            val id = if (requestIndex++ == 0) "t1" else "t2"
            respond(
                content = notificationJson(id),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = GitHubRepository(
            sessionManager = sessionManager,
            networkConfig = GitHubNetworkConfig(sessionManager, baseUrl = "https://example.test"),
            httpClient = HttpClient(engineHttp),
        )
        // Use the real PlatformNotifier callbacks to confirm Inbox sync still runs cleanly.
        val container = AppContainer(
            sessionManager = sessionManager,
            repository = repository,
        )

        assertTrue(container.syncNotifications().isSuccess)
        assertEquals(listOf("t1"), container.unreadThreads.value?.map { it.id })
        assertEquals(1, container.unreadNotificationCount.value)

        val secondCheck = container.syncNotifications().getOrThrow()
        assertEquals(listOf("t2"), secondCheck.allUnread.map { it.id })
        assertEquals(listOf("t2"), secondCheck.newlyArrivedForOs.map { it.id })
        assertEquals(listOf("t2"), container.unreadThreads.value?.map { it.id })
        assertEquals(1, container.unreadNotificationCount.value)
    }

    private fun notificationJson(id: String): String = """
        [
          {
            "id": "$id",
            "unread": true,
            "reason": "comment",
            "updated_at": "2026-11-17T00:00:00Z",
            "subject": {
              "title": "Subject $id",
              "type": "Commit",
              "url": "https://api.github.com/repos/octo/repo/commits/abc"
            },
            "repository": {
              "name": "repo",
              "full_name": "octo/repo",
              "private": false,
              "owner": { "login": "octo", "avatar_url": "" }
            }
          }
        ]
    """.trimIndent()

    private fun thread(
        id: String,
        number: Int? = null,
        subjectType: NotificationSubjectType = if (number != null) {
            NotificationSubjectType.ISSUE
        } else {
            NotificationSubjectType.CHECK_SUITE
        },
    ): NotificationThread = NotificationThread(
        id = id,
        unread = true,
        reason = NotificationReason.COMMENT,
        updatedAt = "v1",
        subjectTitle = "Title $id",
        subjectType = subjectType,
        subjectUrl = if (number != null) {
            "https://api.github.com/repos/octo/repo/issues/$number"
        } else {
            "https://api.github.com/repos/octo/repo/check-suites/9"
        },
        latestCommentUrl = null,
        repoFullName = "octo/repo",
        repoOwner = "octo",
        repoName = "repo",
    )
}

private class FakeNotificationGateway : IosNotificationGateway {
    var registerCategoriesCalls = 0
    var refreshCalls = 0
    var requestAuthorizationCalls = 0
    var authorizationResult: IosNotificationAuthorization = IosNotificationAuthorization.NOT_DETERMINED
    var grantOnRequest: Boolean = false
    val scheduled = mutableListOf<IosLocalNotification>()
    val removed = mutableListOf<String>()

    override fun registerCategories() {
        registerCategoriesCalls++
    }

    override fun refreshAuthorization(onResult: (IosNotificationAuthorization) -> Unit) {
        refreshCalls++
        onResult(authorizationResult)
    }

    override fun requestAuthorization(onResult: (Boolean) -> Unit) {
        requestAuthorizationCalls++
        if (grantOnRequest) {
            authorizationResult = IosNotificationAuthorization.AUTHORIZED
        }
        onResult(grantOnRequest)
    }

    override fun schedule(payload: IosLocalNotification, onResult: (Boolean) -> Unit) {
        scheduled += payload
        onResult(true)
    }

    override fun removeDelivered(identifier: String) {
        removed += identifier
    }
}

private class FakeTokenStore : TokenStore {
    private var session: AuthSession? = AuthSession(accessToken = "token", userLogin = "octo")
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
