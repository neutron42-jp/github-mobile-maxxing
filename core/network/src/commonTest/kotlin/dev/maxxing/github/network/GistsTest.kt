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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class GistsTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_gists", userLogin = "octocat")
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
    override suspend fun loadSeenNotificationMarkers(): Map<String, String> = emptyMap()
}

class GistsTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val jsonCodec = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun browseCreateEditStarAndDeleteGistsUpdateCaches() = runTest {
        val requests = mutableListOf<Triple<HttpMethod, String, String>>()
        var isStarred = false

        val gistSummaryJson = """
            {
              "id": "g1",
              "description": "Quick script",
              "public": false,
              "html_url": "https://gist.github.com/g1",
              "owner": {"login": "octocat", "avatar_url": "https://avatars/octo"},
              "files": {
                "run.sh": {"filename": "run.sh", "language": "Shell", "size": 21, "content": "#!/bin/sh\necho hi\n"}
              },
              "comments": 2
            }
        """.trimIndent()

        val engine = MockEngine { req ->
            val path = req.url.encodedPath
            val body = (req.body as? TextContent)?.text.orEmpty()
            requests += Triple(req.method, path, body)
            when {
                req.method == HttpMethod.Get && path == "/gists" ->
                    respond("[$gistSummaryJson]", HttpStatusCode.OK, jsonHeaders)
                req.method == HttpMethod.Get && path == "/gists/starred" ->
                    respond("[]", HttpStatusCode.OK, jsonHeaders)
                req.method == HttpMethod.Get && path == "/users/hubot/gists" ->
                    respond("[$gistSummaryJson]", HttpStatusCode.OK, jsonHeaders)
                req.method == HttpMethod.Get && path == "/gists/g1" ->
                    respond(gistSummaryJson, HttpStatusCode.OK, jsonHeaders)
                req.method == HttpMethod.Get && path == "/gists/g1/star" ->
                    respond("", if (isStarred) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
                req.method == HttpMethod.Put && path == "/gists/g1/star" -> {
                    isStarred = true
                    respond("", HttpStatusCode.NoContent)
                }
                req.method == HttpMethod.Delete && path == "/gists/g1/star" -> {
                    isStarred = false
                    respond("", HttpStatusCode.NoContent)
                }
                req.method == HttpMethod.Post && path == "/gists" -> respond(
                    """{"id":"g2","description":"New gist","public":true,"html_url":"https://gist.github.com/g2","owner":{"login":"octocat"},"files":{"hello.kt":{"filename":"hello.kt","language":"Kotlin","size":14,"content":"fun main() {}"}},"comments":0}""",
                    HttpStatusCode.Created,
                    jsonHeaders
                )
                req.method == HttpMethod.Patch && path == "/gists/g2" -> respond(
                    """{"id":"g2","description":"Updated desc","public":true,"html_url":"https://gist.github.com/g2","owner":{"login":"octocat"},"files":{"hello.kt":{"filename":"hello.kt","language":"Kotlin","size":19,"content":"println(\"ok\")"}},"comments":0}""",
                    HttpStatusCode.OK,
                    jsonHeaders
                )
                req.method == HttpMethod.Delete && path == "/gists/g2" ->
                    respond("", HttpStatusCode.NoContent)
                else -> error("Unexpected ${req.method} $path")
            }
        }

        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(jsonCodec) }
        }
        val sessionManager = SessionManager(GistsTestTokenStore(), DeviceFlowClient(httpClient))
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = httpClient)

        val mine = repo.getGists().getOrThrow()
        assertEquals(1, mine.size)
        assertEquals("run.sh", mine[0].files.first().filename)
        assertFalse(mine[0].isPublic)

        val starred = repo.getGists(starred = true).getOrThrow()
        assertTrue(starred.isEmpty())

        val userGists = repo.getGists(userLogin = "hubot").getOrThrow()
        assertEquals(1, userGists.size)

        val d1 = repo.getGistDetail("g1").getOrThrow()
        assertFalse(d1.viewerHasStarred)
        assertFalse(d1.summary.filesTruncated)

        repo.setGistStarred("g1", true).getOrThrow()
        assertTrue(repo.peekGistDetail("g1")?.viewerHasStarred == true)
        assertNull(repo.peekGists(starred = true))

        val created = repo.createGist("New gist", isPublic = true, files = mapOf("hello.kt" to "fun main() {}")).getOrThrow()
        assertEquals("g2", created.summary.id)
        val createJson = jsonCodec.parseToJsonElement(requests.first { it.first == HttpMethod.Post && it.second == "/gists" }.third).jsonObject
        assertTrue(createJson["public"]?.jsonPrimitive?.boolean == true)
        assertEquals("fun main() {}", createJson["files"]?.jsonObject?.get("hello.kt")?.jsonObject?.get("content")?.jsonPrimitive?.content)

        val updated = repo.updateGist("g2", description = "Updated desc", updatedFiles = mapOf("hello.kt" to "println(\"ok\")")).getOrThrow()
        assertEquals("Updated desc", updated.summary.description)

        repo.deleteGist("g2").getOrThrow()
        assertNull(repo.peekGistDetail("g2"))
    }

    @Test
    fun `gist 404 surfaces missing gist scope guidance and normalizes gist url id`() = runTest {
        val seenPaths = mutableListOf<String>()
        val engine = MockEngine { req ->
            seenPaths += req.url.encodedPath
            when (req.url.encodedPath) {
                "/gists/starred" -> respond(
                    content = """{"message":"Not Found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/gists/abc123" -> respond(
                    content = """{"id":"abc123","description":"URL id gist","public":true,"html_url":"https://gist.github.com/octocat/abc123","owner":{"login":"octocat"},"files":{"a.txt":{"filename":"a.txt","size":2,"content":"hi"}}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/gists/abc123/star" -> respond("", HttpStatusCode.NotFound)
                else -> error("Unexpected ${req.method} ${req.url.encodedPath}")
            }
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(jsonCodec) }
        }
        val sessionManager = SessionManager(GistsTestTokenStore(), DeviceFlowClient(httpClient))
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = httpClient)

        val err = repo.getGists(starred = true).exceptionOrNull()
        assertTrue(err is GitHubApiException && err.statusCode == 404)
        assertTrue(err.message.contains("gist", ignoreCase = true))

        val detail = repo.getGistDetail("https://gist.github.com/octocat/abc123/").getOrThrow()
        assertEquals("abc123", detail.summary.id)
        assertTrue("/gists/abc123" in seenPaths)
    }
}
