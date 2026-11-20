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
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val REPO_JSON = """{"id":424242,"name":"app","full_name":"octo/app","private":false}"""
private const val ASSET_URL =
    "https://github.com/user-attachments/assets/11111111-1111-1111-1111-111111111111"

private class AttachmentTestTokenStore(var session: AuthSession?) : TokenStore {
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
    override suspend fun loadSeenNotificationMarkers(): Map<String, String> = emptyMap()
}

private suspend fun attachmentTestConfig(
    token: String = "tok_attachments",
    authMethod: AuthMethod = AuthMethod.DEVICE_FLOW
): GitHubNetworkConfig {
    val store = AttachmentTestTokenStore(
        AuthSession(accessToken = token, authMethod = authMethod, userLogin = "octocat")
    )
    val manager = SessionManager(store)
    manager.initialize()
    return GitHubNetworkConfig(manager)
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

class AttachmentUploadTest {

    @Test
    fun validatesSupportedFormatsEmptyAndInclusiveSizeLimits() {
        val png = validateAttachmentFile("shot.png", MAX_IMAGE_ATTACHMENT_BYTES)
        assertTrue(png is AttachmentValidation.Valid)
        assertEquals("image/png", png.format.contentType)
        assertEquals(AttachmentKind.IMAGE, png.format.kind)

        val video = validateAttachmentFile("clip.MOV", MAX_VIDEO_ATTACHMENT_BYTES)
        assertTrue(video is AttachmentValidation.Valid)
        assertEquals("video/quicktime", video.format.contentType)

        val imageTooLarge = validateAttachmentFile("shot.jpeg", MAX_IMAGE_ATTACHMENT_BYTES + 1)
        assertTrue(imageTooLarge is AttachmentValidation.TooLarge)
        assertEquals(AttachmentKind.IMAGE, imageTooLarge.format.kind)

        val videoTooLarge = validateAttachmentFile("clip.webm", MAX_VIDEO_ATTACHMENT_BYTES + 1)
        assertTrue(videoTooLarge is AttachmentValidation.TooLarge)
        assertEquals(AttachmentKind.VIDEO, videoTooLarge.format.kind)

        assertTrue(validateAttachmentFile("shot.png", 0) is AttachmentValidation.Empty)
        assertTrue(validateAttachmentFile("notes.pdf", 10) is AttachmentValidation.UnsupportedType)
        assertTrue(validateAttachmentFile("archive.zip", 10) is AttachmentValidation.UnsupportedType)
        assertTrue(validateAttachmentFile("README", 10) is AttachmentValidation.UnsupportedType)
    }

    @Test
    fun formatsEscapedImageMarkdownAndBareVideoUrl() {
        assertEquals(
            "![shot 1]($ASSET_URL)",
            formatAttachmentMarkdown("shot.1.png", "image/png", ASSET_URL)
        )
        assertEquals(
            "![shot \\[draft\\] 1](https://github.com/user-attachments/assets/22222222-2222-2222-2222-222222222222)",
            formatAttachmentMarkdown(
                "shot [draft] 1.svg",
                "image/svg+xml",
                "https://github.com/user-attachments/assets/22222222-2222-2222-2222-222222222222"
            )
        )
        assertEquals(
            "https://github.com/user-attachments/assets/33333333-3333-3333-3333-333333333333",
            formatAttachmentMarkdown(
                "clip.mp4",
                "video/mp4",
                "https://github.com/user-attachments/assets/33333333-3333-3333-3333-333333333333"
            )
        )
    }

    @Test
    fun acceptsOnlyCanonicalUserAttachmentAssetUrls() {
        assertEquals(ASSET_URL, validateUserAttachmentUrl(ASSET_URL))
        assertNull(validateUserAttachmentUrl("http://github.com/user-attachments/assets/11111111-1111-1111-1111-111111111111"))
        assertNull(validateUserAttachmentUrl("https://uploads.github.com/user-attachments/assets/11111111-1111-1111-1111-111111111111"))
        assertNull(validateUserAttachmentUrl("https://evil.example/user-attachments/assets/11111111-1111-1111-1111-111111111111"))
        assertNull(validateUserAttachmentUrl("https://github.com.evil.example/user-attachments/assets/11111111-1111-1111-1111-111111111111"))
        assertNull(validateUserAttachmentUrl("https://user:pass@github.com/user-attachments/assets/11111111-1111-1111-1111-111111111111"))
        assertNull(validateUserAttachmentUrl("https://github.com:8443/user-attachments/assets/11111111-1111-1111-1111-111111111111"))
        assertNull(validateUserAttachmentUrl("$ASSET_URL?download=1"))
        assertNull(validateUserAttachmentUrl("$ASSET_URL#fragment"))
        assertNull(validateUserAttachmentUrl("$ASSET_URL/extra"))
        assertNull(validateUserAttachmentUrl("https://github.com/user-attachments/assets/not-a-uuid"))
        assertNull(validateUserAttachmentUrl("https://github.com/user-attachments/assets/11111111111111111111111111111111"))
        assertNull(validateUserAttachmentUrl(""))
    }

    @Test
    fun bearerIsScopedToTheUploadPostPathAndExistingApiHost() = runTest {
        val config = attachmentTestConfig()
        val seen = mutableListOf<Triple<HttpMethod, String, String?>>()
        val engine = MockEngine { request ->
            seen += Triple(request.method, request.url.toString(), request.headers[HttpHeaders.Authorization])
            respond("", HttpStatusCode.NoContent)
        }
        val client = config.createHttpClient(engine)
        try {
            client.post("https://uploads.github.com/user-attachments/assets?name=x")
            client.post("https://uploads.github.com/user-attachments/other?name=x")
            client.get("https://uploads.github.com/user-attachments/assets?name=x")
            client.post("https://api.github.com/repos/octo/app/issues")
            client.post("https://evil.example/user-attachments/assets?name=x")
        } finally {
            client.close()
        }

        assertEquals("Bearer tok_attachments", seen[0].third)
        assertNull(seen[1].third)
        assertNull(seen[2].third)
        assertEquals("Bearer tok_attachments", seen[3].third)
        assertNull(seen[4].third)
    }

    @Test
    fun uploadUsesNativeEndpointWithRepositoryIdRawBytesAndValidatedUrl() = runTest {
        val config = attachmentTestConfig()
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        var uploadName: String? = null
        var uploadContentType: String? = null
        var uploadRepositoryId: String? = null
        var uploadBody: ByteArray? = null
        var uploadRequestContentType: String? = null
        var uploadAccept: String? = null
        var uploadAuth: String? = null
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Get && request.url.encodedPath == "/repos/octo/app" -> {
                    respond(
                        """{"id":424242,"name":"app","full_name":"octo/app","private":false,"permissions":{"push":true}}""",
                        HttpStatusCode.OK,
                        jsonHeaders
                    )
                }
                request.method == HttpMethod.Post && request.url.host == "uploads.github.com" -> {
                    uploadName = request.url.parameters["name"]
                    uploadContentType = request.url.parameters["content_type"]
                    uploadRepositoryId = request.url.parameters["repository_id"]
                    uploadBody = request.body.toByteArray()
                    uploadRequestContentType = request.body.contentType?.toString()
                        ?: request.headers[HttpHeaders.ContentType]
                    uploadAccept = request.headers[HttpHeaders.Accept]
                    uploadAuth = request.headers[HttpHeaders.Authorization]
                    respond("""{"url":"$ASSET_URL"}""", HttpStatusCode.Created, jsonHeaders)
                }
                else -> error("Unexpected request ${request.method} ${request.url}")
            }
        }
        val rest = GitHubRestService(config.createHttpClient(engine), config.baseUrl)

        val uploaded = rest.uploadAttachment("octo", "app", "shot #1.png", pngBytes)

        assertEquals("shot #1.png", uploadName)
        assertEquals("image/png", uploadContentType)
        assertEquals("424242", uploadRepositoryId)
        assertContentEquals(pngBytes, uploadBody)
        assertEquals(ContentType.Application.OctetStream.toString(), uploadRequestContentType)
        assertEquals("application/vnd.github+json", uploadAccept)
        assertEquals("Bearer tok_attachments", uploadAuth)
        assertEquals("https://github.com/user-attachments/assets/11111111-1111-1111-1111-111111111111", uploaded.url)
        assertEquals("image/png", uploaded.contentType)
        assertEquals("![shot #1]($ASSET_URL)", uploaded.markdownSnippet)
    }


    @Test
    fun unauthorizedUploadRefreshesTokenAndRetriesTheSameRawBytes() = runTest {
        val now = 1_800_000_000L
        val store = AttachmentTestTokenStore(
            AuthSession(
                accessToken = "ghu_old",
                authMethod = AuthMethod.DEVICE_FLOW,
                userLogin = "octocat",
                refreshToken = "ghr_old",
                accessTokenExpiresAtEpochSeconds = now + 3600,
                refreshTokenExpiresAtEpochSeconds = now + 1_000_000,
                clientId = "Ov23liTestClient"
            )
        )
        val oauth = MockEngine { request ->
            check(request.url.host == "github.com") { "Refresh must not use the API client" }
            respond(
                """{"access_token":"ghu_new","expires_in":28800,"refresh_token":"ghr_new","refresh_token_expires_in":15897600,"token_type":"bearer","scope":"repo"}""",
                HttpStatusCode.OK,
                jsonHeaders
            )
        }
        val authHttp = HttpClient(oauth) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val manager = SessionManager(
            store,
            DeviceFlowClient(httpClient = authHttp, nowEpochSeconds = { now })
        )
        manager.initialize()
        val config = GitHubNetworkConfig(manager)
        val bytes = byteArrayOf(1, 2, 3, 4)
        val authorizations = mutableListOf<String?>()
        val bodies = mutableListOf<ByteArray>()
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(REPO_JSON, HttpStatusCode.OK, jsonHeaders)
            } else {
                authorizations += request.headers[HttpHeaders.Authorization]
                bodies += request.body.toByteArray()
                if (authorizations.size == 1) {
                    respond("""{"message":"Bad credentials"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                } else {
                    respond("""{"url":"$ASSET_URL"}""", HttpStatusCode.Created, jsonHeaders)
                }
            }
        }
        val rest = GitHubRestService(config.createHttpClient(engine), config.baseUrl)

        val uploaded = rest.uploadAttachment("octo", "app", "shot.png", bytes)

        assertEquals(listOf<String?>("Bearer ghu_old", "Bearer ghu_new"), authorizations)
        assertEquals(2, bodies.size)
        assertContentEquals(bytes, bodies[0])
        assertContentEquals(bytes, bodies[1])
        assertEquals(ASSET_URL, uploaded.url)
    }

    @Test
    fun videoUploadSendsVideoContentTypeAndBareUrlMarkdown() = runTest {
        val config = attachmentTestConfig()
        var contentType: String? = null
        val videoUrl = "https://github.com/user-attachments/assets/33333333-3333-3333-3333-333333333333"
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(REPO_JSON, HttpStatusCode.OK, jsonHeaders)
            } else {
                contentType = request.url.parameters["content_type"]
                respond("""{"url":"$videoUrl"}""", HttpStatusCode.Created, jsonHeaders)
            }
        }
        val rest = GitHubRestService(config.createHttpClient(engine), config.baseUrl)

        val uploaded = rest.uploadAttachment("octo", "app", "clip.mp4", byteArrayOf(1, 2, 3))

        assertEquals("video/mp4", contentType)
        assertEquals(videoUrl, uploaded.markdownSnippet)
    }

    @Test
    fun explicitReadPermissionFailsBeforeUpload() = runTest {
        val config = attachmentTestConfig()
        var uploads = 0
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(
                    """{"id":424242,"name":"app","full_name":"octo/app","permissions":{"pull":true}}""",
                    HttpStatusCode.OK,
                    jsonHeaders
                )
            } else {
                uploads++
                respond("""{"url":"$ASSET_URL"}""", HttpStatusCode.Created, jsonHeaders)
            }
        }
        val rest = GitHubRestService(config.createHttpClient(engine), config.baseUrl)

        assertFailsWith<AttachmentException.WriteAccessRequired> {
            rest.uploadAttachment("octo", "app", "shot.png", byteArrayOf(1))
        }
        assertEquals(0, uploads)
    }

    @Test
    fun missingPermissionBlockIsLeftForTheServerToDecide() = runTest {
        val config = attachmentTestConfig()
        var uploads = 0
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(REPO_JSON, HttpStatusCode.OK, jsonHeaders)
            } else {
                uploads++
                respond("""{"url":"$ASSET_URL"}""", HttpStatusCode.Created, jsonHeaders)
            }
        }
        val rest = GitHubRestService(config.createHttpClient(engine), config.baseUrl)

        rest.uploadAttachment("octo", "app", "shot.png", byteArrayOf(1))

        assertEquals(1, uploads)
    }

    @Test
    fun mapsForbiddenNotFoundRejectedAndUnauthorizedResponses() = runTest {
        suspend fun uploadFailingWith(status: HttpStatusCode): Throwable {
            val config = attachmentTestConfig()
            val engine = MockEngine { request ->
                if (request.method == HttpMethod.Get) {
                    respond(REPO_JSON, HttpStatusCode.OK, jsonHeaders)
                } else {
                    respond("""{"message":"nope"}""", status, jsonHeaders)
                }
            }
            val rest = GitHubRestService(config.createHttpClient(engine), config.baseUrl)
            return assertFailsWith<AttachmentException> {
                rest.uploadAttachment("octo", "app", "shot.png", byteArrayOf(1))
            }
        }

        assertTrue(uploadFailingWith(HttpStatusCode.Forbidden) is AttachmentException.WriteAccessRequired)
        assertTrue(uploadFailingWith(HttpStatusCode.NotFound) is AttachmentException.WriteAccessRequired)
        assertTrue(uploadFailingWith(HttpStatusCode.UnprocessableEntity) is AttachmentException.Rejected)
        assertTrue(uploadFailingWith(HttpStatusCode.PayloadTooLarge) is AttachmentException.Rejected)
        assertTrue(uploadFailingWith(HttpStatusCode.Unauthorized) is AttachmentException.UnsupportedCredential)
    }

    @Test
    fun redirectIsNotFollowedAndInvalidResponsesAreRejected() = runTest {
        val config = attachmentTestConfig()
        for (status in listOf(HttpStatusCode.Found, HttpStatusCode.TemporaryRedirect, HttpStatusCode.PermanentRedirect)) {
            var uploadCalls = 0
            val redirectEngine = MockEngine { request ->
                if (request.method == HttpMethod.Get) {
                    respond(REPO_JSON, HttpStatusCode.OK, jsonHeaders)
                } else {
                    uploadCalls++
                    respond(
                        "",
                        status,
                        headersOf(HttpHeaders.Location, "https://evil.example/upload")
                    )
                }
            }
            val rest = GitHubRestService(config.createHttpClient(redirectEngine), config.baseUrl)
            val redirected = assertFailsWith<AttachmentException.Redirected> {
                rest.uploadAttachment("octo", "app", "shot.png", byteArrayOf(1))
            }
            assertEquals(status.value, redirected.statusCode)
            assertEquals(1, uploadCalls)
        }

        val invalidUrlEngine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(REPO_JSON, HttpStatusCode.OK, jsonHeaders)
            } else {
                respond("""{"url":"https://evil.example/user-attachments/assets/11111111-1111-1111-1111-111111111111"}""", HttpStatusCode.Created, jsonHeaders)
            }
        }
        val invalidRest = GitHubRestService(config.createHttpClient(invalidUrlEngine), config.baseUrl)
        assertFailsWith<AttachmentException.InvalidResponse> {
            invalidRest.uploadAttachment("octo", "app", "shot.png", byteArrayOf(1))
        }
    }

    @Test
    fun networkFailureAndLocalValidationLeaveNothingUploaded() = runTest {
        var requests = 0
        val config = attachmentTestConfig()
        val failingEngine = MockEngine {
            requests++
            throw IllegalStateException("offline")
        }
        val failingRest = GitHubRestService(config.createHttpClient(failingEngine), config.baseUrl)
        assertTrue(
            assertFailsWith<AttachmentException.Network> {
                failingRest.uploadAttachment("octo", "app", "shot.png", byteArrayOf(1))
            }.cause is IllegalStateException
        )
        assertEquals(1, requests)

        val validationEngine = MockEngine { error("No request may be made for an invalid local file") }
        val validationRest = GitHubRestService(config.createHttpClient(validationEngine), config.baseUrl)
        assertTrue(
            assertFailsWith<AttachmentException.UnsupportedType> {
                validationRest.uploadAttachment("octo", "app", "notes.pdf", byteArrayOf(1))
            }.fileName == "notes.pdf"
        )
        assertFailsWith<AttachmentException.Empty> {
            validationRest.uploadAttachment("octo", "app", "shot.png", ByteArray(0))
        }
    }
}
