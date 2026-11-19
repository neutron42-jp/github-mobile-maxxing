package dev.maxxing.github.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
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

        // Different host, scheme, port, or embedded userinfo must be rejected
        assertNull(parseNextLinkUrl(listOf("""<https://evil.example/files?page=2>; rel="next""""), base))
        assertNull(parseNextLinkUrl(listOf("""<http://api.github.com/files?page=2>; rel="next""""), base))
        assertNull(parseNextLinkUrl(listOf("""<https://api.github.com:8443/files?page=2>; rel="next""""), base))
        assertNull(parseNextLinkUrl(listOf("""<https://user@api.github.com/files?page=2>; rel="next""""), base))
        assertNull(parseNextLinkUrl(listOf("""<https://user:pass@api.github.com/files?page=2>; rel="next""""), base))

        // No rel="next" returns null
        assertNull(parseNextLinkUrl(listOf("""<https://api.github.com/files?page=1>; rel="prev""""), base))
    }

    /**
     * Serves one file per page. [nextPageFor] returns the page number advertised as `rel="next"`
     * for the given page, or null to end pagination.
     */
    private fun pagingEngine(requestedUrls: MutableList<String>, nextPageFor: (Int) -> Int?) = MockEngine { request ->
        requestedUrls.add(request.url.toString())
        val page = request.url.parameters["page"]?.toIntOrNull() ?: 1
        val next = nextPageFor(page)
        val headers = if (next != null) {
            headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                HttpHeaders.Link to listOf(
                    """<https://api.github.com/repos/octo/app/pulls/9/files?per_page=100&page=$next>; rel="next""""
                )
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

    @Test
    fun fetchPullFilesFollowsNextLinksUntilExhausted() = runTest {
        val requestedUrls = mutableListOf<String>()
        val engine = pagingEngine(requestedUrls) { page -> if (page < 5) page + 1 else null }

        val rest = GitHubRestService(createClient(engine), baseUrl = "https://api.github.com")
        val files = rest.fetchPullFiles("octo", "app", 9)

        assertEquals((1..5).map { "file_$it.kt" }, files.map { it.filename })
        assertEquals(5, requestedUrls.size)
        assertEquals("100", Url(requestedUrls.first()).parameters["per_page"])
    }

    @Test
    fun fetchPullFilesStopsAtPageCapWhenNextNeverEnds() = runTest {
        val requestedUrls = mutableListOf<String>()
        val engine = pagingEngine(requestedUrls) { page -> page + 1 }

        val rest = GitHubRestService(createClient(engine), baseUrl = "https://api.github.com")
        val files = rest.fetchPullFiles("octo", "app", 9)

        assertEquals(30, MAX_PULL_FILES_PAGES)
        assertEquals(MAX_PULL_FILES_PAGES, requestedUrls.size)
        assertEquals((1..MAX_PULL_FILES_PAGES).map { "file_$it.kt" }, files.map { it.filename })
    }

    @Test
    fun fetchPullFilesStopsWhenNextLinkRepeatsAVisitedUrl() = runTest {
        val requestedUrls = mutableListOf<String>()
        // Every page (including page 2 itself) claims page 2 is next.
        val engine = pagingEngine(requestedUrls) { 2 }

        val rest = GitHubRestService(createClient(engine), baseUrl = "https://api.github.com")
        val files = rest.fetchPullFiles("octo", "app", 9)

        assertEquals(listOf("file_1.kt", "file_2.kt"), files.map { it.filename })
        assertEquals(2, requestedUrls.size)
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
