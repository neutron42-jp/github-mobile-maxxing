package dev.maxxing.github.network

import dev.maxxing.github.auth.DeviceFlowClient
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.FileViewedState
import dev.maxxing.github.model.ReviewEvent
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class PullReviewSyncTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_review", userLogin = "reviewer")
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

class PullReviewSyncTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    @Test
    fun syncsViewedStatesPendingReviewCommentsAndSubmitDiscardFlows() = runTest {
        var viewedMap = mutableMapOf(
            "src/Main.kt" to "VIEWED",
            "src/Helper.kt" to "UNVIEWED",
            "package-lock.json" to "DISMISSED"
        )
        var pendingReviewId: String? = "PRR_existing"
        var pendingCommentBody = "Existing web draft"
        var pendingCommentDeleted = false
        var submittedEvent: String? = null
        var submittedReviewId: String? = null
        var discardedReviewId: String? = null
        var addedThreadSubjectType: String? = null
        var addedThreadStartLine: Int? = null

        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            val raw = (request.body as? TextContent)?.text.orEmpty()
            if (path == "/graphql" && request.method == HttpMethod.Post) {
                val reqObj = json.parseToJsonElement(raw).jsonObject
                val query = reqObj["query"]?.jsonPrimitive?.content.orEmpty()
                val vars = reqObj["variables"]?.jsonObject ?: error("missing variables")

                when {
                    query.contains("reviewThreads") && query.contains("viewerViewedState") -> {
                        val pendingNodes = if (pendingReviewId != null) {
                            val commentsJson = if (pendingCommentDeleted) {
                                "[]"
                            } else {
                                """
                                [{
                                  "id": "PRC_1",
                                  "path": "src/Helper.kt",
                                  "line": 12,
                                  "originalLine": 12,
                                  "startLine": null,
                                  "subjectType": "LINE",
                                  "body": "$pendingCommentBody",
                                  "createdAt": "2026-11-20T01:00:00Z",
                                  "viewerCanUpdate": true,
                                  "viewerCanDelete": true,
                                  "author": { "login": "reviewer", "avatarUrl": "", "url": "" }
                                }]
                                """.trimIndent()
                            }
                            """
                            [{
                              "id": "$pendingReviewId",
                              "body": "",
                              "comments": { "nodes": $commentsJson }
                            }]
                            """.trimIndent()
                        } else {
                            "[]"
                        }
                        respond(
                            content = """
                            {
                              "data": {
                                "repository": {
                                  "pullRequest": {
                                    "files": {
                                      "pageInfo": { "hasNextPage": true, "endCursor": "cursor_p2" },
                                      "nodes": [
                                        { "path": "src/Main.kt", "viewerViewedState": "${viewedMap["src/Main.kt"]}" },
                                        { "path": "src/Helper.kt", "viewerViewedState": "${viewedMap["src/Helper.kt"]}" }
                                      ]
                                    },
                                    "reviews": { "nodes": $pendingNodes },
                                    "reviewThreads": {
                                      "nodes": [
                                        {
                                          "id": "PRRT_1",
                                          "path": "src/Helper.kt",
                                          "line": 12,
                                          "originalLine": 12,
                                          "startLine": null,
                                          "diffSide": "RIGHT",
                                          "subjectType": "LINE",
                                          "isResolved": false,
                                          "isOutdated": false,
                                          "comments": {
                                            "nodes": [{
                                              "id": "PRC_1",
                                              "body": "$pendingCommentBody",
                                              "createdAt": "2026-11-20T01:00:00Z",
                                              "state": "PENDING",
                                              "viewerCanUpdate": true,
                                              "viewerCanDelete": true,
                                              "author": { "login": "reviewer", "avatarUrl": "", "url": "" },
                                              "pullRequestReview": { "id": "PRR_existing", "state": "PENDING" }
                                            }]
                                          }
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

                    query.contains("after:") && query.contains("viewerViewedState") -> {
                        assertEquals("cursor_p2", vars["after"]?.jsonPrimitive?.content)
                        respond(
                            content = """
                            {
                              "data": {
                                "repository": {
                                  "pullRequest": {
                                    "files": {
                                      "pageInfo": { "hasNextPage": false, "endCursor": null },
                                      "nodes": [
                                        { "path": "package-lock.json", "viewerViewedState": "${viewedMap["package-lock.json"]}" }
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

                    query.contains("markFileAsViewed") -> {
                        val filePath = vars["path"]?.jsonPrimitive?.content ?: ""
                        viewedMap[filePath] = "VIEWED"
                        respond("""{ "data": { "markFileAsViewed": { "pullRequest": { "id": "PR_1" } } } }""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }

                    query.contains("unmarkFileAsViewed") -> {
                        val filePath = vars["path"]?.jsonPrimitive?.content ?: ""
                        viewedMap[filePath] = "UNVIEWED"
                        respond("""{ "data": { "unmarkFileAsViewed": { "pullRequest": { "id": "PR_1" } } } }""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }

                    query.contains("addPullRequestReview(") -> {
                        pendingReviewId = "PRR_created"
                        respond("""{ "data": { "addPullRequestReview": { "pullRequestReview": { "id": "PRR_created", "body": "", "state": "PENDING" } } } }""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }

                    query.contains("addPullRequestReviewThread") -> {
                        addedThreadSubjectType = vars["subjectType"]?.jsonPrimitive?.content
                        addedThreadStartLine = vars["startLine"]?.jsonPrimitive?.int
                        respond(
                            content = """
                            {
                              "data": {
                                "addPullRequestReviewThread": {
                                  "thread": {
                                    "id": "PRRT_new",
                                    "comments": {
                                      "nodes": [{
                                        "id": "PRC_2",
                                        "path": "${vars["path"]?.jsonPrimitive?.content}",
                                        "line": ${vars["line"] ?: "null"},
                                        "originalLine": ${vars["line"] ?: "null"},
                                        "startLine": ${vars["startLine"] ?: "null"},
                                        "subjectType": "${addedThreadSubjectType}",
                                        "body": "${vars["body"]?.jsonPrimitive?.content}",
                                        "createdAt": "2026-11-20T01:05:00Z",
                                        "author": { "login": "reviewer", "avatarUrl": "", "url": "" }
                                      }]
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

                    query.contains("updatePullRequestReviewComment") -> {
                        pendingCommentBody = vars["body"]?.jsonPrimitive?.content.orEmpty()
                        respond("""{ "data": { "updatePullRequestReviewComment": { "pullRequestReviewComment": { "id": "PRC_1", "body": "$pendingCommentBody" } } } }""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }

                    query.contains("deletePullRequestReviewComment") -> {
                        pendingCommentDeleted = true
                        respond("""{ "data": { "deletePullRequestReviewComment": { "pullRequestReviewComment": { "id": "PRC_1" } } } }""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }

                    query.contains("submitPullRequestReview") -> {
                        submittedReviewId = vars["reviewId"]?.jsonPrimitive?.content
                        submittedEvent = vars["event"]?.jsonPrimitive?.content
                        pendingReviewId = null
                        respond("""{ "data": { "submitPullRequestReview": { "pullRequestReview": { "id": "$submittedReviewId", "state": "APPROVED" } } } }""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }

                    query.contains("deletePullRequestReview(") -> {
                        discardedReviewId = vars["reviewId"]?.jsonPrimitive?.content
                        pendingReviewId = null
                        respond("""{ "data": { "deletePullRequestReview": { "pullRequestReview": { "id": "$discardedReviewId" } } } }""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }

                    else -> respond("""{ "errors": [{ "message": "Unhandled query" }] }""", HttpStatusCode.BadRequest)
                }
            } else {
                respond("""{ "message": "Unexpected path $path" }""", HttpStatusCode.NotFound)
            }
        }

        val tokenStore = PullReviewSyncTestTokenStore()
        val client = createClient(engine)
        val sessionManager = SessionManager(tokenStore, DeviceFlowClient(client))
        sessionManager.initialize()
        val repo = GitHubRepository(
            sessionManager = sessionManager,
            httpClient = client
        )

        val syncState = repo.getPullReviewSyncState("octocat", "app", 74).getOrThrow()
        assertEquals(FileViewedState.VIEWED, syncState.viewedStates["src/Main.kt"])
        assertEquals(FileViewedState.UNVIEWED, syncState.viewedStates["src/Helper.kt"])
        assertEquals(FileViewedState.DISMISSED, syncState.viewedStates["package-lock.json"])
        assertNotNull(syncState.pendingReview)
        assertEquals("PRR_existing", syncState.pendingReview?.id)
        assertEquals("RIGHT", syncState.pendingReview?.comments?.firstOrNull()?.diffSide)

        assertTrue(repo.setPullFileViewed("octocat", "app", 74, "PR_1", "src/Helper.kt", viewed = true).isSuccess)
        assertEquals(FileViewedState.VIEWED, repo.peekPullReviewSyncState("octocat", "app", 74)?.viewedStates?.get("src/Helper.kt"))

        assertTrue(repo.setPullFileViewed("octocat", "app", 74, "PR_1", "src/Main.kt", viewed = false).isSuccess)
        assertEquals(FileViewedState.UNVIEWED, repo.peekPullReviewSyncState("octocat", "app", 74)?.viewedStates?.get("src/Main.kt"))

        val addedMulti = repo.addPendingReviewComment(
            owner = "octocat",
            repo = "app",
            number = 74,
            pullRequestId = "PR_1",
            commitOid = "abc123",
            existingPendingReviewId = "PRR_existing",
            path = "src/Helper.kt",
            body = "Suggest change",
            line = 20,
            side = "RIGHT",
            startLine = 18,
            startSide = "RIGHT"
        ).getOrThrow()
        assertEquals("LINE", addedThreadSubjectType)
        assertEquals(18, addedThreadStartLine)
        assertEquals(20, addedMulti.line)

        assertTrue(repo.updatePendingReviewComment("octocat", "app", 74, "PRC_1", "Updated draft").isSuccess)
        assertEquals("Updated draft", pendingCommentBody)

        assertTrue(repo.deletePendingReviewComment("octocat", "app", 74, "PRC_1").isSuccess)
        assertTrue(pendingCommentDeleted)

        assertTrue(
            repo.submitPullReview(
                owner = "octocat",
                repo = "app",
                number = 74,
                event = ReviewEvent.APPROVE,
                body = "Looks good",
                pendingReviewId = "PRR_existing"
            ).isSuccess
        )
        assertEquals("PRR_existing", submittedReviewId)
        assertEquals("APPROVE", submittedEvent)

        // Creating a new pending review from scratch and discarding it
        val createdFileComment = repo.addPendingReviewComment(
            owner = "octocat",
            repo = "app",
            number = 74,
            pullRequestId = "PR_1",
            commitOid = "abc123",
            existingPendingReviewId = null,
            path = "README.md",
            body = "File note",
            line = null
        ).getOrThrow()
        assertEquals("FILE", addedThreadSubjectType)
        assertNull(createdFileComment.line)

        assertTrue(repo.discardPendingPullReview("octocat", "app", 74, "PRR_created").isSuccess)
        assertEquals("PRR_created", discardedReviewId)
    }
}
