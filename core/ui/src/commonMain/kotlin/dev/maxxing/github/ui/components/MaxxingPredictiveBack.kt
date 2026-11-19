package dev.maxxing.github.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

data class PredictiveBackVisualState(
    val scale: Float = 1f,
    val translationXFraction: Float = 0f,
    val alpha: Float = 1f,
    val cornerRadiusDp: Float = 0f,
    val scrimAlpha: Float = 0f
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
    return PredictiveBackVisualState(
        scale = 1f - (0.10f * eased),
        translationXFraction = directionSign * 0.08f * eased,
        alpha = 1f - (0.08f * eased),
        cornerRadiusDp = 28f * eased,
        scrimAlpha = 0.22f * (1f - clamped)
    )
}

/**
 * Attaches an in-app predictive back handler (Compose NavigationEvent API) and applies
 * real-time scale, translation, corner-rounding, and elevation feedback while swiping back.
 */
@Composable
fun MaxxingPredictiveBackContainer(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    var lastSwipeEdge by remember { mutableIntStateOf(NavigationEvent.EDGE_LEFT) }

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = enabled,
        onBackCompleted = onBack
    )

    val inProgress = navEventState.transitionState as? NavigationEventTransitionState.InProgress
    if (inProgress != null) {
        lastSwipeEdge = inProgress.latestEvent.swipeEdge
    }

    val targetProgress = inProgress?.latestEvent?.progress?.coerceIn(0f, 1f) ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = if (inProgress != null) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            spring(stiffness = Spring.StiffnessMediumLow)
        }
    )

    val visual = calculatePredictiveBackVisualState(
        progress = animatedProgress,
        swipeEdge = lastSwipeEdge
    )
    val isTransforming = animatedProgress > 0.005f

    Box(modifier = modifier.fillMaxSize()) {
        if (isTransforming) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = visual.scrimAlpha))
            )
        }
        Surface(
            color = containerColor,
            shape = if (isTransforming) RoundedCornerShape(visual.cornerRadiusDp.dp) else RectangleShape,
            shadowElevation = if (isTransforming) 10.dp else 0.dp,
            modifier = Modifier
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
    contentKey: (T) -> Any = { it as Any },
    content: @Composable (T) -> Unit
) {
    var lastTarget by remember { mutableStateOf(target) }
    if (target != null) {
        lastTarget = target
    }

    AnimatedVisibility(
        visible = target != null,
        enter = fadeIn() + slideInHorizontally { it / 6 },
        exit = fadeOut() + slideOutHorizontally { it / 5 },
        modifier = modifier.fillMaxSize()
    ) {
        val active = target ?: lastTarget
        if (active != null) {
            MaxxingPredictiveBackContainer(
                enabled = backEnabled && target != null,
                onBack = onBack,
                modifier = Modifier.fillMaxSize()
            ) {
                key(contentKey(active)) {
                    content(active)
                }
            }
        }
    }
}
