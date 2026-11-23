package dev.maxxing.github.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private val PredictiveBackStandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

private val PredictiveBackCornerShapes: Array<Shape> = Array(33) { radius ->
    if (radius == 0) RectangleShape else RoundedCornerShape(radius.dp)
}

/**
 * Returns a pre-allocated card shape for [cornerRadiusDp] to avoid per-frame allocations during
 * predictive back gestures.
 */
fun predictiveBackCardShape(cornerRadiusDp: Float): Shape =
    PredictiveBackCornerShapes[cornerRadiusDp.roundToInt().coerceIn(0, 32)]

data class PredictiveBackVisualState(
    val scale: Float = 1f,
    val translationXFraction: Float = 0f,
    val alpha: Float = 1f,
    val cornerRadiusDp: Float = 0f,
    val scrimAlpha: Float = 0f,
    val backgroundScale: Float = 1f,
    val backgroundTranslationXFraction: Float = 0f
)

fun calculatePredictiveBackVisualState(
    progress: Float,
    swipeEdge: Int
): PredictiveBackVisualState {
    val clamped = progress.coerceIn(0f, 1f)
    if (clamped <= 0f) return PredictiveBackVisualState()

    val eased = PredictiveBackStandardDecelerate.transform(clamped)
    val txFraction = when (swipeEdge) {
        NavigationEvent.EDGE_RIGHT -> 0f
        else -> 0.055f * eased
    }
    return PredictiveBackVisualState(
        scale = 1f - (0.15f * eased),
        translationXFraction = txFraction,
        alpha = 1f,
        cornerRadiusDp = 32f * eased,
        scrimAlpha = 0.24f * (1f - clamped),
        backgroundScale = 0.95f + (0.03f * eased),
        backgroundTranslationXFraction = -0.05f * (1f - eased)
    )
}

/**
 * Live state for an in-app predictive back gesture. Callers should read [isTransforming] and
 * [visual] inside draw/graphicsLayer phases (`Modifier.graphicsLayer`, `Modifier.drawBehind`)
 * so gesture updates avoid recomposing screen trees.
 */
class MaxxingPredictiveBackState internal constructor(
    initialSwipeEdge: Int = NavigationEvent.EDGE_LEFT
) {
    var swipeEdge by mutableIntStateOf(initialSwipeEdge)
        internal set

    var progress by mutableFloatStateOf(0f)
        internal set

    var touchYDeltaPx by mutableFloatStateOf(0f)
        internal set

    internal var startTouchY: Float = 0f
    internal var hasStartTouchY: Boolean = false
    internal var cancelJob: Job? = null
    internal var frozenByCompletion: Boolean = false

    val visual: PredictiveBackVisualState
        get() = calculatePredictiveBackVisualState(progress = progress, swipeEdge = swipeEdge)

    val isTransforming: Boolean
        get() = progress > 0.005f
}

/**
 * Registers a [NavigationEventHandler] and exposes live predictive-back progress and visuals.
 *
 * Gesture progress is written synchronously on every input event with no per-event animation
 * restart, avoiding start-of-gesture lag and allowing callers to observe state strictly in the
 * draw/layer phase.
 *
 * When [freezeOnComplete] is true (used for dismissing overlays that play an exit transition),
 * completing a swipe preserves the shrunk gesture state so the exit animation continues smoothly
 * instead of snapping back to full size. Otherwise, [onBack] runs before progress resets to `0f`,
 * allowing callers to snapshot the pre-commit visual state for their own exit transition.
 */
@Composable
fun rememberMaxxingPredictiveBackState(
    enabled: Boolean,
    onBack: () -> Unit,
    freezeOnComplete: Boolean = false
): MaxxingPredictiveBackState {
    val state = remember { MaxxingPredictiveBackState() }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentFreezeOnComplete by rememberUpdatedState(freezeOnComplete)
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher

    val handler = remember(state, scope) {
        object : NavigationEventHandler<NavigationEventInfo.None>(
            initialInfo = NavigationEventInfo.None,
            isBackEnabled = enabled
        ) {
            override fun onBackStarted(event: NavigationEvent) {
                state.cancelJob?.cancel()
                state.cancelJob = null
                state.frozenByCompletion = false
                state.swipeEdge = event.swipeEdge
                state.startTouchY = event.touchY
                state.hasStartTouchY = true
                state.touchYDeltaPx = 0f
                state.progress = event.progress.coerceIn(0f, 1f)
            }

            override fun onBackProgressed(event: NavigationEvent) {
                state.cancelJob?.cancel()
                state.cancelJob = null
                state.frozenByCompletion = false
                state.swipeEdge = event.swipeEdge
                if (!state.hasStartTouchY) {
                    state.startTouchY = event.touchY
                    state.hasStartTouchY = true
                }
                state.touchYDeltaPx = event.touchY - state.startTouchY
                state.progress = event.progress.coerceIn(0f, 1f)
            }

            override fun onBackCancelled() {
                state.frozenByCompletion = false
                state.hasStartTouchY = false
                val startProgress = state.progress
                val startTouchYDelta = state.touchYDeltaPx
                state.cancelJob?.cancel()
                if (startProgress > 0f || abs(startTouchYDelta) > 0.5f) {
                    state.cancelJob = scope.launch {
                        animate(
                            initialValue = startProgress,
                            targetValue = 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) { value, _ ->
                            state.progress = value
                            val ratio = if (startProgress > 0f) (value / startProgress).coerceIn(0f, 1f) else 0f
                            state.touchYDeltaPx = startTouchYDelta * ratio
                        }
                        state.touchYDeltaPx = 0f
                    }
                } else {
                    state.touchYDeltaPx = 0f
                }
            }

            override fun onBackCompleted() {
                state.cancelJob?.cancel()
                state.cancelJob = null
                state.hasStartTouchY = false
                val shouldFreeze = currentFreezeOnComplete && state.progress > 0.005f
                state.frozenByCompletion = shouldFreeze
                currentOnBack()
                if (!shouldFreeze) {
                    state.progress = 0f
                    state.touchYDeltaPx = 0f
                }
            }
        }
    }

    SideEffect {
        handler.isBackEnabled = enabled
    }

    LaunchedEffect(enabled) {
        if (enabled && state.frozenByCompletion) {
            state.frozenByCompletion = false
            state.hasStartTouchY = false
            state.cancelJob?.cancel()
            state.cancelJob = null
            state.progress = 0f
            state.touchYDeltaPx = 0f
        } else if (!enabled && !state.frozenByCompletion && (state.progress > 0f || state.touchYDeltaPx != 0f)) {
            state.hasStartTouchY = false
            state.cancelJob?.cancel()
            state.cancelJob = null
            state.progress = 0f
            state.touchYDeltaPx = 0f
        }
    }

    DisposableEffect(dispatcher, handler) {
        dispatcher?.addHandler(handler)
        onDispose {
            state.cancelJob?.cancel()
            state.cancelJob = null
            handler.remove()
        }
    }

    return state
}

private fun GraphicsLayerScope.computeTouchYOffset(
    scale: Float,
    touchYDeltaPx: Float
): Float {
    if (touchYDeltaPx == 0f || size.height <= 0f) return 0f
    val maxShiftY = ((size.height * (1f - scale) * 0.5f) - 8.dp.toPx()).coerceAtLeast(0f)
    if (maxShiftY <= 0f) return 0f
    val normalized = (touchYDeltaPx / (size.height * 0.4f)).coerceIn(-1f, 1f)
    val eased = sign(normalized) * PredictiveBackStandardDecelerate.transform(abs(normalized))
    return maxShiftY * eased
}

/**
 * Applies predictive-back foreground transform (scale, centered-margin translation, vertical
 * touch follow, rounded clipping, elevation) inside a `graphicsLayer` block so callers never
 * recompose during gesture frames.
 */
fun GraphicsLayerScope.applyPredictiveBackForeground(
    visual: PredictiveBackVisualState,
    isTransforming: Boolean,
    touchYDeltaPx: Float = 0f,
    extraTranslationX: Float = 0f,
    extraAlpha: Float = 1f,
    extraScale: Float = 1f
) {
    val cardShape = if (isTransforming) predictiveBackCardShape(visual.cornerRadiusDp) else RectangleShape
    shape = cardShape
    clip = cardShape !== RectangleShape
    shadowElevation = if (isTransforming) 14.dp.toPx() else 0f
    val finalScale = visual.scale * extraScale
    scaleX = finalScale
    scaleY = finalScale
    translationX = (size.width * visual.translationXFraction) + extraTranslationX
    translationY = if (isTransforming) computeTouchYOffset(finalScale, touchYDeltaPx) else 0f
    alpha = (visual.alpha * extraAlpha).coerceIn(0f, 1f)
}

/**
 * Applies the post-commit exit animation for an outgoing screen or tab after releasing the
 * predictive-back gesture (always gliding rightward while fading out in the first half, matching
 * Android's DefaultCrossActivityBackAnimation).
 */
fun GraphicsLayerScope.applyPredictiveBackExit(
    visual: PredictiveBackVisualState,
    exitProgress: Float,
    isTransforming: Boolean,
    touchYDeltaPx: Float = 0f
) {
    val clampedExit = exitProgress.coerceIn(0f, 1f)
    val exitAlpha = (1f - (clampedExit / 0.55f)).coerceIn(0f, 1f)
    applyPredictiveBackForeground(
        visual = visual,
        isTransforming = isTransforming,
        touchYDeltaPx = touchYDeltaPx * (1f - clampedExit),
        extraTranslationX = size.width * 0.24f * clampedExit,
        extraAlpha = exitAlpha,
        extraScale = 1f - (0.04f * clampedExit)
    )
}

/**
 * Applies the destination reveal transform underneath an active or exiting foreground screen,
 * interpolating smoothly back to `1f / 0f` during post-commit exit (`exitProgress`).
 */
fun GraphicsLayerScope.applyPredictiveBackBackground(
    visual: PredictiveBackVisualState,
    exitProgress: Float = 0f
) {
    val settle = 1f - exitProgress.coerceIn(0f, 1f)
    shape = RectangleShape
    clip = false
    shadowElevation = 0f
    val targetScale = 1f - ((1f - visual.backgroundScale) * settle)
    scaleX = targetScale
    scaleY = targetScale
    translationX = size.width * visual.backgroundTranslationXFraction * settle
    translationY = 0f
    alpha = 1f
}

/**
 * Resets predictive-back transform properties inside a `graphicsLayer` block.
 */
fun GraphicsLayerScope.resetPredictiveBackTransform(targetAlpha: Float = 1f) {
    shape = RectangleShape
    clip = false
    shadowElevation = 0f
    scaleX = 1f
    scaleY = 1f
    translationX = 0f
    translationY = 0f
    alpha = targetAlpha
}

/**
 * Applies Material predictive-back background reveal motion (subtle scale-up and counter-parallax)
 * to a destination screen rendered underneath an active predictive-back gesture.
 */
fun Modifier.predictiveBackDestinationReveal(
    visual: PredictiveBackVisualState,
    active: Boolean
): Modifier = graphicsLayer {
    if (active) {
        scaleX = visual.backgroundScale
        scaleY = visual.backgroundScale
        translationX = size.width * visual.backgroundTranslationXFraction
    }
}

/**
 * Renders the foreground surface transformed by a [PredictiveBackVisualState].
 */
@Composable
fun MaxxingPredictiveBackSurface(
    visual: PredictiveBackVisualState,
    isTransforming: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Surface(
        color = containerColor,
        shape = RectangleShape,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                applyPredictiveBackForeground(
                    visual = visual,
                    isTransforming = isTransforming
                )
            }
    ) {
        content()
    }
}

@Composable
private fun MaxxingPredictiveBackSurface(
    backState: MaxxingPredictiveBackState,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Surface(
        color = containerColor,
        shape = RectangleShape,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                applyPredictiveBackForeground(
                    visual = backState.visual,
                    isTransforming = backState.isTransforming,
                    touchYDeltaPx = backState.touchYDeltaPx
                )
            }
    ) {
        content()
    }
}

/**
 * Attaches an in-app predictive back handler (Compose NavigationEvent API) and applies
 * real-time scale, translation, corner-rounding, and elevation feedback while swiping back.
 *
 * When [backgroundContent] is provided, it is kept composed underneath and revealed in the
 * draw/layer phase during the swipe so the destination screen appears with zero layout delay.
 */
@Composable
fun MaxxingPredictiveBackContainer(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    freezeOnComplete: Boolean = false,
    backgroundContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val backState = rememberMaxxingPredictiveBackState(
        enabled = enabled,
        onBack = onBack,
        freezeOnComplete = freezeOnComplete
    )

    Box(modifier = modifier.fillMaxSize()) {
        if (backgroundContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = false }
                    .clearAndSetSemantics {}
                    .graphicsLayer {
                        if (backState.isTransforming) {
                            applyPredictiveBackBackground(
                                visual = backState.visual,
                                exitProgress = 0f
                            )
                        } else {
                            resetPredictiveBackTransform(targetAlpha = 0f)
                        }
                    }
            ) {
                backgroundContent()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (backState.isTransforming) {
                        val scrimAlpha = backState.visual.scrimAlpha
                        if (scrimAlpha > 0.002f) {
                            drawRect(Color.Black.copy(alpha = scrimAlpha))
                        }
                    }
                }
        )
        MaxxingPredictiveBackSurface(
            backState = backState,
            containerColor = containerColor,
            content = content
        )
    }
}

internal fun <T> resolveStackExitEntry(
    previousItems: List<Pair<Any, T>>,
    currentItems: List<Pair<Any, T>>,
    currentExiting: Pair<Any, T>?
): Pair<Any, T>? {
    val prevKeys = previousItems.map { it.first }
    val currKeys = currentItems.map { it.first }
    if (prevKeys == currKeys) {
        return currentExiting?.takeIf { it.first !in currKeys }
    }
    val isSingleTopPop = previousItems.size == currentItems.size + 1 &&
        prevKeys.dropLast(1) == currKeys
    return if (isSingleTopPop) previousItems.last() else null
}

private class StackExitTracker<T> {
    var lastStack: List<Pair<Any, T>> = emptyList()
    var initialized: Boolean = false
}

private data class StackRenderEntry<T>(
    val item: T,
    val key: Any,
    val isExiting: Boolean,
    val stackIndex: Int
)

private class TopOverlayVisualHolder {
    var backState by mutableStateOf<MaxxingPredictiveBackState?>(null)
    var enterAnim by mutableStateOf<Animatable<Float, AnimationVector1D>?>(null)
    var exitingBackState by mutableStateOf<MaxxingPredictiveBackState?>(null)
    var exitingAnim by mutableStateOf<Animatable<Float, AnimationVector1D>?>(null)
}

/**
 * Multi-layer stack host combining predictive back gesture feedback with preserved lower entries.
 *
 * Every entry in [stack] stays composed at its stable [contentKey], so swiping back on the top
 * entry immediately reveals the live previous entry underneath (or the host's underlying content
 * when [stack] has a single entry). Completing a back gesture smoothly glides the popped entry
 * out in the swipe direction without disposing or reloading either screen mid-transition.
 */
@Composable
fun <T> MaxxingPredictiveStackHost(
    stack: List<T>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentKey: (T) -> Any = { it as Any },
    content: @Composable (item: T, isTop: Boolean) -> Unit
) {
    val tracker = remember { StackExitTracker<T>() }
    var exitingEntry by remember { mutableStateOf<Pair<Any, T>?>(null) }

    // Deduplicate by key (keeping the topmost occurrence) to guarantee unique Compose keys.
    val currentItems: List<Pair<Any, T>> = remember(stack) {
        val seen = HashSet<Any>()
        val dedupedReversed = ArrayList<Pair<Any, T>>(stack.size)
        for (i in stack.lastIndex downTo 0) {
            val item = stack[i]
            val itemKey = contentKey(item)
            if (seen.add(itemKey)) {
                dedupedReversed.add(itemKey to item)
            }
        }
        dedupedReversed.reverse()
        dedupedReversed
    }

    val resolvedExiting = if (tracker.initialized) {
        resolveStackExitEntry(
            previousItems = tracker.lastStack,
            currentItems = currentItems,
            currentExiting = exitingEntry
        )
    } else {
        null
    }
    if (resolvedExiting != exitingEntry) {
        exitingEntry = resolvedExiting
    }

    val hasCompletedInitialComposition = tracker.initialized
    SideEffect {
        tracker.initialized = true
        tracker.lastStack = currentItems
    }

    val renderedEntries: List<StackRenderEntry<T>> = buildList(currentItems.size + 1) {
        currentItems.forEachIndexed { index, (entryKey, item) ->
            add(StackRenderEntry(item = item, key = entryKey, isExiting = false, stackIndex = index))
        }
        resolvedExiting?.let { (exitKey, exitItem) ->
            if (currentItems.none { it.first == exitKey }) {
                add(StackRenderEntry(item = exitItem, key = exitKey, isExiting = true, stackIndex = currentItems.size))
            }
        }
    }

    if (renderedEntries.isEmpty()) return

    val topHolder = remember { TopOverlayVisualHolder() }
    val hasExiting = resolvedExiting != null
    SideEffect {
        if (!hasExiting && (topHolder.exitingBackState != null || topHolder.exitingAnim != null)) {
            topHolder.exitingBackState = null
            topHolder.exitingAnim = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        renderedEntries.forEach { entry ->
            key(entry.key) {
                val isTopActive = !entry.isExiting && entry.stackIndex == currentItems.lastIndex
                val isDestinationUnderTop = if (hasExiting) {
                    isTopActive
                } else {
                    !entry.isExiting && entry.stackIndex == currentItems.lastIndex - 1
                }
                val startAtRest = remember { !hasCompletedInitialComposition }
                val enterAnim = remember { Animatable(if (startAtRest) 1f else 0f) }
                val exitAnim = remember { Animatable(0f) }

                LaunchedEffect(Unit) {
                    if (enterAnim.value < 1f) {
                        enterAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                        )
                    }
                }

                LaunchedEffect(entry.isExiting) {
                    if (entry.isExiting) {
                        exitAnim.snapTo(0f)
                        exitAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                        )
                        if (exitingEntry?.first == entry.key) {
                            exitingEntry = null
                        }
                    } else if (exitAnim.value != 0f) {
                        exitAnim.snapTo(0f)
                    }
                }

                val backState = rememberMaxxingPredictiveBackState(
                    enabled = backEnabled && isTopActive && !entry.isExiting,
                    onBack = onBack,
                    freezeOnComplete = true
                )

                SideEffect {
                    if (entry.isExiting) {
                        if (topHolder.exitingBackState !== backState) topHolder.exitingBackState = backState
                        if (topHolder.exitingAnim !== exitAnim) topHolder.exitingAnim = exitAnim
                    } else if (isTopActive) {
                        if (topHolder.backState !== backState) topHolder.backState = backState
                        if (topHolder.enterAnim !== enterAnim) topHolder.enterAnim = enterAnim
                    }
                }

                val layerZ = when {
                    entry.isExiting -> 3f
                    isTopActive -> 2f
                    isDestinationUnderTop -> 1f
                    else -> 0f
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(layerZ)
                ) {
                    // Prevent pointer events on the active overlay from falling through to screens below.
                    if (isTopActive && !entry.isExiting) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent().changes.forEach { it.consume() }
                                        }
                                    }
                                }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val alpha = when {
                                    entry.isExiting -> backState.visual.scrimAlpha * (1f - (exitAnim.value / 0.7f).coerceIn(0f, 1f))
                                    isTopActive && backState.isTransforming -> backState.visual.scrimAlpha
                                    else -> 0f
                                }
                                if (alpha > 0.002f) {
                                    drawRect(Color.Black.copy(alpha = alpha))
                                }
                            }
                    )

                    val isInteractive = isTopActive && !entry.isExiting

                    Surface(
                        color = containerColor,
                        shape = RectangleShape,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isInteractive) {
                                    Modifier
                                } else {
                                    Modifier
                                        .focusProperties { canFocus = false }
                                        .clearAndSetSemantics {}
                                }
                            )
                            .graphicsLayer {
                                if (entry.isExiting) {
                                    applyPredictiveBackExit(
                                        visual = backState.visual,
                                        exitProgress = exitAnim.value,
                                        isTransforming = backState.isTransforming,
                                        touchYDeltaPx = backState.touchYDeltaPx
                                    )
                                } else if (isTopActive) {
                                    val exitingBack = if (hasExiting) topHolder.exitingBackState else null
                                    val exitingAnim = if (hasExiting) topHolder.exitingAnim else null
                                    if (exitingBack != null && exitingBack.isTransforming && exitingAnim != null) {
                                        applyPredictiveBackBackground(
                                            visual = exitingBack.visual,
                                            exitProgress = exitingAnim.value
                                        )
                                    } else {
                                        val enterFraction = enterAnim.value
                                        applyPredictiveBackForeground(
                                            visual = backState.visual,
                                            isTransforming = backState.isTransforming,
                                            touchYDeltaPx = backState.touchYDeltaPx,
                                            extraTranslationX = (1f - enterFraction) * (size.width / 6f),
                                            extraAlpha = enterFraction
                                        )
                                    }
                                } else if (isDestinationUnderTop) {
                                    val activeTopBack = topHolder.backState
                                    val activeTopEnter = topHolder.enterAnim?.value ?: 1f
                                    if (hasExiting) {
                                        resetPredictiveBackTransform(targetAlpha = 1f)
                                    } else if (activeTopBack != null && activeTopBack.isTransforming) {
                                        applyPredictiveBackBackground(
                                            visual = activeTopBack.visual,
                                            exitProgress = 0f
                                        )
                                    } else if (activeTopEnter < 0.995f) {
                                        resetPredictiveBackTransform(targetAlpha = 1f)
                                    } else {
                                        resetPredictiveBackTransform(targetAlpha = 0f)
                                    }
                                } else {
                                    resetPredictiveBackTransform(targetAlpha = 0f)
                                }
                            }
                    ) {
                        content(entry.item, isTopActive)
                    }
                }
            }
        }
    }
}

/**
 * Layered overlay host combining predictive back gesture feedback with smooth enter/exit
 * transitions while preserving the underlying screen's scroll and UI state.
 */
@Composable
fun <T> MaxxingPredictiveOverlayHost(
    target: T?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentKey: (T) -> Any = { it as Any },
    content: @Composable (T) -> Unit
) {
    MaxxingPredictiveStackHost(
        stack = listOfNotNull(target),
        onBack = onBack,
        modifier = modifier,
        backEnabled = backEnabled,
        containerColor = containerColor,
        contentKey = contentKey
    ) { active, _ ->
        content(active)
    }
}
