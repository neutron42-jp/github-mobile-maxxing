@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package dev.maxxing.github.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.MergeMethod
import dev.maxxing.github.ui.theme.MaxxingTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class PrFloatingToolbarLayoutTest {

    private fun measureToolbarMaxScroll(
        widthDp: Int,
        language: AppLanguage,
        primaryAction: PrPrimaryAction,
        mergeMethod: MergeMethod = MergeMethod.SQUASH
    ): Int {
        val scrollState = ScrollState(initial = 0)
        val scene = ImageComposeScene(
            width = widthDp,
            height = 120,
            density = Density(1f)
        ) {
            MaxxingTheme(
                useDynamicColor = false,
                darkThemeOverride = false,
                language = language
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IssuePullFloatingToolbar(
                        isPr = true,
                        state = IssueOrPullState.OPEN,
                        primaryAction = primaryAction,
                        canCloseOrReopen = true,
                        selectedMergeMethod = mergeMethod,
                        actionBusy = false,
                        onCommentClick = {},
                        onReviewClick = {},
                        onSelectMergeMethod = {},
                        onMergeClick = {},
                        onReadyForReviewClick = {},
                        onStateToggleClick = {},
                        scrollState = scrollState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 6.dp, vertical = 16.dp)
                    )
                }
            }
        }
        try {
            scene.render()
            scene.render()
        } finally {
            scene.close()
        }
        return scrollState.maxValue
    }

    @Test
    fun nonDraftPrToolbarDoesNotScrollHorizontallyOnPhoneWidths() {
        for (language in listOf(AppLanguage.DEFAULT, AppLanguage.JAPANESE)) {
            for (method in MergeMethod.entries) {
                assertEquals(
                    0,
                    measureToolbarMaxScroll(
                        widthDp = 360,
                        language = language,
                        primaryAction = PrPrimaryAction.MERGE,
                        mergeMethod = method
                    ),
                    "Expected no horizontal scroll at 360dp for $language / $method"
                )
            }
        }
    }

    @Test
    fun draftPrToolbarDoesNotScrollHorizontallyOnPhoneWidths() {
        assertEquals(
            0,
            measureToolbarMaxScroll(
                widthDp = 390,
                language = AppLanguage.DEFAULT,
                primaryAction = PrPrimaryAction.MARK_READY
            )
        )
        assertEquals(
            0,
            measureToolbarMaxScroll(
                widthDp = 390,
                language = AppLanguage.JAPANESE,
                primaryAction = PrPrimaryAction.MARK_READY
            )
        )
    }
}
