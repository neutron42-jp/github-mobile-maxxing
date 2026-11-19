package dev.maxxing.github.network

import dev.maxxing.github.auth.DeviceFlowClient
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.NotificationSubjectType
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GitHubServicesTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    @Test
    fun restServiceParsesNotificationsAndFileContent() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/notifications" -> {
                    assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
                    respond(
                    content = """
                        [
                          {
                            "id": "991",
                            "unread": true,
                            "reason": "review_requested",
                            "updated_at": "2026-11-15T08:00:00Z",
                            "subject": {
                              "title": "Add MD3E Expressive Theme",
                              "type": "PullRequest",
                              "url": "https://api.github.com/repos/octo/app/pulls/42"
                            },
                            "repository": {
                              "name": "app",
                              "full_name": "octo/app",
                              "private": false,
                              "owner": { "login": "octo", "avatar_url": "https://example.com/a.png" }
                            }
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                }
                "/repos/octo/app/contents/README.md" -> respond(
                    content = """
                        {
                          "name": "README.md",
                          "path": "README.md",
                          "sha": "abc",
                          "size": 12,
                          "type": "file",
                          "encoding": "base64",
                          "content": "SGVsbG8gS01QCg=="
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        val rest = GitHubRestService(createClient(engine))
        val notifications = rest.fetchNotifications()
        assertEquals(1, notifications.size)
        assertEquals(NotificationReason.REVIEW_REQUESTED, notifications[0].reason)
        assertEquals(NotificationSubjectType.PULL_REQUEST, notifications[0].subjectType)
        assertEquals(42, notifications[0].subjectNumber)

        val file = rest.fetchFileContent("octo", "app", "README.md")
        assertEquals("Hello KMP\n", file.content)
    }

    @Test
    fun graphQlServiceAppliesClosedAndAllStateFilters() = runTest {
        val capturedQueries = mutableListOf<String>()
        val engine = MockEngine { request ->
            val bodyText = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            capturedQueries.add(bodyText)
            respond(
                content = """{"data":{"search":{"nodes":[]},"repository":{"pullRequests":{"nodes":[]}}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val gql = GitHubGraphQlService(createClient(engine))
        gql.fetchIssuesAndPulls(IssueFilterPreset.CREATED, "neutron42", IssueStateFilter.CLOSED)
        gql.fetchIssuesAndPulls(IssueFilterPreset.CREATED, "neutron42", IssueStateFilter.ALL)
        gql.fetchRepoIssuesAndPulls("octo", "app", pullRequests = true, stateFilter = IssueStateFilter.CLOSED)
        gql.fetchRepoIssuesAndPulls("octo", "app", pullRequests = true, stateFilter = IssueStateFilter.ALL)

        assertEquals(true, capturedQueries[0].contains("is:closed"))
        assertEquals(false, capturedQueries[1].contains("is:open"))
        assertEquals(false, capturedQueries[1].contains("is:closed"))
        assertEquals(true, capturedQueries[2].contains("[CLOSED, MERGED]"))
        assertEquals(true, capturedQueries[3].contains("[OPEN, CLOSED, MERGED]"))
    }

    @Test
    fun graphQlServiceParsesSearchResults() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/graphql") {
                respond(
                    content = """
                        {
                          "data": {
                            "search": {
                              "nodes": [
                                {
                                  "__typename": "PullRequest",
                                  "id": "PR_1",
                                  "number": 128,
                                  "title": "Implement OAuth Device Flow",
                                  "state": "OPEN",
                                  "isDraft": false,
                                  "createdAt": "2026-11-14T10:00:00Z",
                                  "updatedAt": "2026-11-15T07:00:00Z",
                                  "reviewDecision": "APPROVED",
                                  "author": { "login": "neutron42", "avatarUrl": "https://example.com/u.png", "url": "" },
                                  "repository": { "nameWithOwner": "neutron42/octo", "name": "octo", "owner": { "login": "neutron42" } },
                                  "comments": { "totalCount": 4 },
                                  "labels": { "nodes": [ { "name": "enhancement", "color": "a2eeef", "description": "New feature" } ] },
                                  "commits": { "nodes": [ { "commit": { "statusCheckRollup": { "state": "SUCCESS" } } } ] }
                                }
                              ]
                            }
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                error("Unexpected path")
            }
        }

        val gql = GitHubGraphQlService(createClient(engine))
        val items = gql.fetchIssuesAndPulls(IssueFilterPreset.CREATED, "neutron42")
        assertEquals(1, items.size)
        assertEquals(IssueOrPullKind.PULL_REQUEST, items[0].kind)
        assertEquals(IssueOrPullState.OPEN, items[0].state)
        assertEquals(128, items[0].number)
        assertEquals("SUCCESS", items[0].checkRollup?.state)
        assertEquals("enhancement", items[0].labels.first().name)
    }

    @Test
    fun graphQlHandlesNullLanguageDefaultBranchAndSearchNodesSafely() = runTest {
        val engine = MockEngine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            val payload = if (body.contains("starredRepositories")) {
                """
                {
                  "data": {
                    "viewer": {
                      "starredRepositories": {
                        "nodes": [
                          null,
                          {
                            "id": "R_dot",
                            "name": "dotfiles",
                            "nameWithOwner": "octo/dotfiles",
                            "description": null,
                            "isPrivate": false,
                            "isFork": false,
                            "stargazerCount": 42,
                            "forkCount": 3,
                            "updatedAt": "2026-11-15T00:00:00Z",
                            "viewerHasStarred": true,
                            "owner": { "login": "octo", "avatarUrl": "https://example.com/o.png" },
                            "primaryLanguage": null,
                            "defaultBranchRef": null,
                            "issues": { "totalCount": 0 }
                          }
                        ]
                      }
                    }
                  }
                }
                """.trimIndent()
            } else {
                """
                {
                  "data": {
                    "search": {
                      "nodes": [
                        {},
                        {
                          "id": "R_kmp",
                          "name": "kmp-notes",
                          "nameWithOwner": "dev/kmp-notes",
                          "description": "Notes",
                          "isPrivate": false,
                          "isFork": false,
                          "stargazerCount": 900,
                          "forkCount": 12,
                          "updatedAt": "2026-11-15T01:00:00Z",
                          "viewerHasStarred": false,
                          "owner": { "login": "dev", "avatarUrl": "" },
                          "primaryLanguage": null,
                          "defaultBranchRef": { "name": "trunk" },
                          "issues": { "totalCount": 2 }
                        }
                      ]
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
        val starred = gql.fetchStarredRepositories()
        assertEquals(1, starred.size)
        assertEquals("octo/dotfiles", starred[0].fullName)
        assertEquals(null, starred[0].language)
        assertEquals("main", starred[0].defaultBranch)

        val search = gql.searchRepositories("kmp-notes")
        assertEquals(1, search.size)
        assertEquals("dev/kmp-notes", search[0].fullName)
        assertEquals("trunk", search[0].defaultBranch)
    }

    @Test
    fun repositoryFallsBackToRestForStarredAndSearchWhenGraphQlFails() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/graphql" -> respond(
                    content = """{"errors":[{"message":"GraphQL resource limit"}]}""",
                    status = HttpStatusCode.UnprocessableEntity,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/user/starred" -> respond(
                    content = """
                        [
                          {
                            "node_id": "R_star_rest",
                            "id": 101,
                            "name": "awesome-kmp",
                            "full_name": "jet/awesome-kmp",
                            "description": null,
                            "language": "Kotlin",
                            "stargazers_count": 321,
                            "forks_count": 10,
                            "open_issues_count": 4,
                            "private": false,
                            "fork": false,
                            "default_branch": "main",
                            "updated_at": "2026-11-15T02:00:00Z",
                            "owner": { "login": "jet", "avatar_url": "" }
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/search/repositories" -> {
                    assertEquals("compose", request.url.parameters["q"])
                    respond(
                        content = """
                            {
                              "items": [
                                {
                                  "node_id": "R_search_rest",
                                  "id": 202,
                                  "name": "compose-ui",
                                  "full_name": "ui/compose-ui",
                                  "language": null,
                                  "stargazers_count": 555,
                                  "forks_count": 20,
                                  "open_issues_count": 1,
                                  "default_branch": "main",
                                  "owner": { "login": "ui", "avatar_url": "" }
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        val sessionManager = SessionManager(
            tokenStore = object : TokenStore {
                var s: AuthSession? = AuthSession("tok", userLogin = "octo")
                override suspend fun saveSession(session: AuthSession) { s = session }
                override suspend fun loadSession() = s
                override suspend fun clearSession() { s = null }
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
        val repo = GitHubRepository(sessionManager = sessionManager, httpClient = createClient(engine))

        val starred = repo.getStarredRepositories().getOrThrow()
        assertEquals(1, starred.size)
        assertEquals("jet/awesome-kmp", starred[0].fullName)
        assertEquals(true, starred[0].viewerHasStarred)
        assertEquals(1, repo.peekStarredRepositories()?.size)

        val searched = repo.searchRepositories("compose").getOrThrow()
        assertEquals(1, searched.size)
        assertEquals("ui/compose-ui", searched[0].fullName)
        assertEquals(1, repo.peekSearchRepositories("compose")?.size)
    }

    @Test
    fun graphQlServicePassesOwnerAndNameAsVariables() = runTest {
        val capturedPayloads = mutableListOf<String>()
        val engine = MockEngine { request ->
            val bodyText = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            capturedPayloads.add(bodyText)
            val responseJson = if (bodyText.contains("issueOrPullRequest")) {
                """
                {
                  "data": {
                    "repository": {
                      "viewerPermission": "READ",
                      "issueOrPullRequest": {
                        "__typename": "Issue",
                        "id": "I_1",
                        "number": 7,
                        "title": "Sample",
                        "state": "OPEN",
                        "url": "https://github.com/octo/app/issues/7",
                        "createdAt": "2026-11-15T00:00:00Z",
                        "updatedAt": "2026-11-15T00:00:00Z",
                        "body": "",
                        "author": { "login": "octo", "avatarUrl": "" },
                        "labels": { "nodes": [] },
                        "assignees": { "nodes": [] },
                        "reactionGroups": [],
                        "timelineItems": { "nodes": [] }
                      }
                    }
                  }
                }
                """.trimIndent()
            } else {
                """{"data":{"repository":{"issues":{"nodes":[]}}}}"""
            }
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val gql = GitHubGraphQlService(createClient(engine))
        val untrustedOwner = "octo\", name: \"injected"
        val untrustedRepo = "app\" ) { __schema { types { name } } }"

        gql.fetchIssueOrPullDetail(untrustedOwner, untrustedRepo, 7)
        gql.fetchRepoIssuesAndPulls(untrustedOwner, untrustedRepo, pullRequests = false)

        assertEquals(2, capturedPayloads.size)
        for (raw in capturedPayloads) {
            val payload = Json.parseToJsonElement(raw).jsonObject
            val query = payload["query"]!!.jsonPrimitive.content
            val variables = payload["variables"]!!.jsonObject
            assertFalse(query.contains("injected"), "owner must not be interpolated into GraphQL query")
            assertFalse(query.contains("__schema"), "repo must not be interpolated into GraphQL query")
            assertEquals(untrustedOwner, variables["owner"]!!.jsonPrimitive.content)
            assertEquals(untrustedRepo, variables["name"]!!.jsonPrimitive.content)
        }
    }
}
