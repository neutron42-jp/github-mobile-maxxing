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
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val LEFT_COMPOSITION_MESSAGE = "The coroutine was canceled because it left the composition"

private class CancellationTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_cancel", userLogin = "octocat")
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
class CoroutineCancellationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    private suspend fun createRepository(engine: MockEngine): GitHubRepository {
        val http = createClient(engine)
        val sessionManager = SessionManager(CancellationTestTokenStore(), DeviceFlowClient(http))
        sessionManager.initialize()
        return GitHubRepository(sessionManager, httpClient = http)
    }

    @Test
    fun notificationsReadPropagatesLeftCompositionCancellationWithoutCallingOnFailure() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val holdRequest = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/notifications") {
                requestStarted.complete(Unit)
                holdRequest.await()
                respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val repo = createRepository(engine)

        var onFailureCalled = false
        var onSuccessCalled = false
        var caughtCancellation: CancellationException? = null

        val job = launch {
            try {
                repo.getNotifications(forceRefresh = true)
                    .onSuccess { onSuccessCalled = true }
                    .onFailure { onFailureCalled = true }
            } catch (e: CancellationException) {
                caughtCancellation = e
                throw e
            }
        }

        requestStarted.await()
        job.cancel(CancellationException(LEFT_COMPOSITION_MESSAGE))
        job.join()

        assertTrue(job.isCancelled)
        assertNotNull(caughtCancellation)
        assertFalse(onFailureCalled)
        assertFalse(onSuccessCalled)
    }

    @Test
    fun issueDetailReadPropagatesLeftCompositionCancellationWithoutCallingOnFailure() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val holdRequest = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/graphql") {
                requestStarted.complete(Unit)
                holdRequest.await()
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val repo = createRepository(engine)

        var onFailureCalled = false
        var caughtCancellation: CancellationException? = null

        val job = launch {
            try {
                repo.getIssueOrPullDetail("octocat", "app", 77, forceRefresh = true)
                    .onFailure { onFailureCalled = true }
            } catch (e: CancellationException) {
                caughtCancellation = e
                throw e
            }
        }

        requestStarted.await()
        job.cancel(CancellationException(LEFT_COMPOSITION_MESSAGE))
        job.join()

        assertTrue(job.isCancelled)
        assertNotNull(caughtCancellation)
        assertFalse(onFailureCalled)
    }

    @Test
    fun getViewerRepositoriesCancellationDoesNotTriggerRestFallback() = runTest {
        // The cancellation comes out of the GraphQL request while the caller is still active, so a
        // catch-all around the GraphQL call would go on to the REST fallback (and the handler would see it).
        var restFallbackCalled = false
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/graphql") {
                throw CancellationException(LEFT_COMPOSITION_MESSAGE)
            } else {
                restFallbackCalled = true
                respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val repo = createRepository(engine)

        var onFailureCalled = false
        var caughtCancellation: CancellationException? = null
        try {
            repo.getViewerRepositories(forceRefresh = true)
                .onFailure { onFailureCalled = true }
        } catch (e: CancellationException) {
            caughtCancellation = e
        }

        assertNotNull(caughtCancellation)
        assertFalse(restFallbackCalled)
        assertFalse(onFailureCalled)
    }

    @Test
    fun readFailureAfterCallerCancellationIsReportedAsCancellation() = runTest {
        // Some engines surface a cancelled call as an ordinary exception (OkHttp: IOException("Canceled")).
        // Throw it outside the engine, so Ktor passes it through unchanged.
        var callerJob: Job? = null
        val engine = MockEngine { request -> error("Unexpected request: ${request.url.encodedPath}") }
        val http = createClient(engine)
        http.plugin(HttpSend).intercept { _ ->
            callerJob!!.cancel(CancellationException(LEFT_COMPOSITION_MESSAGE))
            throw IllegalStateException("Canceled")
        }
        val sessionManager = SessionManager(CancellationTestTokenStore(), DeviceFlowClient(http))
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = http)

        var onFailureCalled = false
        var caughtCancellation: CancellationException? = null
        val job = launch(start = CoroutineStart.LAZY) {
            try {
                repo.getNotifications(forceRefresh = true)
                    .onFailure { onFailureCalled = true }
            } catch (e: CancellationException) {
                caughtCancellation = e
                throw e
            }
        }
        callerJob = job
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertNotNull(caughtCancellation)
        assertFalse(onFailureCalled)
    }

    @Test
    fun postCommentCompletesRequestAndCacheInvalidationThenPropagatesCancellation() = runTest {
        val postStarted = CompletableDeferred<Unit>()
        val allowPostResponse = CompletableDeferred<Unit>()
        var postCompleted = false

        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/graphql" -> respond(
                    content = """
                        {
                          "data": {
                            "repository": {
                              "viewerPermission": "ADMIN",
                              "issueOrPullRequest": {
                                "__typename": "Issue",
                                "id": "I_77",
                                "number": 77,
                                "title": "Sample issue",
                                "body": "Body text",
                                "state": "OPEN",
                                "url": "https://github.com/octocat/app/issues/77",
                                "viewerCanUpdate": true,
                                "createdAt": "2026-11-15T11:50:00Z",
                                "updatedAt": "2026-11-15T12:00:00Z",
                                "author": { "login": "octocat", "avatarUrl": "", "url": "" },
                                "labels": { "nodes": [] },
                                "assignees": { "nodes": [] },
                                "milestone": null,
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
                path == "/repos/octocat/app/issues/77/comments" && request.method == HttpMethod.Post -> {
                    postStarted.complete(Unit)
                    allowPostResponse.await()
                    postCompleted = true
                    respond("{}", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> error("Unexpected path: $path")
            }
        }
        val repo = createRepository(engine)

        // Warm issue detail cache before posting a comment so we can verify invalidation.
        repo.getIssueOrPullDetail("octocat", "app", 77).getOrThrow()
        assertNotNull(repo.peekIssueOrPullDetail("octocat", "app", 77))

        var onSuccessCalled = false
        var onFailureCalled = false
        var caughtCancellation: CancellationException? = null

        val job = launch {
            try {
                repo.postComment("octocat", "app", 77, "Hello")
                    .onSuccess { onSuccessCalled = true }
                    .onFailure { onFailureCalled = true }
            } catch (e: CancellationException) {
                caughtCancellation = e
                throw e
            }
        }

        postStarted.await()
        job.cancel(CancellationException(LEFT_COMPOSITION_MESSAGE))

        allowPostResponse.complete(Unit)
        job.join()

        assertTrue(postCompleted)
        assertNull(repo.peekIssueOrPullDetail("octocat", "app", 77))
        assertTrue(job.isCancelled)
        assertIs<CancellationException>(caughtCancellation)
        assertFalse(onSuccessCalled)
        assertFalse(onFailureCalled)
    }

    @Test
    fun postCommentFailureAfterCallerCancellationIsStillReturned() = runTest {
        val postStarted = CompletableDeferred<Unit>()
        val allowPostResponse = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/repos/octocat/app/issues/77/comments") {
                postStarted.complete(Unit)
                allowPostResponse.await()
                respond("""{"message":"Validation Failed"}""", HttpStatusCode.UnprocessableEntity, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val repo = createRepository(engine)

        var onSuccessCalled = false
        var failure: Throwable? = null
        val job = launch {
            repo.postComment("octocat", "app", 77, "Hello")
                .onSuccess { onSuccessCalled = true }
                .onFailure { failure = it }
        }

        postStarted.await()
        job.cancel(CancellationException(LEFT_COMPOSITION_MESSAGE))
        allowPostResponse.complete(Unit)
        job.join()

        assertFalse(onSuccessCalled)
        assertNotNull(failure)
        assertFalse(failure is CancellationException)
    }

    @Test
    fun postCommentFailureStillReturnsResultFailureWhenNotCancelled() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/repos/octocat/app/issues/77/comments") {
                respond("""{"message":"Server Error"}""", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val repo = createRepository(engine)

        var failureMessage: String? = null
        val result = repo.postComment("octocat", "app", 77, "Hello")
            .onFailure { failureMessage = it.message }

        assertTrue(result.isFailure)
        assertNotNull(failureMessage)
    }
}
