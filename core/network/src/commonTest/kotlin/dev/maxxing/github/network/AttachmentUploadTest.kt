package dev.maxxing.github.network

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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AttachmentUploadTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val jsonCodec = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun formatAndSanitizeAttachmentHelpersHandleImagesVideosAndFiles() {
        assertEquals(
            "![screenshot.png](https://raw.githubusercontent.com/octocat/github-mobile-attachments/main/attachments/img.png)",
            formatAttachmentMarkdown(
                fileName = "screenshot.png",
                mimeType = "image/png",
                url = "https://raw.githubusercontent.com/octocat/github-mobile-attachments/main/attachments/img.png"
            )
        )
        assertEquals(
            "[screen-record.mp4](https://raw.githubusercontent.com/octocat/github-mobile-attachments/main/attachments/clip.mp4)",
            formatAttachmentMarkdown(
                fileName = "screen-record.mp4",
                mimeType = "video/mp4",
                url = "https://raw.githubusercontent.com/octocat/github-mobile-attachments/main/attachments/clip.mp4"
            )
        )
        assertEquals(
            "![photo.jpg](https://example.com/a%20b.jpg)",
            formatAttachmentMarkdown("photo.jpg", "", "https://example.com/a b.jpg")
        )
        assertEquals("attachment.png", sanitizeAttachmentFileName("スクリーンショット.png", "image/png"))
        assertEquals("demo-clip.mp4", sanitizeAttachmentFileName("demo clip", "video/mp4"))
    }

    @Test
    fun emptyOrOversizedAttachmentIsRejectedWithoutNetworkCalls() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { _ ->
            calls++
            respond("{}", HttpStatusCode.OK, jsonHeaders)
        }) { install(ContentNegotiation) { json(jsonCodec) } }
        val rest = GitHubRestService(client)

        assertFailsWith<AttachmentException.Empty> {
            rest.uploadMediaAttachment("octocat", "app", "empty.png", "image/png", ByteArray(0))
        }
        assertFailsWith<AttachmentException.TooLarge> {
            rest.uploadMediaAttachment(
                "octocat",
                "app",
                "huge.mp4",
                "video/mp4",
                ByteArray(MAX_ATTACHMENT_BYTES + 1)
            )
        }
        assertEquals(0, calls)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun publicRepoCreatesHostRepoAndUploadsImageBytes() = runTest {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        var uploadedBase64: String? = null
        val engine = MockEngine { request ->
            requests += request.method to request.url.encodedPath
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Get && path == "/repos/upstream/project" -> respond(
                    """{"id":1,"name":"project","full_name":"upstream/project","private":false,"default_branch":"main"}""",
                    HttpStatusCode.OK,
                    jsonHeaders
                )
                request.method == HttpMethod.Get && path == "/repos/octocat/$ATTACHMENT_HOST_REPO_NAME" -> respond(
                    """{"message":"Not Found"}""",
                    HttpStatusCode.NotFound,
                    jsonHeaders
                )
                request.method == HttpMethod.Post && path == "/user/repos" -> respond(
                    """{"id":2,"name":"$ATTACHMENT_HOST_REPO_NAME","full_name":"octocat/$ATTACHMENT_HOST_REPO_NAME","private":false,"default_branch":"main","owner":{"login":"octocat"}}""",
                    HttpStatusCode.Created,
                    jsonHeaders
                )
                request.method == HttpMethod.Put && path.startsWith("/repos/octocat/$ATTACHMENT_HOST_REPO_NAME/contents/attachments/") -> {
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    uploadedBase64 = body["content"]?.jsonPrimitive?.content
                    respond(
                        """{"content":{"path":"attachments/shot.png","download_url":"https://raw.githubusercontent.com/octocat/$ATTACHMENT_HOST_REPO_NAME/main/attachments/shot.png"},"commit":{"sha":"c1"}}""",
                        HttpStatusCode.Created,
                        jsonHeaders
                    )
                }
                else -> error("Unexpected request: ${request.method.value} $path")
            }
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(jsonCodec) } }
        val rest = GitHubRestService(client)
        val payload = byteArrayOf(1, 2, 3, 4, 5)

        val uploaded = rest.uploadMediaAttachment(
            targetOwner = "upstream",
            targetRepo = "project",
            fileName = "bug shot.png",
            mimeType = "image/png",
            bytes = payload,
            viewerLoginHint = "octocat"
        )

        assertEquals(Base64.Default.encode(payload), uploadedBase64)
        assertEquals(
            "https://raw.githubusercontent.com/octocat/$ATTACHMENT_HOST_REPO_NAME/main/attachments/shot.png",
            uploaded.downloadUrl
        )
        assertEquals("![bug shot.png](${uploaded.downloadUrl})", uploaded.markdownSnippet)
        assertTrue(requests.any { it.first == HttpMethod.Post && it.second == "/user/repos" })
    }

    @Test
    fun privateRepoUploadsToOrphanBranchInsideSameRepo() = runTest {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            requests += request.method to request.url.encodedPath
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Get && path == "/repos/acme/secret" -> respond(
                    """{"id":9,"name":"secret","full_name":"acme/secret","private":true,"default_branch":"main","permissions":{"push":true}}""",
                    HttpStatusCode.OK,
                    jsonHeaders
                )
                request.method == HttpMethod.Get && path == "/repos/acme/secret/git/ref/heads/$ATTACHMENT_ORPHAN_BRANCH" -> respond(
                    """{"message":"Not Found"}""",
                    HttpStatusCode.NotFound,
                    jsonHeaders
                )
                request.method == HttpMethod.Post && path == "/repos/acme/secret/git/blobs" -> respond(
                    """{"sha":"blob1"}""",
                    HttpStatusCode.Created,
                    jsonHeaders
                )
                request.method == HttpMethod.Post && path == "/repos/acme/secret/git/trees" -> respond(
                    """{"sha":"tree1"}""",
                    HttpStatusCode.Created,
                    jsonHeaders
                )
                request.method == HttpMethod.Post && path == "/repos/acme/secret/git/commits" -> respond(
                    """{"sha":"commit1"}""",
                    HttpStatusCode.Created,
                    jsonHeaders
                )
                request.method == HttpMethod.Post && path == "/repos/acme/secret/git/refs" -> respond(
                    """{"ref":"refs/heads/$ATTACHMENT_ORPHAN_BRANCH","object":{"sha":"commit1"}}""",
                    HttpStatusCode.Created,
                    jsonHeaders
                )
                else -> error("Unexpected request: ${request.method.value} $path")
            }
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(jsonCodec) } }
        val rest = GitHubRestService(client)

        val uploaded = rest.uploadMediaAttachment(
            targetOwner = "acme",
            targetRepo = "secret",
            fileName = "repro.mp4",
            mimeType = "video/mp4",
            bytes = byteArrayOf(9, 8, 7),
            viewerLoginHint = "octocat"
        )

        assertTrue(uploaded.downloadUrl.startsWith("https://github.com/acme/secret/blob/$ATTACHMENT_ORPHAN_BRANCH/"))
        assertTrue(uploaded.downloadUrl.endsWith("?raw=true"))
        assertEquals("[repro.mp4](${uploaded.downloadUrl})", uploaded.markdownSnippet)
        assertTrue(requests.none { it.second == "/user/repos" })
    }

    @Test
    fun privateRepoWithoutPushPermissionRejectsUpload() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get && request.url.encodedPath == "/repos/acme/secret") {
                respond(
                    """{"id":9,"name":"secret","full_name":"acme/secret","private":true,"default_branch":"main","permissions":{"push":false,"pull":true}}""",
                    HttpStatusCode.OK,
                    jsonHeaders
                )
            } else {
                error("Unexpected request: ${request.method.value} ${request.url.encodedPath}")
            }
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(jsonCodec) } }
        val rest = GitHubRestService(client)

        assertFailsWith<AttachmentException.PrivateRepoPermissionDenied> {
            rest.uploadMediaAttachment(
                targetOwner = "acme",
                targetRepo = "secret",
                fileName = "leak.png",
                mimeType = "image/png",
                bytes = byteArrayOf(1, 2, 3),
                viewerLoginHint = "octocat"
            )
        }
    }
}
