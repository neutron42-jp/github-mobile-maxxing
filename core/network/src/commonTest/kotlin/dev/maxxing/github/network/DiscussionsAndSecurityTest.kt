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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class DiscussionSecurityTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_disc", userLogin = "octocat")
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

class DiscussionsAndSecurityTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    @Test
    fun discussionsCrudUpvoteAndNestedRepliesWork() = runTest {
        var upvoted = false
        var upvotes = 4
        var discussionCreated = false
        var replyPosted = false
        var listQueries = 0

        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            if (path == "/graphql" && request.method == HttpMethod.Post) {
                val rawBody = (request.body as? TextContent)?.text.orEmpty()
                val reqObj = json.parseToJsonElement(rawBody).jsonObject
                val query = reqObj["query"]?.jsonPrimitive?.content.orEmpty()
                val vars = reqObj["variables"]?.jsonObject

                when {
                    query.contains("createDiscussion(") -> {
                        discussionCreated = true
                        respond(
                            content = """
                                {
                                  "data": {
                                    "createDiscussion": {
                                      "discussion": {
                                        "id": "D_2",
                                        "number": 12,
                                        "title": "${vars?.get("title")?.jsonPrimitive?.content}",
                                        "body": "${vars?.get("body")?.jsonPrimitive?.content}",
                                        "createdAt": "2026-11-15T12:10:00Z",
                                        "updatedAt": "2026-11-15T12:10:00Z",
                                        "upvoteCount": 1,
                                        "viewerHasUpvoted": true,
                                        "isAnswered": false,
                                        "closed": false,
                                        "url": "https://github.com/octocat/app/discussions/12",
                                        "author": { "login": "octocat", "avatarUrl": "" },
                                        "category": { "id": "CAT_QA", "name": "Q&A", "emoji": "", "description": "Questions", "isAnswerable": true },
                                        "comments": { "totalCount": 0 }
                                      }
                                    }
                                  }
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    query.contains("addDiscussionComment(") -> {
                        replyPosted = true
                        assertEquals("C_1", vars?.get("replyToId")?.jsonPrimitive?.content)
                        respond(
                            content = """
                                {
                                  "data": {
                                    "addDiscussionComment": {
                                      "comment": {
                                        "id": "C_2",
                                        "body": "${vars?.get("body")?.jsonPrimitive?.content}",
                                        "createdAt": "2026-11-15T12:15:00Z",
                                        "isAnswer": false,
                                        "upvoteCount": 0,
                                        "viewerHasUpvoted": false,
                                        "author": { "login": "octocat", "avatarUrl": "" }
                                      }
                                    }
                                  }
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    query.contains("addUpvote(") -> {
                        upvoted = true
                        upvotes += 1
                        respond(
                            content = """{ "data": { "addUpvote": { "subject": { "upvoteCount": $upvotes, "viewerHasUpvoted": true } } } }""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    query.contains("removeUpvote(") -> {
                        upvoted = false
                        upvotes -= 1
                        respond(
                            content = """{ "data": { "removeUpvote": { "subject": { "upvoteCount": $upvotes, "viewerHasUpvoted": false } } } }""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    query.contains("discussion(number:") -> {
                        val repliesJson = if (replyPosted) {
                            """[{ "id": "C_2", "body": "Glad it helped!", "createdAt": "2026-11-15T12:15:00Z", "isAnswer": false, "author": { "login": "octocat", "avatarUrl": "" } }]"""
                        } else {
                            "[]"
                        }
                        respond(
                            content = """
                                {
                                  "data": {
                                    "repository": {
                                      "discussionCategories": {
                                        "nodes": [
                                          { "id": "CAT_QA", "name": "Q&A", "emoji": "", "description": "Questions", "isAnswerable": true }
                                        ]
                                      },
                                      "discussion": {
                                        "id": "D_1",
                                        "number": 5,
                                        "title": "How to build release APK?",
                                        "body": "Run assembleRelease cleanly.",
                                        "createdAt": "2026-11-14T08:00:00Z",
                                        "updatedAt": "2026-11-15T09:00:00Z",
                                        "upvoteCount": $upvotes,
                                        "viewerHasUpvoted": $upvoted,
                                        "isAnswered": true,
                                        "closed": false,
                                        "url": "https://github.com/octocat/app/discussions/5",
                                        "author": { "login": "hubot", "avatarUrl": "" },
                                        "category": { "id": "CAT_QA", "name": "Q&A", "emoji": "", "description": "Questions", "isAnswerable": true },
                                        "comments": {
                                          "totalCount": 1,
                                          "nodes": [
                                            {
                                              "id": "C_1",
                                              "body": "Use `./gradlew :androidApp:assembleRelease`.",
                                              "createdAt": "2026-11-14T09:00:00Z",
                                              "isAnswer": true,
                                              "upvoteCount": 2,
                                              "viewerHasUpvoted": false,
                                              "author": { "login": "octocat", "avatarUrl": "" },
                                              "replies": { "nodes": $repliesJson }
                                            }
                                          ]
                                        }
                                      }
                                    }
                                  }
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    query.contains("discussions(") -> {
                        listQueries++
                        val newEntry = if (discussionCreated) {
                            """
                            {
                              "id": "D_2",
                              "number": 12,
                              "title": "Roadmap 2027",
                              "body": "Share ideas",
                              "createdAt": "2026-11-15T12:10:00Z",
                              "updatedAt": "2026-11-15T12:10:00Z",
                              "upvoteCount": 1,
                              "viewerHasUpvoted": true,
                              "isAnswered": false,
                              "closed": false,
                              "url": "https://github.com/octocat/app/discussions/12",
                              "author": { "login": "octocat", "avatarUrl": "" },
                              "category": { "id": "CAT_QA", "name": "Q&A", "emoji": "", "description": "Questions", "isAnswerable": true },
                              "comments": { "totalCount": 0 }
                            },
                            """.trimIndent()
                        } else {
                            ""
                        }
                        respond(
                            content = """
                                {
                                  "data": {
                                    "repository": {
                                      "id": "R_repo1",
                                      "discussionCategories": {
                                        "nodes": [
                                          { "id": "CAT_QA", "name": "Q&A", "emoji": "", "description": "Questions", "isAnswerable": true }
                                        ]
                                      },
                                      "discussions": {
                                        "nodes": [
                                          $newEntry
                                          {
                                            "id": "D_1",
                                            "number": 5,
                                            "title": "How to build release APK?",
                                            "body": "Run assembleRelease cleanly.",
                                            "createdAt": "2026-11-14T08:00:00Z",
                                            "updatedAt": "2026-11-15T09:00:00Z",
                                            "upvoteCount": $upvotes,
                                            "viewerHasUpvoted": $upvoted,
                                            "isAnswered": true,
                                            "closed": false,
                                            "url": "https://github.com/octocat/app/discussions/5",
                                            "author": { "login": "hubot", "avatarUrl": "" },
                                            "category": { "id": "CAT_QA", "name": "Q&A", "emoji": "", "description": "Questions", "isAnswerable": true },
                                            "comments": { "totalCount": 1 }
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

                    else -> error("Unexpected GraphQL query: $query")
                }
            } else {
                respond("Unexpected path $path", HttpStatusCode.NotFound)
            }
        }

        val http = createClient(engine)
        val sessionManager = SessionManager(DiscussionSecurityTestTokenStore(), DeviceFlowClient(http))
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = http)

        val listData = repo.getDiscussions("octocat", "app").getOrThrow()
        assertEquals("R_repo1", listData.repositoryId)
        assertEquals(listOf("Q&A"), listData.categories.map { it.name })
        assertEquals(1, listData.discussions.size)
        assertTrue(listData.discussions[0].isAnswered)

        // Cached hit
        repo.getDiscussions("octocat", "app").getOrThrow()
        assertEquals(1, listQueries)

        val detail = repo.getDiscussionDetail("octocat", "app", 5).getOrThrow()
        assertEquals(1, detail.comments.size)
        assertTrue(detail.comments[0].isAnswer)

        val (upNow, upCount) = repo.toggleDiscussionUpvote("octocat", "app", "D_1", currentlyUpvoted = false, discussionNumber = 5).getOrThrow()
        assertTrue(upNow)
        assertEquals(5, upCount)
        assertNull(repo.peekDiscussionDetail("octocat", "app", 5))

        val (downNow, downCount) = repo.toggleDiscussionUpvote("octocat", "app", "D_1", currentlyUpvoted = true, discussionNumber = 5).getOrThrow()
        assertFalse(downNow)
        assertEquals(4, downCount)

        val reply = repo.addDiscussionComment("octocat", "app", 5, "D_1", "Glad it helped!", replyToId = "C_1").getOrThrow()
        assertEquals("Glad it helped!", reply.body)
        val refreshedDetail = repo.getDiscussionDetail("octocat", "app", 5).getOrThrow()
        assertEquals(1, refreshedDetail.comments[0].replies.size)

        val created = repo.createDiscussion("octocat", "app", "R_repo1", "CAT_QA", "Roadmap 2027", "Share ideas").getOrThrow()
        assertEquals(12, created.number)
        assertNull(repo.peekDiscussions("octocat", "app"))

        val updatedList = repo.getDiscussions("octocat", "app").getOrThrow()
        assertEquals(listOf(12, 5), updatedList.discussions.map { it.number })
        assertEquals(2, listQueries)
    }

    @Test
    fun securityOverviewParsesAlertsAdvisoriesAndHandlesForbiddenGracefully() = runTest {
        var forbidAlerts = false
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/repos/octocat/app/dependabot/alerts" -> {
                    if (forbidAlerts) {
                        respond("Forbidden", HttpStatusCode.Forbidden)
                    } else {
                        respond(
                            content = """
                                [
                                  {
                                    "number": 7,
                                    "state": "open",
                                    "created_at": "2026-11-12T10:00:00Z",
                                    "html_url": "https://github.com/octocat/app/security/dependabot/7",
                                    "dependency": { "package": { "ecosystem": "gradle", "name": "io.ktor:ktor-client-core" } },
                                    "security_advisory": { "severity": "high", "summary": "Header smuggling in client" },
                                    "security_vulnerability": {
                                      "severity": "high",
                                      "vulnerable_version_range": "< 3.0.1",
                                      "first_patched_version": { "identifier": "3.0.1" }
                                    }
                                  }
                                ]
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }

                path == "/repos/octocat/app/security-advisories" -> respond(
                    content = """
                        [
                          {
                            "ghsa_id": "GHSA-xxxx-yyyy-zzzz",
                            "summary": "Potential token leak in logs",
                            "severity": "medium",
                            "state": "published",
                            "published_at": "2026-11-01T00:00:00Z",
                            "html_url": "https://github.com/octocat/app/security/advisories/GHSA-xxxx-yyyy-zzzz"
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                else -> respond("Not found", HttpStatusCode.NotFound)
            }
        }

        val http = createClient(engine)
        val sessionManager = SessionManager(DiscussionSecurityTestTokenStore(), DeviceFlowClient(http))
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = http)

        val overview = repo.getRepoSecurityOverview("octocat", "app").getOrThrow()
        assertNull(overview.alertsAccessNotice)
        assertNull(overview.alertsAccessStatusCode)
        assertEquals(1, overview.alerts.size)
        assertEquals("io.ktor:ktor-client-core", overview.alerts[0].packageName)
        assertEquals("3.0.1", overview.alerts[0].patchedVersion)
        assertEquals(1, overview.advisories.size)
        assertEquals("GHSA-xxxx-yyyy-zzzz", overview.advisories[0].ghsaId)

        forbidAlerts = true
        val fallbackOverview = repo.getRepoSecurityOverview("octocat", "app", forceRefresh = true).getOrThrow()
        assertTrue(fallbackOverview.alerts.isEmpty())
        assertEquals("Dependabot alerts require repository admin or security_events permission.", fallbackOverview.alertsAccessNotice)
        // The status code lets the UI localize the English notice.
        assertEquals(403, fallbackOverview.alertsAccessStatusCode)
        assertEquals(1, fallbackOverview.advisories.size)
    }
}
