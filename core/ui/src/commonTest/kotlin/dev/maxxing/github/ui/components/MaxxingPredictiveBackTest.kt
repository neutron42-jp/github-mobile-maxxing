package dev.maxxing.github.ui.components

import androidx.navigationevent.NavigationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaxxingPredictiveBackTest {
    @Test
    fun zeroProgressLeavesScreenUntransformed() {
        val rest = calculatePredictiveBackVisualState(0f, NavigationEvent.EDGE_LEFT)
        assertEquals(PredictiveBackVisualState(), rest)

        val negative = calculatePredictiveBackVisualState(-0.5f, NavigationEvent.EDGE_RIGHT)
        assertEquals(PredictiveBackVisualState(), negative)
    }

    @Test
    fun progressFromLeftAndRightEdgesScalesRoundsAndShiftsOppositely() {
        val leftSwipe = calculatePredictiveBackVisualState(0.5f, NavigationEvent.EDGE_LEFT)
        val rightSwipe = calculatePredictiveBackVisualState(0.5f, NavigationEvent.EDGE_RIGHT)

        assertTrue(leftSwipe.scale in 0.90f..0.99f)
        assertTrue(leftSwipe.cornerRadiusDp > 0f)
        assertTrue(leftSwipe.translationXFraction > 0f)
        assertTrue(rightSwipe.translationXFraction < 0f)
        assertEquals(-leftSwipe.translationXFraction, rightSwipe.translationXFraction)

        assertTrue(leftSwipe.backgroundScale in 0.96f..1f)
        assertTrue(leftSwipe.backgroundTranslationXFraction < 0f)
        assertTrue(rightSwipe.backgroundTranslationXFraction > 0f)
        assertEquals(
            -leftSwipe.backgroundTranslationXFraction,
            rightSwipe.backgroundTranslationXFraction
        )

        val full = calculatePredictiveBackVisualState(1f, NavigationEvent.EDGE_LEFT)
        assertEquals(0.90f, full.scale)
        assertEquals(28f, full.cornerRadiusDp)
        assertEquals(0f, full.scrimAlpha)
        assertEquals(1f, full.backgroundScale)
        assertEquals(0f, full.backgroundTranslationXFraction)
    }

    @Test
    fun resolveStackExitEntryCapturesSingleTopPopOnly() {
        val a = "a" to "ScreenA"
        val b = "b" to "ScreenB"
        val c = "c" to "ScreenC"

        // Single pop from [A, B] -> [A] preserves B for smooth exit over live A.
        assertEquals(b, resolveStackExitEntry(listOf(a, b), listOf(a), null))

        // Single pop from [A] -> [] preserves A for smooth exit over underlying host.
        assertEquals(a, resolveStackExitEntry(listOf(a), emptyList(), null))

        // Push clears any active exit.
        assertNull(resolveStackExitEntry(listOf(a), listOf(a, b), currentExiting = c))

        // Clearing multiple entries at once (e.g. bottom tab switch) skips exit animation.
        assertNull(resolveStackExitEntry(listOf(a, b), emptyList(), null))

        // Replacing top item skips exit animation.
        assertNull(resolveStackExitEntry(listOf(a), listOf(b), null))

        // Same keys preserve in-flight exit unless re-pushed into stack.
        assertEquals(b, resolveStackExitEntry(listOf(a), listOf(a), currentExiting = b))
        assertNull(resolveStackExitEntry(listOf(a, b), listOf(a, b), currentExiting = b))
    }
}
