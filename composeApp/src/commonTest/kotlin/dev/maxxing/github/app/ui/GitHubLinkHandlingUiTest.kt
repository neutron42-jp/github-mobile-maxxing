package dev.maxxing.github.app.ui

import dev.maxxing.github.auth.GitHubLinkHandlingState
import kotlin.test.Test
import kotlin.test.assertEquals

class GitHubLinkHandlingUiTest {
    private fun state(
        thisAppHandles: Boolean = false,
        officialInstalled: Boolean = false,
        officialHandles: Boolean? = false
    ) = GitHubLinkHandlingState(
        supportedOnPlatform = true,
        thisAppHandlesGitHubLinks = thisAppHandles,
        officialGitHubInstalled = officialInstalled,
        officialGitHubHandlesLinks = officialHandles
    )

    @Test
    fun enabledHereHidesUnlinkAndEnableAndOffersOnlyManageLink() {
        val expected = LinkHandlingUi(
            status = LinkHandlingStatus.ENABLED_HERE,
            showUnlinkOfficial = false,
            showEnableHere = false,
            enableHereEnabled = false,
            showManageLink = true
        )
        assertEquals(expected, linkHandlingUi(state(thisAppHandles = true)))
        // The official app being installed but not claiming links must not bring back the unlink button.
        assertEquals(expected, linkHandlingUi(state(thisAppHandles = true, officialInstalled = true, officialHandles = false)))
        assertEquals(expected, linkHandlingUi(state(thisAppHandles = true, officialInstalled = true, officialHandles = null)))
    }

    @Test
    fun officialAppClaimsMakesUnlinkPrimaryAndDisablesEnable() {
        assertEquals(
            LinkHandlingUi(
                status = LinkHandlingStatus.OFFICIAL_APP_CLAIMS,
                showUnlinkOfficial = true,
                showEnableHere = true,
                enableHereEnabled = false,
                showManageLink = false
            ),
            linkHandlingUi(state(officialInstalled = true, officialHandles = true))
        )
    }

    @Test
    fun officialAppClaimsTakesPriorityOverEnabledHere() {
        val ui = linkHandlingUi(state(thisAppHandles = true, officialInstalled = true, officialHandles = true))
        assertEquals(LinkHandlingStatus.OFFICIAL_APP_CLAIMS, ui.status)
        assertEquals(true, ui.showUnlinkOfficial)
        assertEquals(false, ui.showManageLink)
    }

    @Test
    fun officialInstalledWithUnknownClaimEnablesBothActions() {
        assertEquals(
            LinkHandlingUi(
                status = LinkHandlingStatus.OFFICIAL_INSTALLED_UNKNOWN,
                showUnlinkOfficial = true,
                showEnableHere = true,
                enableHereEnabled = true,
                showManageLink = false
            ),
            linkHandlingUi(state(officialInstalled = true, officialHandles = null))
        )
    }

    @Test
    fun notEnabledOffersEnableAndUnlinkOnlyWhenOfficialInstalled() {
        val expected = LinkHandlingUi(
            status = LinkHandlingStatus.NOT_ENABLED,
            showUnlinkOfficial = false,
            showEnableHere = true,
            enableHereEnabled = true,
            showManageLink = false
        )
        assertEquals(expected, linkHandlingUi(state()))
        assertEquals(
            expected.copy(showUnlinkOfficial = true),
            linkHandlingUi(state(officialInstalled = true, officialHandles = false))
        )
        // A stale "claims" flag without the official app installed is ignored.
        assertEquals(expected, linkHandlingUi(state(officialInstalled = false, officialHandles = true)))
    }
}
