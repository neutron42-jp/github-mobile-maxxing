package dev.maxxing.github.ui.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

internal enum class HorizontalSwipeIntent {
    NONE,
    LOCK_HORIZONTAL,
    CANCEL_VERTICAL_OR_CONSUMED
}

/**
 * Evaluates whether accumulated pointer movement represents a deliberate horizontal tab swipe.
 *
 * Requires both a clear horizontal distance above [horizontalActivationSlopPx] and strong
 * horizontal dominance ([dominanceRatio]) while yielding immediately if vertical motion
 * crosses [touchSlopPx] first, preventing diagonal thumb scrolling from misfiring tab switches.
 */
internal fun evaluateHorizontalTabSwipeIntent(
    totalDx: Float,
    totalDy: Float,
    touchSlopPx: Float,
    horizontalActivationSlopPx: Float,
    canSwipeToPrevious: Boolean,
    canSwipeToNext: Boolean,
    dominanceRatio: Float = 1.8f
): HorizontalSwipeIntent {
    val absDx = abs(totalDx)
    val absDy = abs(totalDy)
    if (absDy > touchSlopPx * 0.9f && absDx < absDy * dominanceRatio) {
        return HorizontalSwipeIntent.CANCEL_VERTICAL_OR_CONSUMED
    }
    if (absDx < horizontalActivationSlopPx) {
        return HorizontalSwipeIntent.NONE
    }
    if (absDx < absDy * dominanceRatio) {
        return HorizontalSwipeIntent.CANCEL_VERTICAL_OR_CONSUMED
    }
    val directionAllowed = if (totalDx > 0f) canSwipeToPrevious else canSwipeToNext
    return if (directionAllowed) {
        HorizontalSwipeIntent.LOCK_HORIZONTAL
    } else {
        HorizontalSwipeIntent.CANCEL_VERTICAL_OR_CONSUMED
    }
}

/**
 * Resolves the settled target page index after a horizontal tab swipe ends, clamping to at most
 * one adjacent page from [startPage] so a single swipe never overshoots multiple tabs.
 */
internal fun resolveSwipeTargetPage(
    startPage: Int,
    currentPage: Int,
    currentPageOffsetFraction: Float,
    velocityPxPerSec: Float,
    pageCount: Int,
    velocityThresholdPxPerSec: Float,
    offsetThresholdFraction: Float = 0.30f
): Int {
    if (pageCount <= 1) return 0
    val lastIndex = pageCount - 1
    val clampedStart = startPage.coerceIn(0, lastIndex)
    val displacement = (currentPage + currentPageOffsetFraction) - clampedStart
    val target = when {
        velocityPxPerSec <= -velocityThresholdPxPerSec && displacement >= -0.02f -> clampedStart + 1
        velocityPxPerSec >= velocityThresholdPxPerSec && displacement <= 0.02f -> clampedStart - 1
        displacement >= offsetThresholdFraction -> clampedStart + 1
        displacement <= -offsetThresholdFraction -> clampedStart - 1
        else -> clampedStart
    }
    return target.coerceIn(0, lastIndex)
}

private class SwipeDragOutcome(
    val lockedHorizontal: Boolean,
    val startPage: Int,
    val velocityX: Float,
    val deltaChannel: Channel<Float>,
    val dragJob: Job?
)

/**
 * Horizontal tab pager that switches between [tabs] on deliberate horizontal swipes while
 * guarding against vertical/diagonal scroll misfires, trackpad scroll jitter, and nested
 * horizontal scroll bleed (e.g. from code viewers or horizontal chip rows).
 */
@Composable
fun <T> MaxxingSwipeableTabPager(
    tabs: List<T>,
    selectedTab: T,
    onSelectTab: (T) -> Unit,
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true,
    allowStartToEndSwipe: Boolean = true,
    allowEndToStartSwipe: Boolean = true,
    interceptStartToEndBeforeChildren: Boolean = false,
    content: @Composable (T) -> Unit
) {
    if (tabs.isEmpty()) return

    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { tabs.size }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current

    val currentTabs by rememberUpdatedState(tabs)
    val currentSelectedTab by rememberUpdatedState(selectedTab)
    val currentOnSelectTab by rememberUpdatedState(onSelectTab)
    val currentSwipeEnabled by rememberUpdatedState(swipeEnabled)
    val currentAllowStartToEnd by rememberUpdatedState(allowStartToEndSwipe)
    val currentAllowEndToStart by rememberUpdatedState(allowEndToStartSwipe)
    val currentInterceptStartToEnd by rememberUpdatedState(interceptStartToEndBeforeChildren)

    // Keep pager aligned when selectedTab changes via TabRow click or external state reset.
    LaunchedEffect(selectedIndex, tabs.size) {
        if (selectedIndex in 0 until tabs.size &&
            (pagerState.currentPage != selectedIndex || pagerState.currentPageOffsetFraction != 0f) &&
            (!pagerState.isScrollInProgress || pagerState.targetPage != selectedIndex)
        ) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    // Absorbs leftover horizontal nested scroll from child LazyRow / horizontalScroll
    // containers so reaching the end of a chip row or code line never flips the tab.
    val blockChildHorizontalScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = Offset(x = available.x, y = 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(x = available.x, y = 0f)
        }
    }

    val velocityThresholdPx = with(density) { 450.dp.toPx() }
    val minHorizontalActivationPx = with(density) { 20.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(pagerState) {
                while (currentCoroutineContext().isActive) {
                    val outcome = awaitPointerEventScope {
                        val down = awaitFirstDown(
                            requireUnconsumed = true,
                            pass = PointerEventPass.Initial
                        )
                        val pageCount = currentTabs.size
                        if (!currentSwipeEnabled || pageCount <= 1) {
                            return@awaitPointerEventScope null
                        }
                        val startPage = pagerState.currentPage.coerceIn(0, pageCount - 1)
                        val canPrev = currentAllowStartToEnd && startPage > 0
                        val canNext = currentAllowEndToStart && startPage < pageCount - 1
                        if (!canPrev && !canNext) {
                            return@awaitPointerEventScope null
                        }

                        val touchSlop = viewConfiguration.touchSlop
                        val activationSlop = maxOf(touchSlop * 2f, minHorizontalActivationPx)
                        val initialStartToEndSlop = touchSlop * 1.15f
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        val deltaChannel = Channel<Float>(Channel.UNLIMITED)
                        var dragJob: Job? = null
                        var activePointerId: PointerId = down.id
                        var totalDx = 0f
                        var totalDy = 0f
                        var lockedHorizontal = false
                        var aborted = false
                        var lastDisplacementPx = 0f

                        fun ensureDragStarted() {
                            if (dragJob == null) {
                                val pageSizePx = pagerState.layoutInfo.pageSize.toFloat().coerceAtLeast(1f)
                                lastDisplacementPx =
                                    ((pagerState.currentPage + pagerState.currentPageOffsetFraction) - startPage) * pageSizePx
                                dragJob = scope.launch {
                                    pagerState.scroll(MutatePriority.UserInput) {
                                        for (scrollDelta in deltaChannel) {
                                            scrollBy(scrollDelta)
                                        }
                                    }
                                }
                            }
                        }

                        try {
                            while (true) {
                                val initialEvent = awaitPointerEvent(PointerEventPass.Initial)
                                val initialChange = initialEvent.changes.firstOrNull { it.id == activePointerId }
                                    ?: initialEvent.changes.firstOrNull()
                                    ?: break
                                activePointerId = initialChange.id
                                if (!initialChange.pressed) {
                                    if (lockedHorizontal) {
                                        initialChange.consume()
                                    }
                                    val mainUp = awaitPointerEvent(PointerEventPass.Main)
                                    if (lockedHorizontal) {
                                        mainUp.changes.firstOrNull { it.id == activePointerId }?.consume()
                                    }
                                    break
                                }

                                val delta = initialChange.positionChangeIgnoreConsumed()
                                if (!lockedHorizontal && !aborted) {
                                    totalDx += delta.x
                                    totalDy += delta.y
                                    // Optionally claim StartToEnd drags before child SwipeToDismissBox
                                    // when SwipeToDismissBox only enables EndToStart dismissal.
                                    if (currentInterceptStartToEnd && canPrev &&
                                        totalDx >= initialStartToEndSlop &&
                                        totalDx >= abs(totalDy) * 1.8f
                                    ) {
                                        lockedHorizontal = true
                                        ensureDragStarted()
                                        initialChange.consume()
                                    }
                                } else if (lockedHorizontal) {
                                    initialChange.consume()
                                }

                                val mainEvent = awaitPointerEvent(PointerEventPass.Main)
                                val mainChange = mainEvent.changes.firstOrNull { it.id == activePointerId }
                                    ?: mainEvent.changes.firstOrNull()
                                    ?: break
                                if (!mainChange.pressed) {
                                    break
                                }

                                velocityTracker.addPosition(mainChange.uptimeMillis, mainChange.position)

                                if (!lockedHorizontal && !aborted) {
                                    if (mainChange.isConsumed) {
                                        aborted = true
                                    } else {
                                        when (
                                            evaluateHorizontalTabSwipeIntent(
                                                totalDx = totalDx,
                                                totalDy = totalDy,
                                                touchSlopPx = touchSlop,
                                                horizontalActivationSlopPx = activationSlop,
                                                canSwipeToPrevious = canPrev,
                                                canSwipeToNext = canNext
                                            )
                                        ) {
                                            HorizontalSwipeIntent.LOCK_HORIZONTAL -> {
                                                lockedHorizontal = true
                                                ensureDragStarted()
                                                mainChange.consume()
                                            }
                                            HorizontalSwipeIntent.CANCEL_VERTICAL_OR_CONSUMED -> {
                                                aborted = true
                                            }
                                            HorizontalSwipeIntent.NONE -> Unit
                                        }
                                    }
                                } else if (lockedHorizontal) {
                                    mainChange.consume()
                                }

                                if (lockedHorizontal && delta.x != 0f) {
                                    val pageSizePx = pagerState.layoutInfo.pageSize.toFloat().coerceAtLeast(1f)
                                    val minDispPx = if (canPrev) -pageSizePx else 0f
                                    val maxDispPx = if (canNext) pageSizePx else 0f
                                    val nextDispPx = (lastDisplacementPx - delta.x).coerceIn(minDispPx, maxDispPx)
                                    val scrollDelta = nextDispPx - lastDisplacementPx
                                    lastDisplacementPx = nextDispPx
                                    if (scrollDelta != 0f) {
                                        deltaChannel.trySend(scrollDelta)
                                    }
                                }
                            }
                        } finally {
                            deltaChannel.close()
                        }

                        if (!lockedHorizontal) {
                            null
                        } else {
                            val vx = velocityTracker.calculateVelocity().x
                            SwipeDragOutcome(
                                lockedHorizontal = true,
                                startPage = startPage,
                                velocityX = vx,
                                deltaChannel = deltaChannel,
                                dragJob = dragJob
                            )
                        }
                    }

                    if (outcome != null) {
                        outcome.dragJob?.join()
                        val tabsSnapshot = currentTabs
                        if (tabsSnapshot.isNotEmpty()) {
                            val targetPage = resolveSwipeTargetPage(
                                startPage = outcome.startPage,
                                currentPage = pagerState.currentPage,
                                currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                                velocityPxPerSec = outcome.velocityX,
                                pageCount = tabsSnapshot.size,
                                velocityThresholdPxPerSec = velocityThresholdPx
                            )
                            val targetTab = tabsSnapshot[targetPage]
                            if (targetTab != currentSelectedTab) {
                                currentOnSelectTab(targetTab)
                            }
                            scope.launch {
                                pagerState.animateScrollToPage(targetPage)
                            }
                        }
                    }
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val tab = tabs.getOrNull(page)
            if (tab != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(blockChildHorizontalScroll)
                ) {
                    content(tab)
                }
            }
        }
    }
}
