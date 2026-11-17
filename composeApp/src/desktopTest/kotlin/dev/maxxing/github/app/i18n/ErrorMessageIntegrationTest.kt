package dev.maxxing.github.app.i18n

import dev.maxxing.github.network.GitHubGraphQlService
import dev.maxxing.github.network.GitHubRestService
import dev.maxxing.github.ui.i18n.EnglishStrings
import dev.maxxing.github.ui.i18n.JapaneseStrings
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

/**
 * Runs real network/auth failures through AppStrings.errorMessage, so the HttpStatusError link between
 * GitHubApiException and the localized text is covered end to end.
 */
class ErrorMessageIntegrationTest {

    private fun clientFailingWith(status: HttpStatusCode) = HttpClient(
        MockEngine {
            respond(
                content = """{"message":"error"}""",
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    ) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    }

    @Test
    fun gistNotFoundShowsNetworkDetailInEnglishAndScopeHintInJapanese() = runTest {
        val error = runCatching { GitHubRestService(clientFailingWith(HttpStatusCode.NotFound)).fetchGists() }
            .exceptionOrNull()!!

        assertEquals(
            "Failed to load gists (404). Your token may lack the 'gist' scope — sign out and sign in again (or add 'gist' scope).",
            EnglishStrings.errorMessage(error, EnglishStrings.failedLoadGists, EnglishStrings.gistScopeErrorHint)
        )
        assertEquals(
            "Gist の読み込みに失敗しました（HTTP\u00A0404）。${JapaneseStrings.gistScopeErrorHint}\n詳細: ${error.message}",
            JapaneseStrings.errorMessage(error, JapaneseStrings.failedLoadGists, JapaneseStrings.gistScopeErrorHint)
        )
    }

    @Test
    fun serverErrorShowsStatusCodeWithoutScopeHintInJapanese() = runTest {
        val error = runCatching { GitHubRestService(clientFailingWith(HttpStatusCode.InternalServerError)).fetchGists() }
            .exceptionOrNull()!!

        assertEquals("Failed to load gists (500)", EnglishStrings.errorMessage(error, EnglishStrings.failedLoadGists))
        assertEquals(
            "Gist の読み込みに失敗しました（HTTP\u00A0500）\n詳細: Failed to load gists (500)",
            JapaneseStrings.errorMessage(error, JapaneseStrings.failedLoadGists, JapaneseStrings.gistScopeErrorHint)
        )
    }

    @Test
    fun graphQlErrorsDoNotShowAMadeUpHttpStatusInJapanese() = runTest {
        val body = """{"data":null,"errors":[{"message":"Resource not accessible by personal access token"}]}"""
        val http = HttpClient(
            MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
        val error = runCatching { GitHubGraphQlService(http).fetchViewerProfile() }.exceptionOrNull()!!

        assertEquals("Resource not accessible by personal access token", EnglishStrings.errorMessage(error, EnglishStrings.failedLoadRepos))
        assertEquals(
            "${JapaneseStrings.failedLoadRepos}\n詳細: Resource not accessible by personal access token",
            JapaneseStrings.errorMessage(error, JapaneseStrings.failedLoadRepos)
        )
    }
}
