@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package dev.maxxing.github.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dev.maxxing.github.ui.components.MaxxingPredictiveBackContainer
import dev.maxxing.github.ui.components.MaxxingPredictiveOverlayHost
import dev.maxxing.github.ui.components.MaxxingPredictiveStackHost
import dev.maxxing.github.ui.theme.MaxxingTheme
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PredictiveBackDestinationRenderTest {

    private fun red(argb: Int): Int = (argb ushr 16) and 0xFF
    private fun green(argb: Int): Int = (argb ushr 8) and 0xFF
    private fun blue(argb: Int): Int = argb and 0xFF

    private fun createDispatcher(): Pair<NavigationEventDispatcherOwner, DirectNavigationEventInput> {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput()
        dispatcher.addInput(input)
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
        }
        return owner to input
    }

    private fun advanceFrames(scene: ImageComposeScene, startNs: Long, count: Int = 20, stepNs: Long = 16_000_000L): Long {
        var t = startNs
        repeat(count) {
            scene.render(t).close()
            t += stepNs
        }
        return t
    }

    @Test
    fun containerWithBackgroundContentRevealsDestinationDuringSwipe() {
        val (owner, input) = createDispatcher()
        var popped = false
        val width = 360
        val height = 640
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MaxxingTheme(useDynamicColor = false, darkThemeOverride = false) {
                    MaxxingPredictiveBackContainer(
                        enabled = true,
                        onBack = { popped = true },
                        backgroundContent = {
                            Box(Modifier.fillMaxSize().background(Color(0xFFE11D48))) // Red destination
                        }
                    ) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF2563EB))) // Blue foreground
                    }
                }
            }
        }
        try {
            var t = 0L
            t = advanceFrames(scene, t, count = 4)

            // At rest: left margin and center are both blue (foreground).
            scene.render(t).use { img ->
                Bitmap.makeFromImage(img).use { bmp ->
                    val left = bmp.getColor(8, height / 2)
                    assertTrue(blue(left) > red(left) + 80, "Expected blue foreground at rest, got #${left.toUInt().toString(16)}")
                }
            }

            // Start predictive back swipe from left edge to 85% progress.
            input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.2f))
            t = advanceFrames(scene, t, count = 4)
            input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.85f))
            t = advanceFrames(scene, t, count = 20)

            scene.render(t).use { img ->
                Bitmap.makeFromImage(img).use { bmp ->
                    val left = bmp.getColor(8, height / 2)
                    val center = bmp.getColor(width / 2, height / 2)
                    assertTrue(
                        red(left) > blue(left) + 60,
                        "Expected red destination screen visible on left edge during swipe, got #${left.toUInt().toString(16)}"
                    )
                    assertTrue(
                        blue(center) > red(center) + 60,
                        "Expected blue foreground still visible at center during swipe, got #${center.toUInt().toString(16)}"
                    )
                }
            }

            input.backCompleted()
            assertTrue(popped)
        } finally {
            scene.close()
        }
    }

    @Test
    fun stackHostRevealsPreviousStackEntryDuringSwipeAndKeepsItOnComplete() {
        val (owner, input) = createDispatcher()
        var stack by mutableStateOf(listOf("A", "B"))
        val width = 360
        val height = 640
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MaxxingTheme(useDynamicColor = false, darkThemeOverride = false) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {
                        MaxxingPredictiveStackHost(
                            stack = stack,
                            onBack = { stack = stack.dropLast(1) },
                            contentKey = { it }
                        ) { item, _ ->
                            val bg = if (item == "A") Color(0xFF16A34A) else Color(0xFF2563EB) // Green A, Blue B
                            Box(Modifier.fillMaxSize().background(bg))
                        }
                    }
                }
            }
        }
        try {
            var t = 0L
            t = advanceFrames(scene, t, count = 4)

            // At rest: top entry B (blue) covers the screen.
            scene.render(t).use { img ->
                Bitmap.makeFromImage(img).use { bmp ->
                    val left = bmp.getColor(8, height / 2)
                    assertTrue(blue(left) > green(left) + 80, "Expected blue B at rest, got #${left.toUInt().toString(16)}")
                }
            }

            // Swipe back on B: previous stack entry A (green) must appear on the left margin!
            input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.2f))
            t = advanceFrames(scene, t, count = 4)
            input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.85f))
            t = advanceFrames(scene, t, count = 20)

            scene.render(t).use { img ->
                Bitmap.makeFromImage(img).use { bmp ->
                    val left = bmp.getColor(8, height / 2)
                    val center = bmp.getColor(width / 2, height / 2)
                    assertTrue(
                        green(left) > blue(left) + 40,
                        "Expected green destination A visible on left edge during swipe, got #${left.toUInt().toString(16)}"
                    )
                    assertTrue(
                        blue(center) > green(center) + 40,
                        "Expected blue foreground B still visible at center during swipe, got #${center.toUInt().toString(16)}"
                    )
                }
            }

            // Complete back gesture: stack pops to [A], exit finishes, and A (green) fills the screen.
            input.backCompleted()
            assertEquals(listOf("A"), stack)
            t = advanceFrames(scene, t, count = 20)

            scene.render(t).use { img ->
                Bitmap.makeFromImage(img).use { bmp ->
                    val center = bmp.getColor(width / 2, height / 2)
                    assertTrue(
                        green(center) > blue(center) + 60,
                        "Expected green destination A at center after back completes, got #${center.toUInt().toString(16)}"
                    )
                }
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun overlayHostRevealsUnderlyingBaseScreenDuringSwipe() {
        val (owner, input) = createDispatcher()
        var detail by mutableStateOf<String?>("detail-1")
        val width = 360
        val height = 640
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MaxxingTheme(useDynamicColor = false, darkThemeOverride = false) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF16A34A))) // Green base list
                        MaxxingPredictiveOverlayHost(
                            target = detail,
                            onBack = { detail = null },
                            contentKey = { it }
                        ) {
                            Box(Modifier.fillMaxSize().background(Color(0xFF2563EB))) // Blue detail overlay
                        }
                    }
                }
            }
        }
        try {
            var t = 0L
            t = advanceFrames(scene, t, count = 4)

            input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.25f))
            t = advanceFrames(scene, t, count = 4)
            input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.85f))
            t = advanceFrames(scene, t, count = 20)

            scene.render(t).use { img ->
                Bitmap.makeFromImage(img).use { bmp ->
                    val left = bmp.getColor(8, height / 2)
                    val center = bmp.getColor(width / 2, height / 2)
                    assertTrue(
                        green(left) > blue(left) + 40,
                        "Expected green base screen visible on left edge during swipe, got #${left.toUInt().toString(16)}"
                    )
                    assertTrue(
                        blue(center) > green(center) + 40,
                        "Expected blue overlay still visible at center during swipe, got #${center.toUInt().toString(16)}"
                    )
                }
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun rightEdgeSwipeRevealsDestinationOnRightAndCancelRestoresForeground() {
        val (owner, input) = createDispatcher()
        var popped = false
        val width = 360
        val height = 640
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MaxxingTheme(useDynamicColor = false, darkThemeOverride = false) {
                    MaxxingPredictiveBackContainer(
                        enabled = true,
                        onBack = { popped = true },
                        backgroundContent = {
                            Box(Modifier.fillMaxSize().background(Color(0xFFE11D48))) // Red destination
                        }
                    ) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF2563EB))) // Blue foreground
                    }
                }
            }
        }
        try {
            var t = 0L
            t = advanceFrames(scene, t, count = 4)

            // Swipe from right edge: card shifts left, revealing destination on right edge.
            input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_RIGHT, progress = 0.2f))
            t = advanceFrames(scene, t, count = 4)
            input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_RIGHT, progress = 0.85f))
            t = advanceFrames(scene, t, count = 20)

            scene.render(t).use { img ->
                Bitmap.makeFromImage(img).use { bmp ->
                    val right = bmp.getColor(width - 8, height / 2)
                    assertTrue(
                        red(right) > blue(right) + 60,
                        "Expected red destination visible on right edge during right-edge swipe, got #${right.toUInt().toString(16)}"
                    )
                }
            }

            // Cancel gesture: foreground must spring back to cover the entire screen.
            input.backCancelled()
            t = advanceFrames(scene, t, count = 40)

            scene.render(t).use { img ->
                Bitmap.makeFromImage(img).use { bmp ->
                    val right = bmp.getColor(width - 8, height / 2)
                    assertTrue(
                        blue(right) > red(right) + 80,
                        "Expected blue foreground restored after cancel, got #${right.toUInt().toString(16)}"
                    )
                }
            }
            assertEquals(false, popped)
        } finally {
            scene.close()
        }
    }

    @Test
    fun containerProgressedMovesWithinTwoFramesWithZeroRecomposition() {
        val (owner, input) = createDispatcher()
        var fgCompositions = 0
        var bgCompositions = 0
        val width = 360
        val height = 640

        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MaxxingTheme(useDynamicColor = false, darkThemeOverride = false) {
                    MaxxingPredictiveBackContainer(
                        enabled = true,
                        onBack = {},
                        backgroundContent = {
                            SideEffect { bgCompositions++ }
                            Box(Modifier.fillMaxSize().background(Color(0xFFE11D48)))
                        }
                    ) {
                        SideEffect { fgCompositions++ }
                        Box(Modifier.fillMaxSize().background(Color(0xFF2563EB)))
                    }
                }
            }
        }
        try {
            var t = 0L
            t = advanceFrames(scene, t, count = 4)

            val fgBefore = fgCompositions
            val bgBefore = bgCompositions

            input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.2f))
            input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.8f))
            t = advanceFrames(scene, t, count = 1)

            scene.render(t).use { img ->
                Bitmap.makeFromImage(img).use { bmp ->
                    val left = bmp.getColor(8, height / 2)
                    val center = bmp.getColor(width / 2, height / 2)
                    assertTrue(
                        red(left) > blue(left) + 60,
                        "Expected red destination immediately on left edge within 2 frames, got #${left.toUInt().toString(16)}"
                    )
                    assertTrue(
                        blue(center) > red(center) + 60,
                        "Expected blue foreground opaque at center, got #${center.toUInt().toString(16)}"
                    )
                }
            }

            listOf(0.35f, 0.55f, 0.75f, 0.9f).forEach { p ->
                input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = p))
                t = advanceFrames(scene, t, count = 1)
            }

            assertEquals(fgBefore, fgCompositions, "Container foreground must not recompose during swipe")
            assertEquals(bgBefore, bgCompositions, "Container background must not recompose during swipe")
        } finally {
            scene.close()
        }
    }

    @Test
    fun stackHostProgressedMovesWithinTwoFramesWithZeroRecomposition() {
        val (owner, input) = createDispatcher()
        var stackACompositions = 0
        var stackBCompositions = 0
        val width = 360
        val height = 640

        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MaxxingTheme(useDynamicColor = false, darkThemeOverride = false) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {
                        MaxxingPredictiveStackHost(
                            stack = listOf("A", "B"),
                            onBack = {},
                            contentKey = { it }
                        ) { item, _ ->
                            SideEffect {
                                if (item == "A") stackACompositions++ else stackBCompositions++
                            }
                            val bg = if (item == "A") Color(0xFF16A34A) else Color(0xFF2563EB)
                            Box(Modifier.fillMaxSize().background(bg))
                        }
                    }
                }
            }
        }
        try {
            var t = 0L
            t = advanceFrames(scene, t, count = 4)

            val aBefore = stackACompositions
            val bBefore = stackBCompositions

            input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.2f))
            input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.8f))
            t = advanceFrames(scene, t, count = 1)

            scene.render(t).use { img ->
                Bitmap.makeFromImage(img).use { bmp ->
                    val left = bmp.getColor(8, height / 2)
                    val center = bmp.getColor(width / 2, height / 2)
                    assertTrue(
                        green(left) > blue(left) + 60,
                        "Expected green destination A immediately on left edge within 2 frames, got #${left.toUInt().toString(16)}"
                    )
                    assertTrue(
                        blue(center) > green(center) + 60,
                        "Expected blue foreground B opaque at center, got #${center.toUInt().toString(16)}"
                    )
                }
            }

            listOf(0.35f, 0.55f, 0.75f, 0.9f).forEach { p ->
                input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = p))
                t = advanceFrames(scene, t, count = 1)
            }

            assertEquals(aBefore, stackACompositions, "Stack background entry A must not recompose during swipe")
            assertEquals(bBefore, stackBCompositions, "Stack foreground entry B must not recompose during swipe")
        } finally {
            scene.close()
        }
    }

    @Test
    fun leftSwipeCardLeavesOppositeEdgeMarginRevealingBackgroundOnBothSides() {
        val (owner, input) = createDispatcher()
        val width = 360
        val height = 640
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MaxxingTheme(useDynamicColor = false, darkThemeOverride = false) {
                    MaxxingPredictiveBackContainer(
                        enabled = true,
                        onBack = {},
                        backgroundContent = {
                            Box(Modifier.fillMaxSize().background(Color(0xFFE11D48))) // Red destination
                        }
                    ) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF2563EB))) // Blue foreground
                    }
                }
            }
        }
        try {
            var t = 0L
            t = advanceFrames(scene, t, count = 4)

            input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.2f))
            input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.85f))
            t = advanceFrames(scene, t, count = 2)

            scene.render(t).use { img ->
                Bitmap.makeFromImage(img).use { bmp ->
                    val leftMargin = bmp.getColor(8, height / 2)
                    val center = bmp.getColor(width / 2, height / 2)
                    val rightMargin = bmp.getColor(width - 4, height / 2)
                    assertTrue(
                        red(leftMargin) > blue(leftMargin) + 60,
                        "Expected red background visible on left margin, got #${leftMargin.toUInt().toString(16)}"
                    )
                    assertTrue(
                        blue(center) > red(center) + 60,
                        "Expected blue foreground card centered, got #${center.toUInt().toString(16)}"
                    )
                    assertTrue(
                        red(rightMargin) > blue(rightMargin) + 60,
                        "Expected red background visible on right margin (card stays centered without clipping), got #${rightMargin.toUInt().toString(16)}"
                    )
                }
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun rightEdgeSwipeCentersCardSymmetricallyWithinViewport() {
        val (owner, input) = createDispatcher()
        val width = 360
        val height = 640
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MaxxingTheme(useDynamicColor = false, darkThemeOverride = false) {
                    MaxxingPredictiveBackContainer(
                        enabled = true,
                        onBack = {},
                        backgroundContent = {
                            Box(Modifier.fillMaxSize().background(Color(0xFFE11D48))) // Red destination
                        }
                    ) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF2563EB))) // Blue foreground
                    }
                }
            }
        }
        try {
            var t = 0L
            t = advanceFrames(scene, t, count = 4)

            input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_RIGHT, progress = 0.2f))
            input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_RIGHT, progress = 0.85f))
            t = advanceFrames(scene, t, count = 2)

            scene.render(t).use { img ->
                Bitmap.makeFromImage(img).use { bmp ->
                    val leftMargin = bmp.getColor(8, height / 2)
                    val center = bmp.getColor(width / 2, height / 2)
                    val rightMargin = bmp.getColor(width - 8, height / 2)
                    assertTrue(
                        red(leftMargin) > blue(leftMargin) + 60,
                        "Expected red background visible symmetrically on left margin during right swipe, got #${leftMargin.toUInt().toString(16)}"
                    )
                    assertTrue(
                        blue(center) > red(center) + 60,
                        "Expected blue foreground card centered during right swipe, got #${center.toUInt().toString(16)}"
                    )
                    assertTrue(
                        red(rightMargin) > blue(rightMargin) + 60,
                        "Expected red background visible symmetrically on right margin during right swipe, got #${rightMargin.toUInt().toString(16)}"
                    )
                }
            }
        } finally {
            scene.close()
        }
    }
}
