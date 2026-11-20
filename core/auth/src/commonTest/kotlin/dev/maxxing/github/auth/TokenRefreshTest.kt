package dev.maxxing.github.auth

import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.DeviceCodeState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val NOW = 1_700_000_000L

/** In-memory store; several SessionManagers can share one instance like they share the device keystore. */
private class SharedTokenStore(initial: AuthSession? = null) : TokenStore {
    var session: AuthSession? = initial
    var saveCount = 0
    var loadCount = 0
    override suspend fun saveSession(session: AuthSession) {
        saveCount++
        this.session = session
    }
    override suspend fun loadSession(): AuthSession? {
        loadCount++
        return session
    }
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

class TokenRefreshTest {

    private fun jsonClient(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    }

    private fun MockRequestHandleScope.json(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    private fun HttpRequestData.form(): Parameters = assertIs<FormDataContent>(body).formData

    private fun expiringSession(
        accessToken: String,
        refreshToken: String,
        expiresAt: Long = NOW - 10,
        refreshExpiresAt: Long? = NOW + 1_000_000
    ) = AuthSession(
        accessToken = accessToken,
        scope = "repo",
        authMethod = AuthMethod.DEVICE_FLOW,
        userLogin = "octocat",
        refreshToken = refreshToken,
        accessTokenExpiresAtEpochSeconds = expiresAt,
        refreshTokenExpiresAtEpochSeconds = refreshExpiresAt,
        clientId = "Ov23liTestClient"
    )

    /** A refresh endpoint that hands out ghu_new_N / ghr_new_N and counts calls. */
    private class RefreshServer {
        var calls = 0
        val forms = mutableListOf<Parameters>()
    }

    private fun refreshingClient(server: RefreshServer, gate: CompletableDeferred<Unit>? = null): DeviceFlowClient {
        val engine = MockEngine { request ->
            assertEquals("/login/oauth/access_token", request.url.encodedPath)
            server.calls++
            server.forms += request.form()
            gate?.await()
            val n = server.calls
            json("""{"access_token":"ghu_new_$n","expires_in":28800,"refresh_token":"ghr_new_$n","refresh_token_expires_in":15897600,"token_type":"bearer","scope":""}""")
        }
        return DeviceFlowClient(httpClient = jsonClient(engine), delayMillis = {}, nowEpochSeconds = { NOW })
    }

    @Test
    fun deviceFlowStoresExpiringTokenFieldsWithInjectedClock() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/login/device/code" -> json(
                    """{"device_code":"dev_1","user_code":"AAAA-BBBB","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
                )
                "/login/oauth/access_token" -> json(
                    """{"access_token":"ghu_first","expires_in":28800,"refresh_token":"ghr_first","refresh_token_expires_in":15897600,"token_type":"bearer","scope":"repo"}"""
                )
                "/user" -> json("""{"login":"octocat"}""")
                else -> error("Unexpected path ${request.url.encodedPath}")
            }
        }
        val client = DeviceFlowClient(httpClient = jsonClient(engine), delayMillis = {}, nowEpochSeconds = { NOW })

        val session = assertIs<DeviceCodeState.Authenticated>(client.startDeviceFlow(" Ov23liTestClient ").toList().last()).session

        assertEquals("ghu_first", session.accessToken)
        assertEquals("ghr_first", session.refreshToken)
        assertEquals(NOW + 28800, session.accessTokenExpiresAtEpochSeconds)
        assertEquals(NOW + 15897600, session.refreshTokenExpiresAtEpochSeconds)
        assertEquals("Ov23liTestClient", session.clientId)
        assertEquals("octocat", session.userLogin)
    }

    @Test
    fun deviceFlowWithNonExpiringTokenLeavesExpiryFieldsNull() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/login/device/code" -> json(
                    """{"device_code":"dev_1","user_code":"AAAA-BBBB","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
                )
                "/login/oauth/access_token" -> json("""{"access_token":"gho_plain","token_type":"bearer","scope":""}""")
                "/user" -> json("""{"login":"octocat"}""")
                else -> error("Unexpected path ${request.url.encodedPath}")
            }
        }
        val client = DeviceFlowClient(httpClient = jsonClient(engine), delayMillis = {}, nowEpochSeconds = { NOW })

        val session = assertIs<DeviceCodeState.Authenticated>(client.startDeviceFlow("Ov23liTestClient").toList().last()).session

        assertEquals("gho_plain", session.accessToken)
        // An empty granted scope is stored as-is at sign-in (only refresh falls back to the previous scope).
        assertEquals("", session.scope)
        assertNull(session.refreshToken)
        assertNull(session.accessTokenExpiresAtEpochSeconds)
        assertNull(session.refreshTokenExpiresAtEpochSeconds)
        assertEquals("Ov23liTestClient", session.clientId)
    }

    @Test
    fun refreshAccessTokenSendsRefreshGrantWithoutClientSecret() = runTest {
        val server = RefreshServer()
        val client = refreshingClient(server)

        val refreshed = client.refreshAccessToken(expiringSession("ghu_old", "ghr_old")).getOrThrow()

        val form = server.forms.single()
        assertEquals("Ov23liTestClient", form["client_id"])
        assertEquals("refresh_token", form["grant_type"])
        assertEquals("ghr_old", form["refresh_token"])
        assertFalse(form.contains("client_secret"))
        assertEquals("ghu_new_1", refreshed.accessToken)
        assertEquals("ghr_new_1", refreshed.refreshToken)
        assertEquals(NOW + 28800, refreshed.accessTokenExpiresAtEpochSeconds)
        assertEquals(NOW + 15897600, refreshed.refreshTokenExpiresAtEpochSeconds)
        // Kept from the previous session (the blank scope in the response falls back too).
        assertEquals("octocat", refreshed.userLogin)
        assertEquals(AuthMethod.DEVICE_FLOW, refreshed.authMethod)
        assertEquals("repo", refreshed.scope)
        assertEquals("Ov23liTestClient", refreshed.clientId)
    }

    @Test
    fun refreshAccessTokenMapsBadRefreshTokenToDedicatedFailure() = runTest {
        val engine = MockEngine {
            json("""{"error":"bad_refresh_token","error_description":"The refresh token passed is incorrect or expired."}""")
        }
        val client = DeviceFlowClient(httpClient = jsonClient(engine), nowEpochSeconds = { NOW })

        val failure = client.refreshAccessToken(expiringSession("ghu_old", "ghr_bad")).exceptionOrNull()

        val rejected = assertIs<RefreshTokenRejectedException>(failure)
        assertTrue(rejected.isBadRefreshToken)
    }

    @Test
    fun refreshAccessTokenReportsNetworkErrorsAsOtherFailures() = runTest {
        val engine = MockEngine { throw IllegalStateException("offline") }
        val client = DeviceFlowClient(httpClient = jsonClient(engine), nowEpochSeconds = { NOW })

        val failure = client.refreshAccessToken(expiringSession("ghu_old", "ghr_old")).exceptionOrNull()

        assertNotNull(failure)
        assertFalse(failure is RefreshTokenRejectedException)
    }

    @Test
    fun sessionManagerRefreshSavesNewSession() = runTest {
        val server = RefreshServer()
        val store = SharedTokenStore(expiringSession("ghu_a1", "ghr_a1"))
        val manager = SessionManager(store, refreshingClient(server)).also { it.initialize() }

        val refreshed = manager.refreshAfterUnauthorized("ghu_a1")

        assertEquals("ghu_new_1", refreshed?.accessToken)
        assertEquals("ghu_new_1", store.session?.accessToken)
        assertEquals("ghr_new_1", store.session?.refreshToken)
        assertEquals("ghu_new_1", manager.session.value?.accessToken)
        assertEquals(1, server.calls)
    }

    @Test
    fun secondInstanceAdoptsTokenRefreshedByAnotherInstance() = runTest {
        val server = RefreshServer()
        val store = SharedTokenStore(expiringSession("ghu_b1", "ghr_b1"))
        val app = SessionManager(store, refreshingClient(server)).also { it.initialize() }
        val worker = SessionManager(store, refreshingClient(server)).also { it.initialize() }

        assertEquals("ghu_new_1", app.refreshAfterUnauthorized("ghu_b1")?.accessToken)
        // The worker still holds the old token in memory and hits a 401 with it.
        assertEquals("ghu_b1", worker.session.value?.accessToken)
        val adopted = worker.refreshAfterUnauthorized("ghu_b1")

        assertEquals("ghu_new_1", adopted?.accessToken)
        assertEquals("ghu_new_1", worker.session.value?.accessToken)
        assertEquals(1, server.calls)
    }

    @Test
    fun sessionWithoutRefreshTokenIsNotRefreshed() = runTest {
        val server = RefreshServer()
        val pat = AuthSession(accessToken = "ghp_pat", authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN, userLogin = "octocat")
        val store = SharedTokenStore(pat)
        val manager = SessionManager(store, refreshingClient(server)).also { it.initialize() }
        val loadsAfterInit = store.loadCount

        assertNull(manager.refreshAfterUnauthorized("ghp_pat"))
        assertEquals(pat, manager.refreshSessionIfExpiringSoon())
        assertEquals(pat, manager.session.value)
        assertEquals(0, server.calls)
        assertEquals(0, store.saveCount)
        // A 401 with a token that cannot be refreshed must not cost a (keyring) store read.
        assertEquals(loadsAfterInit, store.loadCount)
    }

    @Test
    fun concurrentRefreshRequestsPerformOneNetworkRefresh() = runTest {
        val server = RefreshServer()
        val gate = CompletableDeferred<Unit>()
        val store = SharedTokenStore(expiringSession("ghu_c1", "ghr_c1"))
        val app = SessionManager(store, refreshingClient(server, gate)).also { it.initialize() }
        val worker = SessionManager(store, refreshingClient(server, gate)).also { it.initialize() }

        val first = async { app.refreshAfterUnauthorized("ghu_c1") }
        val second = async { worker.refreshAfterUnauthorized("ghu_c1") }
        val third = async { app.refreshAfterUnauthorized("ghu_c1") }
        repeat(5) { yield() }
        gate.complete(Unit)

        assertEquals("ghu_new_1", first.await()?.accessToken)
        assertEquals("ghu_new_1", second.await()?.accessToken)
        assertEquals("ghu_new_1", third.await()?.accessToken)
        assertEquals(1, server.calls)
        assertEquals(1, store.saveCount)
    }

    @Test
    fun proactiveRefreshOnlyWhenExpiryIsNear() = runTest {
        val server = RefreshServer()
        val fresh = expiringSession("ghu_d1", "ghr_d1", expiresAt = NOW + SessionManager.PROACTIVE_REFRESH_WINDOW_SECONDS + 60)
        val store = SharedTokenStore(fresh)
        val manager = SessionManager(store, refreshingClient(server)).also { it.initialize() }

        assertEquals("ghu_d1", manager.refreshSessionIfExpiringSoon()?.accessToken)
        assertEquals(0, server.calls)

        store.session = fresh.copy(accessTokenExpiresAtEpochSeconds = NOW + 60)
        manager.initialize()
        assertEquals("ghu_new_1", manager.refreshSessionIfExpiringSoon()?.accessToken)
        assertEquals(1, server.calls)
    }

    @Test
    fun rejectedRefreshTokenKeepsSessionAndIsNotRetried() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            json("""{"error":"bad_refresh_token"}""")
        }
        val client = DeviceFlowClient(httpClient = jsonClient(engine), nowEpochSeconds = { NOW })
        val session = expiringSession("ghu_e1", "ghr_e1_rejected")
        val store = SharedTokenStore(session)
        val manager = SessionManager(store, client).also { it.initialize() }

        assertNull(manager.refreshAfterUnauthorized("ghu_e1"))
        val loadsAfterRejection = store.loadCount
        assertNull(manager.refreshAfterUnauthorized("ghu_e1"))
        assertEquals("ghu_e1", manager.refreshSessionIfExpiringSoon()?.accessToken)

        assertEquals(1, calls)
        assertEquals(loadsAfterRejection, store.loadCount)
        assertEquals(session, store.session)
        assertEquals(session, manager.session.value)
    }

    @Test
    fun networkErrorDuringRefreshKeepsSession() = runTest {
        val engine = MockEngine { throw IllegalStateException("offline") }
        val client = DeviceFlowClient(httpClient = jsonClient(engine), nowEpochSeconds = { NOW })
        val session = expiringSession("ghu_f1", "ghr_f1")
        val store = SharedTokenStore(session)
        val manager = SessionManager(store, client).also { it.initialize() }

        assertNull(manager.refreshAfterUnauthorized("ghu_f1"))
        assertEquals(session, manager.session.value)
        assertEquals(session, store.session)
    }

    @Test
    fun expiredRefreshTokenIsNotSent() = runTest {
        val server = RefreshServer()
        val store = SharedTokenStore(expiringSession("ghu_g1", "ghr_g1", refreshExpiresAt = NOW - 1))
        val manager = SessionManager(store, refreshingClient(server)).also { it.initialize() }
        val loadsAfterInit = store.loadCount

        assertNull(manager.refreshAfterUnauthorized("ghu_g1"))
        assertEquals("ghu_g1", manager.refreshSessionIfExpiringSoon()?.accessToken)
        assertEquals(0, server.calls)
        assertEquals(loadsAfterInit, store.loadCount)
    }

    @Test
    fun failedRefreshIsRetriedOnlyAfterBackoff() = runTest {
        var now = NOW
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) throw IllegalStateException("offline")
            json("""{"access_token":"ghu_i2","expires_in":28800,"refresh_token":"ghr_i2","refresh_token_expires_in":15897600}""")
        }
        val client = DeviceFlowClient(httpClient = jsonClient(engine), nowEpochSeconds = { now })
        val store = SharedTokenStore(expiringSession("ghu_i1", "ghr_i1"))
        val manager = SessionManager(store, client).also { it.initialize() }

        assertNull(manager.refreshAfterUnauthorized("ghu_i1"))
        val loadsAfterFailure = store.loadCount

        // Within the pause: no network call and no store read, for either entry point.
        now += SessionManager.REFRESH_RETRY_BACKOFF_SECONDS - 1
        assertEquals("ghu_i1", manager.refreshSessionIfExpiringSoon()?.accessToken)
        assertNull(manager.refreshAfterUnauthorized("ghu_i1"))
        assertEquals(1, calls)
        assertEquals(loadsAfterFailure, store.loadCount)

        now += 2
        assertEquals("ghu_i2", manager.refreshAfterUnauthorized("ghu_i1")?.accessToken)
        assertEquals(2, calls)
        assertEquals("ghu_i2", store.session?.accessToken)
    }

    @Test
    fun requestsQueuedBehindFailedRefreshDoNotRetryIt() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val engine = MockEngine {
            calls++
            gate.await()
            throw IllegalStateException("github.com unreachable")
        }
        val client = DeviceFlowClient(httpClient = jsonClient(engine), nowEpochSeconds = { NOW })
        val store = SharedTokenStore(expiringSession("ghu_j1", "ghr_j1"))
        val app = SessionManager(store, client).also { it.initialize() }
        val worker = SessionManager(store, client).also { it.initialize() }
        val loadsAfterInit = store.loadCount

        val first = async { app.refreshSessionIfExpiringSoon() }
        val second = async { worker.refreshAfterUnauthorized("ghu_j1") }
        val third = async { app.refreshAfterUnauthorized("ghu_j1") }
        repeat(5) { yield() }
        gate.complete(Unit)

        assertEquals("ghu_j1", first.await()?.accessToken)
        assertNull(second.await())
        assertNull(third.await())
        assertEquals(1, calls)
        // Only the caller that actually tried read the store.
        assertEquals(loadsAfterInit + 1, store.loadCount)
    }

    @Test
    fun refreshTimeoutIsAFailureWithBackoffNotACancellation() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            awaitCancellation()
        }
        val http = HttpClient(engine) {
            install(HttpTimeout) { requestTimeoutMillis = 50 }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = DeviceFlowClient(httpClient = http, nowEpochSeconds = { NOW })
        val store = SharedTokenStore(expiringSession("ghu_k1", "ghr_k1"))
        val manager = SessionManager(store, client).also { it.initialize() }

        val failure = client.refreshAccessToken(expiringSession("ghu_k1", "ghr_k1_direct")).exceptionOrNull()
        assertNotNull(failure)
        assertFalse(failure is RefreshTokenRejectedException)

        assertNull(manager.refreshAfterUnauthorized("ghu_k1"))
        assertNull(manager.refreshAfterUnauthorized("ghu_k1"))
        assertEquals(2, calls)
        assertEquals("ghu_k1", manager.session.value?.accessToken)
    }

    @Test
    fun oldStoredSessionJsonStillDecodesAndNewFieldsRoundTrip() {
        val legacy = """{"accessToken":"gho_legacy","tokenType":"bearer","scope":"repo","authMethod":"DEVICE_FLOW","userLogin":"octocat"}"""
        val decoded = TokenStoreJson.decodeFromString<AuthSession>(legacy)
        assertEquals("gho_legacy", decoded.accessToken)
        assertEquals("octocat", decoded.userLogin)
        assertNull(decoded.refreshToken)
        assertNull(decoded.accessTokenExpiresAtEpochSeconds)
        assertNull(decoded.refreshTokenExpiresAtEpochSeconds)
        assertNull(decoded.clientId)

        val full = expiringSession("ghu_h1", "ghr_h1")
        assertEquals(full, TokenStoreJson.decodeFromString<AuthSession>(TokenStoreJson.encodeToString(full)))
        val pretty = Json(from = TokenStoreJson) { prettyPrint = true }
        assertEquals(full, pretty.decodeFromString<AuthSession>(pretty.encodeToString(full)))
    }

    @Test
    fun saveFailureAfterRefreshDoesNotRevertToStaleStoredTokenOnNext401() = runTest {
        var refreshCalls = 0
        val engine = MockEngine { request ->
            refreshCalls++
            val form = request.form()
            assertEquals(if (refreshCalls == 1) "ghr_old" else "ghr_fresh_1", form["refresh_token"])
            val n = refreshCalls
            json("""{"access_token":"ghu_fresh_$n","expires_in":28800,"refresh_token":"ghr_fresh_$n","refresh_token_expires_in":15897600,"token_type":"bearer","scope":"repo"}""")
        }
        val client = DeviceFlowClient(httpClient = jsonClient(engine), nowEpochSeconds = { NOW })
        val flakyStore = object : TokenStore {
            var stored: AuthSession? = expiringSession("ghu_old", "ghr_old")
            var failSaves = true
            override suspend fun saveSession(session: AuthSession) {
                if (failSaves) error("keyring locked")
                stored = session
            }
            override suspend fun loadSession(): AuthSession? = stored
            override suspend fun clearSession() { stored = null }
            override suspend fun saveClientId(clientId: String) {}
            override suspend fun loadClientId(): String? = null
            override suspend fun saveThemePreferences(prefs: ThemePreferences) {}
            override suspend fun loadThemePreferences() = ThemePreferences()
            override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {}
            override suspend fun loadNotificationPreferences() = NotificationPreferences()
            override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) {}
            override suspend fun loadSeenNotificationMarkers() = emptyMap<String, String>()
        }

        val manager = SessionManager(flakyStore, client).also { it.initialize() }
        val firstRefreshed = manager.refreshAfterUnauthorized("ghu_old")
        assertEquals("ghu_fresh_1", firstRefreshed?.accessToken)
        assertEquals("ghu_fresh_1", manager.session.value?.accessToken)
        // Disk still has ghu_old because save failed.
        assertEquals("ghu_old", flakyStore.stored?.accessToken)

        // Next 401 on ghu_fresh_1 must NOT revert to ghu_old; it must refresh using ghr_fresh_1.
        flakyStore.failSaves = false
        val secondRefreshed = manager.refreshAfterUnauthorized("ghu_fresh_1")
        assertEquals("ghu_fresh_2", secondRefreshed?.accessToken)
        assertEquals("ghu_fresh_2", flakyStore.stored?.accessToken)
        assertEquals(2, refreshCalls)
    }

    @Test
    fun transientOauthErrorRetriesAfterBackoffWindow() = runTest {
        var now = NOW
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) {
                json("""{"error":"temporarily_unavailable","error_description":"GitHub busy"}""")
            } else {
                json("""{"access_token":"ghu_recovered","expires_in":28800,"refresh_token":"ghr_recovered","refresh_token_expires_in":15897600,"token_type":"bearer","scope":"repo"}""")
            }
        }
        val client = DeviceFlowClient(httpClient = jsonClient(engine), nowEpochSeconds = { now })
        val store = SharedTokenStore(expiringSession("ghu_t1", "ghr_t1"))
        val manager = SessionManager(store, client).also { it.initialize() }

        assertNull(manager.refreshAfterUnauthorized("ghu_t1"))
        assertEquals(1, calls)

        // During backoff window it does not hammer GitHub.
        assertNull(manager.refreshAfterUnauthorized("ghu_t1"))
        assertEquals(1, calls)

        // After backoff expires (e.g. 15 minutes later), it retries and succeeds.
        now += 15 * 60L
        val recovered = manager.refreshAfterUnauthorized("ghu_t1")
        assertEquals("ghu_recovered", recovered?.accessToken)
        assertEquals(2, calls)
    }
}
