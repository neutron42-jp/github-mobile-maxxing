package dev.maxxing.github.app

import androidx.compose.runtime.saveable.SaverScope
import dev.maxxing.github.model.RepoSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellNavStateTest {
    @Test
    fun bottomDestinationSelectionAlwaysClearsProfileAndOverlays() {
        val sampleRepo = RepoSummary(id = "r1", name = "app", owner = "hubot", fullName = "hubot/app")
        var state = ShellNavState(currentDestination = ShellDestination.HOME)
            .openUserProfile("hubot")
            .copy(
                overlayRepo = sampleRepo,
                overlayIssue = IssuePullTarget("hubot", "app", 9),
                overlayGistsUser = "hubot"
            )

        assertTrue(state.hasOverlay)

        // Switching to another bottom nav destination prioritizes bottom nav and clears profile stack/overlays
        state = state.selectBottomDestination(ShellDestination.REPOS)
        assertEquals(ShellDestination.REPOS, state.currentDestination)
        assertFalse(state.hasOverlay)
        assertTrue(state.profileBackStack.isEmpty())
        assertNull(state.overlayRepo)
        assertNull(state.overlayIssue)
        assertNull(state.overlayGistsUser)

        // Even re-tapping the currently active bottom destination while inside a profile returns to that root tab
        state = state.openUserProfile("alice")
        assertTrue(state.hasOverlay)
        state = state.selectBottomDestination(ShellDestination.REPOS)
        assertEquals(ShellDestination.REPOS, state.currentDestination)
        assertFalse(state.hasOverlay)
        assertTrue(state.tabReselectNonce > 0)
    }

    @Test
    fun repoStackPushesForkAndPopsBackToUpstreamBeforeClosingOverlay() {
        val upstream = RepoSummary(id = "r1", name = "app", owner = "upstream", fullName = "upstream/app")
        val fork = RepoSummary(id = "r2", name = "app", owner = "forker", fullName = "forker/app", isFork = true)

        var state = ShellNavState(currentDestination = ShellDestination.HOME)
            .openRepo(upstream)
            .openRepo(fork)

        assertEquals("forker/app", state.activeRepo?.fullName)
        assertEquals(listOf("upstream/app", "forker/app"), state.repoBackStack.map { it.fullName })

        state = state.popBackOrReturnHome()
        assertEquals("upstream/app", state.activeRepo?.fullName)

        state = state.popBackOrReturnHome()
        assertNull(state.activeRepo)
        assertFalse(state.hasOverlay)
    }

    @Test
    fun saverKeepsOnlyTheDestinationAndFallsBackToHome() {
        val scope = SaverScope { true }
        val sampleRepo = RepoSummary(id = "r1", name = "app", owner = "hubot", fullName = "hubot/app")
        val state = ShellNavState(currentDestination = ShellDestination.SETTINGS)
            .openUserProfile("hubot")
            .openRepo(sampleRepo)
        val saved = with(ShellNavStateSaver) { scope.save(state) }
        assertEquals("SETTINGS", saved)
        assertEquals(ShellNavState(currentDestination = ShellDestination.SETTINGS), ShellNavStateSaver.restore(saved!!))
        assertEquals(ShellNavState(), ShellNavStateSaver.restore("REMOVED_TAB"))
    }

    @Test
    fun settingsSectionNavigatesAndPopsBeforeReturningHome() {
        var state = ShellNavState(currentDestination = ShellDestination.SETTINGS)
            .openSettingsSection(SettingsSection.APPEARANCE)

        assertEquals(ShellDestination.SETTINGS, state.currentDestination)
        assertEquals(SettingsSection.APPEARANCE, state.settingsSection)
        assertFalse(state.hasOverlay)

        // Back inside a settings sub-page returns to the Settings hub first.
        state = state.popBackOrReturnHome()
        assertEquals(ShellDestination.SETTINGS, state.currentDestination)
        assertNull(state.settingsSection)

        // Next back from the Settings hub returns to Home.
        state = state.popBackOrReturnHome()
        assertEquals(ShellDestination.HOME, state.currentDestination)
        assertNull(state.settingsSection)
    }

    @Test
    fun bottomDestinationSelectionClearsSettingsSectionAndResetsTab() {
        val inSection = ShellNavState(currentDestination = ShellDestination.SETTINGS)
            .openSettingsSection(SettingsSection.NOTIFICATIONS)

        val reselected = inSection.selectBottomDestination(ShellDestination.SETTINGS)
        assertEquals(ShellDestination.SETTINGS, reselected.currentDestination)
        assertNull(reselected.settingsSection)
        assertEquals(inSection.tabReselectNonce + 1, reselected.tabReselectNonce)

        val switchedHome = inSection.selectBottomDestination(ShellDestination.HOME)
        assertEquals(ShellDestination.HOME, switchedHome.currentDestination)
        assertNull(switchedHome.settingsSection)
    }

    @Test
    fun saverRoundTripsActiveSettingsSectionAndHandlesUnknownSection() {
        val scope = SaverScope { true }
        val state = ShellNavState(currentDestination = ShellDestination.SETTINGS)
            .openSettingsSection(SettingsSection.APPEARANCE)
        val saved = with(ShellNavStateSaver) { scope.save(state) }
        assertEquals("SETTINGS:APPEARANCE", saved)
        assertEquals(
            ShellNavState(
                currentDestination = ShellDestination.SETTINGS,
                settingsSection = SettingsSection.APPEARANCE
            ),
            ShellNavStateSaver.restore(saved!!)
        )
        assertEquals(
            ShellNavState(currentDestination = ShellDestination.SETTINGS, settingsSection = null),
            ShellNavStateSaver.restore("SETTINGS:UNKNOWN_SECTION")
        )
    }
}
