package dev.maxxing.github.app

import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.NotificationThread
import dev.maxxing.github.network.GitHubNetworkConfig
import dev.maxxing.github.network.GitHubRepository
import dev.maxxing.github.network.NotificationMonitor
import dev.maxxing.github.network.PersistentCacheStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two overlapping `syncNotifications()` calls must be serialized so the
 * `NotificationMonitor` read-modify-write of the seen markers cannot lose an
 * update.
 */
class AppContainerTest {

    @Test
    fun overlappingSyncsSerializeAndKeepAllSeenMarkers() = runTest {
        val store = FakeTokenStore().apply {
            loadDelayMillis = 50
            saveDelayMillis = 5
        }
        val sessionManager = SessionManager(tokenStore = store)
        sessionManager.initialize()
        assertEquals("octo", sessionManager.session.value?.userLogin)

        val ids = listOf("thread-1", "thread-2")
        val indexMutex = Mutex()
        var nextIndex = 0
        val engine = MockEngine {
            val index = indexMutex.withLock { nextIndex++ }
            respond(
                content = notificationJson(ids[index.coerceAtMost(ids.lastIndex)]),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = GitHubRepository(
            sessionManager = sessionManager,
            networkConfig = GitHubNetworkConfig(sessionManager, baseUrl = "https://example.test"),
            httpClient = HttpClient(engine),
        )
        val monitor = NotificationMonitor(sessionManager, repository)
        val notified = mutableListOf<List<NotificationThread>>()
        val container = AppContainer(
            sessionManager = sessionManager,
            repository = repository,
            notificationMonitor = monitor,
            showOsNotifications = { notified += it },
        )

        coroutineScope {
            listOf(
                async { container.syncNotifications() },
                async { container.syncNotifications() },
            ).awaitAll()
        }

        // Each call loads the markers exactly once: the second call must have
        // started after the first finished, so it sees the first call's write.
        assertEquals(2, store.loadCount)
        assertEquals(2, store.saveCount)
        val finalMarkers = store.lastSavedMarkers
        assertTrue("thread-1" in finalMarkers, "first sync's marker was lost: $finalMarkers")
        assertTrue("thread-2" in finalMarkers, "second sync's marker was lost: $finalMarkers")
        // Each sync replaces the unread list with its own response; the last
        // one wins, which is the pre-existing behaviour.
        assertEquals(listOf("thread-2"), container.unreadThreads.value?.map { it.id })
        assertEquals(1, container.unreadNotificationCount.value)
    }

    @Test
    fun syncUpdatesUnreadStateAndNotifiesOsForNewArrivals() = runTest {
        val store = FakeTokenStore()
        val sessionManager = SessionManager(tokenStore = store)
        sessionManager.initialize()

        val ids = listOf("thread-1", "thread-2")
        var nextIndex = 0
        val engine = MockEngine {
            val index = nextIndex++
            respond(
                content = notificationJson(ids[index.coerceAtMost(ids.lastIndex)]),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = GitHubRepository(
            sessionManager = sessionManager,
            networkConfig = GitHubNetworkConfig(sessionManager, baseUrl = "https://example.test"),
            httpClient = HttpClient(engine),
        )
        val notified = mutableListOf<List<NotificationThread>>()
        val container = AppContainer(
            sessionManager = sessionManager,
            repository = repository,
            notificationMonitor = NotificationMonitor(sessionManager, repository),
            showOsNotifications = { notified += it },
        )

        // First sync establishes the baseline; the baseline never notifies.
        assertTrue(container.syncNotifications().isSuccess)
        assertTrue(notified.isEmpty())

        assertTrue(container.syncNotifications().isSuccess)
        assertEquals(listOf("thread-2"), container.unreadThreads.value?.map { it.id })
        assertEquals(1, container.unreadNotificationCount.value)
        assertEquals(1, notified.size)
        assertEquals(listOf("thread-2"), notified.single().map { it.id })
    }

    @Test
    fun hydrateFromDiskRestoresProfileUnreadAndRepositoriesImmediately() = runTest {
        val store = FakeTokenStore()
        val sessionManager = SessionManager(tokenStore = store)
        sessionManager.initialize()

        val disk = object : PersistentCacheStore {
            val map = LinkedHashMap<String, String>()
            override suspend fun read(key: String): String? = map[key]
            override suspend fun write(key: String, payload: String) {
                map[key] = payload
            }
            override suspend fun clearAll() {
                map.clear()
            }
        }
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/notifications" -> respond(
                    content = notificationJson("thread-disk"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/graphql" -> respond(
                    content = """
                        {
                          "data": {
                            "viewer": {
                              "login": "octo",
                              "name": "Octocat",
                              "avatarUrl": "",
                              "followers": { "totalCount": 1 },
                              "following": { "totalCount": 2 },
                              "repositories": {
                                "totalCount": 1,
                                "nodes": [
                                  {
                                    "id": "R_d1",
                                    "name": "fast-app",
                                    "nameWithOwner": "octo/fast-app",
                                    "isPrivate": false,
                                    "isFork": false,
                                    "stargazerCount": 10,
                                    "forkCount": 1,
                                    "updatedAt": "2026-11-19T00:00:00Z",
                                    "viewerHasStarred": false,
                                    "owner": { "login": "octo", "avatarUrl": "" }
                                  }
                                ]
                              }
                            }
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val writerConfig = GitHubNetworkConfig(sessionManager, baseUrl = "https://example.test")
        val writerRepo = GitHubRepository(
            sessionManager = sessionManager,
            networkConfig = writerConfig,
            httpClient = writerConfig.createHttpClient(engine),
            diskCache = disk,
            diskPersistScope = this
        )
        writerRepo.getViewerProfile().getOrThrow()
        writerRepo.getViewerRepositories().getOrThrow()
        writerRepo.getNotifications(all = false, participating = false, forceRefresh = true).getOrThrow()
        testScheduler.advanceUntilIdle()

        val coldConfig = GitHubNetworkConfig(sessionManager, baseUrl = "https://example.test")
        val coldRepo = GitHubRepository(
            sessionManager = sessionManager,
            networkConfig = coldConfig,
            httpClient = coldConfig.createHttpClient(MockEngine { error("Network must not be called during hydrateFromDisk") }),
            diskCache = disk,
            diskPersistScope = this
        )
        val container = AppContainer(
            sessionManager = sessionManager,
            repository = coldRepo,
            notificationMonitor = NotificationMonitor(sessionManager, coldRepo)
        )

        container.hydrateFromDisk("octo")
        assertEquals("octo", container.viewerProfile.value?.login)
        assertEquals(listOf("thread-disk"), container.unreadThreads.value?.map { it.id })
        assertEquals(1, container.unreadNotificationCount.value)
        assertEquals(listOf("octo/fast-app"), coldRepo.peekViewerRepositories()?.map { it.fullName })
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
}

private class FakeTokenStore : TokenStore {
    private var session: AuthSession? = AuthSession(
        accessToken = "test-token",
        userLogin = "octo",
    )
    private var clientId: String? = null
    private var theme = ThemePreferences()
    private var notifications = NotificationPreferences()
    private var markers: Map<String, String> = emptyMap()

    var loadDelayMillis: Long = 0
    var saveDelayMillis: Long = 0
    var loadCount: Int = 0
    var saveCount: Int = 0
    var lastSavedMarkers: Map<String, String> = emptyMap()

    override suspend fun saveSession(session: AuthSession) {
        this.session = session
    }

    override suspend fun loadSession(): AuthSession? = session

    override suspend fun clearSession() {
        session = null
    }

    override suspend fun saveClientId(clientId: String) {
        this.clientId = clientId
    }

    override suspend fun loadClientId(): String? = clientId

    override suspend fun saveThemePreferences(prefs: ThemePreferences) {
        theme = prefs
    }

    override suspend fun loadThemePreferences(): ThemePreferences = theme

    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {
        notifications = prefs
    }

    override suspend fun loadNotificationPreferences(): NotificationPreferences = notifications

    override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) {
        saveCount++
        lastSavedMarkers = markers.toMap()
        if (saveDelayMillis > 0) delay(saveDelayMillis)
        this.markers = markers.toMap()
    }

    override suspend fun loadSeenNotificationMarkers(): Map<String, String> {
        loadCount++
        if (loadDelayMillis > 0) delay(loadDelayMillis)
        return markers.toMap()
    }
}
