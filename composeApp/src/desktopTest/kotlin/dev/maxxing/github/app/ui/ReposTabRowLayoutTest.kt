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
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.ui.theme.MaxxingTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ReposTabRowLayoutTest {

    @Test
    fun repoListTabRowSpansFullWidthSymmetricallyInBothLanguages() {
        for (language in listOf(AppLanguage.DEFAULT, AppLanguage.JAPANESE)) {
            for (widthDp in listOf(360, 412)) {
                var rowBounds: Rect? = null
                val scene = ImageComposeScene(
                    width = widthDp,
                    height = 96,
                    density = Density(1f)
                ) {
                    MaxxingTheme(
                        useDynamicColor = false,
                        darkThemeOverride = false,
                        language = language
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            RepoListTabRow(
                                activeTab = RepoListTab.MINE,
                                onSelectTab = {},
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    rowBounds = coords.boundsInRoot()
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
                val bounds = assertNotNull(rowBounds)
                assertEquals((widthDp - 32).toFloat(), bounds.width, "TabRow should fill container at ${widthDp}dp ($language)")
            }
        }
    }
}
