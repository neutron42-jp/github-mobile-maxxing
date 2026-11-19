package dev.maxxing.github.app.ui

import dev.maxxing.github.app.IssuePullTarget

/**
 * Stable identity key for an issue/PR detail pane. Excludes [IssuePullTarget.hintTitle] so
 * updating or omitting the display hint never resets in-progress drafts or open dialogs.
 */
internal fun issuePullIdentityKey(target: IssuePullTarget): Triple<String, String, Int> =
    Triple(target.owner.trim().lowercase(), target.repo.trim().lowercase(), target.number)

/**
 * Non-Compose monotonic sequence guard for dropping stale async responses without
 * triggering extra recompositions on every request start.
 */
internal class LatestRequestGuard {
    private var current: Int = 0

    fun next(): Int = ++current

    fun isLatest(token: Int): Boolean = token == current
}
