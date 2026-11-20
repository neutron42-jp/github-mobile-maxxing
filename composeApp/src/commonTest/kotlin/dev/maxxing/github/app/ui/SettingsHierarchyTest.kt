package dev.maxxing.github.app.ui

import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Lucide
import dev.maxxing.github.app.AppUpdateCheckState
import dev.maxxing.github.app.PlatformCapabilities
import dev.maxxing.github.app.SettingsSection
import dev.maxxing.github.app.update.AppBuildVersion
import dev.maxxing.github.app.update.AvailableAppUpdate
import dev.maxxing.github.auth.GitHubLinkHandlingState
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import dev.maxxing.github.model.ReleaseAsset
import dev.maxxing.github.model.ReleaseSummary
import dev.maxxing.github.ui.i18n.EnglishStrings
import dev.maxxing.github.ui.i18n.JapaneseStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettingsHierarchyTest {

    @Test
    fun formatAppearanceSummaryCoversModesColorsAndFonts() {
        val dynamicSystem = ThemePreferences(
            useDynamicColor = true,
            darkModeOverride = null
        )
        assertEquals("System · Dynamic Color", formatAppearanceSummary(dynamicSystem, EnglishStrings))
        assertEquals("システム · ダイナミックカラー", formatAppearanceSummary(dynamicSystem, JapaneseStrings))
        assertEquals(
            "System · Graphite",
            formatAppearanceSummary(dynamicSystem, EnglishStrings, supportsDynamicColor = false)
        )
        assertEquals(
            "システム · Graphite",
            formatAppearanceSummary(dynamicSystem, JapaneseStrings, supportsDynamicColor = false)
        )

        val darkPresetWithPrimaryFont = ThemePreferences(
            seedColorHex = 0xFF6750A4,
            useDynamicColor = false,
            darkModeOverride = true,
            primaryFont = FontSelection(
                kind = FontSourceKind.SYSTEM_FAMILY,
                value = "Inter",
                displayName = "Inter"
            )
        )
        assertEquals("Dark · Iris · Inter", formatAppearanceSummary(darkPresetWithPrimaryFont, EnglishStrings))
        assertEquals("ダーク · Iris · Inter", formatAppearanceSummary(darkPresetWithPrimaryFont, JapaneseStrings))

        val lightCustomWithFallbackFont = ThemePreferences(
            seedColorHex = 0xFF123456,
            useDynamicColor = false,
            darkModeOverride = false,
            fallbackFont = FontSelection(
                kind = FontSourceKind.CUSTOM_FILE,
                value = "/fonts/NotoSansJP.ttf",
                displayName = "Noto Sans JP"
            )
        )
        assertEquals(
            "Light · Custom · Noto Sans JP",
            formatAppearanceSummary(lightCustomWithFallbackFont, EnglishStrings)
        )
        assertEquals(
            "ライト · カスタム · Noto Sans JP",
            formatAppearanceSummary(lightCustomWithFallbackFont, JapaneseStrings)
        )
    }

    @Test
    fun formatNotificationsSummaryCoversDisabledBlockedUrgentAndNormal() {
        val off = NotificationPreferences(osNotificationsEnabled = false, pollIntervalMinutes = 5)
        assertEquals("Off", formatNotificationsSummary(off, osNotificationsBlocked = true, EnglishStrings))
        assertEquals("オフ", formatNotificationsSummary(off, osNotificationsBlocked = true, JapaneseStrings))

        val blocked = NotificationPreferences(osNotificationsEnabled = true, pollIntervalMinutes = 5)
        assertEquals(
            "Blocked by system",
            formatNotificationsSummary(blocked, osNotificationsBlocked = true, EnglishStrings)
        )
        assertEquals(
            "システムでブロック中",
            formatNotificationsSummary(blocked, osNotificationsBlocked = true, JapaneseStrings)
        )

        val urgent = NotificationPreferences(osNotificationsEnabled = true, urgentOnly = true, pollIntervalMinutes = 5)
        assertEquals(
            "Urgent Only · 5 min",
            formatNotificationsSummary(urgent, osNotificationsBlocked = false, EnglishStrings)
        )
        assertEquals(
            "重要な通知のみ · 5 分",
            formatNotificationsSummary(urgent, osNotificationsBlocked = false, JapaneseStrings)
        )

        val normal = NotificationPreferences(osNotificationsEnabled = true, urgentOnly = false, pollIntervalMinutes = 1)
        assertEquals(
            "On · 1 min",
            formatNotificationsSummary(normal, osNotificationsBlocked = false, EnglishStrings)
        )
        assertEquals(
            "オン · 1 分",
            formatNotificationsSummary(normal, osNotificationsBlocked = false, JapaneseStrings)
        )

        assertEquals(
            "1 min",
            formatNotificationsSummary(
                normal,
                osNotificationsBlocked = true,
                EnglishStrings,
                supportsOsNotifications = false
            )
        )
        assertEquals(
            "5 分",
            formatNotificationsSummary(
                off,
                osNotificationsBlocked = true,
                JapaneseStrings,
                supportsOsNotifications = false
            )
        )
    }

    @Test
    fun buildSettingsCategoryItemsAppliesIosCapabilitiesToAppearanceAndNotifications() {
        val iosCapabilities = PlatformCapabilities(
            usesIosCopy = true,
            supportsDynamicColor = false,
            supportsDynamicColorToggle = false,
            supportsCustomFonts = false,
            avoidsKeyboardOnSignIn = true,
            supportsOsNotifications = false,
            supportsInAppUpdates = false
        )
        val availableUpdate = AppUpdateCheckState.Available(
            AvailableAppUpdate(
                release = ReleaseSummary(
                    id = 1L,
                    tagName = "v1.0.90",
                    name = "v1.0.90",
                    isDraft = false,
                    isPrerelease = false,
                    publishedAt = "2026-11-19T00:00:00Z",
                    assets = emptyList(),
                    htmlUrl = "https://github.com/neutron42-jp/github-mobile-maxxing/releases/tag/v1.0.90"
                ),
                asset = ReleaseAsset(
                    id = 2L,
                    name = "app.apk",
                    sizeBytes = 100L,
                    downloadUrl = "https://example.com/app.apk",
                    contentType = "application/vnd.android.package-archive"
                ),
                versionLabel = "v1.0.90",
                versionKey = "v1.0.90:v1.0.90",
                parsedVersion = AppBuildVersion(1, 0, 90, isNightly = false, commitSha = null),
                isPrerelease = false
            )
        )
        val items = buildSettingsCategoryItems(
            themePrefs = ThemePreferences(useDynamicColor = true, darkModeOverride = null),
            notifPrefs = NotificationPreferences(
                osNotificationsEnabled = false,
                urgentOnly = true,
                pollIntervalMinutes = 1
            ),
            osNotificationsBlocked = true,
            linkHandlingState = GitHubLinkHandlingState(supportedOnPlatform = false),
            strings = EnglishStrings,
            capabilities = iosCapabilities,
            versionName = "1.0.89",
            commitSha = "abc1234",
            updateCheckState = availableUpdate
        )
        val appearance = items.first { it.section == SettingsSection.APPEARANCE }
        assertEquals("System · Graphite", appearance.subtitle)

        val notifications = items.first { it.section == SettingsSection.NOTIFICATIONS }
        assertEquals("1 min", notifications.subtitle)
        assertEquals(Lucide.Bell, notifications.icon)
        assertFalse(notifications.isWarning)

        val appInfo = items.first { it.section == SettingsSection.APP_INFO }
        assertEquals("v1.0.89 (abc1234)", appInfo.subtitle)

        assertEquals(
            "v1.0.89 (abc1234) · Update available: v1.0.90",
            buildSettingsAppInfoSubtitle(
                versionName = "1.0.89",
                commitSha = "abc1234",
                updateCheckState = availableUpdate,
                supportsInAppUpdates = true,
                strings = EnglishStrings
            )
        )
    }

    @Test
    fun appearanceSummarySeparatesWallpaperAvailabilityFromTheToggle() {
        // iOS / Desktop / Android 26-30: no wallpaper palette, so the saved Graphite preset is named.
        val iosLike = PlatformCapabilities(supportsDynamicColor = false, supportsDynamicColorToggle = false)
        assertEquals(
            "System · Graphite",
            buildSettingsCategoryItems(
                themePrefs = ThemePreferences(useDynamicColor = true),
                notifPrefs = NotificationPreferences(),
                osNotificationsBlocked = false,
                linkHandlingState = GitHubLinkHandlingState(supportedOnPlatform = false),
                strings = EnglishStrings,
                capabilities = iosLike
            ).first { it.section == SettingsSection.APPEARANCE }.subtitle
        )
        val desktopLike = PlatformCapabilities(supportsDynamicColor = false, supportsDynamicColorToggle = false)
        assertEquals(
            "System · Graphite",
            buildSettingsCategoryItems(
                themePrefs = ThemePreferences(useDynamicColor = true),
                notifPrefs = NotificationPreferences(),
                osNotificationsBlocked = false,
                linkHandlingState = GitHubLinkHandlingState(supportedOnPlatform = false),
                strings = EnglishStrings,
                capabilities = desktopLike
            ).first { it.section == SettingsSection.APPEARANCE }.subtitle
        )
        // Android 12+: the wallpaper palette is applied, so the summary says Dynamic Color.
        val android12 = PlatformCapabilities(supportsDynamicColor = true, supportsDynamicColorToggle = true)
        assertEquals(
            "System · Dynamic Color",
            buildSettingsCategoryItems(
                themePrefs = ThemePreferences(useDynamicColor = true),
                notifPrefs = NotificationPreferences(),
                osNotificationsBlocked = false,
                linkHandlingState = GitHubLinkHandlingState(supportedOnPlatform = true),
                strings = EnglishStrings,
                capabilities = android12
            ).first { it.section == SettingsSection.APPEARANCE }.subtitle
        )
        // Graphite selected explicitly with wallpaper colors off names Graphite.
        assertEquals(
            "System · Graphite",
            buildSettingsCategoryItems(
                themePrefs = ThemePreferences(useDynamicColor = false),
                notifPrefs = NotificationPreferences(),
                osNotificationsBlocked = false,
                linkHandlingState = GitHubLinkHandlingState(supportedOnPlatform = true),
                strings = EnglishStrings,
                capabilities = android12
            ).first { it.section == SettingsSection.APPEARANCE }.subtitle
        )
        // A custom color equal to Graphite's seed stays Custom, not Graphite.
        assertEquals(
            "System · Custom",
            buildSettingsCategoryItems(
                themePrefs = ThemePreferences(
                    seedColorHex = 0xFF606060,
                    monochromePresetSelected = false,
                    useDynamicColor = false
                ),
                notifPrefs = NotificationPreferences(),
                osNotificationsBlocked = false,
                linkHandlingState = GitHubLinkHandlingState(supportedOnPlatform = true),
                strings = EnglishStrings,
                capabilities = android12
            ).first { it.section == SettingsSection.APPEARANCE }.subtitle
        )
    }

    @Test
    fun formatGeneralSummaryCoversDesktopAndAndroidLinkHandling() {
        val desktopLinks = GitHubLinkHandlingState(supportedOnPlatform = false)
        assertEquals("English", formatGeneralSummary(AppLanguage.DEFAULT, desktopLinks, EnglishStrings))
        assertEquals("日本語", formatGeneralSummary(AppLanguage.JAPANESE, desktopLinks, JapaneseStrings))

        val androidEnabled = GitHubLinkHandlingState(
            supportedOnPlatform = true,
            thisAppHandlesGitHubLinks = true
        )
        assertEquals(
            "English · github.com links on",
            formatGeneralSummary(AppLanguage.DEFAULT, androidEnabled, EnglishStrings)
        )
        assertEquals(
            "日本語 · github.com リンク有効",
            formatGeneralSummary(AppLanguage.JAPANESE, androidEnabled, JapaneseStrings)
        )

        val androidDisabled = GitHubLinkHandlingState(
            supportedOnPlatform = true,
            thisAppHandlesGitHubLinks = false
        )
        assertEquals(
            "English · github.com links off",
            formatGeneralSummary(AppLanguage.DEFAULT, androidDisabled, EnglishStrings)
        )
        assertEquals(
            "日本語 · github.com リンク無効",
            formatGeneralSummary(AppLanguage.JAPANESE, androidDisabled, JapaneseStrings)
        )
    }
}
