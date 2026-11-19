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

class PullFilesPrefetchTest {

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

    private fun pullDetailJson(number: Int, changedFiles: Int) = """
        {
          "data": {
            "repository": {
              "viewerPermission": "WRITE",
              "issueOrPullRequest": {
                "__typename": "PullRequest",
                "id": "PR_$number",
                "number": $number,
                "title": "Prefetch test",
                "body": "",
                "state": "OPEN",
                "isDraft": false,
                "url": "https://github.com/octo/app/pull/$number",
                "createdAt": "2026-11-15T00:00:00Z",
                "updatedAt": "2026-11-15T00:00:00Z",
                "headRefName": "feat/x",
                "headRefOid": "abc123",
                "baseRefName": "main",
                "additions": 1,
                "deletions": 0,
                "changedFiles": $changedFiles,
                "author": { "login": "octo", "avatarUrl": "", "url": "" },
                "labels": { "nodes": [] },
                "assignees": { "nodes": [] },
                "reviewRequests": { "nodes": [] },
                "milestone": null,
                "reactionGroups": [],
                "commits": { "nodes": [] },
                "timelineItems": { "nodes": [] }
              }
            }
          }
        }
    """.trimIndent()

    /** PR #1 has [smallCount] changed files, PR #2 has [largeCount]. Counts REST file-list requests per PR. */
    private fun engine(smallCount: Int, largeCount: Int, fileHits: MutableMap<Int, Int>) = MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path == "/graphql" -> {
                val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                val number = Regex("""issueOrPullRequest\(number:\s*(\d+)\)""").find(body)?.groupValues?.get(1)?.toInt()
                    ?: error("No PR number in GraphQL body: $body")
                val count = if (number == 1) smallCount else largeCount
                respond(
                    content = pullDetailJson(number, count),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            path.endsWith("/files") -> {
                val number = path.removeSuffix("/files").substringAfterLast('/').toInt()
                fileHits[number] = (fileHits[number] ?: 0) + 1
                respond(
                    content = """[{"filename":"Main.kt","status":"modified","additions":1,"deletions":0}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            else -> error("Unexpected path: $path")
        }
    }

    @Test
    fun prefetchFetchesFilesForPullsUpToThresholdAndSkipsLargerOnes() = runTest {
        val fileHits = mutableMapOf<Int, Int>()
        val repo = createRepository(
            engine(
                smallCount = PREFETCH_PULL_FILES_MAX_COUNT,
                largeCount = PREFETCH_PULL_FILES_MAX_COUNT + 1,
                fileHits = fileHits
            )
        )

        repo.prefetchIssueOrPull("octo", "app", 1)
        repo.prefetchIssueOrPull("octo", "app", 2)

        assertNotNull(repo.peekIssueOrPullDetail("octo", "app", 1))
        assertNotNull(repo.peekIssueOrPullDetail("octo", "app", 2))
        assertEquals(1, fileHits[1])
        assertNull(fileHits[2])
        assertNotNull(repo.peekPullFiles("octo", "app", 1))
        assertNull(repo.peekPullFiles("octo", "app", 2))

        // Opening the large PR still loads its files on demand.
        repo.getPullFiles("octo", "app", 2).getOrThrow()
        assertEquals(1, fileHits[2])
    }
}
