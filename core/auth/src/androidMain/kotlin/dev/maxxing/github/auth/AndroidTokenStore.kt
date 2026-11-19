package dev.maxxing.github.auth

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.maxxing.github.model.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AndroidAuthContextHolder {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun requireContext(): Context =
        requireNotNull(appContext) { "AndroidAuthContextHolder.init(context) must be called before using TokenStore." }
}

private class AndroidKeystoreTokenStore(
    private val context: Context
) : TokenStore {
    private val json = TokenStoreJson
    private val prefs by lazy { context.getSharedPreferences("maxxing_prefs", Context.MODE_PRIVATE) }
    private val sessionFile by lazy { File(context.filesDir, "maxxing_session.enc") }

    private fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    override suspend fun saveSession(session: AuthSession) = withContext(Dispatchers.IO) {
        val plaintext = json.encodeToString(session).encodeToByteArray()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        val payload = "${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(ciphertext, Base64.NO_WRAP)}"
        writeSessionFileAtomically(payload)
    }

    // Atomic write: the ciphertext is written to a temp file next to the session file
    // and renamed over it, so a reader never observes a partially written session.
    private fun writeSessionFileAtomically(payload: String) {
        val tmpFile = File.createTempFile("maxxing_session", ".tmp", context.filesDir)
        try {
            tmpFile.writeText(payload)
            if (!tmpFile.renameTo(sessionFile)) {
                // renameTo can refuse to replace an existing target on some filesystems;
                // remove the old file and retry once before giving up.
                if ((sessionFile.exists() && !sessionFile.delete()) || !tmpFile.renameTo(sessionFile)) {
                    throw IOException("Failed to replace ${sessionFile.name} atomically")
                }
            }
        } finally {
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    override suspend fun loadSession(): AuthSession? = withContext(Dispatchers.IO) {
        if (!sessionFile.exists()) return@withContext null
        return@withContext try {
            val parts = sessionFile.readText().split(':')
            if (parts.size != 2) return@withContext null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext).decodeToString()
            json.decodeFromString<AuthSession>(plaintext)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun clearSession() = withContext(Dispatchers.IO) {
        if (sessionFile.exists()) {
            sessionFile.delete()
        }
    }

    override suspend fun saveClientId(clientId: String) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId).apply()
    }

    override suspend fun loadClientId(): String? = prefs.getString(KEY_CLIENT_ID, null)

    override suspend fun saveThemePreferences(prefs: ThemePreferences) {
        this.prefs.edit().putString(KEY_THEME_PREFS, json.encodeToString(prefs)).apply()
    }

    override suspend fun loadThemePreferences(): ThemePreferences {
        val raw = prefs.getString(KEY_THEME_PREFS, null) ?: return ThemePreferences()
        return runCatching { json.decodeFromString<ThemePreferences>(raw) }.getOrDefault(ThemePreferences())
    }

    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {
        this.prefs.edit().putString(KEY_NOTIF_PREFS, json.encodeToString(prefs)).apply()
    }

    override suspend fun loadNotificationPreferences(): NotificationPreferences {
        val raw = prefs.getString(KEY_NOTIF_PREFS, null) ?: return NotificationPreferences()
        return runCatching { json.decodeFromString<NotificationPreferences>(raw) }
            .getOrDefault(NotificationPreferences())
    }

    override suspend fun saveUpdatePreferences(prefs: UpdatePreferences) {
        this.prefs.edit().putString(KEY_UPDATE_PREFS, json.encodeToString(prefs)).apply()
    }

    override suspend fun loadUpdatePreferences(): UpdatePreferences {
        val raw = prefs.getString(KEY_UPDATE_PREFS, null) ?: return UpdatePreferences()
        return runCatching { json.decodeFromString<UpdatePreferences>(raw) }
            .getOrDefault(UpdatePreferences())
    }

    override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) {
        prefs.edit().putString(KEY_SEEN_NOTIFS, json.encodeToString(markers)).apply()
    }

    override suspend fun loadSeenNotificationMarkers(): Map<String, String> {
        val raw = prefs.getString(KEY_SEEN_NOTIFS, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    override suspend fun savePinnedRepos(repos: List<String>) {
        prefs.edit().putString(KEY_PINNED_REPOS, json.encodeToString(repos)).apply()
    }

    override suspend fun loadPinnedRepos(): List<String> {
        val raw = prefs.getString(KEY_PINNED_REPOS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "maxxing_session_aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_CLIENT_ID = "github_client_id"
        private const val KEY_THEME_PREFS = "theme_prefs"
        private const val KEY_NOTIF_PREFS = "notif_prefs"
        private const val KEY_UPDATE_PREFS = "update_prefs"
        private const val KEY_SEEN_NOTIFS = "seen_notifs"
        private const val KEY_PINNED_REPOS = "pinned_repos"
    }
}

actual fun createPlatformTokenStore(): TokenStore =
    AndroidKeystoreTokenStore(AndroidAuthContextHolder.requireContext())

private const val OFFICIAL_GITHUB_PACKAGE = "com.github.android"

private fun resolveExternalBrowserPackage(pm: PackageManager, selfPackage: String): String? {
    val genericHttpsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    val defaultBrowser = pm.resolveActivity(genericHttpsIntent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName
        ?.takeIf { it != selfPackage && it != OFFICIAL_GITHUB_PACKAGE && it != "android" }
    if (defaultBrowser != null) return defaultBrowser

    val browsableCandidates = pm.queryIntentActivities(genericHttpsIntent, PackageManager.MATCH_DEFAULT_ONLY)
        .mapNotNull { it.activityInfo?.packageName }
        .firstOrNull { it != selfPackage && it != OFFICIAL_GITHUB_PACKAGE && it != "android" }
    if (browsableCandidates != null) return browsableCandidates

    val appBrowserIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
    return pm.queryIntentActivities(appBrowserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        .mapNotNull { it.activityInfo?.packageName }
        .firstOrNull { it != selfPackage && it != OFFICIAL_GITHUB_PACKAGE && it != "android" }
}

actual fun openUrlInBrowser(url: String): Boolean {
    return try {
        val context = AndroidAuthContextHolder.requireContext()
        val parsedUri = Uri.parse(url)
        // Same restriction as the desktop implementation: only http/https URLs are ever opened.
        if (!isAllowedBrowserUrlScheme(parsedUri.scheme)) return false
        val intent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pm = context.packageManager
        val resolvedPkg = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (resolvedPkg == null ||
            resolvedPkg == context.packageName ||
            resolvedPkg == OFFICIAL_GITHUB_PACKAGE ||
            resolvedPkg == "android"
        ) {
            val externalBrowser = resolveExternalBrowserPackage(pm, context.packageName)
            if (externalBrowser != null) {
                intent.setPackage(externalBrowser)
            } else if (resolvedPkg == context.packageName) {
                return false
            }
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

actual fun shareText(text: String, title: String?): Boolean {
    return try {
        val context = AndroidAuthContextHolder.requireContext()
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!title.isNullOrBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    } catch (_: Exception) {
        false
    }
}

private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, 0)
    }
}.isSuccess

private fun openOpenByDefaultSettings(context: Context, packageName: String): Boolean {
    val pkgUri = Uri.parse("package:$packageName")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val opened = runCatching {
            val intent = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkgUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (opened) return true
    }
    return runCatching {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
        true
    }.getOrDefault(false)
}

actual fun inspectGitHubLinkHandling(): GitHubLinkHandlingState {
    val context = AndroidAuthContextHolder.requireContext()
    val pm = context.packageManager
    val officialInstalled = isPackageInstalled(pm, OFFICIAL_GITHUB_PACKAGE)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dvm = runCatching { context.getSystemService(DomainVerificationManager::class.java) }.getOrNull()
        val selfState = runCatching { dvm?.getDomainVerificationUserState(context.packageName) }.getOrNull()
        val selfGitHubState = selfState?.hostToStateMap?.get("github.com")
        val selfHandles = selfState?.isLinkHandlingAllowed == true && (
            selfGitHubState == DomainVerificationUserState.DOMAIN_STATE_VERIFIED ||
                selfGitHubState == DomainVerificationUserState.DOMAIN_STATE_SELECTED
            )

        val officialHandles = if (!officialInstalled) {
            false
        } else {
            runCatching { dvm?.getDomainVerificationUserState(OFFICIAL_GITHUB_PACKAGE) }.getOrNull()?.let { ghState ->
                val ghMap = ghState.hostToStateMap["github.com"]
                ghState.isLinkHandlingAllowed && (
                    ghMap == DomainVerificationUserState.DOMAIN_STATE_VERIFIED ||
                        ghMap == DomainVerificationUserState.DOMAIN_STATE_SELECTED
                    )
            }
        }

        return GitHubLinkHandlingState(
            supportedOnPlatform = true,
            thisAppHandlesGitHubLinks = selfHandles,
            officialGitHubInstalled = officialInstalled,
            officialGitHubHandlesLinks = officialHandles
        )
    }

    val resolvedPkg = runCatching {
        pm.resolveActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/octocat")),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
    }.getOrNull()

    return GitHubLinkHandlingState(
        supportedOnPlatform = true,
        thisAppHandlesGitHubLinks = resolvedPkg == context.packageName,
        officialGitHubInstalled = officialInstalled,
        officialGitHubHandlesLinks = if (officialInstalled) resolvedPkg == OFFICIAL_GITHUB_PACKAGE else false
    )
}

actual fun openThisAppLinkSettings(): Boolean {
    val context = AndroidAuthContextHolder.requireContext()
    return openOpenByDefaultSettings(context, context.packageName)
}

actual fun openOfficialGitHubAppLinkSettings(): Boolean {
    val context = AndroidAuthContextHolder.requireContext()
    return openOpenByDefaultSettings(context, OFFICIAL_GITHUB_PACKAGE)
}
