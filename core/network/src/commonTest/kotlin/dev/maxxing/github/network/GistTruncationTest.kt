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
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GistTruncationTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    @Test
    fun fetchGistMapsTruncatedFlagAndDescriptionOnlyPatchOmitsFiles() = runTest {
        var patchBody: String? = null
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Get && request.url.encodedPath == "/gists/g1" -> respond(
                    content = """
                        {
                          "id": "g1",
                          "description": "Large gist",
                          "public": false,
                          "html_url": "https://gist.github.com/g1",
                          "created_at": "2026-11-20T00:00:00Z",
                          "updated_at": "2026-11-20T00:00:00Z",
                          "owner": { "login": "octo", "avatar_url": "" },
                          "files": {
                            "big.txt": {
                              "filename": "big.txt",
                              "language": "Text",
                              "raw_url": "https://gist.githubusercontent.com/raw/big.txt",
                              "size": 2000000,
                              "content": "prefix only",
                              "truncated": true
                            }
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                request.method == HttpMethod.Patch && request.url.encodedPath == "/gists/g1" -> {
                    patchBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    respond(
                        content = """
                            {
                              "id": "g1",
                              "description": "Updated description",
                              "public": false,
                              "html_url": "https://gist.github.com/g1",
                              "created_at": "2026-11-20T00:00:00Z",
                              "updated_at": "2026-11-20T01:00:00Z",
                              "owner": { "login": "octo", "avatar_url": "" },
                              "files": {
                                "big.txt": {
                                  "filename": "big.txt",
                                  "language": "Text",
                                  "raw_url": "https://gist.githubusercontent.com/raw/big.txt",
                                  "size": 2000000,
                                  "content": "prefix only",
                                  "truncated": true
                                }
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond("false", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

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
        val repository = GitHubRepository(sessionManager = sessionManager, httpClient = createClient(engine))

        val detail = repository.getGistDetail("g1").getOrThrow()
        assertEquals(1, detail.files.size)
        assertTrue(detail.files[0].truncated)

        val updated = repository.updateGist("g1", "Updated description", emptyMap()).getOrThrow()
        assertEquals("Updated description", updated.summary.description)
        val parsedPatch = Json.parseToJsonElement(patchBody!!).jsonObject
        assertFalse(parsedPatch.containsKey("files"))
    }
}
