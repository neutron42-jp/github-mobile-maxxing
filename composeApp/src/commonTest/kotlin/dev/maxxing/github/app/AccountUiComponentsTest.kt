package dev.maxxing.github.app

import dev.maxxing.github.app.ui.AccountClusterLayout
import dev.maxxing.github.app.ui.resolveAccountClusterLayout
import dev.maxxing.github.auth.AccountIndex
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.ProviderType
import dev.maxxing.github.network.GitHubNetworkConfig
import dev.maxxing.github.network.GitHubRepository
import dev.maxxing.github.network.NotificationMonitor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class UiTestMultiAccountTokenStore : TokenStore {
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

class AccountUiComponentsTest {

    @Test
    fun avatarClusterLayoutFollowsAccountCountSpec() {
        assertEquals(AccountClusterLayout.EMPTY, resolveAccountClusterLayout(0))
        assertEquals(AccountClusterLayout.SINGLE, resolveAccountClusterLayout(1))
        assertEquals(AccountClusterLayout.PAIR, resolveAccountClusterLayout(2))
        assertEquals(AccountClusterLayout.TRIANGLE, resolveAccountClusterLayout(3))
        assertEquals(AccountClusterLayout.GRID_FOUR, resolveAccountClusterLayout(4))
        assertEquals(AccountClusterLayout.GRID_OVERFLOW, resolveAccountClusterLayout(5))
        assertEquals(AccountClusterLayout.GRID_OVERFLOW, resolveAccountClusterLayout(9))
    }

    @Test
    fun appContainerSwitchesAndLogsOutIndividualAccounts() = runTest {
        val store = UiTestMultiAccountTokenStore()
        val manager = SessionManager(tokenStore = store)
        manager.initialize()

        val engine = MockEngine {
            respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val repo = GitHubRepository(
            sessionManager = manager,
            networkConfig = GitHubNetworkConfig(manager, baseUrl = "https://example.test"),
            httpClient = HttpClient(engine)
        )
        val container = AppContainer(
            sessionManager = manager,
            repository = repo,
            notificationMonitor = NotificationMonitor(manager, repo),
            showOsNotifications = {}
        )

        val alice = AuthSession(
            accessToken = "tok_alice",
            scope = "repo",
            authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN,
            providerType = ProviderType.GITHUB,
            userLogin = "alice",
            userId = 1L
        )
        val bob = AuthSession(
            accessToken = "tok_bob",
            scope = "",
            authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN,
            providerType = ProviderType.FORGEJO_GITEA,
            baseUrl = "https://codeberg.org",
            apiBaseUrl = "https://codeberg.org/api/v1",
            userLogin = "bob_cb",
            userId = 2L
        )

        val savedAlice = manager.addOrUpdateAccount(alice, makeActive = true)
        val savedBob = manager.addOrUpdateAccount(bob, makeActive = false)
        assertEquals(savedAlice.resolvedAccountId(), manager.activeAccountId.value)

        // Switching to Bob updates active account & repository provider capability.
        assertTrue(container.switchAccount(savedBob.resolvedAccountId()))
        assertEquals("bob_cb", manager.session.value?.userLogin)
        assertEquals(ProviderType.FORGEJO_GITEA, container.repository.activeProviderType())

        // Logging out Bob falls back to Alice automatically.
        container.logoutAccount(savedBob.resolvedAccountId())
        assertEquals("alice", manager.session.value?.userLogin)
        assertEquals(ProviderType.GITHUB, container.repository.activeProviderType())

        // Logging out Alice clears everything cleanly.
        container.logoutAccount(savedAlice.resolvedAccountId())
        assertNull(manager.session.value)
        assertEquals(0, manager.accounts.value.size)
    }
}
