package dev.maxxing.github.network

import dev.maxxing.github.auth.DeviceFlowClient
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.NotificationSubjectType
import dev.maxxing.github.model.NotificationThread
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
import kotlin.test.assertTrue

private class RetentionTestTokenStore(
    initialMarkers: Map<String, String>
) : TokenStore {
    var session: AuthSession? = AuthSession("tok", userLogin = "octo")
    var markers: Map<String, String> = initialMarkers
    var prefs = NotificationPreferences(osNotificationsEnabled = true, urgentOnly = false, pollIntervalMinutes = 1)

    override suspend fun saveSession(session: AuthSession) { this.session = session }
    override suspend fun loadSession() = session
    override suspend fun clearSession() { session = null }
    override suspend fun saveClientId(clientId: String) {}
    override suspend fun loadClientId(): String? = null
    override suspend fun saveThemePreferences(prefs: ThemePreferences) {}
    override suspend fun loadThemePreferences() = ThemePreferences()
    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) { this.prefs = prefs }
    override suspend fun loadNotificationPreferences() = prefs
    override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) { this.markers = markers }
    override suspend fun loadSeenNotificationMarkers() = markers
}

class NotificationMonitorRetentionTest {

    @Test
    fun markThreadsSeenPreservesUnrelatedMarkersAboveTrimThreshold() = runTest {
        val initialMarkers = buildMap {
            put(NotificationMonitor.BASELINE_MARKER_KEY, "1")
            for (i in 1..205) {
                val padded = i.toString().padStart(3, '0')
                put("thread-$padded", "2026-11-10T00:0${padded.takeLast(1)}:00Z")
            }
            put("other-important-thread", "2026-11-20T09:00:00Z")
        }
        val tokenStore = RetentionTestTokenStore(initialMarkers)

        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/notifications") {
                respond(
                    content = """
                        [
                          {
                            "id": "other-important-thread",
                            "unread": true,
                            "reason": "mention",
                            "updated_at": "2026-11-20T09:00:00Z",
                            "subject": {
                              "title": "Existing notification",
                              "type": "Issue",
                              "url": "https://api.github.com/repos/octo/app/issues/1"
                            },
                            "repository": {
                              "name": "app",
                              "full_name": "octo/app",
                              "private": false,
                              "owner": { "login": "octo", "avatar_url": "" }
                            }
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
        val sessionManager = SessionManager(tokenStore, DeviceFlowClient(HttpClient(engine)))
        sessionManager.initialize()
        val repository = GitHubRepository(sessionManager = sessionManager, httpClient = client)
        val monitor = NotificationMonitor(sessionManager, repository)

        monitor.markThreadsSeen(
            listOf(
                NotificationThread(
                    id = "active-1",
                    unread = true,
                    reason = NotificationReason.COMMENT,
                    updatedAt = "2026-11-20T10:00:00Z",
                    subjectTitle = "Active",
                    subjectType = NotificationSubjectType.ISSUE,
                    subjectUrl = "https://api.github.com/repos/octo/app/issues/2",
                    latestCommentUrl = null,
                    repoFullName = "octo/app",
                    repoOwner = "octo",
                    repoName = "app"
                )
            )
        )

        assertTrue(tokenStore.markers.containsKey(NotificationMonitor.BASELINE_MARKER_KEY))
        assertTrue(tokenStore.markers.containsKey("active-1"))
        assertTrue(tokenStore.markers.containsKey("other-important-thread"))

        val result = monitor.checkForNewNotifications(suppressOsAlerts = false).getOrThrow()
        assertEquals(0, result.newlyArrivedForOs.size)
    }
}
