@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package dev.maxxing.github.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.SlidersHorizontal
import dev.maxxing.github.app.SettingsSection
import dev.maxxing.github.auth.GitHubLinkHandlingState
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.ui.i18n.stringsFor
import dev.maxxing.github.ui.theme.MaxxingTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettingsScreenLayoutTest {

    @Test
    fun settingsHubCategoryGroupRendersFullWidthWithoutOverflowInBothLanguages() {
        for (language in listOf(AppLanguage.DEFAULT, AppLanguage.JAPANESE)) {
            val strings = stringsFor(language)
            val items = listOf(
                SettingsCategoryItem(
                    section = SettingsSection.APPEARANCE,
                    title = strings.settingsCategoryAppearance,
                    subtitle = formatAppearanceSummary(ThemePreferences(), strings),
                    icon = Lucide.Palette
                ),
                SettingsCategoryItem(
                    section = SettingsSection.NOTIFICATIONS,
                    title = strings.settingsCategoryNotifications,
                    subtitle = formatNotificationsSummary(
                        NotificationPreferences(urgentOnly = true, pollIntervalMinutes = 5),
                        osNotificationsBlocked = false,
                        strings = strings
                    ),
                    icon = Lucide.Bell
                ),
                SettingsCategoryItem(
                    section = SettingsSection.GENERAL,
                    title = strings.settingsCategoryGeneral,
                    subtitle = formatGeneralSummary(
                        language = language,
                        linkHandlingState = GitHubLinkHandlingState(
                            supportedOnPlatform = true,
                            thisAppHandlesGitHubLinks = true
                        ),
                        strings = strings
                    ),
                    icon = Lucide.SlidersHorizontal
                ),
                SettingsCategoryItem(
                    section = SettingsSection.APP_INFO,
                    title = strings.settingsCategoryAppInfo,
                    subtitle = formatAppBuildFooter("1.0.89", "f075a64"),
                    icon = Lucide.Info
                )
            )
            for (widthDp in listOf(360, 412)) {
                var groupBounds: Rect? = null
                val scene = ImageComposeScene(
                    width = widthDp,
                    height = 600,
                    density = Density(1f)
                ) {
                    MaxxingTheme(
                        useDynamicColor = false,
                        darkThemeOverride = false,
                        language = language
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            SettingsHubCategoryGroup(
                                items = items,
                                onSelect = {},
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    groupBounds = coords.boundsInRoot()
                                }
                            )
                        }
                    }
                }
                try {
                    scene.render()
                } finally {
                    scene.close()
                }
                val bounds = assertNotNull(groupBounds)
                assertEquals(
                    (widthDp - 40).toFloat(),
                    bounds.width,
                    "Category group should fill container at ${widthDp}dp ($language)"
                )
                assertTrue(
                    bounds.height in 200f..560f,
                    "Category group height (${bounds.height}) should fit cleanly inside viewport at ${widthDp}dp ($language)"
                )
            }
        }
    }
}
