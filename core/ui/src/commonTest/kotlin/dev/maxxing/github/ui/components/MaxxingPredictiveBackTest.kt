package dev.maxxing.github.ui.components

import androidx.navigationevent.NavigationEvent
import kotlin.math.abs
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
    fun progressFromLeftAndRightEdgesScalesRoundsAndShiftsAsymmetrically() {
        val leftSwipe = calculatePredictiveBackVisualState(0.5f, NavigationEvent.EDGE_LEFT)
        val rightSwipe = calculatePredictiveBackVisualState(0.5f, NavigationEvent.EDGE_RIGHT)

        assertTrue(leftSwipe.scale in 0.85f..0.99f)
        assertEquals(leftSwipe.scale, rightSwipe.scale)
        assertTrue(leftSwipe.cornerRadiusDp > 0f)
        assertTrue(leftSwipe.translationXFraction > 0f)
        assertEquals(0f, rightSwipe.translationXFraction)
        assertEquals(1f, leftSwipe.alpha)

        assertTrue(leftSwipe.backgroundScale in 0.95f..0.98f)
        assertTrue(leftSwipe.backgroundTranslationXFraction < 0f)
        assertEquals(leftSwipe.backgroundTranslationXFraction, rightSwipe.backgroundTranslationXFraction)

        val fullLeft = calculatePredictiveBackVisualState(1f, NavigationEvent.EDGE_LEFT)
        assertEquals(0.85f, fullLeft.scale, 0.0001f)
        assertEquals(0.055f, fullLeft.translationXFraction, 0.0001f)
        assertEquals(32f, fullLeft.cornerRadiusDp, 0.0001f)
        assertEquals(0f, fullLeft.scrimAlpha, 0.0001f)
        assertEquals(0.98f, fullLeft.backgroundScale, 0.0001f)
        assertEquals(0f, fullLeft.backgroundTranslationXFraction, 0.0001f)
        assertEquals(1f, fullLeft.alpha, 0.0001f)

        val fullRight = calculatePredictiveBackVisualState(1f, NavigationEvent.EDGE_RIGHT)
        assertEquals(0.85f, fullRight.scale, 0.0001f)
        assertEquals(0f, fullRight.translationXFraction, 0.0001f)
        assertEquals(0.98f, fullRight.backgroundScale, 0.0001f)
    }

    @Test
    fun foregroundCardStaysCenteredWithinViewportMarginsAcrossProgress() {
        listOf(0.1f, 0.25f, 0.5f, 0.85f, 1f).forEach { progress ->
            listOf(NavigationEvent.EDGE_LEFT, NavigationEvent.EDGE_RIGHT).forEach { edge ->
                val visual = calculatePredictiveBackVisualState(progress, edge)
                val halfMargin = (1f - visual.scale) * 0.5f
                assertTrue(
                    abs(visual.translationXFraction) < halfMargin,
                    "Card clipped viewport edge at progress=$progress edge=$edge: tx=${visual.translationXFraction}, halfMargin=$halfMargin"
                )
            }
        }
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
