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
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RepoCaseNormalizationAndGraphQlMutationTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    private suspend fun createRepository(engine: MockEngine): GitHubRepository {
        val tokenStore = object : TokenStore {
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
        }
        val sessionManager = SessionManager(tokenStore, DeviceFlowClient(HttpClient(engine)))
        sessionManager.initialize()
        return GitHubRepository(sessionManager = sessionManager, httpClient = createClient(engine))
    }

    @Test
    fun graphQlMutationFailsWhenPartialErrorsPresentEvenWithData() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "data": { "updateIssueComment": null },
                      "errors": [{ "message": "Forbidden to update comment" }]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val gql = GitHubGraphQlService(createClient(engine))
        assertFailsWith<GitHubApiException> {
            gql.updateIssueComment("IC_1", "edited body")
        }
    }

    @Test
    fun setStarredInvalidatesRepoDetailAcrossCaseVariants() = runTest {
        var starPutCalls = 0
        var subscriptionGetCalls = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/graphql" -> respond(
                    content = """
                        {
                          "data": {
                            "repository": {
                              "id": "R_1",
                              "name": "app",
                              "nameWithOwner": "octo/app",
                              "description": "desc",
                              "isPrivate": false,
                              "isFork": false,
                              "stargazerCount": 5,
                              "forkCount": 1,
                              "updatedAt": "2026-11-20T00:00:00Z",
                              "viewerHasStarred": false,
                              "owner": { "login": "octo", "avatarUrl": "" },
                              "primaryLanguage": null,
                              "defaultBranchRef": { "name": "main" },
                              "issues": { "totalCount": 0 },
                              "homepageUrl": null,
                              "licenseInfo": null,
                              "watchers": { "totalCount": 1 },
                              "repositoryTopics": { "nodes": [] },
                              "parent": null
                            }
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                request.method == HttpMethod.Get && request.url.encodedPath == "/repos/octo/app" -> respond(
                    content = """
                        {
                          "id": 1,
                          "name": "app",
                          "full_name": "octo/app",
                          "owner": { "login": "octo", "avatar_url": "" },
                          "private": false,
                          "fork": false,
                          "description": "desc",
                          "stargazers_count": 5,
                          "forks_count": 1,
                          "open_issues_count": 0,
                          "default_branch": "main",
                          "updated_at": "2026-11-20T00:00:00Z",
                          "html_url": "https://github.com/octo/app"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                request.method == HttpMethod.Get && request.url.encodedPath == "/user/starred/octo/app" ->
                    respond("", HttpStatusCode.NotFound)
                request.method == HttpMethod.Put && request.url.encodedPath == "/user/starred/OCTO/App" -> {
                    starPutCalls++
                    respond("", HttpStatusCode.NoContent)
                }
                request.method == HttpMethod.Get && request.url.encodedPath.equals("/repos/octo/app/subscription", ignoreCase = true) -> {
                    subscriptionGetCalls++
                    respond(
                        content = """{"subscribed":true,"ignored":false}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                request.method == HttpMethod.Get && request.url.encodedPath == "/repos/octo/app/readme" ->
                    respond("", HttpStatusCode.NotFound)
                else -> respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val repo = createRepository(engine)
        repo.getRepoDetail("octo", "app").getOrThrow()
        assertEquals("app", repo.peekRepoDetail("OCTO", "APP")?.summary?.name)

        repo.setStarred("OCTO", "App", starred = true).getOrThrow()
        assertEquals(1, starPutCalls)
        assertNull(repo.peekRepoDetail("octo", "app"))

        repo.getRepoSubscription("Octo", "App").getOrThrow()
        repo.getRepoSubscription("octo", "app").getOrThrow()
        assertEquals(1, subscriptionGetCalls)
    }
}
