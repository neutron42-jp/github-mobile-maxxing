package dev.maxxing.github.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import dev.maxxing.github.auth.IosSharePresenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.UIKit.UIViewController
import kotlin.concurrent.AtomicLong

/**
 * The one pending navigation request. `StateFlow` conflates equal values, so each
 * request carries a nonce that makes two consecutive taps on the same target distinct.
 */
sealed interface PendingNavigation {
    val nonce: Long

    /** Open the issue/PR detail of [target]. */
    data class Thread(val target: IssuePullTarget, override val nonce: Long) : PendingNavigation

    /** Show the Inbox tab (a notification without an issue/PR number). */
    data class Inbox(override val nonce: Long) : PendingNavigation
}

/**
 * Deep-link / notification-tap bridge between the Swift `AppDelegate` and the Compose UI.
 * Thread and Inbox requests share a single pending value with a "latest only" policy: a
 * newer request, of either kind, replaces the pending one. The UI consumes it after navigating
 * (while signed out it stays pending and is applied after sign-in).
 */
object IosDeepLinkBridge {
    private val _pending = MutableStateFlow<PendingNavigation?>(null)
    val pending: StateFlow<PendingNavigation?> = _pending.asStateFlow()

    private val nextNonce = AtomicLong(0L)

    /** Returns true when [url] is a valid `githubmaxxing://thread/...` link (strict parsing rules). */
    fun handleUrl(url: String): Boolean {
        val target = parseStrictDeepLink(url) ?: return false
        return handleTarget(target)
    }

    /** Records a parsed issue/PR target. Returns true (a valid target). */
    fun handleTarget(target: IssuePullTarget): Boolean {
        _pending.value = PendingNavigation.Thread(target, nextNonce.incrementAndGet())
        return true
    }

    /** Records a request to show the Inbox tab. */
    fun requestInbox() {
        _pending.value = PendingNavigation.Inbox(nextNonce.incrementAndGet())
    }

    /** Clears the pending request only if it is still the one that was consumed. */
    fun consume(nonce: Long) {
        val current = _pending.value
        if (current?.nonce == nonce) {
            _pending.compareAndSet(current, null)
        }
    }
}

/**
 * Swift-facing entry point. The overload with `onDarkThemeChanged` lets a host
 * controller update the status bar style when Compose reports an effective theme change.
 */
fun MainViewController(): UIViewController = createMainViewController(onDarkThemeChanged = null)

fun MainViewController(onDarkThemeChanged: (Boolean) -> Unit): UIViewController =
    createMainViewController(onDarkThemeChanged)

private fun createMainViewController(
    onDarkThemeChanged: ((Boolean) -> Unit)?,
): UIViewController {
    val controller = ComposeUIViewController {
        val pendingState by IosDeepLinkBridge.pending.collectAsState()
        val pending = pendingState

        GitHubMaxxingApp(
            // The shell parses the URL (`parseAppDeepLink`); the iOS entry already validated it
            // with `parseStrictDeepLink`, so this is the canonical notification link of that target.
            deepLinkUrl = (pending as? PendingNavigation.Thread)?.target
                ?.let { buildThreadDeepLinkUrl(it.owner, it.repo, it.number) },
            deepLinkNonce = (pending as? PendingNavigation.Thread)?.nonce ?: 0L,
            onConsumeDeepLink = { pending?.let { IosDeepLinkBridge.consume(it.nonce) } },
            inboxRequest = (pending as? PendingNavigation.Inbox)?.nonce,
            onConsumeInboxRequest = { pending?.let { IosDeepLinkBridge.consume(it.nonce) } },
            onDarkThemeChanged = { dark -> onDarkThemeChanged?.invoke(dark) },
            container = IosAppGraph.container,
            initializeSession = { IosAppGraph.ensureSessionLoaded() },
        )
    }

    // `shareText` presents from this controller instead of accessing `keyWindow`.
    // The reference held by IosSharePresenter is weak.
    IosSharePresenter.setRootViewController(controller)
    return controller
}
