package dev.maxxing.github.network

import dev.maxxing.github.auth.DeviceFlowClient
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private const val NOW = 1_800_000_000L

private class RefreshTestTokenStore(var session: AuthSession?) : TokenStore {
    override suspend fun saveSession(session: AuthSession) { this.session = session }
    override suspend fun loadSession(): AuthSession? = session
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

class TokenRefreshNetworkTest {

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        remaining: Int? = null
    ): HttpResponseData {
        val headers = buildList {
            add(HttpHeaders.ContentType to listOf("application/json"))
            if (remaining != null) {
                add("X-RateLimit-Limit" to listOf("5000"))
                add("X-RateLimit-Remaining" to listOf(remaining.toString()))
                add("X-RateLimit-Reset" to listOf("1800003600"))
            }
        }
        return respond(body, status, headersOf(*headers.toTypedArray()))
    }

    private fun expiringSession(accessToken: String, refreshToken: String, expiresAt: Long) = AuthSession(
        accessToken = accessToken,
        scope = "repo",
        authMethod = AuthMethod.DEVICE_FLOW,
        userLogin = "octocat",
        refreshToken = refreshToken,
        accessTokenExpiresAtEpochSeconds = expiresAt,
        refreshTokenExpiresAtEpochSeconds = NOW + 1_000_000,
        clientId = "Ov23liTestClient"
    )

    /** OAuth endpoint on github.com; issues ghu_new_N / ghr_new_N. */
    private class OAuthServer {
        val refreshedWith = mutableListOf<String?>()
        val engine = MockEngine { request ->
            check(request.url.host == "github.com") { "Refresh must not go through the API client: ${request.url}" }
            val form = (request.body as io.ktor.client.request.forms.FormDataContent).formData
            refreshedWith += form["refresh_token"]
            val n = refreshedWith.size
            respond(
                """{"access_token":"ghu_new_$n","expires_in":28800,"refresh_token":"ghr_new_$n","refresh_token_expires_in":15897600,"token_type":"bearer","scope":"repo"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    }

    private suspend fun sessionManager(store: TokenStore, oauth: OAuthServer): SessionManager {
        val authHttp = HttpClient(oauth.engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return SessionManager(store, DeviceFlowClient(httpClient = authHttp, nowEpochSeconds = { NOW }))
            .also { it.initialize() }
    }

    @Test
    fun unauthorizedResponseTriggersOneRefreshAndRetryWithNewToken() = runTest {
        val oauth = OAuthServer()
        val store = RefreshTestTokenStore(expiringSession("ghu_old", "ghr_old", expiresAt = NOW + 3600))
        val manager = sessionManager(store, oauth)
        val config = GitHubNetworkConfig(manager)
        val seen = mutableListOf<Pair<String?, String>>()
        val api = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization]
            seen += auth to request.body.toByteArray().decodeToString()
            if (auth == "Bearer ghu_old") {
                jsonResponse("""{"message":"Bad credentials"}""", HttpStatusCode.Unauthorized, remaining = 4999)
            } else {
                jsonResponse("""{"ok":true}""", remaining = 4998)
            }
        }
        val client = config.createHttpClient(api)

        val response = client.post("https://api.github.com/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query":"{ viewer { login } }"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"ok":true}""", response.bodyAsText())
        assertEquals(listOf<String?>("ghr_old"), oauth.refreshedWith)
        assertEquals(
            listOf<Pair<String?, String>>(
                "Bearer ghu_old" to """{"query":"{ viewer { login } }"}""",
                "Bearer ghu_new_1" to """{"query":"{ viewer { login } }"}"""
            ),
            seen
        )
        assertEquals("ghu_new_1", store.session?.accessToken)
        assertEquals(4998, config.rateLimit.value.remaining)
    }

    @Test
    fun unauthorizedWithoutRefreshTokenIsReturnedAsBefore() = runTest {
        val oauth = OAuthServer()
        val store = RefreshTestTokenStore(AuthSession("ghp_pat", authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN))
        val manager = sessionManager(store, oauth)
        val config = GitHubNetworkConfig(manager)
        var apiCalls = 0
        val api = MockEngine { request ->
            apiCalls++
            assertEquals("Bearer ghp_pat", request.headers[HttpHeaders.Authorization])
            jsonResponse("""{"message":"Bad credentials"}""", HttpStatusCode.Unauthorized)
        }
        val rest = GitHubRestService(config.createHttpClient(api), config.baseUrl)

        val error = assertFailsWith<GitHubApiException> { rest.fetchNotifications() }

        assertEquals(401, error.statusCode)
        assertEquals("Failed to load notifications (401)", error.message)
        assertEquals(1, apiCalls)
        assertEquals(emptyList<String?>(), oauth.refreshedWith)
    }

    @Test
    fun persistentUnauthorizedIsRetriedAtMostOnce() = runTest {
        val oauth = OAuthServer()
        val store = RefreshTestTokenStore(expiringSession("ghu_x", "ghr_x", expiresAt = NOW + 3600))
        val manager = sessionManager(store, oauth)
        val config = GitHubNetworkConfig(manager)
        var apiCalls = 0
        val api = MockEngine {
            apiCalls++
            jsonResponse("""{"message":"Bad credentials"}""", HttpStatusCode.Unauthorized)
        }

        val response = config.createHttpClient(api).get("https://api.github.com/user")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(2, apiCalls)
        assertEquals(1, oauth.refreshedWith.size)
    }

    @Test
    fun tokenIsRefreshedProactivelyWhenExpiryIsNear() = runTest {
        val oauth = OAuthServer()
        val store = RefreshTestTokenStore(expiringSession("ghu_soon", "ghr_soon", expiresAt = NOW + 120))
        val manager = sessionManager(store, oauth)
        val config = GitHubNetworkConfig(manager)
        val seen = mutableListOf<String?>()
        val api = MockEngine { request ->
            seen += request.headers[HttpHeaders.Authorization]
            jsonResponse("""{"login":"octocat"}""")
        }
        val client = config.createHttpClient(api)

        client.get("https://api.github.com/user")
        client.get("https://api.github.com/user")

        assertEquals(listOf<String?>("Bearer ghu_new_1", "Bearer ghu_new_1"), seen)
        assertEquals(listOf<String?>("ghr_soon"), oauth.refreshedWith)
    }

    @Test
    fun failedProactiveRefreshIsNotRepeatedAfterTheResulting401() = runTest {
        var refreshCalls = 0
        val failingOAuth = MockEngine {
            refreshCalls++
            respond("""{"message":"unavailable"}""", HttpStatusCode.ServiceUnavailable)
        }
        val authHttp = HttpClient(failingOAuth) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val store = RefreshTestTokenStore(expiringSession("ghu_expired", "ghr_expired", expiresAt = NOW - 60))
        val manager = SessionManager(store, DeviceFlowClient(httpClient = authHttp, nowEpochSeconds = { NOW }))
            .also { it.initialize() }
        val config = GitHubNetworkConfig(manager)
        val seen = mutableListOf<String?>()
        val api = MockEngine { request ->
            seen += request.headers[HttpHeaders.Authorization]
            jsonResponse("""{"message":"Bad credentials"}""", HttpStatusCode.Unauthorized)
        }
        val client = config.createHttpClient(api)

        assertEquals(HttpStatusCode.Unauthorized, client.get("https://api.github.com/user").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("https://api.github.com/user").status)

        // One refresh attempt in total; the 401s and the next request fall inside the retry pause.
        assertEquals(1, refreshCalls)
        assertEquals(listOf<String?>("Bearer ghu_expired", "Bearer ghu_expired"), seen)
        assertEquals("ghu_expired", store.session?.accessToken)
    }

    @Test
    fun tokenIsNotSentToOtherHostsIncludingRedirectTargets() = runTest {
        val oauth = OAuthServer()
        val store = RefreshTestTokenStore(AuthSession("gho_token"))
        val manager = sessionManager(store, oauth)
        val config = GitHubNetworkConfig(manager)
        val seen = mutableListOf<Pair<String, String?>>()
        val api = MockEngine { request ->
            seen += request.url.toString() to request.headers[HttpHeaders.Authorization]
            when (request.url.toString()) {
                "https://api.github.com/repos/o/r/actions/jobs/1/logs" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(HttpHeaders.Location, "https://logs.example.net/job/1.txt")
                )
                else -> respond("log line", HttpStatusCode.OK)
            }
        }
        val client = config.createHttpClient(api)

        assertEquals("log line", client.get("https://api.github.com/repos/o/r/actions/jobs/1/logs").bodyAsText())
        client.get("https://api.github.com:8443/user")
        client.get("http://api.github.com/user")

        assertEquals("Bearer gho_token", seen[0].second)
        assertEquals("https://logs.example.net/job/1.txt", seen[1].first)
        assertNull(seen[1].second)
        assertNull(seen[2].second)
        assertNull(seen[3].second)
        assertEquals(4, seen.size)
    }
}
