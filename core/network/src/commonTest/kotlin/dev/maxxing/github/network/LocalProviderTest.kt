package dev.maxxing.github.network

import dev.maxxing.github.auth.AccountIndex
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.ProviderType
import dev.maxxing.github.model.capabilities
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
import kotlin.test.assertTrue

private class LocalTestTokenStore : TokenStore {
    override val supportsMultiAccountStorage: Boolean = true
    private val accounts = linkedMapOf<String, AuthSession>()
    private var index: AccountIndex? = null
    private var localIdentity: String? = null
    private val localReposByAccount = linkedMapOf<String, List<String>>()

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
    override suspend fun saveLocalIdentitySecret(secretHex: String) { localIdentity = secretHex }
    override suspend fun loadLocalIdentitySecret(): String? = localIdentity
    override suspend fun saveLocalReposForAccount(accountId: String, repoPaths: List<String>) {
        localReposByAccount[accountId] = repoPaths
    }
    override suspend fun loadLocalReposForAccount(accountId: String): List<String> =
        localReposByAccount[accountId].orEmpty()
}

class LocalProviderTest {

    @Test
    fun localRepoRelativePathRoundTripsLosslessly() {
        val cases = listOf(
            "." to ".",
            "sample" to "sample",
            "org/nested/project" to "org/nested/project"
        )
        for ((input, expectedJoined) in cases) {
            val (owner, name) = splitLocalRepoRelativePath(input, "demo")
            assertEquals(expectedJoined, joinLocalRepoRelativePath(owner, name))
        }
    }

    @Test
    fun localProviderCapabilitiesExposeReposAndWorkingDiffOnly() {
        val caps = ProviderType.LOCAL.capabilities()
        assertTrue(caps.supportsRepositories)
        assertTrue(caps.supportsWorkingTreeDiff)
        assertFalse(caps.supportsInbox)
        assertFalse(caps.supportsIssuesAndPulls)
        assertFalse(caps.supportsGraphQl)
        assertFalse(caps.supportsActions)
    }

    @Test
    fun sessionManagerPersistsLocalIdentityAndHostAccount() = runTest {
        val store = LocalTestTokenStore()
        val manager = SessionManager(tokenStore = store)
        manager.initialize()

        var generatedCount = 0
        val first = manager.getOrCreateLocalIdentitySecret {
            generatedCount++
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        }
        val second = manager.getOrCreateLocalIdentitySecret {
            generatedCount++
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        }
        assertEquals(1, generatedCount)
        assertEquals(first, second)

        val account = manager.addLocalAccount(
            hostName = "workstation",
            rootName = "projects",
            endpointId = "node123abc",
            endpointTicket = "endpointxyz999",
            makeActive = true
        )
        assertEquals(ProviderType.LOCAL, account.providerType)
        assertEquals("local:node123abc", account.resolvedAccountId())
        assertEquals("projects · P2P", account.instanceHostLabel())
        assertEquals(account.resolvedAccountId(), manager.activeAccountId.value)

        assertTrue(manager.addLocalRepoPath("/dev/app/"))
        assertFalse(manager.addLocalRepoPath("dev/app"))
        assertEquals(listOf("dev/app"), manager.localRepos.value)
        assertTrue(manager.addLocalRepoPath("work\\tool"))
        assertEquals(listOf("work/tool", "dev/app"), manager.localRepos.value)

        // Switching to another account clears active local repo list, switching back restores it
        val secondHost = manager.addLocalAccount(
            hostName = "laptop",
            rootName = "user",
            endpointId = "node999zzz",
            endpointTicket = "ticket999",
            makeActive = true
        )
        assertTrue(manager.localRepos.value.isEmpty())
        assertTrue(manager.switchAccount(account.resolvedAccountId()))
        assertEquals(listOf("work/tool", "dev/app"), manager.localRepos.value)

        assertTrue(manager.removeLocalRepoPath("work/tool"))
        assertEquals(listOf("dev/app"), manager.localRepos.value)
        assertTrue(manager.switchAccount(secondHost.resolvedAccountId()))
        assertTrue(manager.localRepos.value.isEmpty())
    }

    @Test
    fun switchingFromForgejoToLocalNeverLeaksRemoteProfile() = runTest {
        val store = LocalTestTokenStore()
        val manager = SessionManager(tokenStore = store)
        manager.initialize()

        val forgejo = AuthSession(
            accessToken = "pat_codeberg",
            tokenType = "token",
            authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN,
            userLogin = "cb_alice",
            accountId = "forgejo:codeberg.org:42",
            providerType = ProviderType.FORGEJO_GITEA,
            baseUrl = "https://codeberg.org",
            apiBaseUrl = "https://codeberg.org/api/v1",
            avatarUrl = "https://codeberg.org/avatars/42"
        )
        manager.addOrUpdateAccount(forgejo, makeActive = true)

        val engine = MockEngine {
            respond(
                content = """{"id":42,"login":"cb_alice","avatar_url":"https://codeberg.org/avatars/42","full_name":"Alice CB"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val repo = GitHubRepository(sessionManager = manager, httpClient = http)
        val cbProfile = repo.getViewerProfile().getOrThrow()
        assertEquals("cb_alice", cbProfile.login)
        assertEquals("https://codeberg.org/avatars/42", cbProfile.avatarUrl)

        // Add Local host WITHOUT calling clearMemoryCaches first (simulating Compose race)
        val local = manager.addLocalAccount(
            hostName = "fedora",
            rootName = "user",
            endpointId = "node_fedora",
            endpointTicket = "ticket_fedora",
            makeActive = true
        )
        assertEquals("fedora", local.userLogin)
        assertEquals("", local.avatarUrl)

        // peekViewerProfile and getViewerProfile MUST NOT return Codeberg's profile
        assertEquals(null, repo.peekViewerProfile())
        val localProfile = repo.getViewerProfile().getOrThrow()
        assertEquals("fedora", localProfile.login)
        assertEquals("", localProfile.avatarUrl)
    }
}
