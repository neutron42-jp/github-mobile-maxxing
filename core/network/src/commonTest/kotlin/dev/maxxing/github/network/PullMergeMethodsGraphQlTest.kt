package dev.maxxing.github.network

import dev.maxxing.github.model.MergeMethod
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

class PullMergeMethodsGraphQlTest {

    @Test
    fun fetchIssueOrPullDetailReadsAllowedMergeMethods() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "data": {
                        "repository": {
                          "viewerPermission": "ADMIN",
                          "mergeCommitAllowed": true,
                          "squashMergeAllowed": false,
                          "rebaseMergeAllowed": true,
                          "issueOrPullRequest": {
                            "__typename": "PullRequest",
                            "id": "PR_99",
                            "number": 99,
                            "title": "Rebase or merge only",
                            "body": "",
                            "state": "OPEN",
                            "isDraft": false,
                            "url": "https://github.com/octo/app/pull/99",
                            "viewerCanUpdate": true,
                            "createdAt": "2026-11-20T00:00:00Z",
                            "updatedAt": "2026-11-20T00:00:00Z",
                            "mergeable": "MERGEABLE",
                            "mergeStateStatus": "CLEAN",
                            "author": { "login": "octo", "avatarUrl": "" },
                            "labels": { "nodes": [] },
                            "assignees": { "nodes": [] },
                            "reviewRequests": { "nodes": [] },
                            "reactionGroups": [],
                            "timelineItems": { "nodes": [] }
                          }
                        }
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
        val gql = GitHubGraphQlService(client)
        val detail = gql.fetchIssueOrPullDetail("octo", "app", 99)
        assertEquals(listOf(MergeMethod.MERGE, MergeMethod.REBASE), detail.allowedMergeMethods)
    }
}
