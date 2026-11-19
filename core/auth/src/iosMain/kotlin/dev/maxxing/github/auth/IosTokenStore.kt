@file:OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package dev.maxxing.github.auth

import dev.maxxing.github.model.AuthSession
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
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
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSLog
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.getBytes
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
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
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Internal seam over the `platform.Security` Keychain C functions so the
 * `iosTest` suite can simulate specific `OSStatus` values (for example
 * `errSecInteractionNotAllowed`) without weakening the real-Keychain tests:
 * the default implementations are the real `SecItem*` functions.
 */
internal object IosKeychainOps {
    var secItemAdd: (CFDictionaryRef?, CPointer<CFTypeRefVar>?) -> Int =
        { attributes, result -> SecItemAdd(attributes, result) }
    var secItemUpdate: (CFDictionaryRef?, CFDictionaryRef?) -> Int =
        { query, attributes -> SecItemUpdate(query, attributes) }
    var secItemCopyMatching: (CFDictionaryRef?, CPointer<CFTypeRefVar>?) -> Int =
        { query, result -> SecItemCopyMatching(query, result) }
    var secItemDelete: (CFDictionaryRef?) -> Int =
        { query -> SecItemDelete(query) }

    fun reset() {
        secItemAdd = { attributes, result -> SecItemAdd(attributes, result) }
        secItemUpdate = { query, attributes -> SecItemUpdate(query, attributes) }
        secItemCopyMatching = { query, result -> SecItemCopyMatching(query, result) }
        secItemDelete = { query -> SecItemDelete(query) }
    }
}

/**
 * iOS `TokenStore` implementation.
 *
 * - `AuthSession` is stored in the Keychain (`kSecClassGenericPassword`).
 * - Non-sensitive settings are stored in `NSUserDefaults`.
 *
 * The primary constructor takes an explicit `service` and `userDefaults` so the
 * `iosTest` suite can isolate tests from the real app's Keychain item and
 * standard defaults. The no-arg constructor derives the service from the app
 * bundle identifier and uses `NSUserDefaults.standardUserDefaults`.
 */
internal class IosTokenStore(
    private val service: String,
    private val userDefaults: NSUserDefaults,
) : TokenStore {

    constructor() : this(
        service = resolveService(),
        userDefaults = NSUserDefaults.standardUserDefaults,
    )

    private val json = TokenStoreJson
    private val keychainMutex = Mutex()
    private var firstLaunchChecked = false // guarded by keychainMutex

    private suspend fun <T> withKeychain(block: () -> T): T =
        keychainMutex.withLock {
            if (!firstLaunchChecked) {
                if (!userDefaults.boolForKey(KEY_HAS_LAUNCHED_BEFORE)) {
                    // Reinstall: NSUserDefaults was wiped but the Keychain item
                    // may have survived. Delete any stale item before use.
                    deleteSessionItem()
                    userDefaults.setBool(true, forKey = KEY_HAS_LAUNCHED_BEFORE)
                }
                firstLaunchChecked = true
            }
            block()
        }

    // ---- (A) AuthSession in Keychain ------------------------------------

    override suspend fun saveSession(session: AuthSession) = withKeychain {
        val data = json.encodeToString(session).encodeToByteArray()
        // Fail fast if any CF allocation fails: a missing service/account would
        // silently widen the query to every generic-password item.
        withCFRefs {
            val cfService = keep(createCFString(service), "CFStringCreateWithCString failed")
            val cfAccount = keep(createCFString(KEYCHAIN_ACCOUNT), "CFStringCreateWithCString failed")
            val cfData = keep(createCFData(data), "CFDataCreate failed")

            val addStatus = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to cfService,
                kSecAttrAccount to cfAccount,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                kSecValueData to cfData,
            ) { attributes -> IosKeychainOps.secItemAdd(attributes, null) }

            when (addStatus) {
                errSecSuccess -> Unit
                errSecDuplicateItem -> {
                    val updateStatus = cfDictionaryOf(
                        kSecValueData to cfData,
                        kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                    ) { attributes ->
                        cfDictionaryOf(
                            kSecClass to kSecClassGenericPassword,
                            kSecAttrService to cfService,
                            kSecAttrAccount to cfAccount,
                        ) { query -> IosKeychainOps.secItemUpdate(query, attributes) }
                    }
                    if (updateStatus != errSecSuccess) {
                        throw IllegalStateException("SecItemUpdate failed with OSStatus $updateStatus")
                    }
                }
                else -> throw IllegalStateException("SecItemAdd failed with OSStatus $addStatus")
            }
        }
    }

    override suspend fun loadSession(): AuthSession? = withKeychain {
        withCFRefs {
            val cfService = keep(createCFString(service), "CFStringCreateWithCString failed")
            val cfAccount = keep(createCFString(KEYCHAIN_ACCOUNT), "CFStringCreateWithCString failed")
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = cfDictionaryOf(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to cfService,
                    kSecAttrAccount to cfAccount,
                    kSecReturnData to kCFBooleanTrue,
                    kSecMatchLimit to kSecMatchLimitOne,
                ) { query -> IosKeychainOps.secItemCopyMatching(query, result.ptr) }

                when (status) {
                    errSecSuccess -> decodeSessionOrNull(result.value)
                    errSecItemNotFound -> null
                    // errSecInteractionNotAllowed and any other unexpected OSStatus:
                    // throw WITHOUT deleting the Keychain item.
                    else -> throw IllegalStateException("SecItemCopyMatching failed with OSStatus $status")
                }
            }
        }
    }

    override suspend fun clearSession() = withKeychain {
        deleteSessionItem()
    }

    private fun decodeSessionOrNull(cfType: CFTypeRef?): AuthSession? {
        if (cfType == null) return null
        val nsData = CFBridgingRelease(cfType) as? NSData
        if (nsData == null) {
            // Unexpected type in the Keychain item: treat as corrupt.
            deleteSessionItem()
            return null
        }
        val len = nsData.length.toInt()
        // Copy explicitly instead of `bytes?.readBytes(len)`: `readBytes` keeps a
        // pointer into `nsData` while reading, which is unsafe if ARC releases
        // `nsData` mid-copy in optimized builds.
        val bytes = ByteArray(len)
        if (len > 0) {
            bytes.usePinned { pinned ->
                nsData.getBytes(pinned.addressOf(0), length = len.toULong())
            }
        }
        val text = try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (_: Exception) {
            // Non-UTF8 data: treat as corrupt.
            deleteSessionItem()
            return null
        }
        return try {
            json.decodeFromString<AuthSession>(text)
        } catch (_: Exception) {
            // Corrupted JSON: delete and report as signed-out.
            deleteSessionItem()
            null
        }
    }

    private fun deleteSessionItem() {
        withCFRefs {
            val cfService = keep(createCFString(service), "CFStringCreateWithCString failed")
            val cfAccount = keep(createCFString(KEYCHAIN_ACCOUNT), "CFStringCreateWithCString failed")
            val status = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to cfService,
                kSecAttrAccount to cfAccount,
            ) { query -> IosKeychainOps.secItemDelete(query) }
            // errSecSuccess and errSecItemNotFound both mean "no item remains".
            if (status != errSecSuccess && status != errSecItemNotFound) {
                throw IllegalStateException("SecItemDelete failed with OSStatus $status")
            }
        }
    }

    // ---- (B) Non-sensitive settings in NSUserDefaults -------------------

    override suspend fun saveClientId(clientId: String) {
        userDefaults.setObject(clientId, forKey = KEY_CLIENT_ID)
    }

    override suspend fun loadClientId(): String? =
        userDefaults.stringForKey(KEY_CLIENT_ID)

    override suspend fun saveThemePreferences(prefs: ThemePreferences) {
        userDefaults.setObject(json.encodeToString(prefs), forKey = KEY_THEME_PREFS)
    }

    override suspend fun loadThemePreferences(): ThemePreferences {
        val raw = userDefaults.stringForKey(KEY_THEME_PREFS) ?: return ThemePreferences()
        return runCatching { json.decodeFromString<ThemePreferences>(raw) }
            .getOrDefault(ThemePreferences())
    }

    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {
        userDefaults.setObject(json.encodeToString(prefs), forKey = KEY_NOTIF_PREFS)
    }

    override suspend fun loadNotificationPreferences(): NotificationPreferences {
        val raw = userDefaults.stringForKey(KEY_NOTIF_PREFS) ?: return NotificationPreferences()
        return runCatching { json.decodeFromString<NotificationPreferences>(raw) }
            .getOrDefault(NotificationPreferences())
    }

    override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) {
        userDefaults.setObject(json.encodeToString(markers), forKey = KEY_SEEN_NOTIFS)
    }

    override suspend fun loadSeenNotificationMarkers(): Map<String, String> {
        val raw = userDefaults.stringForKey(KEY_SEEN_NOTIFS) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    override suspend fun savePinnedRepos(repos: List<String>) {
        userDefaults.setObject(json.encodeToString(repos), forKey = KEY_PINNED_REPOS)
    }

    override suspend fun loadPinnedRepos(): List<String> {
        val raw = userDefaults.stringForKey(KEY_PINNED_REPOS) ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEYCHAIN_ACCOUNT = "github_auth_session"
        private const val DEFAULT_BUNDLE_ID = "io.github.neutron42-jp.gitmax"

        private const val KEY_CLIENT_ID = "github_client_id"
        private const val KEY_THEME_PREFS = "theme_prefs"
        private const val KEY_NOTIF_PREFS = "notif_prefs"
        private const val KEY_SEEN_NOTIFS = "seen_notifs"
        private const val KEY_PINNED_REPOS = "pinned_repos"
        private const val KEY_HAS_LAUNCHED_BEFORE = "has_launched_before"

        internal fun resolveService(): String {
            val bundleId = NSBundle.mainBundle.bundleIdentifier
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_BUNDLE_ID
            return "$bundleId.session"
        }
    }
}

/**
 * Tracks the CoreFoundation objects allocated for a single `SecItem*` call so
 * that every one of them is released when the scope closes — including when a
 * later allocation fails and throws.
 */
private class CFRefScope {
    private val refs = mutableListOf<CFTypeRef>()

    /** Registers [allocation], or releases everything and throws if it is null. */
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

private inline fun <T> withCFRefs(block: CFRefScope.() -> T): T {
    val scope = CFRefScope()
    return try {
        scope.block()
    } finally {
        scope.close()
    }
}

private fun createCFString(value: String): CFStringRef? =
    CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8)

private fun createCFData(bytes: ByteArray): CFDataRef? =
    if (bytes.isEmpty()) {
        // `addressOf(0)` is invalid for an empty array.
        CFDataCreate(kCFAllocatorDefault, null, 0L)
    } else {
        bytes.usePinned { pinned ->
            CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
        }
    }

/**
 * Creates an `NSDictionary`-backed query, runs [block], then releases it.
 *
 * Returns only a non-null dictionary: passing `null` to `SecItem*` would drop
 * the class/service/account filters and widen the operation to every item.
 */
private inline fun cfDictionaryOf(
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
            // Never silently drop a filter: a missing key/value would make
            // `SecItemCopyMatching`/`SecItemUpdate`/`SecItemDelete` match items
            // other than the one this store owns.
            val nonNullKey = key ?: error("Keychain query key must not be null")
            val nonNullValue = value ?: error("Keychain query value must not be null")
            CFDictionaryAddValue(dict, nonNullKey, nonNullValue)
        }
        block(dict)
    } finally {
        cfRelease(dict)
    }
}

/** Null-safe `CFRelease` (the C function crashes on `NULL`). */
private fun cfRelease(ref: CFTypeRef?) {
    if (ref != null) CFRelease(ref)
}

actual fun createPlatformTokenStore(): TokenStore = IosTokenStore()

actual fun openUrlInBrowser(url: String): Boolean {
    val nsUrl = parseHttpUrl(url) ?: return false

    fun open() {
        UIApplication.sharedApplication.openURL(
            url = nsUrl,
            options = emptyMap<Any?, Any>(),
            completionHandler = { success ->
                if (!success) {
                    // Deliberately avoid logging query parameters.
                    val scheme = nsUrl.scheme ?: "unknown"
                    val host = nsUrl.host ?: "unknown"
                    NSLog("openUrlInBrowser: OS failed to open URL (scheme=$scheme host=$host)")
                }
            },
        )
    }

    if (NSThread.isMainThread) {
        open()
    } else {
        dispatch_async(dispatch_get_main_queue()) { open() }
    }
    // `true` means "request accepted", not "did open".
    return true
}

/**
 * iOS `shareText` implementation (see the `expect` declaration in
 * `core/auth/src/commonMain/kotlin/dev/maxxing/github/auth/TokenStore.kt`).
 *
 * `core/auth` cannot depend on `composeApp` (the dependency runs the other
 * way), so the host application registers the Compose root view controller
 * here from `composeApp/src/iosMain/.../MainViewController.kt`. The reference
 * is weak so holding it does not keep the controller alive.
 *
 * Presents strictly from the registered controller rather than reaching for
 * `UIApplication.sharedApplication` `keyWindow` / `windows`. When no
 * controller is registered, or it is not attached to a window, or we are off
 * the main thread, return `false` so callers fall back to the clipboard path.
 */
object IosSharePresenter {
    private var weakController: kotlin.native.ref.WeakReference<platform.UIKit.UIViewController>? = null

    fun setRootViewController(controller: platform.UIKit.UIViewController?) {
        weakController = controller?.let { kotlin.native.ref.WeakReference(it) }
    }

    fun clearRootViewController() {
        weakController = null
    }

    fun currentController(): platform.UIKit.UIViewController? = weakController?.get()
}

actual fun shareText(text: String, title: String?): Boolean {
    if (text.isBlank()) return false
    val presenter = IosSharePresenter.currentController() ?: return false

    // UIKit presentation is main-thread only. Reporting `true` off the main
    // thread would make the caller skip its clipboard fallback while nothing
    // is ever presented.
    if (!NSThread.isMainThread) return false

    // Resolve the controller that will actually present, then require *it* to
    // be on screen: a controller that is not in a window hierarchy, or that is
    // being dismissed, cannot present; return false instead of reporting an
    // accepted share that never appears.
    var topController = presenter
    while (true) {
        val presented = topController.presentedViewController ?: break
        topController = presented
    }
    if (topController.isBeingDismissed()) return false
    val anchorView = topController.viewIfLoaded ?: return false
    if (anchorView.window == null) return false

    // The activity's `title` parameter has no reliable UIKit equivalent
    // without fragile KVC or a custom `UIActivityItemSource`; sharing the text
    // itself is the contract that matters (the caller already used `title` for
    // its own UI).
    val activityViewController = platform.UIKit.UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null,
    )

    // iPad requires a popover anchor; without one UIKit raises an exception.
    activityViewController.popoverPresentationController?.let { popover ->
        popover.sourceView = anchorView
        val centerX = anchorView.bounds.useContents { size.width / 2.0 }
        val centerY = anchorView.bounds.useContents { size.height / 2.0 }
        popover.sourceRect = platform.CoreGraphics.CGRectMake(centerX, centerY, 0.0, 0.0)
        popover.permittedArrowDirections = 0uL
    }

    topController.presentViewController(activityViewController, animated = true, completion = null)
    return true
}

private fun parseHttpUrl(url: String): NSURL? {
    if (url.isBlank()) return null
    val nsUrl = NSURL.URLWithString(url) ?: return null
    val scheme = nsUrl.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    if (nsUrl.host.isNullOrBlank()) return null
    return nsUrl
}

// GitHub web link handling settings are an Android-only concept (domain verification); iOS reports
// "not supported" like Desktop, so the Settings section stays hidden.
actual fun inspectGitHubLinkHandling(): GitHubLinkHandlingState = GitHubLinkHandlingState()

actual fun openThisAppLinkSettings(): Boolean = false

actual fun openOfficialGitHubAppLinkSettings(): Boolean = false
