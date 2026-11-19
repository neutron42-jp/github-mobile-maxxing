package dev.maxxing.github.auth

import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.DeviceCodeState
import dev.maxxing.github.model.DeviceFlowErrorKind
import dev.maxxing.github.model.DeviceFlowWaitReason
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeviceFlowClientTest {

    private fun createMockClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    @Test
    fun deviceFlowHandlesPendingSlowDownAndSuccess() = runTest {
        var tokenPollAttempt = 0
        val recordedDelays = mutableListOf<Long>()
        var requestedDeviceScope: String? = null
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when (path) {
                "/login/device/code" -> {
                    requestedDeviceScope = (request.body as? io.ktor.client.request.forms.FormDataContent)
                        ?.formData?.get("scope")
                    respond(
                        content = """{"device_code":"dev_123","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/login/oauth/access_token" -> {
                    tokenPollAttempt++
                    val payload = when (tokenPollAttempt) {
                        1 -> """{"error":"authorization_pending"}"""
                        2 -> """{"error":"slow_down","interval":10}"""
                        else -> """{"access_token":"gho_test_token","token_type":"bearer","scope":"repo gist notifications"}"""
                    }
                    respond(
                        content = payload,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/user" -> respond(
                    content = """{"login":"octocat"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path: $path")
            }
        }

        val client = DeviceFlowClient(
            httpClient = createMockClient(engine),
            delayMillis = { ms -> recordedDelays.add(ms) }
        )

        val states = client.startDeviceFlow("Ov23liTestClient").toList()

        assertIs<DeviceCodeState.RequestingCode>(states[0])
        val sentScopes = requestedDeviceScope?.split(' ').orEmpty()
        assertTrue("gist" in sentScopes)
        assertTrue("user:follow" in sentScopes)
        assertTrue("gist" in DeviceFlowClient.DEFAULT_OAUTH_SCOPE.split(' '))
        assertTrue("user:follow" in DeviceFlowClient.DEFAULT_OAUTH_SCOPE.split(' '))
        val firstAwait = assertIs<DeviceCodeState.AwaitingUser>(states[1])
        assertEquals("ABCD-EFGH", firstAwait.userCode)
        // Typed reasons let the UI localize the English status text.
        assertEquals(
            listOf(
                DeviceFlowWaitReason.CODE_ISSUED,
                DeviceFlowWaitReason.AUTHORIZATION_PENDING,
                DeviceFlowWaitReason.SLOW_DOWN
            ),
            states.filterIsInstance<DeviceCodeState.AwaitingUser>().map { it.waitReason }
        )
        assertEquals("Rate limited by GitHub; polling every 10s...", states.filterIsInstance<DeviceCodeState.AwaitingUser>().last().statusMessage)
        assertEquals(listOf(5000L, 5000L, 10000L), recordedDelays)

        val authed = assertIs<DeviceCodeState.Authenticated>(states.last())
        assertEquals("gho_test_token", authed.session.accessToken)
        assertEquals("octocat", authed.session.userLogin)
        assertEquals(AuthMethod.DEVICE_FLOW, authed.session.authMethod)
    }

    @Test
    fun deviceFlowHandlesAccessDenied() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/login/device/code" -> respond(
                    content = """{"device_code":"dev_123","user_code":"1234-5678","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/login/oauth/access_token" -> respond(
                    content = """{"error":"access_denied"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path")
            }
        }

        val client = DeviceFlowClient(
            httpClient = createMockClient(engine),
            delayMillis = {}
        )
        val states = client.startDeviceFlow("Ov23liTestClient").toList()
        val errorState = assertIs<DeviceCodeState.Error>(states.last())
        assertTrue(errorState.message.contains("denied", ignoreCase = true))
        assertEquals(DeviceFlowErrorKind.DENIED, errorState.kind)
    }

    @Test
    fun verifyPersonalAccessTokenChecksAuth() = runTest {
        val engine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization]
            if (auth == "Bearer ghp_valid") {
                respond(
                    content = """{"login":"neutron42"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"message":"Bad credentials"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val client = DeviceFlowClient(httpClient = createMockClient(engine))
        val ok = client.verifyPersonalAccessToken("ghp_valid")
        assertTrue(ok.isSuccess)
        assertEquals("neutron42", ok.getOrThrow().userLogin)

        val bad = client.verifyPersonalAccessToken("ghp_bad")
        assertTrue(bad.isFailure)
        val error = bad.exceptionOrNull()
        assertEquals("GitHub returned HTTP 401. Check token scopes.", error?.message)
    }

    private val deviceCodeJson =
        """{"device_code":"dev_123","user_code":"1234-5678","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""

    /** Runs the device flow against a mock whose token endpoint answers with [tokenPayloads] in order (last one repeats). */
    private suspend fun runFlow(
        clientId: String = "Ov23liTestClient",
        deviceCodeStatus: HttpStatusCode = HttpStatusCode.OK,
        deviceCodeBody: String = deviceCodeJson,
        failDeviceCode: Boolean = false,
        failFirstPoll: Boolean = false,
        tokenPayloads: List<String> = listOf("""{"error":"authorization_pending"}""")
    ): List<DeviceCodeState> {
        var poll = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/login/device/code" -> {
                    if (failDeviceCode) throw RuntimeException("Unable to resolve host github.com")
                    respond(deviceCodeBody, deviceCodeStatus, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                "/login/oauth/access_token" -> {
                    poll++
                    if (failFirstPoll && poll == 1) throw RuntimeException("Connection reset")
                    val index = (if (failFirstPoll) poll - 2 else poll - 1).coerceIn(0, tokenPayloads.lastIndex)
                    respond(tokenPayloads[index], HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                "/user" -> respond("""{"login":"octocat"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        return DeviceFlowClient(httpClient = createMockClient(engine), delayMillis = {})
            .startDeviceFlow(clientId)
            .toList()
    }

    @Test
    fun deviceFlowTagsEveryErrorKind() = runTest {
        val missing = runFlow(clientId = "  ")
        assertEquals(1, missing.size)
        assertEquals(DeviceFlowErrorKind.MISSING_CLIENT_ID, assertIs<DeviceCodeState.Error>(missing.single()).kind)

        val rejected = assertIs<DeviceCodeState.Error>(
            runFlow(deviceCodeStatus = HttpStatusCode.NotFound, deviceCodeBody = """{"error":"Not Found"}""").last()
        )
        assertEquals(DeviceFlowErrorKind.CODE_REQUEST_REJECTED, rejected.kind)
        assertEquals(404, rejected.httpStatusCode)
        assertEquals("GitHub rejected device code request (404). Verify your Client ID.", rejected.message)

        val network = assertIs<DeviceCodeState.Error>(runFlow(failDeviceCode = true).last())
        assertEquals(DeviceFlowErrorKind.NETWORK, network.kind)
        assertTrue(network.message.startsWith("Failed to contact GitHub: "))

        val expired = assertIs<DeviceCodeState.Error>(runFlow(tokenPayloads = listOf("""{"error":"expired_token"}""")).last())
        assertEquals(DeviceFlowErrorKind.EXPIRED, expired.kind)
        assertEquals("The device code expired. Request a new code.", expired.message)

        val timedOut = assertIs<DeviceCodeState.Error>(
            runFlow(deviceCodeBody = """{"device_code":"d","user_code":"u","verification_uri":"https://github.com/login/device","expires_in":10,"interval":5}""").last()
        )
        assertEquals(DeviceFlowErrorKind.TIMED_OUT, timedOut.kind)
        assertEquals("Device code timed out. Please try again.", timedOut.message)

        val other = assertIs<DeviceCodeState.Error>(
            runFlow(
                tokenPayloads = listOf("""{"error":"device_flow_disabled","error_description":"Device Flow must be explicitly enabled for this App"}""")
            ).last()
        )
        assertEquals(DeviceFlowErrorKind.OTHER, other.kind)
        assertEquals("device_flow_disabled", other.oauthErrorCode)
        assertEquals("Device Flow must be explicitly enabled for this App", other.message)
    }

    @Test
    fun deviceFlowReportsNetworkRetryWhilePolling() = runTest {
        val states = runFlow(
            failFirstPoll = true,
            tokenPayloads = listOf("""{"access_token":"gho_x","token_type":"bearer","scope":"repo"}""")
        )
        assertEquals(
            listOf(DeviceFlowWaitReason.CODE_ISSUED, DeviceFlowWaitReason.NETWORK_RETRY),
            states.filterIsInstance<DeviceCodeState.AwaitingUser>().map { it.waitReason }
        )
        assertEquals("Temporary network hiccup, retrying...", states.filterIsInstance<DeviceCodeState.AwaitingUser>().last().statusMessage)
        assertIs<DeviceCodeState.Authenticated>(states.last())
    }

    @Test
    fun deviceFlowValidatesVerificationUriStrictly() = runTest {
        assertTrue(DeviceFlowClient.isTrustedDeviceVerificationUri("https://github.com/login/device"))
        assertTrue(DeviceFlowClient.isTrustedDeviceVerificationUri("https://github.com/login/device/"))
        assertTrue(DeviceFlowClient.isTrustedDeviceVerificationUri("https://github.com/login/device?user_code=ABCD-EFGH"))

        assertFalse(DeviceFlowClient.isTrustedDeviceVerificationUri("http://github.com/login/device"))
        assertFalse(DeviceFlowClient.isTrustedDeviceVerificationUri("https://evil.example/login/device"))
        assertFalse(DeviceFlowClient.isTrustedDeviceVerificationUri("https://github.com/login/device-evil"))
        assertFalse(DeviceFlowClient.isTrustedDeviceVerificationUri("https://github.com/login/device/../../phish"))
        assertFalse(DeviceFlowClient.isTrustedDeviceVerificationUri("https://github.com:8443/login/device"))
        assertFalse(DeviceFlowClient.isTrustedDeviceVerificationUri("https://user@github.com/login/device"))
        assertFalse(DeviceFlowClient.isTrustedDeviceVerificationUri("javascript:alert(1)"))

        val badStates = runFlow(
            deviceCodeBody = """{"device_code":"d","user_code":"u","verification_uri":"https://github.com/login/device-evil","expires_in":60,"interval":5}"""
        )
        val err = assertIs<DeviceCodeState.Error>(badStates.last())
        assertEquals(DeviceFlowErrorKind.OTHER, err.kind)
        assertTrue(err.message.contains("unexpected verification URL"))
    }
}
