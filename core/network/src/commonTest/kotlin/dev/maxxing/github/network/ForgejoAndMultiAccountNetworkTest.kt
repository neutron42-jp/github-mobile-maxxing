package dev.maxxing.github.network

import dev.maxxing.github.auth.AccountIndex
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.ProviderType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class NetworkMultiAccountStore : TokenStore {
    override val supportsMultiAccountStorage: Boolean = true
    private val accounts = linkedMapOf<String, AuthSession>()
    private var index: AccountIndex? = null

    override suspend fun saveSession(session: AuthSession) = saveAccount(session)
    override suspend fun loadSession(): AuthSession? = accounts.values.firstOrNull()
    override suspend fun clearSession() { accounts.clear(); index = null }
    override suspend fun saveAccount(session: AuthSession) { accounts[session.resolvedAccountId()] = session }
    override suspend fun loadAccount(accountId: String): AuthSession? = accounts[accountId]
    override suspend fun deleteAccount(accountId: String) { accounts.remove(accountId) }
    override suspend fun saveAccountIndex(index: AccountIndex) { this.index = index }
    override suspend fun loadAccountIndex(): AccountIndex? = index
    override suspend fun saveClientId(clientId: String) {}
    override suspend fun loadClientId(): String? = null
    override suspend fun saveThemePreferences(prefs: ThemePreferences) {}
    override suspend fun loadThemePreferences() = ThemePreferences()
    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {}
    override suspend fun loadNotificationPreferences() = NotificationPreferences()
    override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) {}
    override suspend fun loadSeenNotificationMarkers() = emptyMap<String, String>()
}

class ForgejoAndMultiAccountNetworkTest {

    @Test
    fun networkConfigIsolatesAuthHeadersPerProviderAndFlags401Reauth() = runTest {
        val store = NetworkMultiAccountStore()
        val manager = SessionManager(tokenStore = store)
        manager.initialize()

        val githubAccount = AuthSession(
            accessToken = "ghp_github_token",
            scope = "repo",
            authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN,
            providerType = ProviderType.GITHUB,
            userLogin = "gh_user",
            userId = 10L
        )
        val forgejoAccount = AuthSession(
            accessToken = "fg_secret_pat",
            scope = "",
            authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN,
            providerType = ProviderType.FORGEJO_GITEA,
            baseUrl = "https://codeberg.org",
            apiBaseUrl = "https://codeberg.org/api/v1",
            userLogin = "cb_user",
            userId = 20L
        )

        val ghSaved = manager.addOrUpdateAccount(githubAccount, makeActive = true)
        val cbSaved = manager.addOrUpdateAccount(forgejoAccount, makeActive = false)

        val authHeadersSeen = mutableListOf<Pair<String, String?>>()
        var force401OnCodeberg = false
        val engine = MockEngine { request ->
            authHeadersSeen += request.url.toString() to request.headers[HttpHeaders.Authorization]
            val status = if (force401OnCodeberg && request.url.host == "codeberg.org") {
                HttpStatusCode.Unauthorized
            } else {
                HttpStatusCode.OK
            }
            respond("{}", status, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val http = GitHubNetworkConfig(manager).createHttpClient(engine)

        // GitHub active -> sends Bearer to api.github.com, strips auth on foreign hosts.
        http.get("https://api.github.com/user")
        http.get("https://codeberg.org/api/v1/user")
        assertEquals("Bearer ghp_github_token", authHeadersSeen[0].second)
        assertNull(authHeadersSeen[1].second)

        // Switch to Codeberg -> sends `token ...` to codeberg.org, strips auth on api.github.com.
        assertTrue(manager.switchAccount(cbSaved.resolvedAccountId()))
        http.get("https://codeberg.org/api/v1/user")
        http.get("https://api.github.com/user")
        assertEquals("token fg_secret_pat", authHeadersSeen[2].second)
        assertNull(authHeadersSeen[3].second)

        // HTTP 401 without refresh token flags account for reauth.
        force401OnCodeberg = true
        http.get("https://codeberg.org/api/v1/user")
        assertTrue(cbSaved.resolvedAccountId() in manager.reauthRequiredAccountIds.value)
        assertTrue(ghSaved.resolvedAccountId() !in manager.reauthRequiredAccountIds.value)
    }

    @Test
    fun forgejoServiceMapsViewerReposNotificationsIssuesAndPullFiles() = runTest {
        val engine = MockEngine { req ->
            val path = req.url.encodedPath
            val responseJson = when {
                path == "/api/v1/user" -> """
                    {"id":5,"login":"jdoe","full_name":"Jane Doe","avatar_url":"https://codeberg.org/a/5.png","followers_count":12,"following_count":4}
                """.trimIndent()
                path == "/api/v1/user/repos" -> """
                    [{"id":101,"name":"demo","full_name":"jdoe/demo","description":"Sample repo","private":false,"html_url":"https://codeberg.org/jdoe/demo","default_branch":"main","stars_count":9,"forks_count":2,"open_issues_count":3,"owner":{"login":"jdoe","avatar_url":"https://codeberg.org/a/5.png"},"updated_at":"2026-01-01T00:00:00Z"}]
                """.trimIndent()
                path == "/api/v1/notifications" -> """
                    [{"id":9001,"unread":true,"updated_at":"2026-01-02T00:00:00Z","subject":{"title":"Fix build","type":"Pull","url":"https://codeberg.org/api/v1/repos/jdoe/demo/pulls/7"},"repository":{"full_name":"jdoe/demo","owner":{"login":"jdoe","avatar_url":""},"name":"demo","private":false,"html_url":"https://codeberg.org/jdoe/demo"}}]
                """.trimIndent()
                path == "/api/v1/repos/issues/search" -> """
                    [{"id":55,"number":7,"title":"Fix build","state":"open","body":"PR description","comments":1,"user":{"login":"jdoe","avatar_url":""},"pull_request":{"merged":false},"repository":{"full_name":"jdoe/demo"},"html_url":"https://codeberg.org/jdoe/demo/pulls/7","created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-02T00:00:00Z"}]
                """.trimIndent()
                path == "/api/v1/repos/jdoe/demo/issues/7" -> """
                    {"id":55,"number":7,"title":"Fix build","state":"open","body":"PR description","comments":1,"user":{"login":"jdoe","avatar_url":""},"pull_request":{"merged":false},"html_url":"https://codeberg.org/jdoe/demo/pulls/7","created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-02T00:00:00Z"}
                """.trimIndent()
                path == "/api/v1/repos/jdoe/demo/pulls/7" -> """
                    {"id":55,"number":7,"title":"Fix build","state":"open","body":"PR description","user":{"login":"jdoe","avatar_url":""},"head":{"ref":"feat","sha":"abc123"},"base":{"ref":"main","sha":"def456"},"merged":false,"mergeable":true,"changed_files":1,"additions":3,"deletions":1,"html_url":"https://codeberg.org/jdoe/demo/pulls/7","created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-02T00:00:00Z"}
                """.trimIndent()
                path == "/api/v1/repos/jdoe/demo/issues/7/comments" -> """
                    [{"id":801,"body":"Looks good","user":{"login":"reviewer","avatar_url":""},"created_at":"2026-01-02T01:00:00Z","updated_at":"2026-01-02T01:00:00Z"}]
                """.trimIndent()
                path == "/api/v1/repos/jdoe/demo/pulls/7/reviews" -> "[]"
                path == "/api/v1/repos/jdoe/demo/pulls/7/files" -> """
                    [{"filename":"README.md","status":"modified","additions":3,"deletions":1,"patch":"@@ -1 +1,3 @@\n-old\n+new"}]
                """.trimIndent()
                else -> "[]"
            }
            respond(responseJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val service = ForgejoGiteaService(http)
        val apiBase = "https://codeberg.org/api/v1"

        val profile = service.fetchViewerProfile(apiBase)
        assertEquals("jdoe", profile.login)
        assertEquals("Jane Doe", profile.name)
        assertEquals(12, profile.followersCount)

        val repos = service.fetchViewerRepositories(apiBase)
        assertEquals(1, repos.size)
        assertEquals("jdoe/demo", repos.first().fullName)

        val notifications = service.fetchNotifications(apiBase, all = false, participating = false)
        assertEquals(1, notifications.size)
        assertEquals("Fix build", notifications.first().subjectTitle)
        assertEquals("jdoe/demo", notifications.first().repoFullName)

        val issuesAndPulls = service.fetchMyIssuesAndPulls(
            apiBaseUrl = apiBase,
            preset = IssueFilterPreset.CREATED,
            stateFilter = IssueStateFilter.OPEN
        )
        assertEquals(1, issuesAndPulls.size)
        assertEquals(IssueOrPullKind.PULL_REQUEST, issuesAndPulls.first().kind)

        val detail = service.fetchIssueOrPullDetail(apiBase, "jdoe", "demo", 7)
        assertEquals(IssueOrPullKind.PULL_REQUEST, detail.kind)
        assertEquals(IssueOrPullState.OPEN, detail.state)
        assertEquals("feat", detail.headRefName)
        assertEquals("main", detail.baseRefName)
        assertEquals(1, detail.timeline.size)

        val files = service.fetchPullFiles(apiBase, "jdoe", "demo", 7)
        assertEquals(1, files.size)
        assertEquals("README.md", files.first().filename)
        assertEquals(3, files.first().additions)
    }
}
