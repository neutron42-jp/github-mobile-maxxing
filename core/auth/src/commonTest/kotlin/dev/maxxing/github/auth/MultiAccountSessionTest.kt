package dev.maxxing.github.auth

import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.ProviderType
import dev.maxxing.github.model.buildForgejoTokenCreationUrl
import dev.maxxing.github.model.normalizeInstanceBaseUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class InMemoryMultiAccountStore : TokenStore {
    override val supportsMultiAccountStorage: Boolean = true

    val accounts = linkedMapOf<String, AuthSession>()
    var accountIndex: AccountIndex? = null
    var legacySession: AuthSession? = null
    var clientId: String? = null
    val pinnedByAccount = mutableMapOf<String, List<String>>()
    val markersByAccount = mutableMapOf<String, Map<String, String>>()

    override suspend fun saveSession(session: AuthSession) {
        legacySession = session
        saveAccount(session)
    }

    override suspend fun loadSession(): AuthSession? = legacySession

    override suspend fun clearSession() {
        legacySession = null
        accounts.clear()
        accountIndex = null
    }

    override suspend fun saveAccount(session: AuthSession) {
        accounts[session.resolvedAccountId()] = session
    }

    override suspend fun loadAccount(accountId: String): AuthSession? = accounts[accountId]

    override suspend fun deleteAccount(accountId: String) {
        accounts.remove(accountId)
        pinnedByAccount.remove(accountId)
        markersByAccount.remove(accountId)
    }

    override suspend fun saveAccountIndex(index: AccountIndex) {
        accountIndex = index
    }

    override suspend fun loadAccountIndex(): AccountIndex? = accountIndex

    override suspend fun saveClientId(clientId: String) {
        this.clientId = clientId
    }

    override suspend fun loadClientId(): String? = clientId

    override suspend fun saveThemePreferences(prefs: ThemePreferences) {}
    override suspend fun loadThemePreferences(): ThemePreferences = ThemePreferences()
    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {}
    override suspend fun loadNotificationPreferences(): NotificationPreferences = NotificationPreferences()
    override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) {}
    override suspend fun loadSeenNotificationMarkers(): Map<String, String> = emptyMap()

    override suspend fun saveSeenNotificationMarkersForAccount(accountId: String, markers: Map<String, String>) {
        markersByAccount[accountId] = markers
    }

    override suspend fun loadSeenNotificationMarkersForAccount(accountId: String): Map<String, String> =
        markersByAccount[accountId].orEmpty()

    override suspend fun savePinnedReposForAccount(accountId: String, repos: List<String>) {
        pinnedByAccount[accountId] = repos
    }

    override suspend fun loadPinnedReposForAccount(accountId: String): List<String> =
        pinnedByAccount[accountId].orEmpty()
}

class MultiAccountSessionTest {

    @Test
    fun normalizeBaseUrlAndForgejoTokenUrl() {
        assertEquals("https://codeberg.org", normalizeInstanceBaseUrl("codeberg.org/"))
        assertEquals("https://git.example.com/forge", normalizeInstanceBaseUrl(" https://git.example.com/forge/// "))
        assertEquals("http://localhost:3000", normalizeInstanceBaseUrl("http://localhost:3000/"))
        assertNull(normalizeInstanceBaseUrl(""))
        assertNull(normalizeInstanceBaseUrl("ftp://invalid.example"))

        assertEquals(
            "https://codeberg.org/user/settings/applications",
            buildForgejoTokenCreationUrl("codeberg.org")
        )
        assertEquals(
            "https://git.example.com/sub/user/settings/applications",
            buildForgejoTokenCreationUrl("https://git.example.com/sub/")
        )
    }

    @Test
    fun verifyForgejoPatFetchesUserAndNormalizesSession() = runTest {
        var capturedUrl = ""
        var capturedAuth = ""
        val engine = MockEngine { req ->
            capturedUrl = req.url.toString()
            capturedAuth = req.headers[HttpHeaders.Authorization].orEmpty()
            respond(
                content = """{"id":42,"login":"alice_fg","avatar_url":"https://codeberg.org/avatars/42"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = DeviceFlowClient(httpClient = http)

        val result = client.verifyForgejoPat("codeberg.org/", "fg_pat_secret")
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()

        assertEquals("https://codeberg.org/api/v1/user", capturedUrl)
        assertEquals("token fg_pat_secret", capturedAuth)
        assertEquals(ProviderType.FORGEJO_GITEA, session.providerType)
        assertEquals("https://codeberg.org", session.baseUrl)
        assertEquals("https://codeberg.org/api/v1", session.apiBaseUrl)
        assertEquals("alice_fg", session.userLogin)
        assertEquals(42L, session.userId)
        assertEquals("https://codeberg.org/avatars/42", session.avatarUrl)
    }

    @Test
    fun multiAccountAddSwitchRefreshAndLogoutFlow() = runTest {
        val now = 1_800_000_000L
        val store = InMemoryMultiAccountStore()
        var refreshCalls = 0
        val engine = MockEngine { req ->
            when {
                req.url.encodedPath.endsWith("/login/oauth/access_token") -> {
                    refreshCalls++
                    respond(
                        content = """{"access_token":"ghu_alice_refreshed","expires_in":28800,"refresh_token":"ghr_alice_2","refresh_token_expires_in":15897600,"token_type":"bearer","scope":"repo"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                req.url.encodedPath.endsWith("/api/v1/user") -> {
                    respond(
                        content = """{"id":77,"username":"cb_user","avatar_url":"https://codeberg.org/avatars/77"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected URL: ${req.url}")
            }
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = DeviceFlowClient(httpClient = http, nowEpochSeconds = { now })
        val manager = SessionManager(tokenStore = store, deviceFlowClient = client)
        manager.initialize()

        val alice = AuthSession(
            accessToken = "ghu_alice_1",
            scope = "repo",
            authMethod = AuthMethod.DEVICE_FLOW,
            providerType = ProviderType.GITHUB,
            userLogin = "alice",
            userId = 1L,
            refreshToken = "ghr_alice_1",
            accessTokenExpiresAtEpochSeconds = now - 10,
            refreshTokenExpiresAtEpochSeconds = now + 100_000,
            clientId = "Ov23Test"
        )
        val bob = AuthSession(
            accessToken = "ghp_bob_1",
            scope = "repo",
            authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN,
            providerType = ProviderType.GITHUB,
            userLogin = "bob",
            userId = 2L
        )

        val savedAlice = manager.addOrUpdateAccount(alice, makeActive = true)
        manager.togglePinnedRepo("octo/alice-repo")

        val savedBob = manager.addOrUpdateAccount(bob, makeActive = true)
        manager.togglePinnedRepo("octo/bob-repo")

        assertEquals(2, manager.accounts.value.size)
        assertEquals(savedBob.resolvedAccountId(), manager.activeAccountId.value)
        assertEquals(listOf("octo/bob-repo"), manager.pinnedRepos.value)

        // Add Codeberg account without clobbering existing GitHub accounts.
        val codebergResult = manager.signInWithForgejoPat("https://codeberg.org", "fg_pat", makeActive = true)
        assertTrue(codebergResult.isSuccess)
        val savedCb = codebergResult.getOrThrow()
        assertEquals(3, manager.accounts.value.size)
        assertEquals(savedCb.resolvedAccountId(), manager.activeAccountId.value)

        // Switch back to Alice and verify pinned repos & active session swap cleanly.
        assertTrue(manager.switchAccount(savedAlice.resolvedAccountId()))
        assertEquals("alice", manager.session.value?.userLogin)
        assertEquals(listOf("octo/alice-repo"), manager.pinnedRepos.value)

        // Refresh Alice's token; Bob & Codeberg remain untouched.
        val refreshedAlice = manager.refreshSessionIfExpiringSoon(savedAlice.resolvedAccountId())
        assertNotNull(refreshedAlice)
        assertEquals(1, refreshCalls)
        assertEquals("ghu_alice_refreshed", refreshedAlice.accessToken)
        assertEquals("ghp_bob_1", store.accounts[savedBob.resolvedAccountId()]?.accessToken)
        assertEquals("fg_pat", store.accounts[savedCb.resolvedAccountId()]?.accessToken)

        // Remove active account -> auto-switches to another stored account.
        val fallback = manager.removeAccount(savedAlice.resolvedAccountId())
        assertNotNull(fallback)
        assertEquals(2, manager.accounts.value.size)
        assertFalse(manager.accounts.value.any { it.resolvedAccountId() == savedAlice.resolvedAccountId() })
        assertNull(store.accounts[savedAlice.resolvedAccountId()])

        // Remove remaining accounts -> returns to signed-out state.
        manager.removeAccount(savedBob.resolvedAccountId())
        val noneLeft = manager.removeAccount(savedCb.resolvedAccountId())
        assertNull(noneLeft)
        assertNull(manager.session.value)
        assertNull(manager.activeAccountId.value)
        assertTrue(manager.accounts.value.isEmpty())
    }
}
