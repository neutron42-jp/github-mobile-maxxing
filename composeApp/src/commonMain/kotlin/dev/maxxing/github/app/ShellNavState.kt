package dev.maxxing.github.app

import androidx.compose.runtime.saveable.Saver
import dev.maxxing.github.model.RepoSummary

enum class ShellDestination {
    HOME,
    INBOX,
    ISSUES_PRS,
    REPOS,
    SETTINGS
}

enum class SettingsSection {
    APPEARANCE,
    NOTIFICATIONS,
    GENERAL,
    APP_INFO
}

data class ShellNavState(
    val currentDestination: ShellDestination = ShellDestination.HOME,
    val settingsSection: SettingsSection? = null,
    val profileBackStack: List<String> = emptyList(),
    val repoBackStack: List<RepoSummary> = emptyList(),
    val repoBranchStack: List<String?> = emptyList(),
    val overlayRepo: RepoSummary? = null,
    val overlayIssue: IssuePullTarget? = null,
    val overlayGistsUser: String? = null,
    val tabReselectNonce: Int = 0
) {
    val activeRepo: RepoSummary?
        get() = repoBackStack.lastOrNull() ?: overlayRepo

    val activeRepoInitialBranch: String?
        get() = if (repoBackStack.isNotEmpty()) repoBranchStack.lastOrNull() else null

    val hasOverlay: Boolean
        get() = overlayGistsUser != null ||
            overlayIssue != null ||
            activeRepo != null ||
            profileBackStack.isNotEmpty()

    fun selectBottomDestination(destination: ShellDestination): ShellNavState {
        val cleared = copy(
            currentDestination = destination,
            settingsSection = null,
            profileBackStack = emptyList(),
            repoBackStack = emptyList(),
            repoBranchStack = emptyList(),
            overlayRepo = null,
            overlayIssue = null,
            overlayGistsUser = null,
            tabReselectNonce = if (currentDestination == destination || hasOverlay || settingsSection != null) {
                tabReselectNonce + 1
            } else {
                tabReselectNonce
            }
        )
        return cleared
    }

    fun openSettingsSection(section: SettingsSection): ShellNavState = copy(
        currentDestination = ShellDestination.SETTINGS,
        settingsSection = section
    )

    fun popSettingsSection(): ShellNavState = copy(settingsSection = null)

    fun openUserProfile(login: String): ShellNavState {
        if (login.isBlank()) return this
        return copy(
            repoBackStack = emptyList(),
            repoBranchStack = emptyList(),
            overlayRepo = null,
            overlayIssue = null,
            overlayGistsUser = null,
            profileBackStack = profileBackStack + login
        )
    }

    fun openRepo(repo: RepoSummary, initialBranch: String? = null): ShellNavState {
        val base = if (repoBackStack.isNotEmpty()) repoBackStack else listOfNotNull(overlayRepo)
        val baseBranches = if (repoBackStack.isNotEmpty()) {
            List(base.size) { idx -> repoBranchStack.getOrNull(idx) }
        } else {
            List(base.size) { null }
        }
        val cleanBranch = initialBranch?.trim()?.takeIf { it.isNotEmpty() }
        val replacingTop = base.lastOrNull()?.fullName?.equals(repo.fullName, ignoreCase = true) == true
        val next = if (replacingTop) base.dropLast(1) + repo else base + repo
        val nextBranches = if (replacingTop) baseBranches.dropLast(1) + cleanBranch else baseBranches + cleanBranch
        return copy(
            repoBackStack = next,
            repoBranchStack = nextBranches,
            overlayRepo = next.lastOrNull()
        )
    }

    fun popRepo(): ShellNavState {
        val base = if (repoBackStack.isNotEmpty()) repoBackStack else listOfNotNull(overlayRepo)
        val baseBranches = if (repoBackStack.isNotEmpty()) {
            List(base.size) { idx -> repoBranchStack.getOrNull(idx) }
        } else {
            List(base.size) { null }
        }
        val next = if (base.isNotEmpty()) base.dropLast(1) else emptyList()
        val nextBranches = if (baseBranches.isNotEmpty()) baseBranches.dropLast(1) else emptyList()
        return copy(
            repoBackStack = next,
            repoBranchStack = nextBranches,
            overlayRepo = next.lastOrNull()
        )
    }

    fun popBackOrReturnHome(): ShellNavState = when {
        overlayGistsUser != null -> copy(overlayGistsUser = null)
        overlayIssue != null -> copy(overlayIssue = null)
        activeRepo != null -> popRepo()
        profileBackStack.isNotEmpty() -> copy(profileBackStack = profileBackStack.dropLast(1))
        currentDestination == ShellDestination.SETTINGS && settingsSection != null -> copy(settingsSection = null)
        currentDestination != ShellDestination.HOME -> copy(currentDestination = ShellDestination.HOME)
        else -> this
    }
}

/**
 * Saves the bottom destination and active Settings sub-page, so the app comes back on the same
 * Settings view after Android recreates the process (for example while the system file picker is
 * open from Appearance Settings, whose result is then still delivered). Back stacks and overlays
 * start empty. An unknown name restores HOME.
 */
internal val ShellNavStateSaver: Saver<ShellNavState, String> = Saver(
    save = { state ->
        if (state.currentDestination == ShellDestination.SETTINGS && state.settingsSection != null) {
            "${state.currentDestination.name}:${state.settingsSection.name}"
        } else {
            state.currentDestination.name
        }
    },
    restore = { raw ->
        val parts = raw.split(':', limit = 2)
        val destination = ShellDestination.entries.firstOrNull { it.name == parts[0] } ?: ShellDestination.HOME
        val section = if (destination == ShellDestination.SETTINGS && parts.size == 2) {
            SettingsSection.entries.firstOrNull { it.name == parts[1] }
        } else {
            null
        }
        ShellNavState(
            currentDestination = destination,
            settingsSection = section
        )
    }
)
