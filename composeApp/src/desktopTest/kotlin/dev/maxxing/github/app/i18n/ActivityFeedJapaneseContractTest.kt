package dev.maxxing.github.app.i18n

import dev.maxxing.github.model.TimelineEvent
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
 * The network layer builds English activity/timeline phrases and AppStrings parses them back for Japanese.
 * These tests run the real producers (GitHubRestService / GitHubGraphQlService) and pin each English
 * phrase to its Japanese line, so a producer wording change fails here instead of silently falling back.
 */
class ActivityFeedJapaneseContractTest {

    private data class Case(val type: String, val payload: String, val english: String, val japanese: String)

    private val cases = listOf(
        Case("PushEvent", """{"ref":"refs/heads/feature/ui","size":2,"commits":[{"sha":"abc","message":"m"}]}""",
            "pushed 2 commits to feature/ui", "@alice が feature/ui に 2 件のコミットをプッシュしました"),
        Case("PushEvent", """{"size":1}""",
            "pushed 1 commit to main", "@alice が main に 1 件のコミットをプッシュしました"),
        Case("PullRequestEvent", """{"action":"opened","number":5,"pull_request":{"number":5,"title":"t"}}""",
            "opened pull request #5", "@alice がプルリクエスト #5 を作成しました"),
        Case("PullRequestEvent", """{"action":"closed","pull_request":{"number":77,"merged_at":"2026-11-15T08:00:00Z"}}""",
            "merged pull request #77", "@alice がプルリクエスト #77 をマージしました"),
        Case("PullRequestEvent", """{"action":"closed","pull_request":{"number":6}}""",
            "closed pull request #6", "@alice がプルリクエスト #6 をクローズしました"),
        Case("PullRequestEvent", """{"action":"reopened","pull_request":{"number":6}}""",
            "reopened pull request #6", "@alice がプルリクエスト #6 を再開しました"),
        Case("PullRequestEvent", """{"action":"edited","pull_request":{"number":6}}""",
            "edited pull request #6", "@alice がプルリクエスト #6 を編集しました"),
        Case("PullRequestEvent", """{"action":"synchronize","pull_request":{"number":6}}""",
            "synchronize pull request #6", "@alice がプルリクエスト #6 を更新しました"),
        Case("PullRequestEvent", """{"action":"ready_for_review","pull_request":{"number":6}}""",
            "ready_for_review pull request #6", "@alice がプルリクエスト #6 をレビュー可能にしました"),
        Case("PullRequestEvent", """{"action":"converted_to_draft","pull_request":{"number":6}}""",
            "converted_to_draft pull request #6", "@alice がプルリクエスト #6 を下書きに変換しました"),
        Case("PullRequestEvent", """{"action":"review_requested","pull_request":{"number":6}}""",
            "review_requested pull request #6", "@alice がプルリクエスト #6 のレビューを依頼しました"),
        Case("PullRequestEvent", """{"action":"assigned","pull_request":{"number":6}}""",
            "assigned pull request #6", "@alice がプルリクエスト #6 のアサインを変更しました"),
        Case("PullRequestEvent", """{"action":"labeled","pull_request":{"number":6}}""",
            "labeled pull request #6", "@alice がプルリクエスト #6 を更新しました"),
        Case("PullRequestEvent", """{"action":"opened"}""",
            "opened pull request", "@alice がプルリクエストを作成しました"),
        Case("PullRequestEvent", """{}""",
            "updated pull request", "@alice がプルリクエストを更新しました"),
        Case("IssuesEvent", """{"action":"opened","issue":{"number":19,"title":"t"}}""",
            "opened issue #19", "@alice が Issue #19 を作成しました"),
        Case("IssuesEvent", """{"action":"closed","issue":{"number":19}}""",
            "closed issue #19", "@alice が Issue #19 をクローズしました"),
        Case("IssuesEvent", """{"action":"reopened","issue":{"number":19}}""",
            "reopened issue #19", "@alice が Issue #19 を再開しました"),
        Case("IssuesEvent", """{"action":"edited","issue":{"number":19}}""",
            "edited issue #19", "@alice が Issue #19 を編集しました"),
        Case("IssuesEvent", """{"action":"assigned","issue":{"number":19}}""",
            "assigned issue #19", "@alice が Issue #19 のアサインを変更しました"),
        Case("IssuesEvent", """{"action":"labeled","issue":{"number":19}}""",
            "labeled issue #19", "@alice が Issue #19 を更新しました"),
        Case("IssuesEvent", """{"action":"opened"}""",
            "opened issue", "@alice が Issue を作成しました"),
        Case("IssueCommentEvent", """{"issue":{"number":9,"title":"t"}}""",
            "commented on #9", "@alice が #9 にコメントしました"),
        Case("IssueCommentEvent", """{}""",
            "commented on thread", "@alice がスレッドにコメントしました"),
        Case("PullRequestReviewEvent", """{"pull_request":{"number":88},"review":{"state":"approved"}}""",
            "approved pull request #88", "@alice がプルリクエスト #88 を承認しました"),
        Case("PullRequestReviewEvent", """{"pull_request":{"number":88},"review":{"state":"changes_requested"}}""",
            "requested changes on #88", "@alice がプルリクエスト #88 に変更を要求しました"),
        Case("PullRequestReviewEvent", """{"pull_request":{"number":88},"review":{"state":"commented"}}""",
            "reviewed pull request #88", "@alice がプルリクエスト #88 をレビューしました"),
        Case("PullRequestReviewEvent", """{"review":{"state":"approved"}}""",
            "approved pull request", "@alice がプルリクエストを承認しました"),
        Case("PullRequestReviewEvent", """{"review":{"state":"changes_requested"}}""",
            "requested changes on", "@alice がプルリクエストに変更を要求しました"),
        Case("PullRequestReviewCommentEvent", """{"pull_request":{"number":88}}""",
            "reviewed pull request #88", "@alice がプルリクエスト #88 をレビューしました"),
        Case("WatchEvent", """{"action":"started"}""",
            "starred repository", "@alice がリポジトリにスターを付けました"),
        Case("ForkEvent", """{"forkee":{"full_name":"alice/app"}}""",
            "forked repository", "@alice がリポジトリをフォークしました"),
        Case("CreateEvent", """{"ref_type":"branch","ref":"dev"}""",
            "created branch dev", "@alice がブランチ dev を作成しました"),
        Case("CreateEvent", """{"ref_type":"tag","ref":"v1.0.0"}""",
            "created tag v1.0.0", "@alice がタグ v1.0.0 を作成しました"),
        Case("CreateEvent", """{"ref_type":"repository"}""",
            "created repository", "@alice がリポジトリを作成しました"),
        Case("CreateEvent", """{"ref":"x"}""",
            "created ref x", "@alice が参照 x を作成しました"),
        Case("DeleteEvent", """{"ref_type":"branch","ref":"old"}""",
            "deleted branch old", "@alice がブランチ old を削除しました"),
        Case("DeleteEvent", """{"ref_type":"tag","ref":"v0.1.0"}""",
            "deleted tag v0.1.0", "@alice がタグ v0.1.0 を削除しました"),
        Case("DeleteEvent", """{"ref_type":"branch"}""",
            "deleted branch", "@alice がブランチを削除しました"),
        Case("ReleaseEvent", """{"action":"published","release":{"tag_name":"v2.0.0","name":"n"}}""",
            "published release v2.0.0", "@alice がリリース v2.0.0 を公開しました"),
        Case("ReleaseEvent", """{"action":"created","release":{"tag_name":"v2.0.0"}}""",
            "created release v2.0.0", "@alice がリリース v2.0.0 を作成しました"),
        Case("ReleaseEvent", """{"action":"prereleased","release":{"tag_name":"v3-rc"}}""",
            "prereleased release v3-rc", "@alice がプレリリース v3-rc を公開しました"),
        Case("ReleaseEvent", """{"action":"released","release":{"tag_name":"v2.0.0"}}""",
            "released release v2.0.0", "@alice がリリース v2.0.0 を公開しました"),
        Case("ReleaseEvent", """{"action":"edited","release":{"tag_name":"v2.0.0"}}""",
            "edited release v2.0.0", "@alice がリリース v2.0.0 を編集しました"),
        Case("ReleaseEvent", """{"action":"deleted","release":{"tag_name":"v2.0.0"}}""",
            "deleted release v2.0.0", "@alice がリリース v2.0.0 を削除しました"),
        Case("ReleaseEvent", """{"action":"unpublished","release":{"tag_name":"v2.0.0"}}""",
            "unpublished release v2.0.0", "@alice がリリース v2.0.0 の公開を取り消しました"),
        Case("ReleaseEvent", """{}""",
            "published release", "@alice がリリースを公開しました"),
        Case("PublicEvent", """{}""",
            "made repository public", "@alice がリポジトリを公開しました"),
        Case("MemberEvent", """{"action":"added","member":{"login":"bob"}}""",
            "added @bob", "@alice が @bob をコラボレーターに追加しました"),
        Case("MemberEvent", """{"action":"removed","member":{"login":"bob"}}""",
            "removed @bob", "@alice が @bob をコラボレーターから削除しました"),
        Case("MemberEvent", """{"action":"edited","member":{"login":"bob"}}""",
            "edited @bob", "@alice が @bob の権限を変更しました"),
        Case("MemberEvent", """{}""",
            "added collaborator", "@alice がコラボレーターを追加しました"),
        Case("GollumEvent", """{}""",
            "Gollum", "@alice が Wiki を更新しました"),
        Case("CommitCommentEvent", """{}""",
            "CommitComment", "@alice がコミットにコメントしました"),
        Case("SponsorshipEvent", """{}""",
            "Sponsorship", "@alice のアクティビティ（Sponsorship）"),
        Case("Event", """{}""",
            "activity", "@alice のアクティビティ")
    )

    private fun clientReturning(body: String): HttpClient = HttpClient(
        MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    ) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    }

    @Test
    fun everyActivityProducerFormatHasAJapaneseLineWithTheActorFirst() = runTest {
        val body = cases.mapIndexed { i, c ->
            """{"id":"e$i","type":"${c.type}","actor":{"login":"alice","avatar_url":""},""" +
                """"repo":{"name":"octo/core"},"created_at":"2026-11-15T09:00:00Z","payload":${c.payload}}"""
        }.joinToString(",", prefix = "[", postfix = "]")

        val events = GitHubRestService(clientReturning(body)).fetchReceivedEvents("octocat")
        assertEquals(cases.size, events.size)

        events.zip(cases).forEach { (event, case) ->
            // Pins the producer's English phrase: a wording change in GitHubRestService fails here.
            assertEquals(case.english, event.summaryText, "producer phrase for ${case.type} ${case.payload}")
            assertEquals("@alice ${case.english}", EnglishStrings.formatActivityEventLine(event))
            assertEquals(case.japanese, JapaneseStrings.formatActivityEventLine(event), "Japanese line for '${case.english}'")
        }
    }

    @Test
    fun everyTimelineStateChangeHasAJapaneseLine() = runTest {
        fun node(type: String, extra: String = "") =
            """{"__typename":"$type","id":"$type","createdAt":"2026-11-15T10:00:00Z","actor":{"login":"bob","avatarUrl":"","url":""}$extra}"""
        val body = """
            {"data":{"repository":{"viewerPermission":"READ","issueOrPullRequest":{
              "__typename":"Issue","id":"I_1","number":7,"title":"t","body":"","state":"CLOSED","url":"",
              "viewerCanUpdate":false,"createdAt":"2026-11-15T10:00:00Z","updatedAt":"2026-11-15T10:00:00Z",
              "author":{"login":"bob","avatarUrl":"","url":""},"labels":{"nodes":[]},"assignees":{"nodes":[]},
              "commits":{"nodes":[]},
              "timelineItems":{"nodes":[
                ${node("ClosedEvent")},
                ${node("ReopenedEvent")},
                ${node("MergedEvent", ",\"mergeRefName\":\"main\"")},
                ${node("MergedEvent")}
              ]}
            }}}}
        """.trimIndent()

        val detail = GitHubGraphQlService(clientReturning(body)).fetchIssueOrPullDetail("octo", "core", 7)
        val labels = detail.timeline.filterIsInstance<TimelineEvent.StateChangeEvent>().map { it.actionLabel }
        assertEquals(listOf("closed this", "reopened this", "merged into main", "merged into base"), labels)

        assertEquals(
            listOf("@bob closed this", "@bob reopened this", "@bob merged into main", "@bob merged into base"),
            labels.map { EnglishStrings.formatTimelineLine("bob", it) }
        )
        assertEquals(
            listOf("@bob がクローズしました", "@bob が再開しました", "@bob が main にマージしました", "@bob がベースブランチにマージしました"),
            labels.map { JapaneseStrings.formatTimelineLine("bob", it) }
        )
    }
}
