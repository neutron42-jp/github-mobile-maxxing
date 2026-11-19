package dev.maxxing.github.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Test support for the simulator E2E suite (`iosApp/iosAppUITests`).
 *
 * The app only offers GitHub Device Flow on its sign-in screen (upstream removed the Personal Access
 * Token form), which cannot be automated. The Swift host calls this from a `#if DEBUG` block only
 * (see `SceneDelegate.swift`), so Release builds never reach it, and only when the UI test runner
 * puts a token into the launch environment. The token is passed straight to `signInWithPat`; it is
 * never logged or stored anywhere else.
 */
object IosDebugHooks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Signs in with [token] after [delayMillis] (a delay lets a test deliver a link while signed out). */
    fun signInWithPatForTesting(token: String, delayMillis: Long) {
        scope.launch {
            if (delayMillis > 0) delay(delayMillis)
            runCatching {
                IosAppGraph.ensureSessionLoaded()
                IosAppGraph.container.sessionManager.signInWithPat(token).getOrThrow()
            }.onFailure { error ->
                iosLog("IosDebugHooks: signInWithPat failed (${error::class.simpleName ?: "Throwable"})")
            }
        }
    }
}
