package dev.maxxing.github.network

import dev.maxxing.github.auth.DeviceFlowClient
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class PerfTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_perf", userLogin = "octocat")
    override suspend fun saveSession(session: AuthSession) { this.session = session }
    override suspend fun loadSession() = session
    override suspend fun clearSession() { session = null }
    override suspend fun saveClientId(clientId: String) {}
    override suspend fun loadClientId(): String? = null
    override suspend fun saveThemePreferences(prefs: ThemePreferences) {}
    override suspend fun loadThemePreferences() = ThemePreferences()
    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {}
    override suspend fun loadNotificationPreferences() = NotificationPreferences()
    override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) {}
    override suspend fun loadSeenNotificationMarkers() = emptyMap<String, String>()
}

private class InMemoryPersistentCacheStore : PersistentCacheStore {
    val entries = LinkedHashMap<String, String>()
    override suspend fun read(key: String): String? = entries[key]
    override suspend fun write(key: String, payload: String) {
        entries[key] = payload
    }
    override suspend fun clearAll() {
        entries.clear()
    }
}

class PerformanceCachingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    @Test
    fun repoListGraphQlQueriesOmitHeavyDetailFieldsAndStarredDefaultsTrue() = runTest {
        val capturedQueries = mutableListOf<String>()
        val engine = MockEngine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            capturedQueries += body
            val payload = when {
                body.contains("starredRepositories") -> """
                    {
                      "data": {
                        "viewer": {
                          "starredRepositories": {
                            "nodes": [
                              {
                                "id": "R_star",
                                "name": "kmp",
                                "nameWithOwner": "octo/kmp",
                                "description": "Fast",
                                "isPrivate": false,
                                "isFork": false,
                                "isArchived": false,
                                "hasIssuesEnabled": true,
                                "stargazerCount": 99,
                                "forkCount": 4,
                                "updatedAt": "2026-11-18T00:00:00Z",
                                "owner": { "login": "octo", "avatarUrl": "" },
                                "primaryLanguage": { "name": "Kotlin", "color": "#A97BFF" },
                                "defaultBranchRef": { "name": "main" }
                              }
                            ]
                          }
                        }
                      }
                    }
                """.trimIndent()
                else -> """
                    {
                      "data": {
                        "viewer": {
                          "repositories": {
                            "nodes": [
                              {
                                "id": "R_mine",
                                "name": "app",
                                "nameWithOwner": "octocat/app",
                                "description": "Mine",
                                "isPrivate": true,
                                "isFork": false,
                                "isArchived": false,
                                "hasIssuesEnabled": true,
                                "stargazerCount": 5,
                                "forkCount": 1,
                                "updatedAt": "2026-11-18T00:00:00Z",
                                "viewerHasStarred": false,
                                "owner": { "login": "octocat", "avatarUrl": "" },
                                "primaryLanguage": null,
                                "defaultBranchRef": { "name": "main" }
                              }
                            ]
                          }
                        }
                      }
                    }
                """.trimIndent()
            }
            respond(
                content = payload,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val gql = GitHubGraphQlService(createClient(engine))
        val mine = gql.fetchViewerRepositories()
        val starred = gql.fetchStarredRepositories()

        assertEquals(1, mine.size)
        assertFalse(mine[0].viewerHasStarred)
        assertEquals(1, starred.size)
        assertTrue(starred[0].viewerHasStarred)

        assertEquals(2, capturedQueries.size)
        capturedQueries.forEach { query ->
            assertFalse(query.contains("issues("), "List query must not request open issues count: $query")
            assertFalse(query.contains("pullRequests("), "List query must not request open PR count: $query")
            assertFalse(query.contains("viewerPermission"), "List query must not request viewerPermission: $query")
        }
        assertFalse(capturedQueries[1].contains("viewerHasStarred"))
    }

    @Test
    fun singleFlightDeduplicatesConcurrentReadsAndIsolatesSingleCallerCancellation() = runTest {
        var requestCount = 0
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            requestCount += 1
            started.complete(Unit)
            gate.await()
            respond(
                content = """
                    {
                      "data": {
                        "viewer": {
                          "repositories": {
                            "nodes": [
                              {
                                "id": "R_1",
                                "name": "one",
                                "nameWithOwner": "octocat/one",
                                "isPrivate": false,
                                "isFork": false,
                                "stargazerCount": 1,
                                "forkCount": 0,
                                "updatedAt": "2026-11-18T00:00:00Z",
                                "viewerHasStarred": true,
                                "owner": { "login": "octocat", "avatarUrl": "" }
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
        }

        val http = createClient(engine)
        val sm = SessionManager(PerfTestTokenStore(), DeviceFlowClient(http))
        sm.initialize()
        val repo = GitHubRepository(sm, httpClient = http)

        coroutineScope {
            val callerA = async { repo.getViewerRepositories() }
            val callerB = async { repo.getViewerRepositories() }
            started.await()
            callerA.cancel(CancellationException("callerA left screen"))
            gate.complete(Unit)

            val resB = callerB.await().getOrThrow()
            assertEquals(listOf("octocat/one"), resB.map { it.fullName })
            assertEquals(1, requestCount)
        }
    }

    @Test
    fun restConditionalRequestsUseETagAndHandle304NotModified() = runTest {
        var callIndex = 0
        val seenIfNoneMatch = mutableListOf<String?>()
        val engine = MockEngine { request ->
            seenIfNoneMatch += request.headers[HttpHeaders.IfNoneMatch]
            callIndex += 1
            if (callIndex == 1) {
                respond(
                    content = """[{"id":"n1","unread":true,"reason":"mention","updated_at":"2026-11-18T00:00:00Z","subject":{"title":"Ping","type":"Issue","url":null},"repository":{"name":"app","full_name":"octo/app","owner":{"login":"octo"}}}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.ETag to listOf("\"etag-v1\"")
                    )
                )
            } else if (callIndex == 2) {
                respond(
                    content = "",
                    status = HttpStatusCode.NotModified,
                    headers = headersOf(HttpHeaders.ETag to listOf("\"etag-v1\""))
                )
            } else {
                respond(
                    content = """[{"id":"n2","unread":true,"reason":"assign","updated_at":"2026-11-18T01:00:00Z","subject":{"title":"Assigned","type":"PullRequest","url":null},"repository":{"name":"app","full_name":"octo/app","owner":{"login":"octo"}}}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.ETag to listOf("\"etag-v2\"")
                    )
                )
            }
        }

        val http = createClient(engine)
        val sm = SessionManager(PerfTestTokenStore(), DeviceFlowClient(http))
        sm.initialize()
        val repo = GitHubRepository(sm, httpClient = http)

        val first = repo.getNotifications(forceRefresh = true).getOrThrow()
        assertEquals(listOf("n1"), first.map { it.id })
        assertNull(seenIfNoneMatch[0])

        val second = repo.getNotifications(forceRefresh = true).getOrThrow()
        assertEquals(listOf("n1"), second.map { it.id })
        assertEquals("\"etag-v1\"", seenIfNoneMatch[1])

        repo.clearCache()
        val third = repo.getNotifications(forceRefresh = true).getOrThrow()
        assertEquals(listOf("n2"), third.map { it.id })
        assertNull(seenIfNoneMatch[2])
    }

    @Test
    fun persistentDiskCacheHydratesStaleDataImmediatelyOnColdStartAndClearsOnReset() = runTest {
        val store = InMemoryPersistentCacheStore()
        var networkCalls = 0
        val engine = MockEngine {
            networkCalls += 1
            respond(
                content = """
                    {
                      "data": {
                        "viewer": {
                          "repositories": {
                            "nodes": [
                              {
                                "id": "R_disk",
                                "name": "cached-repo",
                                "nameWithOwner": "octocat/cached-repo",
                                "isPrivate": false,
                                "isFork": false,
                                "stargazerCount": $networkCalls,
                                "forkCount": 0,
                                "updatedAt": "2026-11-18T00:00:00Z",
                                "viewerHasStarred": false,
                                "owner": { "login": "octocat", "avatarUrl": "" }
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
        }

        val http = createClient(engine)
        val sm = SessionManager(PerfTestTokenStore(), DeviceFlowClient(http))
        sm.initialize()

        val firstRepo = GitHubRepository(
            sessionManager = sm,
            httpClient = http,
            diskCache = store,
            diskPersistScope = this
        )
        val initial = firstRepo.getViewerRepositories().getOrThrow()
        assertEquals(1, initial[0].stargazersCount)
        testScheduler.advanceUntilIdle()
        assertTrue(store.entries.isNotEmpty())

        // Cold start with a fresh GitHubRepository instance sharing the same disk store
        val secondRepo = GitHubRepository(
            sessionManager = sm,
            httpClient = http,
            diskCache = store,
            diskPersistScope = this
        )
        assertNull(secondRepo.peekViewerRepositories())
        secondRepo.hydrateDiskCache("octocat")
        val hydrated = secondRepo.peekViewerRepositories()
        assertNotNull(hydrated)
        assertEquals(listOf("octocat/cached-repo"), hydrated.map { it.fullName })
        assertEquals(1, hydrated[0].stargazersCount)

        // Hydrated entries are marked stale, so a non-forced fetch still revalidates in the background
        val refreshed = secondRepo.getViewerRepositories(forceRefresh = false).getOrThrow()
        assertEquals(2, refreshed[0].stargazersCount)
        assertEquals(2, networkCalls)

        secondRepo.clearCache()
        assertTrue(store.entries.isEmpty())
    }
}
