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
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class JobLogTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_logs", userLogin = "octocat")
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

@OptIn(ExperimentalCoroutinesApi::class)
class JobLogStreamingTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    private suspend fun repositoryFor(engine: MockEngine): GitHubRepository {
        val sessionManager = SessionManager(JobLogTestTokenStore(), DeviceFlowClient(HttpClient(engine)))
        sessionManager.initialize()
        return GitHubRepository(sessionManager, httpClient = createClient(engine))
    }

    private fun textHeaders() = headersOf(HttpHeaders.ContentType, "text/plain; charset=utf-8")

    @Test
    fun normalLogResponseIsReturnedVerbatim() = runTest {
        val body = buildString {
            appendLine("2026-11-20T00:00:00Z Run tests ✓")
            repeat(40) { appendLine("2026-11-20T00:00:${it.toString().padStart(2, '0')}Z step $it 日本語 🚀") }
            append("BUILD SUCCESSFUL")
        }
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/repos/octocat/app/actions/jobs/9001/logs" -> respond(
                    content = body.encodeToByteArray(),
                    status = HttpStatusCode.OK,
                    headers = textHeaders()
                )
                else -> error("Unexpected request: ${request.method} ${request.url.encodedPath}")
            }
        }

        val logs = repositoryFor(engine).getJobLogs("octocat", "app", 9001L).getOrThrow()

        assertEquals(body, logs)
        assertFalse(logs.startsWith(JOB_LOG_TRUNCATION_MARKER))
    }

    @Test
    fun oversizedLogKeepsOnlyTheTrailingWindowAndMarksTruncation() = runTest {
        val body = "EARLY-CONTENT-".repeat(4_000) + "LATEST-" + "z".repeat(JOB_LOG_TAIL_CHARS - 7)
        assertTrue(body.length > JOB_LOG_TAIL_CHARS)
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/repos/octocat/app/actions/jobs/9001/logs" -> respond(
                    content = body.encodeToByteArray(),
                    status = HttpStatusCode.OK,
                    headers = textHeaders()
                )
                else -> error("Unexpected request: ${request.method} ${request.url.encodedPath}")
            }
        }

        val logs = repositoryFor(engine).getJobLogs("octocat", "app", 9001L).getOrThrow()

        assertEquals(JOB_LOG_TRUNCATION_MARKER, logs.take(JOB_LOG_TRUNCATION_MARKER.length))
        assertEquals(JOB_LOG_TRUNCATION_MARKER.length + JOB_LOG_TAIL_CHARS, logs.length)
        assertEquals(body.takeLast(JOB_LOG_TAIL_CHARS), logs.substring(JOB_LOG_TRUNCATION_MARKER.length))
        assertFalse(logs.contains("EARLY-CONTENT-"))
    }

    @Test
    fun logExactlyAtTheLimitIsNotMarked() = runTest {
        val body = "C".repeat(JOB_LOG_TAIL_CHARS)
        val engine = MockEngine { _ ->
            respond(content = body.encodeToByteArray(), status = HttpStatusCode.OK, headers = textHeaders())
        }

        val logs = repositoryFor(engine).getJobLogs("octocat", "app", 9001L).getOrThrow()

        assertEquals(body, logs)
        assertFalse(logs.startsWith(JOB_LOG_TRUNCATION_MARKER))
    }

    @Test
    fun multiByteCharactersSplitAcrossReadChunksDecodeLikeAFullBody() = runTest {
        val payload = buildString {
            append("start ")
            repeat(8) { append("日本語✓🚀 ") }
            append("end")
        }
        val bytes = payload.encodeToByteArray()
        // Vary chunk sizes so lead/continuation bytes are split at many different offsets.
        val chunkSizes = listOf(1, 2, 3, 1, 4, 2, 3)
        val engine = MockEngine { _ ->
            val channel = ByteChannel()
            var offset = 0
            var index = 0
            while (offset < bytes.size) {
                val size = minOf(chunkSizes[index % chunkSizes.size], bytes.size - offset)
                channel.writeFully(bytes, offset, offset + size)
                channel.flush()
                offset += size
                index++
            }
            channel.flushAndClose()
            respond(content = channel, status = HttpStatusCode.OK, headers = textHeaders())
        }

        val logs = repositoryFor(engine).getJobLogs("octocat", "app", 9001L).getOrThrow()

        assertEquals(payload, logs)
    }

    @Test
    fun malformedAndTruncatedUtf8DecodesLikeTheWholeBody() = runTest {
        val bytes = byteArrayOf(
            'o'.code.toByte(), 'k'.code.toByte(), 0x20,
            0xF0.toByte(), 0x9F.toByte(), // incomplete 4-byte sequence at a chunk boundary
            0x9A.toByte(), 0x80.toByte(), // remaining emoji bytes
            0x20,
            0xC3.toByte(), 0x28, // invalid 2-byte sequence
            0x20,
            0xE3.toByte() // truncated 3-byte sequence at EOF
        )
        val engine = MockEngine { _ ->
            val channel = ByteChannel()
            channel.writeFully(bytes, 0, 5)
            channel.flush()
            channel.writeFully(bytes, 5, bytes.size)
            channel.flushAndClose()
            respond(content = channel, status = HttpStatusCode.OK, headers = textHeaders())
        }

        val logs = repositoryFor(engine).getJobLogs("octocat", "app", 9001L).getOrThrow()

        assertEquals(bytes.decodeToString(), logs)
    }

    @Test
    fun nonSuccessStatusKeepsTheExistingApiExceptionContract() = runTest {
        val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.Gone) }

        val result = repositoryFor(engine).getJobLogs("octocat", "app", 9001L)

        val error = result.exceptionOrNull() as GitHubApiException
        assertEquals(410, error.statusCode)
        assertEquals("Failed to load job logs (410)", error.message)
    }

    @Test
    fun boundedTailKeepsTheLastWindowWhenAppendsWrap() {
        val tail = BoundedCharTail(5)
        for (chunk in listOf("ab", "cde", "fgh", "ijkl")) tail.append(chunk)
        assertEquals(12L, tail.totalChars)
        assertEquals("hijkl", tail.text())

        tail.append("mn")
        assertEquals("jklmn", tail.text())

        val oversizedChunk = BoundedCharTail(3)
        oversizedChunk.append("abcdef")
        oversizedChunk.append("gh")
        assertEquals("fgh", oversizedChunk.text())
    }

    @Test
    fun utf8SplitAtEveryByteBoundaryMatchesFullDecode() {
        val text = "aé日本語🚀z"
        val bytes = text.encodeToByteArray()
        for (split in 1 until bytes.size) {
            val decoder = Utf8ChunkDecoder()
            val decoded = StringBuilder()
            decoder.feed(bytes, split) { decoded.append(it) }
            decoder.feed(bytes.copyOfRange(split, bytes.size), bytes.size - split) { decoded.append(it) }
            decoder.finish { decoded.append(it) }
            assertEquals(text, decoded.toString(), "split at byte $split")
        }
    }

    @Test
    fun cancellingAStreamingReadPropagatesAndDoesNotReturnPartialText() = runTest {
        val partial = "partial log line".encodeToByteArray()
        // Leave the body open so the read stays suspended inside the streaming loop.
        val channel = ByteChannel()
        channel.writeFully(partial, 0, partial.size)
        channel.flush()
        val engine = MockEngine { _ ->
            respond(content = channel, status = HttpStatusCode.OK, headers = textHeaders())
        }
        val repository = repositoryFor(engine)
        val results = mutableListOf<Result<String>>()

        val read = launch { results += repository.getJobLogs("octocat", "app", 9001L) }
        runCurrent()
        assertTrue(results.isEmpty())

        // `cancelAndJoin` only returns if the streaming loop and `HttpStatement.execute` unwind.
        read.cancelAndJoin()

        assertTrue(read.isCancelled)
        assertTrue(results.isEmpty(), "a cancelled read must not surface partial log text")
    }
}
