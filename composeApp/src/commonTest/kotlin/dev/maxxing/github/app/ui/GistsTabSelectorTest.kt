package dev.maxxing.github.app.ui

import dev.maxxing.github.ui.i18n.EnglishStrings
import dev.maxxing.github.ui.i18n.JapaneseStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class GistsTabSelectorTest {
    @Test
    fun gistsTabsExposeMineAndStarredForConnectedPillGroup() {
        assertEquals(listOf(GistsTab.MINE, GistsTab.STARRED), GistsTab.entries)
        assertEquals("My Gists", GistsTab.MINE.localizedLabel(EnglishStrings))
        assertEquals("Starred", GistsTab.STARRED.localizedLabel(EnglishStrings))
        assertEquals("自分の Gist", GistsTab.MINE.localizedLabel(JapaneseStrings))
        assertEquals("スター済み", GistsTab.STARRED.localizedLabel(JapaneseStrings))
    }
}
