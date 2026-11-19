package dev.maxxing.github.app.notifications

/**
 * Single master switch for iOS APNs & OS notification features.
 *
 * While [APNS_ENABLED] is `false`:
 * - APNs registration and device-token callbacks are skipped,
 * - incoming push / local notification delegates and Mark Read / Done actions are disabled,
 * - local-notification fallback and test notifications are suppressed,
 * - OS notification permission prompts and blocked-state UI are suppressed,
 * while GitHub Inbox fetching/syncing and unread badges remain active.
 *
 * Flip [APNS_ENABLED] to `true` once the push-server specification is finalized to re-enable
 * all iOS APNs & OS notification features.
 */
object IosPushFeature {
    const val APNS_ENABLED: Boolean = false

    internal var enabledOverrideForTesting: Boolean? = null

    val isApnsEnabled: Boolean
        get() = enabledOverrideForTesting ?: APNS_ENABLED
}
