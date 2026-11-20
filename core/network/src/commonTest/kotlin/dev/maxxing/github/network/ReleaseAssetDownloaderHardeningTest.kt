package dev.maxxing.github.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReleaseAssetDownloaderHardeningTest {

    @Test
    fun rejectsNonHttpsUrl() = runTest {
        val client = HttpClient(MockEngine { error("Network should not be called") })
        val error = assertFailsWith<IllegalArgumentException> {
            downloadBinaryAsset(
                url = "http://github.com/octo/app/releases/download/v1/app.apk",
                httpClient = client,
                expectedSizeBytes = 10L
            ) { _, _, _, _ -> }
        }
        assertTrue(error.message?.contains("HTTPS", ignoreCase = true) == true)
    }

    @Test
    fun rejectsHeaderSizeMismatchAgainstExpectedSize() = runTest {
        val engine = MockEngine {
            respond(
                content = "abc",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, "3")
            )
        }
        val client = HttpClient(engine)
        assertFailsWith<IllegalStateException> {
            downloadBinaryAsset(
                url = "https://github.com/octo/app/releases/download/v1/app.apk",
                httpClient = client,
                expectedSizeBytes = 10L
            ) { _, _, _, _ -> }
        }
    }

    @Test
    fun rejectsTruncatedStreamOnEarlyEof() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(byteArrayOf(1, 2, 3)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, "10")
            )
        }
        val client = HttpClient(engine)
        assertFailsWith<IllegalStateException> {
            downloadBinaryAsset(
                url = "https://github.com/octo/app/releases/download/v1/app.apk",
                httpClient = client,
                expectedSizeBytes = null
            ) { _, _, _, _ -> }
        }
    }
}
