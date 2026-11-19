package dev.maxxing.github.auth

import dev.maxxing.github.model.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

private class LinuxDesktopTokenStore : TokenStore {
    // The shared token store settings, pretty-printed so the files stay readable.
    private val json = Json(from = TokenStoreJson) { prettyPrint = true }

    private val dataDir: File by lazy {
        val xdgData = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: "${System.getProperty("user.home")}/.local/share"
        File(xdgData, "github-mobile-maxxing").apply { mkdirs() }
    }

    private val sessionFile: File by lazy { File(dataDir, "session.json") }
    private val configFile: File by lazy { File(dataDir, "config.json") }
    private val themeFile: File by lazy { File(dataDir, "theme.json") }
    private val notifPrefsFile: File by lazy { File(dataDir, "notif_prefs.json") }
    private val updatePrefsFile: File by lazy { File(dataDir, "update_prefs.json") }
    private val seenNotifsFile: File by lazy { File(dataDir, "seen_notifs.json") }
    private val pinnedReposFile: File by lazy { File(dataDir, "pinned_repos.json") }

    override suspend fun saveSession(session: AuthSession) = withContext(Dispatchers.IO) {
        val encoded = json.encodeToString(session)
        if (!trySecretToolStore(encoded)) {
            writeOwnerOnlyFileAtomically(sessionFile, encoded)
        } else if (sessionFile.exists()) {
            sessionFile.delete()
        }
    }

    override suspend fun loadSession(): AuthSession? = withContext(Dispatchers.IO) {
        val fromKeyring = trySecretToolLookup()
        if (!fromKeyring.isNullOrBlank()) {
            return@withContext runCatching { json.decodeFromString<AuthSession>(fromKeyring) }.getOrNull()
        }
        if (!sessionFile.exists()) return@withContext null
        return@withContext runCatching { json.decodeFromString<AuthSession>(sessionFile.readText()) }.getOrNull()
    }

    override suspend fun clearSession() = withContext(Dispatchers.IO) {
        trySecretToolClear()
        if (sessionFile.exists()) {
            sessionFile.delete()
        }
    }

    override suspend fun saveClientId(clientId: String) = withContext(Dispatchers.IO) {
        configFile.writeText(clientId)
        restrictPermissions(configFile)
    }

    override suspend fun loadClientId(): String? = withContext(Dispatchers.IO) {
        if (configFile.exists()) configFile.readText().trim().takeIf { it.isNotEmpty() } else null
    }

    override suspend fun saveThemePreferences(prefs: ThemePreferences) = withContext(Dispatchers.IO) {
        themeFile.writeText(json.encodeToString(prefs))
    }

    override suspend fun loadThemePreferences(): ThemePreferences = withContext(Dispatchers.IO) {
        if (!themeFile.exists()) return@withContext ThemePreferences()
        runCatching { json.decodeFromString<ThemePreferences>(themeFile.readText()) }
            .getOrDefault(ThemePreferences())
    }

    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) = withContext(Dispatchers.IO) {
        notifPrefsFile.writeText(json.encodeToString(prefs))
    }

    override suspend fun loadNotificationPreferences(): NotificationPreferences = withContext(Dispatchers.IO) {
        if (!notifPrefsFile.exists()) return@withContext NotificationPreferences()
        runCatching { json.decodeFromString<NotificationPreferences>(notifPrefsFile.readText()) }
            .getOrDefault(NotificationPreferences())
    }

    override suspend fun saveUpdatePreferences(prefs: UpdatePreferences) = withContext(Dispatchers.IO) {
        updatePrefsFile.writeText(json.encodeToString(prefs))
    }

    override suspend fun loadUpdatePreferences(): UpdatePreferences = withContext(Dispatchers.IO) {
        if (!updatePrefsFile.exists()) return@withContext UpdatePreferences()
        runCatching { json.decodeFromString<UpdatePreferences>(updatePrefsFile.readText()) }
            .getOrDefault(UpdatePreferences())
    }

    override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) = withContext(Dispatchers.IO) {
        seenNotifsFile.writeText(json.encodeToString(markers))
    }

    override suspend fun loadSeenNotificationMarkers(): Map<String, String> = withContext(Dispatchers.IO) {
        if (!seenNotifsFile.exists()) return@withContext emptyMap()
        runCatching { json.decodeFromString<Map<String, String>>(seenNotifsFile.readText()) }
            .getOrDefault(emptyMap())
    }

    override suspend fun savePinnedRepos(repos: List<String>) = withContext(Dispatchers.IO) {
        pinnedReposFile.writeText(json.encodeToString(repos))
    }

    override suspend fun loadPinnedRepos(): List<String> = withContext(Dispatchers.IO) {
        if (!pinnedReposFile.exists()) return@withContext emptyList()
        runCatching { json.decodeFromString<List<String>>(pinnedReposFile.readText()) }
            .getOrDefault(emptyList())
    }

    private fun restrictPermissions(file: File) {
        runCatching {
            val perms = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            Files.setPosixFilePermissions(file.toPath(), perms)
        }
    }

    private fun trySecretToolStore(secret: String): Boolean =
        runCommandWithTimeout(
            command = listOf(
                "secret-tool", "store",
                "--label=github-mobile-maxxing GitHub Session",
                "service", "github-mobile-maxxing",
                "account", "github"
            ),
            stdinText = secret
        ) != null

    private fun trySecretToolLookup(): String? =
        runCommandWithTimeout(
            command = listOf(
                "secret-tool", "lookup",
                "service", "github-mobile-maxxing",
                "account", "github"
            ),
            captureStdout = true
        )?.trim()?.takeIf { it.isNotEmpty() }

    private fun trySecretToolClear(): Boolean =
        runCommandWithTimeout(
            command = listOf(
                "secret-tool", "clear",
                "service", "github-mobile-maxxing",
                "account", "github"
            )
        ) != null
}

/**
 * Plaintext fallback write: creates the temp file owner-only (POSIX 0600) so the secret is
 * never briefly readable by others, then swaps it into [targetFile] via rename.
 */
internal fun writeOwnerOnlyFileAtomically(targetFile: File, content: String) {
    val target = targetFile.toPath()
    val tmp = Files.createTempFile(
        target.parent,
        "${targetFile.name}.",
        ".tmp",
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
    )
    try {
        Files.writeString(tmp, content)
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
        runCatching {
            val perms = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            Files.setPosixFilePermissions(target, perms)
        }
    } finally {
        runCatching { Files.deleteIfExists(tmp) }
    }
}

/**
 * Runs a local helper command (such as `secret-tool`) with a hard timeout.
 * Returns stdout (or empty string when [captureStdout] is false) on exit code 0, or null on
 * failure or timeout.
 */
internal fun runCommandWithTimeout(
    command: List<String>,
    stdinText: String? = null,
    captureStdout: Boolean = false,
    timeoutMs: Long = 10_000L
): String? {
    var process: Process? = null
    return try {
        val pb = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD)
        if (!captureStdout) pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        val started = pb.start()
        process = started

        val writerThread = if (stdinText != null) {
            Thread {
                runCatching { started.outputStream.bufferedWriter().use { it.write(stdinText) } }
            }.apply { isDaemon = true; start() }
        } else {
            runCatching { started.outputStream.close() }
            null
        }

        var output = ""
        val readerThread = if (captureStdout) {
            Thread {
                output = runCatching { started.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("")
            }.apply { isDaemon = true; start() }
        } else {
            null
        }

        if (!started.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            started.destroyForcibly()
            return null
        }
        writerThread?.join(1_000L)
        readerThread?.join(1_000L)
        if (started.exitValue() == 0) output else null
    } catch (_: Exception) {
        process?.destroyForcibly()
        null
    }
}

actual fun createPlatformTokenStore(): TokenStore = LinuxDesktopTokenStore()

actual fun openUrlInBrowser(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return false

    // Try AWT Desktop.browse first (uses native GTK/GIO directly when supported)
    val awtOpened = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri)
            true
        } else {
            false
        }
    }.getOrDefault(false)
    if (awtOpened) return true

    // Fallback to xdg-open / gio open with discarded stdio and exit-code check
    val commands = listOf(
        listOf("xdg-open", url),
        listOf("gio", "open", url)
    )
    for (cmd in commands) {
        val launched = runCatching {
            val pb = ProcessBuilder(cmd)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
            // Avoid bundled JVM runtime LD_LIBRARY_PATH interfering with system gio/xdg-open
            pb.environment().remove("LD_LIBRARY_PATH")
            val process = pb.start()
            process.outputStream.close()
            val finished = process.waitFor(1500, TimeUnit.MILLISECONDS)
            !finished || process.exitValue() == 0
        }.getOrDefault(false)
        if (launched) return true
    }

    return false
}

actual fun shareText(text: String, title: String?): Boolean = false

actual fun inspectGitHubLinkHandling(): GitHubLinkHandlingState = GitHubLinkHandlingState()

actual fun openThisAppLinkSettings(): Boolean = false

actual fun openOfficialGitHubAppLinkSettings(): Boolean = false
