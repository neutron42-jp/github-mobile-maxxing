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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IssuePullCacheKeyNormalizationTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    private suspend fun createRepository(engine: MockEngine): GitHubRepository {
        val sessionManager = SessionManager(
            tokenStore = object : TokenStore {
                var session: AuthSession? = AuthSession("tok", userLogin = "octo")
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
            },
            deviceFlowClient = DeviceFlowClient(HttpClient(engine))
        )
        sessionManager.initialize()
        return GitHubRepository(sessionManager = sessionManager, httpClient = createClient(engine))
    }

    @Test
    fun issueDetailAndPullFilesCachesMatchAcrossCaseVariants() = runTest {
        var issueDetailHits = 0
        var pullFilesHits = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/graphql" -> {
                    val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    if (body.contains("issueOrPullRequest")) {
                        issueDetailHits++
                        respond(
                            content = """
                                {
                                  "data": {
                                    "repository": {
                                      "viewerPermission": "WRITE",
                                      "issueOrPullRequest": {
                                        "__typename": "Issue",
                                        "id": "I_42",
                                        "number": 42,
                                        "title": "Case test",
                                        "state": "OPEN",
                                        "url": "https://github.com/Octo/App/issues/42",
                                        "createdAt": "2026-11-15T00:00:00Z",
                                        "updatedAt": "2026-11-15T00:00:00Z",
                                        "body": "hello",
                                        "author": { "login": "Octo", "avatarUrl": "" },
                                        "labels": { "nodes": [] },
                                        "assignees": { "nodes": [] },
                                        "reactionGroups": [],
                                        "timelineItems": { "nodes": [] }
                                      }
                                    }
                                  }
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    } else {
                        error("Unexpected GraphQL body: $body")
                    }
                }
                request.url.encodedPath.endsWith("/issues/42/comments") -> {
                    respond(
                        content = """{"id": 1}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                request.url.encodedPath.endsWith("/pulls/42/files") -> {
                    pullFilesHits++
                    respond(
                        content = """[{"filename":"Main.kt","status":"modified","additions":2,"deletions":1}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        val repo = createRepository(engine)

        // Issue detail: fetch with mixed case, read and invalidate with different casing
        repo.getIssueOrPullDetail("Octo", "App", 42).getOrThrow()
        assertNotNull(repo.peekIssueOrPullDetail("octo", "app", 42))
        repo.getIssueOrPullDetail("OCTO", "APP", 42).getOrThrow()
        assertEquals(1, issueDetailHits)

        repo.postComment("octo", "app", 42, "comment").getOrThrow()
        assertNull(repo.peekIssueOrPullDetail("Octo", "App", 42))

        // Pull files: fetch with mixed case, peek and re-fetch with lowercase
        val files = repo.getPullFiles("Octo", "App", 42).getOrThrow()
        assertEquals(1, files.size)
        assertNotNull(repo.peekPullFiles("octo", "app", 42))
        repo.getPullFiles("OCTO", "APP", 42).getOrThrow()
        assertEquals(1, pullFilesHits)
    }
}
