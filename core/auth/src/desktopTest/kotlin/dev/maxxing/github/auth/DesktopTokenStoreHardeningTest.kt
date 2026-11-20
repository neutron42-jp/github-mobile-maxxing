package dev.maxxing.github.auth

import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.AuthSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopTokenStoreHardeningTest {

    @Test
    fun writeOwnerOnlyFileAtomicallyCreates0600FileAndCleansUpTempFiles() {
        val dir = Files.createTempDirectory("maxxing-token-test")
        try {
            val target = dir.resolve("session.json").toFile()

            writeOwnerOnlyFileAtomically(target, """{"accessToken":"first"}""")
            assertEquals("""{"accessToken":"first"}""", target.readText())
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(target.toPath())))

            writeOwnerOnlyFileAtomically(target, """{"accessToken":"second"}""")
            assertEquals("""{"accessToken":"second"}""", target.readText())
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(target.toPath())))

            val remaining = dir.toFile().listFiles().orEmpty().map { it.name }
            assertEquals(listOf("session.json"), remaining)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun runCommandWithTimeoutCapturesStdoutPassesStdinAndAbortsHungCommands() {
        val echoed = runCommandWithTimeout(
            command = listOf("cat"),
            stdinText = "secret-payload",
            captureStdout = true,
            timeoutMs = 2_000L
        )
        assertEquals("secret-payload", echoed)

        val startNanos = System.nanoTime()
        val hung = runCommandWithTimeout(
            command = listOf("sleep", "30"),
            captureStdout = true,
            timeoutMs = 200L
        )
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
        assertNull(hung)
        assertTrue(elapsedMs < 3_000L, "Expected command to abort quickly on timeout, took ${elapsedMs}ms")
    }

    @Test
    fun encodeSessionForSecretStoreHasNoNewlinesAndDecodesLegacyPrettyJson() {
        val session = AuthSession(
            accessToken = "ghu_secret_token",
            tokenType = "bearer",
            scope = "repo gist",
            authMethod = AuthMethod.DEVICE_FLOW,
            userLogin = "octocat",
            refreshToken = "ghr_refresh_token",
            accessTokenExpiresAtEpochSeconds = 1_700_010_000L,
            refreshTokenExpiresAtEpochSeconds = 1_705_000_000L,
            clientId = "Ov23liTest"
        )
        val encoded = encodeSessionForSecretStore(session)
        assertFalse('\n' in encoded, "Encoded secret payload must not contain LF")
        assertFalse('\r' in encoded, "Encoded secret payload must not contain CR")
        assertEquals(session, decodeSessionFromSecretStore(encoded))

        val legacyPretty = Json(from = TokenStoreJson) { prettyPrint = true }.encodeToString(session)
        assertTrue('\n' in legacyPretty)
        assertEquals(session, decodeSessionFromSecretStore(legacyPretty))
    }
}
