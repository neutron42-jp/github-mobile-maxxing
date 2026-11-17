package dev.maxxing.github.app.ui

import dev.maxxing.github.model.UserProfile
import dev.maxxing.github.ui.i18n.EnglishStrings
import dev.maxxing.github.ui.i18n.JapaneseStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileTabLabelTest {
    private val profile = UserProfile(
        login = "octocat",
        publicReposCount = 20,
        followersCount = 34,
        followingCount = 139
    )

    @Test
    fun formatLocalizedProfileTabLabelIncludesCountsForReposFollowersAndFollowing() {
        fun en(tab: ProfileTab) = formatLocalizedProfileTabLabel(tab, profile, EnglishStrings)
        assertEquals("Repos (20)", en(ProfileTab.REPOS))
        assertEquals("Followers (34)", en(ProfileTab.FOLLOWERS))
        assertEquals("Following (139)", en(ProfileTab.FOLLOWING))
        assertEquals("Starred", en(ProfileTab.STARRED))
        assertEquals("Activity", en(ProfileTab.ACTIVITY))
    }

    @Test
    fun formatLocalizedProfileTabLabelIncludesCountsInJapanese() {
        fun ja(tab: ProfileTab) = formatLocalizedProfileTabLabel(tab, profile, JapaneseStrings)
        assertEquals("リポジトリ (20)", ja(ProfileTab.REPOS))
        assertEquals("フォロワー (34)", ja(ProfileTab.FOLLOWERS))
        assertEquals("フォロー中 (139)", ja(ProfileTab.FOLLOWING))
        assertEquals("スター済み", ja(ProfileTab.STARRED))
        assertEquals("アクティビティ", ja(ProfileTab.ACTIVITY))
    }
}
