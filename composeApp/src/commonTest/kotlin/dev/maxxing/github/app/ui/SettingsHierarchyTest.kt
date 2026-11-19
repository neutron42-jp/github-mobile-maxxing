package dev.maxxing.github.app.ui

import dev.maxxing.github.auth.GitHubLinkHandlingState
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import dev.maxxing.github.ui.i18n.EnglishStrings
import dev.maxxing.github.ui.i18n.JapaneseStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsHierarchyTest {

    @Test
    fun formatAppearanceSummaryCoversModesColorsAndFonts() {
        val dynamicSystem = ThemePreferences(
            useDynamicColor = true,
            darkModeOverride = null
        )
        assertEquals("System · Dynamic Color", formatAppearanceSummary(dynamicSystem, EnglishStrings))
        assertEquals("システム · ダイナミックカラー", formatAppearanceSummary(dynamicSystem, JapaneseStrings))

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
