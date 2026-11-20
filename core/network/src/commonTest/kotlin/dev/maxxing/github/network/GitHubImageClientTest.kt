package dev.maxxing.github.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GitHubImageClientTest {
    @Test
    fun authenticatesOnlyTrustedHttpsOrigins() = runTest {
        val engine = MockEngine { respond("image") }
        val client = createGitHubImageHttpClient(HttpClient(engine)) { "token" }
        val cases = mapOf(
            "https://github.com/image" to "Bearer token",
            "https://api.github.com/image" to "Bearer token",
            "https://raw.githubusercontent.com/image" to "Bearer token",
            "https://raw.githubusercontent.com:443/image" to "Bearer token",
            "http://github.com/image" to null,
            "http://api.github.com/image" to null,
            "http://raw.githubusercontent.com/image" to null,
            "https://raw.githubusercontent.com:8443/image" to null,
            "https://example.com/image" to null,
            "https://github.com.example.com/image" to null
        )
        try {
            for ((url, authorization) in cases) {
                client.get(url)
                val lastHeaders = engine.requestHistory.last().headers
                assertEquals(authorization, lastHeaders[HttpHeaders.Authorization], url)
                assertEquals(
                    "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
                    lastHeaders[HttpHeaders.Accept],
                    url
                )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun authenticatesEachRedirectOrigin() = runTest {
        val redirects = mapOf(
            "https://github.com/image" to "https://example.com/image",
            "https://example.com/image" to "https://raw.githubusercontent.com/image",
            "https://raw.githubusercontent.com/image" to "https://raw.githubusercontent.com:8443/image"
        )
        val engine = MockEngine { request ->
            val location = redirects[request.url.toString()]
            if (location == null) {
                respond("image")
            } else {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, location))
            }
        }
        val client = createGitHubImageHttpClient(HttpClient(engine)) { "token" }
        try {
            client.get("https://github.com/image")
            assertEquals(
                listOf("Bearer token", null, "Bearer token", null),
                engine.requestHistory.map { it.headers[HttpHeaders.Authorization] }
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun authenticatesOnlyAfterHttpUpgrade() = runTest {
        val engine = MockEngine { request ->
            if (request.url.protocol.name == "http") {
                respond(
                    "", HttpStatusCode.MovedPermanently,
                    headersOf(HttpHeaders.Location, "https://raw.githubusercontent.com/image")
                )
            } else {
                respond("image")
            }
        }
        val client = createGitHubImageHttpClient(HttpClient(engine)) { "token" }
        try {
            client.get("http://raw.githubusercontent.com/image")
            assertEquals(
                listOf(null, "Bearer token"),
                engine.requestHistory.map { it.headers[HttpHeaders.Authorization] }
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun rejectsHttpsDowngrade() = runTest {
        val engine = MockEngine {
            respond(
                "", HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "http://raw.githubusercontent.com/image")
            )
        }
        val client = createGitHubImageHttpClient(HttpClient(engine)) { "token" }
        try {
            val response = client.get("https://raw.githubusercontent.com/image")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(1, engine.requestHistory.size)
        } finally {
            client.close()
        }
    }

    @Test
    fun usesCurrentSessionToken() = runTest {
        var token: String? = null
        val engine = MockEngine { respond("image") }
        val client = createGitHubImageHttpClient(HttpClient(engine)) { token }
        try {
            client.get("https://github.com/image")
            token = "token"
            client.get("https://github.com/image")
            token = "replacement-token"
            client.get("https://github.com/image")
            token = null
            client.get("https://github.com/image")
            assertEquals(
                listOf(null, "Bearer token", "Bearer replacement-token", null),
                engine.requestHistory.map { it.headers[HttpHeaders.Authorization] }
            )
        } finally {
            client.close()
        }
    }
}
