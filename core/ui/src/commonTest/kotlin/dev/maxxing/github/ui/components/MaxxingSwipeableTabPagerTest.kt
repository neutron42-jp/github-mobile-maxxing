package dev.maxxing.github.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class MaxxingSwipeableTabPagerTest {

    @Test
    fun verticalAndDiagonalScrollsYieldInsteadOfMisfiringHorizontalTabSwipe() {
        val touchSlop = 16f
        val activationSlop = 40f

        // Pure vertical scroll
        assertEquals(
            HorizontalSwipeIntent.CANCEL_VERTICAL_OR_CONSUMED,
            evaluateHorizontalTabSwipeIntent(
                totalDx = 4f,
                totalDy = 24f,
                touchSlopPx = touchSlop,
                horizontalActivationSlopPx = activationSlop,
                canSwipeToPrevious = true,
                canSwipeToNext = true
            )
        )

        // Diagonal thumb scroll (~45 deg) reaching activation slop still cancels
        assertEquals(
            HorizontalSwipeIntent.CANCEL_VERTICAL_OR_CONSUMED,
            evaluateHorizontalTabSwipeIntent(
                totalDx = 45f,
                totalDy = 42f,
                touchSlopPx = touchSlop,
                horizontalActivationSlopPx = activationSlop,
                canSwipeToPrevious = true,
                canSwipeToNext = true
            )
        )

        // Small horizontal jitter before activation slop stays undecided
        assertEquals(
            HorizontalSwipeIntent.NONE,
            evaluateHorizontalTabSwipeIntent(
                totalDx = 20f,
                totalDy = 4f,
                touchSlopPx = touchSlop,
                horizontalActivationSlopPx = activationSlop,
                canSwipeToPrevious = true,
                canSwipeToNext = true
            )
        )
    }

    @Test
    fun deliberateHorizontalSwipeLocksOnlyWhenTargetDirectionIsValid() {
        val touchSlop = 16f
        val activationSlop = 40f

        assertEquals(
            HorizontalSwipeIntent.LOCK_HORIZONTAL,
            evaluateHorizontalTabSwipeIntent(
                totalDx = -48f,
                totalDy = 8f,
                touchSlopPx = touchSlop,
                horizontalActivationSlopPx = activationSlop,
                canSwipeToPrevious = false,
                canSwipeToNext = true
            )
        )

        // Swiping past first tab (dx > 0 with canSwipeToPrevious = false) cancels cleanly
        assertEquals(
            HorizontalSwipeIntent.CANCEL_VERTICAL_OR_CONSUMED,
            evaluateHorizontalTabSwipeIntent(
                totalDx = 48f,
                totalDy = 4f,
                touchSlopPx = touchSlop,
                horizontalActivationSlopPx = activationSlop,
                canSwipeToPrevious = false,
                canSwipeToNext = true
            )
        )

        // Swiping past last tab (dx < 0 with canSwipeToNext = false) cancels cleanly
        assertEquals(
            HorizontalSwipeIntent.CANCEL_VERTICAL_OR_CONSUMED,
            evaluateHorizontalTabSwipeIntent(
                totalDx = -48f,
                totalDy = 4f,
                touchSlopPx = touchSlop,
                horizontalActivationSlopPx = activationSlop,
                canSwipeToPrevious = true,
                canSwipeToNext = false
            )
        )
    }

    @Test
    fun resolveSwipeTargetPageHandlesThresholdFlingVelocityAndClamping() {
        // Dragging >= 30% toward next page advances by 1
        assertEquals(
            2,
            resolveSwipeTargetPage(
                startPage = 1,
                currentPage = 1,
                currentPageOffsetFraction = 0.35f,
                velocityPxPerSec = 0f,
                pageCount = 5,
                velocityThresholdPxPerSec = 900f
            )
        )

        // Dragging < 30% without velocity snaps back to startPage
        assertEquals(
            1,
            resolveSwipeTargetPage(
                startPage = 1,
                currentPage = 1,
                currentPageOffsetFraction = 0.20f,
                velocityPxPerSec = 0f,
                pageCount = 5,
                velocityThresholdPxPerSec = 900f
            )
        )

        // Fast fling left advances 1 page
        assertEquals(
            2,
            resolveSwipeTargetPage(
                startPage = 1,
                currentPage = 1,
                currentPageOffsetFraction = 0.10f,
                velocityPxPerSec = -1200f,
                pageCount = 5,
                velocityThresholdPxPerSec = 900f
            )
        )

        // Dragging forward then flinging back snaps back to startPage instead of overshooting to startPage - 1
        assertEquals(
            1,
            resolveSwipeTargetPage(
                startPage = 1,
                currentPage = 1,
                currentPageOffsetFraction = 0.25f,
                velocityPxPerSec = 1200f,
                pageCount = 5,
                velocityThresholdPxPerSec = 900f
            )
        )

        // Clamped within [0, pageCount - 1]
        assertEquals(
            0,
            resolveSwipeTargetPage(
                startPage = 0,
                currentPage = 0,
                currentPageOffsetFraction = -0.4f,
                velocityPxPerSec = 1500f,
                pageCount = 5,
                velocityThresholdPxPerSec = 900f
            )
        )
        assertEquals(
            4,
            resolveSwipeTargetPage(
                startPage = 4,
                currentPage = 4,
                currentPageOffsetFraction = 0.4f,
                velocityPxPerSec = -1500f,
                pageCount = 5,
                velocityThresholdPxPerSec = 900f
            )
        )
    }
}
