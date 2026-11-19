package dev.maxxing.github.network

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
import kotlin.test.assertNull

class PullFilesPaginationTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    @Test
    fun parseNextLinkUrlHandlesMultipleHeadersCommasAndOriginChecks() {
        val base = "https://api.github.com"

        // URL containing a comma in query parameters and multiple rel entries
        val withComma = listOf(
            """<https://api.github.com/repos/o/r/pulls/1/files?filter=a,b&page=2>; rel="next", <https://api.github.com/repos/o/r/pulls/1/files?page=5>; rel="last""""
        )
        assertEquals(
            "https://api.github.com/repos/o/r/pulls/1/files?filter=a,b&page=2",
            parseNextLinkUrl(withComma, base)
        )

        // Multiple Link headers where next is in the second header
        val multiHeaders = listOf(
            """<https://api.github.com/repos/o/r/pulls/1/files?page=1>; rel="prev"""",
            """<https://api.github.com/repos/o/r/pulls/1/files?page=3>; REL="NEXT""""
        )
        assertEquals(
            "https://api.github.com/repos/o/r/pulls/1/files?page=3",
            parseNextLinkUrl(multiHeaders, base)
        )

        // Different host, scheme, or port must be rejected
        assertNull(parseNextLinkUrl(listOf("""<https://evil.example/files?page=2>; rel="next""""), base))
        assertNull(parseNextLinkUrl(listOf("""<http://api.github.com/files?page=2>; rel="next""""), base))
        assertNull(parseNextLinkUrl(listOf("""<https://api.github.com:8443/files?page=2>; rel="next""""), base))

        // No rel="next" returns null
        assertNull(parseNextLinkUrl(listOf("""<https://api.github.com/files?page=1>; rel="prev""""), base))
    }

    @Test
    fun fetchPullFilesPaginatesUpToThreePagesAndStops() = runTest {
        val requestedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            val url = request.url.toString()
            requestedUrls.add(url)
            val page = request.url.parameters["page"]?.toIntOrNull() ?: 1
            val nextHeader = if (page < 5) {
                """<https://api.github.com/repos/octo/app/pulls/9/files?per_page=100&page=${page + 1}>; rel="next""""
            } else {
                ""
            }
            val headers = if (nextHeader.isNotEmpty()) {
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.Link to listOf(nextHeader)
                )
            } else {
                headersOf(HttpHeaders.ContentType, "application/json")
            }
            respond(
                content = """[{"filename":"file_$page.kt","status":"modified","additions":1,"deletions":0}]""",
                status = HttpStatusCode.OK,
                headers = headers
            )
        }

        val rest = GitHubRestService(createClient(engine), baseUrl = "https://api.github.com")
        val files = rest.fetchPullFiles("octo", "app", 9)

        assertEquals(listOf("file_1.kt", "file_2.kt", "file_3.kt"), files.map { it.filename })
        assertEquals(3, requestedUrls.size)
    }

    @Test
    fun fetchPullFilesStopsWhenNextLinkPointsToDifferentOrigin() = runTest {
        val requestedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedUrls.add(request.url.toString())
            respond(
                content = """[{"filename":"only_page.kt","status":"added","additions":5,"deletions":0}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.Link to listOf("""<https://attacker.example/steal?page=2>; rel="next"""")
                )
            )
        }

        val rest = GitHubRestService(createClient(engine), baseUrl = "https://api.github.com")
        val files = rest.fetchPullFiles("octo", "app", 9)

        assertEquals(listOf("only_page.kt"), files.map { it.filename })
        assertEquals(1, requestedUrls.size)
    }
}
