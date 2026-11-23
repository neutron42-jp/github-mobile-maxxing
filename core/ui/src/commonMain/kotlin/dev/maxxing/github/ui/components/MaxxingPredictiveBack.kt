package dev.maxxing.github.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch

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

    // Decelerate curve mirrors platform predictive back preview responsiveness.
    val inverse = 1f - clamped
    val eased = 1f - (inverse * inverse)
    val directionSign = when (swipeEdge) {
        NavigationEvent.EDGE_RIGHT -> -1f
        else -> 1f
    }
    val backgroundShift = if (eased >= 1f) 0f else -directionSign * 0.04f * (1f - eased)
    return PredictiveBackVisualState(
        scale = 1f - (0.10f * eased),
        translationXFraction = directionSign * 0.10f * eased,
        alpha = 1f - (0.08f * eased),
        cornerRadiusDp = 28f * eased,
        scrimAlpha = 0.22f * (1f - clamped),
        backgroundScale = 0.96f + (0.04f * eased),
        backgroundTranslationXFraction = backgroundShift
    )
}

/**
 * Live state for an in-app predictive back gesture. Callers that already compose both the
 * foreground screen and the destination screen (for example, bottom-navigation tabs returning
 * to Home) can observe [isTransforming] and [visual] to reveal the destination screen directly
 * underneath the shrinking foreground screen.
 */
class MaxxingPredictiveBackState internal constructor(
    private val progressAnimatable: Animatable<Float, AnimationVector1D>,
    initialSwipeEdge: Int = NavigationEvent.EDGE_LEFT
) {
    var swipeEdge by mutableIntStateOf(initialSwipeEdge)
        internal set

    val progress: Float
        get() = progressAnimatable.value

    val visual: PredictiveBackVisualState
        get() = calculatePredictiveBackVisualState(progress = progress, swipeEdge = swipeEdge)

    val isTransforming: Boolean
        get() = progress > 0.005f
}

/**
 * Registers a [NavigationBackHandler] and exposes live predictive-back progress and visuals.
 *
 * When [freezeOnComplete] is true (used for dismissing overlays that play an exit transition),
 * completing a swipe preserves the shrunk gesture state so the exit animation continues smoothly
 * instead of snapping back to full size. Otherwise, progress snaps to `0f` on completion so the
 * newly revealed destination does not bounce back from the outgoing gesture's transform.
 */
@Composable
fun rememberMaxxingPredictiveBackState(
    enabled: Boolean,
    onBack: () -> Unit,
    freezeOnComplete: Boolean = false
): MaxxingPredictiveBackState {
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    val progressAnimatable = remember { Animatable(0f) }
    val state = remember(progressAnimatable) { MaxxingPredictiveBackState(progressAnimatable) }
    val scope = rememberCoroutineScope()
    var frozenByCompletion by remember { mutableStateOf(false) }
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(enabled) {
        if (enabled && frozenByCompletion) {
            frozenByCompletion = false
            progressAnimatable.snapTo(0f)
        }
    }

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = enabled,
        onBackCancelled = {
            frozenByCompletion = false
            scope.launch {
                progressAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            }
        },
        onBackCompleted = {
            if (freezeOnComplete && progressAnimatable.value > 0.005f) {
                frozenByCompletion = true
            } else {
                frozenByCompletion = false
                scope.launch { progressAnimatable.snapTo(0f) }
            }
            currentOnBack()
        }
    )

    val inProgress = navEventState.transitionState as? NavigationEventTransitionState.InProgress
    val inProgressValue = inProgress?.latestEvent?.progress?.coerceIn(0f, 1f)
    val inProgressEdge = inProgress?.latestEvent?.swipeEdge

    if (inProgressEdge != null) {
        state.swipeEdge = inProgressEdge
    }

    LaunchedEffect(inProgressValue, frozenByCompletion) {
        if (inProgressValue != null) {
            frozenByCompletion = false
            progressAnimatable.animateTo(
                targetValue = inProgressValue,
                animationSpec = spring(stiffness = Spring.StiffnessHigh)
            )
        } else if (!frozenByCompletion && progressAnimatable.value > 0f) {
            progressAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    return state
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
        shape = if (isTransforming) RoundedCornerShape(visual.cornerRadiusDp.dp) else RectangleShape,
        shadowElevation = if (isTransforming) 10.dp else 0.dp,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = visual.scale
                scaleY = visual.scale
                translationX = size.width * visual.translationXFraction
                alpha = visual.alpha
            }
    ) {
        content()
    }
}

/**
 * Attaches an in-app predictive back handler (Compose NavigationEvent API) and applies
 * real-time scale, translation, corner-rounding, and elevation feedback while swiping back.
 *
 * When [backgroundContent] is provided, it is revealed underneath the shrinking foreground
 * screen during the swipe so the destination screen is visible in real time.
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
    val visual = backState.visual
    val isTransforming = backState.isTransforming

    Box(modifier = modifier.fillMaxSize()) {
        if (isTransforming && backgroundContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = false }
                    .clearAndSetSemantics {}
                    .predictiveBackDestinationReveal(visual = visual, active = true)
            ) {
                backgroundContent()
            }
        }
        if (isTransforming) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = visual.scrimAlpha))
            )
        }
        MaxxingPredictiveBackSurface(
            visual = visual,
            isTransforming = isTransforming,
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
                            animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)
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
                val visual = backState.visual
                val isTransforming = backState.isTransforming

                SideEffect {
                    if (isTopActive) {
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

                    val scrimAlpha = when {
                        entry.isExiting -> visual.scrimAlpha * (1f - exitAnim.value)
                        isTopActive && isTransforming -> visual.scrimAlpha
                        else -> 0f
                    }
                    if (scrimAlpha > 0.002f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = scrimAlpha))
                        )
                    }

                    val isInteractive = isTopActive && !entry.isExiting
                    val directionSign = if (backState.swipeEdge == NavigationEvent.EDGE_RIGHT) -1f else 1f
                    val showCardChrome = isTransforming

                    Surface(
                        color = containerColor,
                        shape = if (showCardChrome) RoundedCornerShape(visual.cornerRadiusDp.dp) else RectangleShape,
                        shadowElevation = if (showCardChrome) 10.dp else 0.dp,
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
                                    val exitProgress = exitAnim.value
                                    val exitScale = visual.scale * (1f - (0.04f * exitProgress))
                                    scaleX = exitScale
                                    scaleY = exitScale
                                    translationX = size.width * (
                                        visual.translationXFraction + (directionSign * 0.22f * exitProgress)
                                    )
                                    alpha = (visual.alpha * (1f - exitProgress)).coerceIn(0f, 1f)
                                } else if (isTopActive) {
                                    val enterFraction = enterAnim.value
                                    val enterOffset = (1f - enterFraction) * (size.width / 6f)
                                    scaleX = visual.scale
                                    scaleY = visual.scale
                                    translationX = (size.width * visual.translationXFraction) + enterOffset
                                    alpha = (visual.alpha * enterFraction).coerceIn(0f, 1f)
                                } else if (isDestinationUnderTop) {
                                    val activeTopBack = topHolder.backState
                                    val activeTopEnter = topHolder.enterAnim?.value ?: 1f
                                    if (hasExiting) {
                                        alpha = 1f
                                    } else if (activeTopBack != null && activeTopBack.isTransforming) {
                                        val topVis = activeTopBack.visual
                                        scaleX = topVis.backgroundScale
                                        scaleY = topVis.backgroundScale
                                        translationX = size.width * topVis.backgroundTranslationXFraction
                                        alpha = 1f
                                    } else if (activeTopEnter < 0.995f) {
                                        alpha = 1f
                                    } else {
                                        alpha = 0f
                                    }
                                } else {
                                    alpha = 0f
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
