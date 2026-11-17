package dev.maxxing.github.ui.components

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class GitHubImageTransformerTest {

    @Test
    fun badgeSizeIsScaledByScreenDensitySoItMatchesItsGitHubSize() {
        // A shields.io badge is 90x20 px; on a 2.625x screen it should take 90x20 dp = 236.25x52.5 px.
        assertEquals(Size(236.25f, 52.5f), scaleToDensity(Size(90f, 20f), density = 2.625f))
    }

    @Test
    fun densityOneLeavesTheSizeUnchanged() {
        assertEquals(Size(90f, 20f), scaleToDensity(Size(90f, 20f), density = 1f))
    }

    @Test
    fun unknownSizeStaysUnknownWhileTheImageIsLoading() {
        assertEquals(Size.Unspecified, scaleToDensity(Size.Unspecified, density = 2.625f))
    }
}
