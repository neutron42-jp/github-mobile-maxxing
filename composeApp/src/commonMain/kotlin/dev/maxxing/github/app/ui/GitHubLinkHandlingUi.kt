package dev.maxxing.github.app.ui

import dev.maxxing.github.auth.GitHubLinkHandlingState

internal enum class LinkHandlingStatus {
    /** Official GitHub Mobile is verified/selected for github.com; the OS will not let this app be selected. */
    OFFICIAL_APP_CLAIMS,
    ENABLED_HERE,
    /** Official GitHub Mobile is installed but whether it claims github.com links could not be determined. */
    OFFICIAL_INSTALLED_UNKNOWN,
    NOT_ENABLED
}

internal data class LinkHandlingUi(
    val status: LinkHandlingStatus,
    val showUnlinkOfficial: Boolean,
    val showEnableHere: Boolean,
    val enableHereEnabled: Boolean,
    val showManageLink: Boolean
)

/** Maps the OS link-handling snapshot to which actions the Settings card should offer. */
internal fun linkHandlingUi(state: GitHubLinkHandlingState): LinkHandlingUi {
    val officialClaims = state.officialGitHubInstalled && state.officialGitHubHandlesLinks == true
    return when {
        // Checked first: a verified official app wins over this app's selection at the OS level.
        officialClaims -> LinkHandlingUi(
            status = LinkHandlingStatus.OFFICIAL_APP_CLAIMS,
            showUnlinkOfficial = true,
            showEnableHere = true,
            enableHereEnabled = false,
            showManageLink = false
        )
        state.thisAppHandlesGitHubLinks -> LinkHandlingUi(
            status = LinkHandlingStatus.ENABLED_HERE,
            showUnlinkOfficial = false,
            showEnableHere = false,
            enableHereEnabled = false,
            showManageLink = true
        )
        state.officialGitHubInstalled && state.officialGitHubHandlesLinks == null -> LinkHandlingUi(
            status = LinkHandlingStatus.OFFICIAL_INSTALLED_UNKNOWN,
            showUnlinkOfficial = true,
            showEnableHere = true,
            enableHereEnabled = true,
            showManageLink = false
        )
        // Unlinking stays available as a secondary action whenever the official app is installed.
        else -> LinkHandlingUi(
            status = LinkHandlingStatus.NOT_ENABLED,
            showUnlinkOfficial = state.officialGitHubInstalled,
            showEnableHere = true,
            enableHereEnabled = true,
            showManageLink = false
        )
    }
}
