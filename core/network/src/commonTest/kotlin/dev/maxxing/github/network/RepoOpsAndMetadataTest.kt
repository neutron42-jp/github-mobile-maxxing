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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RepoOpsTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_ops", userLogin = "octocat")
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

class RepoOpsAndMetadataTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    @Test
    fun repositoryCreateForkWatchAndPeopleEndpointsWork() = runTest {
        var subscribedState = false
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/user/repos" && request.method == HttpMethod.Post -> {
                    val bodyText = (request.body as TextContent).text
                    val obj = json.parseToJsonElement(bodyText).jsonObject
                    assertEquals("octo-new", obj["name"]?.jsonPrimitive?.content)
                    assertEquals("true", obj["private"]?.jsonPrimitive?.content)
                    respond(
                        content = """
                            {
                              "id": 1001,
                              "node_id": "R_1001",
                              "name": "octo-new",
                              "full_name": "octocat/octo-new",
                              "private": true,
                              "fork": false,
                              "default_branch": "main",
                              "owner": { "login": "octocat", "avatar_url": "" }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                path == "/repos/jetbrains/kotlin/forks" && request.method == HttpMethod.Post -> respond(
                    content = """
                        {
                          "id": 1002,
                          "node_id": "R_1002",
                          "name": "kotlin",
                          "full_name": "octocat/kotlin",
                          "private": false,
                          "fork": true,
                          "default_branch": "master",
                          "owner": { "login": "octocat", "avatar_url": "" }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.Accepted,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                path == "/repos/jetbrains/kotlin/subscription" && request.method == HttpMethod.Get -> {
                    if (!subscribedState) {
                        respond(content = """{"message":"Not Found"}""", status = HttpStatusCode.NotFound, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    } else {
                        respond(content = """{"subscribed":true,"ignored":false}""", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }

                path == "/repos/jetbrains/kotlin/subscription" && request.method == HttpMethod.Put -> {
                    subscribedState = true
                    respond(content = """{"subscribed":true,"ignored":false}""", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }

                path == "/repos/jetbrains/kotlin/subscription" && request.method == HttpMethod.Delete -> {
                    subscribedState = false
                    respond(content = "", status = HttpStatusCode.NoContent)
                }

                path == "/repos/jetbrains/kotlin/stargazers" -> respond(
                    content = """[{"login":"alice","avatar_url":"https://a/1.png","html_url":"https://github.com/alice"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                path == "/repos/jetbrains/kotlin/forks" && request.method == HttpMethod.Get -> respond(
                    content = """[{"id":11,"node_id":"R_11","name":"kotlin","full_name":"bob/kotlin","stargazers_count":5,"owner":{"login":"bob","avatar_url":""}}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                path == "/repos/jetbrains/kotlin/contributors" -> respond(
                    content = """[{"login":"carol","avatar_url":"https://a/3.png","html_url":"https://github.com/carol"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                else -> error("Unexpected request: ${request.method} $path")
            }
        }

        val http = createClient(engine)
        val sm = SessionManager(RepoOpsTokenStore(), DeviceFlowClient(http, "cid"))
        sm.initialize()
        val repo = GitHubRepository(sm, httpClient = http)

        val created = repo.createRepository("octo-new", "Test repo", isPrivate = true, autoInit = true).getOrThrow()
        assertEquals("octocat/octo-new", created.fullName)
        assertTrue(created.isPrivate)

        val forked = repo.forkRepository("jetbrains", "kotlin").getOrThrow()
        assertEquals("octocat/kotlin", forked.fullName)
        assertTrue(forked.isFork)

        assertFalse(repo.getRepoSubscription("jetbrains", "kotlin").getOrThrow())
        repo.setRepoSubscription("jetbrains", "kotlin", true).getOrThrow()
        assertEquals(true, repo.peekRepoSubscription("jetbrains", "kotlin"))
        assertTrue(repo.getRepoSubscription("jetbrains", "kotlin", forceRefresh = true).getOrThrow())
        repo.setRepoSubscription("jetbrains", "kotlin", false).getOrThrow()
        assertFalse(repo.getRepoSubscription("jetbrains", "kotlin", forceRefresh = true).getOrThrow())

        assertEquals("alice", repo.getRepoStargazers("jetbrains", "kotlin").getOrThrow().single().login)
        assertEquals("bob/kotlin", repo.getRepoForks("jetbrains", "kotlin").getOrThrow().single().fullName)
        assertEquals("carol", repo.getRepoContributors("jetbrains", "kotlin").getOrThrow().single().login)
    }

    @Test
    fun `stargazers fallback to GraphQL when REST returns 404 and handle fullName repo argument`() = runTest {
        val seenPaths = mutableListOf<String>()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            seenPaths += path
            when {
                path == "/repos/other-org/cool-lib/stargazers" -> respond(
                    content = """{"message":"Not Found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                path == "/graphql" -> respond(
                    content = """
                        {
                          "data": {
                            "repository": {
                              "stargazers": {
                                "nodes": [
                                  { "login": "bob", "avatarUrl": "https://a/2.png", "url": "https://github.com/bob" }
                                ]
                              }
                            }
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected request: ${request.method} $path")
            }
        }

        val http = createClient(engine)
        val sm = SessionManager(RepoOpsTokenStore(), DeviceFlowClient(http, "cid"))
        sm.initialize()
        val repo = GitHubRepository(sm, httpClient = http)

        val users = repo.getRepoStargazers("", "other-org/cool-lib").getOrThrow()
        assertEquals(listOf("bob"), users.map { it.login })
        assertTrue("/repos/other-org/cool-lib/stargazers" in seenPaths)
        assertTrue("/graphql" in seenPaths)
    }

    @Test
    fun issueMetadataFetchCreateAndPatchWork() = runTest {
        val patchBodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/repos/octo/app/labels" && request.method == HttpMethod.Get -> respond(
                    content = """[{"name":"bug","color":"d73a4a","description":"Something broke"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                path == "/repos/octo/app/labels" && request.method == HttpMethod.Post -> {
                    val reqObj = json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals("mobile", reqObj["name"]?.jsonPrimitive?.content)
                    assertEquals("1d76db", reqObj["color"]?.jsonPrimitive?.content)
                    respond(
                        content = """{"name":"mobile","color":"1d76db","description":"Android/iOS"}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                path == "/repos/octo/app/milestones" -> respond(
                    content = """[{"number":4,"title":"v2.0","description":"Major release","state":"open","due_on":"2026-12-01T00:00:00Z","open_issues":5,"closed_issues":12}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                path == "/repos/octo/app/assignees" -> respond(
                    content = """[{"login":"octocat","avatar_url":"","html_url":"https://github.com/octocat"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                path == "/repos/octo/app/issues/77" && request.method == HttpMethod.Patch -> {
                    patchBodies += (request.body as TextContent).text
                    respond(content = """{"number":77}""", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }

                path == "/graphql" -> respond(
                    content = """
                        {
                          "data": {
                            "repository": {
                              "issueOrPullRequest": {
                                "__typename": "Issue",
                                "id": "I_77",
                                "number": 77,
                                "title": "Add metadata",
                                "body": "Body",
                                "state": "OPEN",
                                "url": "https://github.com/octo/app/issues/77",
                                "createdAt": "2026-11-10T00:00:00Z",
                                "updatedAt": "2026-11-15T00:00:00Z",
                                "author": { "login": "octocat", "avatarUrl": "", "url": "" },
                                "labels": { "nodes": [{ "name": "bug", "color": "d73a4a", "description": null }] },
                                "assignees": { "nodes": [] },
                                "milestone": { "number": 4, "title": "v2.0", "description": "Major release", "state": "OPEN", "dueOn": "2026-12-01T00:00:00Z" },
                                "timelineItems": { "nodes": [] }
                              }
                            }
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                else -> error("Unexpected request: ${request.method} $path")
            }
        }

        val http = createClient(engine)
        val sm = SessionManager(RepoOpsTokenStore(), DeviceFlowClient(http, "cid"))
        sm.initialize()
        val repo = GitHubRepository(sm, httpClient = http)

        val labels = repo.getRepoLabels("octo", "app").getOrThrow()
        assertEquals("bug", labels.single().name)
        val newLabel = repo.createRepoLabel("octo", "app", "mobile", "#1d76db", "Android/iOS").getOrThrow()
        assertEquals("mobile", newLabel.name)

        val ms = repo.getRepoMilestones("octo", "app").getOrThrow().single()
        assertEquals(4, ms.number)
        assertEquals("v2.0", ms.title)

        assertEquals("octocat", repo.getRepoAssignees("octo", "app").getOrThrow().single().login)

        // Update labels + assignees + milestone number
        repo.updateIssueOrPullMetadata(
            owner = "octo",
            repo = "app",
            number = 77,
            labels = listOf("bug", "mobile"),
            assignees = listOf("octocat"),
            milestoneNumber = 4
        ).getOrThrow()
        val firstPatch = json.parseToJsonElement(patchBodies[0]).jsonObject
        assertEquals(2, firstPatch["labels"]?.jsonArray?.size)
        assertEquals("octocat", firstPatch["assignees"]?.jsonArray?.single()?.jsonPrimitive?.content)
        assertEquals("4", firstPatch["milestone"]?.jsonPrimitive?.content)

        // Clear milestone explicitly sends JsonNull and omits labels/assignees
        repo.updateIssueOrPullMetadata(
            owner = "octo",
            repo = "app",
            number = 77,
            clearMilestone = true
        ).getOrThrow()
        val secondPatch = json.parseToJsonElement(patchBodies[1]).jsonObject
        assertEquals(JsonNull, secondPatch["milestone"])
        assertNull(secondPatch["labels"])

        // GraphQL detail includes milestone
        val detail = GitHubGraphQlService(http)
            .fetchIssueOrPullDetail("octo", "app", 77)
        assertEquals("v2.0", detail.milestone?.title)
        assertEquals(4, detail.milestone?.number)
    }

    @Test
    fun `graphql and rest parse fork parent summary and forks sort parameter`() = runTest {
        val requestedSorts = mutableListOf<String?>()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when (path) {
                "/graphql" -> respond(
                    content = """
                        {
                          "data": {
                            "repository": {
                              "id": "R_fork1",
                              "name": "app",
                              "nameWithOwner": "forker/app",
                              "description": "Forked app",
                              "isPrivate": false,
                              "isFork": true,
                              "isArchived": false,
                              "hasIssuesEnabled": true,
                              "viewerPermission": "ADMIN",
                              "stargazerCount": 2,
                              "forkCount": 0,
                              "updatedAt": "2026-11-16T00:00:00Z",
                              "viewerHasStarred": false,
                              "owner": { "login": "forker", "avatarUrl": "" },
                              "primaryLanguage": { "name": "Kotlin", "color": "#A97BFF" },
                              "defaultBranchRef": { "name": "dev" },
                              "issues": { "totalCount": 1 },
                              "homepageUrl": null,
                              "licenseInfo": null,
                              "watchers": { "totalCount": 1 },
                              "repositoryTopics": { "nodes": [] },
                              "parent": {
                                "name": "app",
                                "nameWithOwner": "upstream/app",
                                "owner": { "login": "upstream", "avatarUrl": "https://a/up.png" },
                                "defaultBranchRef": { "name": "main" }
                              }
                            }
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/repos/forker/app/branches" -> respond(
                    content = """[{"name":"dev","commit":{"sha":"abc123"}}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/repos/upstream/app/forks" -> {
                    requestedSorts += request.url.parameters["sort"]
                    respond(
                        content = """
                            [
                              {
                                "id": 22,
                                "node_id": "R_22",
                                "name": "app",
                                "full_name": "forker/app",
                                "fork": true,
                                "default_branch": "dev",
                                "stargazers_count": 0,
                                "updated_at": "2026-11-16T05:00:00Z",
                                "owner": { "login": "forker", "avatar_url": "" },
                                "parent": {
                                  "name": "app",
                                  "full_name": "upstream/app",
                                  "default_branch": "main",
                                  "owner": { "login": "upstream", "avatar_url": "" }
                                }
                              }
                            ]
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected request: ${request.method} $path")
            }
        }

        val http = createClient(engine)
        val sm = SessionManager(RepoOpsTokenStore(), DeviceFlowClient(http, "cid"))
        sm.initialize()
        val repo = GitHubRepository(sm, httpClient = http)

        val detail = repo.getRepoDetail("forker", "app").getOrThrow()
        assertTrue(detail.summary.isFork)
        assertEquals("upstream/app", detail.summary.parent?.fullName)
        assertEquals("upstream", detail.summary.parent?.owner)
        assertEquals("main", detail.summary.parent?.defaultBranch)

        val newestForks = repo.getRepoForks("upstream", "app", sort = "newest").getOrThrow()
        assertEquals(listOf<String?>("newest"), requestedSorts)
        assertEquals("forker/app", newestForks.single().fullName)
        assertEquals("upstream/app", newestForks.single().parent?.fullName)
        assertEquals("dev", newestForks.single().defaultBranch)
    }

    @Test
    fun `204 No Content and empty response bodies resolve to empty collections or null`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                // Simulate 200 OK with empty body for one endpoint too
                "/repos/octo/empty/releases" -> respond(
                    content = "",
                    status = HttpStatusCode.OK
                )
                else -> respond(
                    content = "",
                    status = HttpStatusCode.NoContent
                )
            }
        }

        val http = createClient(engine)
        val rest = GitHubRestService(http)

        assertTrue(rest.fetchRepoContributors("octo", "empty").isEmpty())
        assertTrue(rest.fetchRepoStargazers("octo", "empty").isEmpty())
        assertTrue(rest.fetchRepoForks("octo", "empty").isEmpty())
        assertTrue(rest.fetchRepoLanguages("octo", "empty").isEmpty())
        assertTrue(rest.fetchRepoCommits("octo", "empty").isEmpty())
        assertTrue(rest.fetchBranches("octo", "empty").isEmpty())
        assertTrue(rest.fetchRepoTags("octo", "empty").isEmpty())
        assertTrue(rest.fetchDirectoryContents("octo", "empty").isEmpty())
        assertNull(rest.fetchReadme("octo", "empty"))
        assertTrue(rest.fetchReleases("octo", "empty").isEmpty())
        assertTrue(rest.fetchPullFiles("octo", "empty", 1).isEmpty())
        assertTrue(rest.fetchWorkflows("octo", "empty").isEmpty())
        assertTrue(rest.fetchWorkflowRuns("octo", "empty").isEmpty())
        assertTrue(rest.fetchWorkflowRunJobs("octo", "empty", 10L).isEmpty())
        assertTrue(rest.fetchCheckRunsForRef("octo", "empty", "main").isEmpty())
        assertTrue(rest.fetchCheckRunsForCheckSuite("octo", "empty", 99L).isEmpty())
        assertTrue(rest.fetchDependabotAlerts("octo", "empty").isEmpty())
        assertTrue(rest.fetchSecurityAdvisories("octo", "empty").isEmpty())
        assertTrue(rest.fetchRepoLabels("octo", "empty").isEmpty())
        assertTrue(rest.fetchRepoMilestones("octo", "empty").isEmpty())
        assertTrue(rest.fetchRepoAssignees("octo", "empty").isEmpty())
        assertTrue(rest.fetchNotifications().isEmpty())
        assertTrue(rest.fetchGists().isEmpty())
        assertTrue(rest.fetchViewerRepositoriesRest().isEmpty())
        assertTrue(rest.fetchStarredRepositoriesRest().isEmpty())
        assertTrue(rest.fetchUserRepositories("octo").isEmpty())
        assertTrue(rest.fetchUserStarredRepositories("octo").isEmpty())
        assertTrue(rest.fetchUserOrganizations("octo").isEmpty())
        assertTrue(rest.fetchUserFollowers("octo").isEmpty())
        assertTrue(rest.fetchUserFollowing("octo").isEmpty())
        assertTrue(rest.fetchReceivedEvents("octo").isEmpty())
        assertTrue(rest.fetchUserEvents("octo").isEmpty())
        assertTrue(rest.searchRepositoriesRest("kmp").isEmpty())
        assertTrue(rest.searchIssuesAndPullsRest("bug").isEmpty())
        assertTrue(rest.searchCodeRest("remember").isEmpty())
        assertTrue(rest.searchUsersRest("octo").isEmpty())
    }

    @Test
    fun `createIssue includes deduplicated labels when provided and omits labels when empty`() = runTest {
        val postedPayloads = mutableListOf<String>()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/repos/octo/app/issues" && request.method == HttpMethod.Post -> {
                    postedPayloads += (request.body as TextContent).text
                    val nextNum = 100 + postedPayloads.size
                    respond(
                        content = """{"number":$nextNum}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                else -> error("Unexpected request: ${request.method} $path")
            }
        }

        val http = createClient(engine)
        val sm = SessionManager(RepoOpsTokenStore(), DeviceFlowClient(http, "cid"))
        sm.initialize()
        val repo = GitHubRepository(sm, httpClient = http)

        val numWithLabels = repo.createIssue(
            owner = "octo",
            repo = "app",
            title = "Labeled bug",
            body = "Details",
            labels = listOf("bug", " enhancement ", "bug", "")
        ).getOrThrow()
        assertEquals(101, numWithLabels)

        val firstObj = json.parseToJsonElement(postedPayloads[0]).jsonObject
        assertEquals("Labeled bug", firstObj["title"]?.jsonPrimitive?.content)
        assertEquals("Details", firstObj["body"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("bug", "enhancement"),
            firstObj["labels"]?.jsonArray?.map { it.jsonPrimitive.content }
        )

        val numWithoutLabels = repo.createIssue(
            owner = "octo",
            repo = "app",
            title = "Plain issue",
            body = "Body"
        ).getOrThrow()
        assertEquals(102, numWithoutLabels)

        val secondObj = json.parseToJsonElement(postedPayloads[1]).jsonObject
        assertNull(secondObj["labels"])
    }

    @Test
    fun labelUpdateDeleteAndMilestoneCrudWork() = runTest {
        val seenRequests = mutableListOf<String>()
        val milestoneStates = mutableListOf<String?>()
        val patchBodies = mutableListOf<String>()

        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            seenRequests += "${request.method.value} $path"
            when {
                path == "/repos/octo/app/labels" && request.method == HttpMethod.Get -> respond(
                    content = """[{"name":"good first issue","color":"7057ff","description":"Starter"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                path == "/repos/octo/app/labels/good%20first%20issue" && request.method == HttpMethod.Patch -> {
                    val body = (request.body as TextContent).text
                    patchBodies += body
                    respond(
                        content = """{"name":"starter","color":"008672","description":"Updated"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                path == "/repos/octo/app/labels/starter" && request.method == HttpMethod.Delete -> respond(
                    content = "",
                    status = HttpStatusCode.NoContent
                )

                path == "/repos/octo/app/milestones" && request.method == HttpMethod.Get -> {
                    milestoneStates += request.url.parameters["state"]
                    respond(
                        content = """[{"number":1,"title":"v1.0","description":"Ship","state":"open","due_on":"2026-12-31T23:59:59Z","open_issues":2,"closed_issues":8}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                path == "/repos/octo/app/milestones" && request.method == HttpMethod.Post -> {
                    val req = json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals("v1.1", req["title"]?.jsonPrimitive?.content)
                    assertEquals("2027-01-15T23:59:59Z", req["due_on"]?.jsonPrimitive?.content)
                    respond(
                        content = """{"number":2,"title":"v1.1","description":"Next","state":"open","due_on":"2027-01-15T23:59:59Z","open_issues":0,"closed_issues":0}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                path == "/repos/octo/app/milestones/2" && request.method == HttpMethod.Patch -> {
                    val body = (request.body as TextContent).text
                    patchBodies += body
                    respond(
                        content = """{"number":2,"title":"v1.1.0","description":"Next","state":"closed","due_on":null,"open_issues":0,"closed_issues":0}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                path == "/repos/octo/app/milestones/2" && request.method == HttpMethod.Delete -> respond(
                    content = "",
                    status = HttpStatusCode.NoContent
                )

                else -> error("Unexpected request: ${request.method.value} $path")
            }
        }

        val http = createClient(engine)
        val sm = SessionManager(RepoOpsTokenStore(), DeviceFlowClient(http, "cid"))
        sm.initialize()
        val repo = GitHubRepository(sm, httpClient = http)

        assertEquals("good first issue", repo.getRepoLabels("octo", "app").getOrThrow().single().name)
        val updatedLabel = repo.updateRepoLabel(
            owner = "octo",
            repo = "app",
            currentName = "good first issue",
            newName = "starter",
            colorHex = "#008672",
            description = "Updated"
        ).getOrThrow()
        assertEquals("starter", updatedLabel.name)
        assertEquals("008672", updatedLabel.colorHex)
        assertNull(repo.peekRepoLabels("octo", "app"))

        repo.deleteRepoLabel("octo", "app", "starter").getOrThrow()
        assertTrue("DELETE /repos/octo/app/labels/starter" in seenRequests)

        val milestones = repo.getRepoMilestones("octo", "app", state = "all").getOrThrow()
        assertEquals(listOf<String?>("all"), milestoneStates)
        assertEquals(1, milestones.single().number)

        val createdMs = repo.createRepoMilestone(
            owner = "octo",
            repo = "app",
            title = "v1.1",
            description = "Next",
            dueOn = "2027-01-15T23:59:59Z"
        ).getOrThrow()
        assertEquals(2, createdMs.number)
        assertNull(repo.peekRepoMilestones("octo", "app", state = "all"))

        val updatedMs = repo.updateRepoMilestone(
            owner = "octo",
            repo = "app",
            number = 2,
            title = "v1.1.0",
            state = "closed",
            clearDueOn = true
        ).getOrThrow()
        assertEquals("closed", updatedMs.state)
        assertNull(updatedMs.dueOn)
        val msPatch = json.parseToJsonElement(patchBodies.last()).jsonObject
        assertEquals(JsonNull, msPatch["due_on"])
        assertEquals("closed", msPatch["state"]?.jsonPrimitive?.content)

        repo.deleteRepoMilestone("octo", "app", 2).getOrThrow()
        assertTrue("DELETE /repos/octo/app/milestones/2" in seenRequests)
    }
}
