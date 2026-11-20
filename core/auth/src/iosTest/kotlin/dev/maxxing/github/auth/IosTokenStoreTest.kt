@file:OptIn(ExperimentalForeignApi::class)

package dev.maxxing.github.auth

import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import dev.maxxing.github.model.MaxxingThemeDefaults
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSString
import platform.Foundation.NSUserDefaults
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecInteractionNotAllowed
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.UIKit.UIViewController
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real-Keychain + real-NSUserDefaults tests for `IosTokenStore`, run on the
 * iOS simulator. Uses an isolated `service` and an `NSUserDefaults` suite,
 * both cleaned up before and after every test.
 *
 * These tests exercise the real simulator Keychain, not a fake. Two things in
 * `core/auth/build.gradle.kts` make that possible without a host app or
 * provisioning profile:
 *  - `src/iosTest/keychain-entitlements.plist` is linked into the
 *    `iosSimulatorArm64` test binary as a `__TEXT,__entitlements` section
 *    (`-sectcreate`). Without it, `SecItem*` returns `errSecMissingEntitlement`
 *    (-34018).
 *  - the test task spawns the binary with `simctl spawn` (`standalone = false`),
 *    so it runs inside `launchd_sim` where the Keychain service is reachable.
 *    With `--standalone`, `SecItem*` returns `errSecNotAvailable` (-25291).
 */
class IosTokenStoreTest {

    private val json = TokenStoreJson

    private lateinit var defaults: NSUserDefaults

    @BeforeTest
    fun setUp() {
        IosKeychainOps.reset()
        defaults = NSUserDefaults(suiteName = SUITE_NAME)
        defaults.removePersistentDomainForName(SUITE_NAME)
        deleteKeychainItem(TEST_SERVICE, ACCOUNT)
    }

    @AfterTest
    fun tearDown() {
        deleteKeychainItem(TEST_SERVICE, ACCOUNT)
        defaults.removePersistentDomainForName(SUITE_NAME)
        IosKeychainOps.reset()
    }

    private fun store() = IosTokenStore(TEST_SERVICE, defaults)

    @Test
    fun saveUpdateLoadClearAndNullWhenUnsaved() = runTest {
        val store = store()
        assertNull(store.loadSession())

        val session = AuthSession("tok1", userLogin = "octocat")
        store.saveSession(session)
        assertEquals(session, store.loadSession())
        assertStoredAccessibleIsAfterFirstUnlockThisDeviceOnly(TEST_SERVICE, ACCOUNT)

        // Second save hits errSecDuplicateItem -> SecItemUpdate overwrites.
        val updated = AuthSession(
            accessToken = "tok2",
            tokenType = "bearer",
            scope = "repo gist",
            authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN,
            userLogin = "octocat2",
        )
        store.saveSession(updated)
        assertEquals(updated, store.loadSession())
        assertEquals("tok2", store.loadSession()?.accessToken)
        // The update path must set kSecAttrAccessible again, not just the data.
        assertStoredAccessibleIsAfterFirstUnlockThisDeviceOnly(TEST_SERVICE, ACCOUNT)

        store.clearSession()
        assertNull(store.loadSession())
    }

    @Test
    fun corruptedKeychainDataIsDeletedAndReturnsNull() = runTest {
        // Prevent the first-launch check from deleting our fixture up front.
        defaults.setBool(true, forKey = "has_launched_before")
        val store = store()

        // Corrupted JSON (valid UTF-8).
        writeKeychainData(TEST_SERVICE, ACCOUNT, "not valid json".encodeToByteArray())
        assertNull(store.loadSession())
        assertNull(store.loadSession()) // subsequent load still null
        assertFalse(keychainItemExists(TEST_SERVICE, ACCOUNT))

        // Non-UTF8 bytes.
        writeKeychainData(TEST_SERVICE, ACCOUNT, byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x80.toByte(), 0x00.toByte()))
        assertNull(store.loadSession())
        assertFalse(keychainItemExists(TEST_SERVICE, ACCOUNT))
    }

    @Test
    fun firstLaunchClearsStaleKeychainItem() = runTest {
        // Simulate a reinstall: defaults wiped but a Keychain item survives.
        val stale = AuthSession("stale_token", userLogin = "ghost")
        writeKeychainData(TEST_SERVICE, ACCOUNT, json.encodeToString(stale).encodeToByteArray())
        assertFalse(defaults.boolForKey("has_launched_before"))

        val store = store()
        assertNull(store.loadSession()) // first-launch check deletes the stale item
        assertTrue(defaults.boolForKey("has_launched_before"))
        assertFalse(keychainItemExists(TEST_SERVICE, ACCOUNT))
    }

    @Test
    fun preferencesRoundTripAndCorruptionFallback() = runTest {
        val store = store()

        assertNull(store.loadClientId())
        store.saveClientId("client123")
        assertEquals("client123", store.loadClientId())

        val theme = ThemePreferences(
            seedColorHex = 0xFF112233,
            monochromePresetSelected = false,
            useDynamicColor = false,
            darkModeOverride = true
        )
        store.saveThemePreferences(theme)
        assertEquals(theme, store.loadThemePreferences())

        // New Graphite records persist and restore the explicit Monochrome selection.
        val graphite = ThemePreferences()
        store.saveThemePreferences(graphite)
        val reloadedGraphite = store.loadThemePreferences()
        assertEquals(graphite, reloadedGraphite)
        assertEquals(MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX, reloadedGraphite.seedColorHex)
        assertTrue(reloadedGraphite.monochromePresetSelected)

        val notif = NotificationPreferences(osNotificationsEnabled = false, urgentOnly = true, pollIntervalMinutes = 5)
        store.saveNotificationPreferences(notif)
        assertEquals(notif, store.loadNotificationPreferences())

        val updates = UpdatePreferences(
            includePrereleases = true,
            autoCheckOnLaunch = false,
            dismissedVersionKey = "v1.0.9"
        )
        store.saveUpdatePreferences(updates)
        assertEquals(updates, store.loadUpdatePreferences())

        val seen = mapOf("1" to "read", "2" to "done")
        store.saveSeenNotificationMarkers(seen)
        assertEquals(seen, store.loadSeenNotificationMarkers())

        val pinned = listOf("octo/repo", "maxxing/app")
        store.savePinnedRepos(pinned)
        assertEquals(pinned, store.loadPinnedRepos())

        // Corrupted JSON in NSUserDefaults falls back to defaults.
        defaults.setObject("{bad json", forKey = "theme_prefs")
        assertEquals(ThemePreferences(), store.loadThemePreferences())

        defaults.setObject("not json", forKey = "notif_prefs")
        assertEquals(NotificationPreferences(), store.loadNotificationPreferences())

        defaults.setObject("not json", forKey = "update_prefs")
        assertEquals(UpdatePreferences(), store.loadUpdatePreferences())

        defaults.setObject("garbage", forKey = "seen_notifs")
        assertEquals(emptyMap<String, String>(), store.loadSeenNotificationMarkers())

        defaults.setObject("garbage", forKey = "pinned_repos")
        assertEquals(emptyList<String>(), store.loadPinnedRepos())
    }

    @Test
    fun legacyThemeRecordsAreDisambiguatedFromGraphiteRecords() = runTest {
        val store = store()

        // Legacy record with no seed (including {}) follows the Graphite default but keeps its fields.
        defaults.setObject("""{"useDynamicColor":false}""", forKey = "theme_prefs")
        val defaulted = store.loadThemePreferences()
        assertEquals(MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX, defaulted.seedColorHex)
        assertTrue(defaulted.monochromePresetSelected)
        assertFalse(defaulted.useDynamicColor)

        // Legacy Iris record keeps its seed and stays Expressive.
        defaults.setObject(
            """{"seedColorHex":${MaxxingThemeDefaults.IRIS_SEED_COLOR_HEX},"useDynamicColor":false}""",
            forKey = "theme_prefs"
        )
        val iris = store.loadThemePreferences()
        assertEquals(MaxxingThemeDefaults.IRIS_SEED_COLOR_HEX, iris.seedColorHex)
        assertFalse(iris.monochromePresetSelected)

        // Legacy custom #606060 keeps the Expressive style instead of silently becoming Graphite.
        defaults.setObject(
            """{"seedColorHex":${MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX},"useDynamicColor":false}""",
            forKey = "theme_prefs"
        )
        val customGray = store.loadThemePreferences()
        assertEquals(MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX, customGray.seedColorHex)
        assertFalse(customGray.monochromePresetSelected)
    }

    @Test
    fun unknownEnumValuesInThemePreferencesFallBackToDefaultsWithoutLosingOtherFields() = runTest {
        val store = store()

        // Future language enum value falls back to DEFAULT while preserving color, dark mode, and fonts.
        val futureLanguageJson = """{"seedColorHex":16734003,"useDynamicColor":false,"darkModeOverride":true,""" +
            """"primaryFont":{"kind":"SYSTEM_FAMILY","value":"Inter","displayName":"Inter"},""" +
            """"language":"FUTURE_LANG"}"""
        defaults.setObject(futureLanguageJson, forKey = "theme_prefs")
        assertEquals(
            ThemePreferences(
                seedColorHex = 16734003L,
                monochromePresetSelected = false,
                useDynamicColor = false,
                darkModeOverride = true,
                primaryFont = FontSelection(
                    kind = FontSourceKind.SYSTEM_FAMILY,
                    value = "Inter",
                    displayName = "Inter"
                ),
                fallbackFont = FontSelection.SystemDefault,
                language = AppLanguage.DEFAULT
            ),
            store.loadThemePreferences()
        )

        // Future FontSourceKind enum values fall back to SYSTEM_DEFAULT while preserving language and other fields.
        val futureFontKindJson = """{"seedColorHex":16734003,"useDynamicColor":false,"darkModeOverride":false,""" +
            """"primaryFont":{"kind":"FUTURE_FONT_KIND","value":"x","displayName":"X"},""" +
            """"fallbackFont":{"kind":"FUTURE_FALLBACK_KIND"},""" +
            """"language":"JAPANESE"}"""
        defaults.setObject(futureFontKindJson, forKey = "theme_prefs")
        assertEquals(
            ThemePreferences(
                seedColorHex = 16734003L,
                monochromePresetSelected = false,
                useDynamicColor = false,
                darkModeOverride = false,
                primaryFont = FontSelection(
                    kind = FontSourceKind.SYSTEM_DEFAULT,
                    value = "x",
                    displayName = "X"
                ),
                fallbackFont = FontSelection.SystemDefault,
                language = AppLanguage.JAPANESE
            ),
            store.loadThemePreferences()
        )
    }

    @Test
    fun openUrlInBrowserRejectsInvalidAndNonHttpUrls() {
        assertFalse(openUrlInBrowser(""))
        assertFalse(openUrlInBrowser("   "))
        assertFalse(openUrlInBrowser("not a url"))
        assertFalse(openUrlInBrowser("ftp://example.com/file"))
        assertFalse(openUrlInBrowser("javascript:alert(1)"))
        assertFalse(openUrlInBrowser("file:///etc/passwd"))
        assertFalse(openUrlInBrowser("mailto:test@example.com"))
        assertFalse(openUrlInBrowser("githubmaxxing://thread/octo/repo/1"))
    }

    /**
     * `shareText` follows `TokenStore.kt`'s contract: `false` means "the caller
     * should fall back to clipboard copy". With no registered root controller
     * (and no `UIApplication.keyWindow` fallback — Choice A), it reports
     * `false` for blank and non-blank input, regardless of the optional title.
     */
    @Test
    fun shareTextReturnsFalseUntilShareSheetPresentationExists() {
        assertFalse(shareText("https://github.com/octo/repo"))
        assertFalse(shareText("https://github.com/octo/repo", title = "octo/repo"))
        assertFalse(shareText("https://github.com/octo/repo", title = "  "))
        assertFalse(shareText("", title = "octo/repo"))
    }

    @Test
    fun shareTextReturnsFalseWhenRegisteredControllerIsNotInAWindow() {
        val controller = UIViewController()
        IosSharePresenter.setRootViewController(controller)
        try {
            assertFalse(shareText("https://github.com/octo/repo"))
        } finally {
            IosSharePresenter.clearRootViewController()
        }
    }

    @Test
    fun interactionNotAllowedThrowsWithoutDeletingItem() = runTest {
        defaults.setBool(true, forKey = "has_launched_before")
        val realStore = store()
        val session = AuthSession("tok_keep", userLogin = "octocat")
        realStore.saveSession(session)
        assertTrue(keychainItemExists(TEST_SERVICE, ACCOUNT))

        IosKeychainOps.secItemCopyMatching = { _, _ -> errSecInteractionNotAllowed }
        try {
            assertIs<IllegalStateException>(catching { realStore.loadSession() })
        } finally {
            IosKeychainOps.reset()
        }

        // The failed load must NOT have deleted the item.
        assertTrue(keychainItemExists(TEST_SERVICE, ACCOUNT))
        assertEquals(session, store().loadSession())
    }

    @Test
    fun unexpectedStatusOnSaveAndClearThrows() = runTest {
        defaults.setBool(true, forKey = "has_launched_before")
        val store = store()

        IosKeychainOps.secItemAdd = { _, _ -> -50 /* errSecParam */ }
        try {
            assertIs<IllegalStateException>(catching { store.saveSession(AuthSession("t", userLogin = "u")) })
        } finally {
            IosKeychainOps.reset()
        }

        IosKeychainOps.secItemDelete = { _ -> -50 /* errSecParam */ }
        try {
            assertIs<IllegalStateException>(catching { store.clearSession() })
        } finally {
            IosKeychainOps.reset()
        }
    }

    private suspend fun <T> catching(block: suspend () -> T): Throwable? =
        try {
            block()
            null
        } catch (t: Throwable) {
            t
        }

    private companion object {
        const val SUITE_NAME = "dev.maxxing.github.auth.IosTokenStoreTest"
        const val TEST_SERVICE = "$SUITE_NAME.session"
        const val ACCOUNT = "github_auth_session"
    }
}

// ---- Raw Keychain helpers (always use the real Security functions) --------

private class TestCFRefScope {
    private val refs = mutableListOf<CFTypeRef>()

    fun <T : CFTypeRef> keep(allocation: T?, failureMessage: String): T {
        if (allocation == null) {
            close()
            error(failureMessage)
        }
        refs += allocation
        return allocation
    }

    fun close() {
        for (ref in refs) cfRelease(ref)
        refs.clear()
    }
}

private inline fun <T> withTestCFRefs(block: TestCFRefScope.() -> T): T {
    val scope = TestCFRefScope()
    return try {
        scope.block()
    } finally {
        scope.close()
    }
}

private fun cfString(s: String): CFStringRef? =
    CFStringCreateWithCString(null, s, kCFStringEncodingUTF8)

private fun cfRelease(ref: CFTypeRef?) {
    if (ref != null) CFRelease(ref)
}

private inline fun cfDict(
    vararg pairs: Pair<CFStringRef?, CFTypeRef?>,
    block: (CFDictionaryRef) -> Int,
): Int {
    val dict = CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        pairs.size.toLong(),
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
    ) ?: error("CFDictionaryCreateMutable failed")
    return try {
        for ((key, value) in pairs) {
            val nonNullKey = key ?: error("query key must not be null")
            val nonNullValue = value ?: error("query value must not be null")
            CFDictionaryAddValue(dict, nonNullKey, nonNullValue)
        }
        block(dict)
    } finally {
        cfRelease(dict)
    }
}

/**
 * Reads the item's raw attributes (not through `IosTokenStore`) and asserts the
 * stored `kSecAttrAccessible` is `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`.
 */
private fun assertStoredAccessibleIsAfterFirstUnlockThisDeviceOnly(service: String, account: String) {
    withTestCFRefs {
        val cfService = keep(cfString(service), "CFStringCreateWithCString failed")
        val cfAccount = keep(cfString(account), "CFStringCreateWithCString failed")
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = cfDict(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to cfService,
                kSecAttrAccount to cfAccount,
                kSecReturnAttributes to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            ) { SecItemCopyMatching(it, result.ptr) }
            assertEquals(errSecSuccess, status, "SecItemCopyMatching(attributes) failed with OSStatus $status")

            val attributes = assertNotNull(result.value, "SecItemCopyMatching returned no attributes")
            try {
                val dictionary: CFDictionaryRef = attributes.reinterpret()
                val actual = assertNotNull(
                    CFDictionaryGetValue(dictionary, kSecAttrAccessible),
                    "stored item has no kSecAttrAccessible attribute",
                )
                // `CFEqual` is unavailable in the Kotlin 2.4 bindings, so compare
                // the toll-free-bridged strings instead.
                val actualAccessible = CFBridgingRelease(CFRetain(actual)) as? NSString
                val expectedAccessible =
                    CFBridgingRelease(CFRetain(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)) as? NSString
                assertEquals(
                    expectedAccessible,
                    actualAccessible,
                    "stored kSecAttrAccessible is not kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly",
                )
            } finally {
                cfRelease(attributes)
            }
        }
    }
}

private fun writeKeychainData(service: String, account: String, bytes: ByteArray) {
    withTestCFRefs {
        val cfService = keep(cfString(service), "CFStringCreateWithCString failed")
        val cfAccount = keep(cfString(account), "CFStringCreateWithCString failed")
        val rawCfData = if (bytes.isEmpty()) {
            CFDataCreate(kCFAllocatorDefault, null, 0L)
        } else {
            bytes.usePinned { pinned ->
                CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
            }
        }
        val cfData = keep(rawCfData, "CFDataCreate failed")
        val addStatus = cfDict(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfService,
            kSecAttrAccount to cfAccount,
            kSecValueData to cfData,
        ) { SecItemAdd(it, null) }
        if (addStatus == errSecDuplicateItem) {
            val updateStatus = cfDict(kSecValueData to cfData) { attrs ->
                cfDict(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to cfService,
                    kSecAttrAccount to cfAccount,
                ) { SecItemUpdate(it, attrs) }
            }
            check(updateStatus == errSecSuccess) { "writeKeychainData update failed: $updateStatus" }
        } else {
            check(addStatus == errSecSuccess) { "writeKeychainData add failed: $addStatus" }
        }
    }
}

private fun keychainItemExists(service: String, account: String): Boolean =
    withTestCFRefs {
        val cfService = keep(cfString(service), "CFStringCreateWithCString failed")
        val cfAccount = keep(cfString(account), "CFStringCreateWithCString failed")
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = cfDict(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to cfService,
                kSecAttrAccount to cfAccount,
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            ) { SecItemCopyMatching(it, result.ptr) }
            when (status) {
                errSecSuccess -> {
                    CFBridgingRelease(result.value)
                    true
                }
                errSecItemNotFound -> false
                else -> throw IllegalStateException("keychainItemExists failed with OSStatus $status")
            }
        }
    }

private fun deleteKeychainItem(service: String, account: String) {
    withTestCFRefs {
        val cfService = keep(cfString(service), "CFStringCreateWithCString failed")
        val cfAccount = keep(cfString(account), "CFStringCreateWithCString failed")
        val status = cfDict(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfService,
            kSecAttrAccount to cfAccount,
        ) { SecItemDelete(it) }
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "cleanup SecItemDelete failed with OSStatus $status"
        }
    }
}
