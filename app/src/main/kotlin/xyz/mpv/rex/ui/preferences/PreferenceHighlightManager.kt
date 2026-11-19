package xyz.mpv.rex.ui.preferences

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Coordinates scrolling to and highlighting a target preference when navigated to from settings search.
 */
object PreferenceHighlightManager {
    var highlightedKey: Any? by mutableStateOf(null)
        private set
    var targetItemIndex: Int by mutableIntStateOf(0)
        private set
    private var isConsumed: Boolean by mutableStateOf(false)

    fun requestHighlight(key: Any?, targetIndex: Int = 0) {
        highlightedKey = key
        targetItemIndex = targetIndex
        isConsumed = false
    }

    fun consumeTargetIndex(): Int? {
        if (isConsumed || highlightedKey == null) return null
        isConsumed = true
        return targetItemIndex
    }

    fun isHighlighted(key: Any?): Boolean {
        val current = highlightedKey ?: return false
        if (key == null) return false
        if (key is Collection<*>) return current in key
        if (key is Array<*>) return current in key
        return current == key
    }

    fun clear() {
        highlightedKey = null
        targetItemIndex = 0
        isConsumed = true
    }
}

/**
 * Creates and remembers a [LazyListState] that automatically scrolls to the searched setting
 * when a search highlight is active.
 */
@Composable
fun rememberPreferenceLazyListState(): LazyListState {
    val state = rememberLazyListState()

    // Automatically clean up any active highlight when leaving the screen
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            PreferenceHighlightManager.clear()
        }
    }

    LaunchedEffect(Unit) {
        val targetIndex = PreferenceHighlightManager.consumeTargetIndex()
        if (targetIndex != null && targetIndex > 0) {
            delay(50) // Wait for initial composition and measurement
            val totalItems = state.layoutInfo.totalItemsCount
            if (totalItems > 0) {
                val safeIndex = targetIndex.coerceIn(0, totalItems - 1)
                runCatching {
                    state.animateScrollToItem(safeIndex)
                }
            }
        }
        // Safety timeout: ensure highlight request is cleared after 2.5s even if not matched
        delay(2500)
        PreferenceHighlightManager.clear()
    }

    return state
}

/**
 * Modifier that highlights a preference item and brings it into view when it matches
 * the currently requested highlight key.
 */
@Composable
fun Modifier.preferenceHighlight(
    key: Any?,
    shape: Shape = RectangleShape,
): Modifier {
    if (key == null) return this
    val isHighlighted = PreferenceHighlightManager.isHighlighted(key)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val animAlpha = remember { Animatable(0f) }

    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            delay(200)
            runCatching { bringIntoViewRequester.bringIntoView() }
            // Pulse animation: smoothly flash in, hold, then fade out
            animAlpha.animateTo(0.4f, tween(300))
            delay(1200)
            animAlpha.animateTo(0f, tween(600))
            PreferenceHighlightManager.clear()
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.primaryContainer

    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .drawWithContent {
            drawContent()
            if (animAlpha.value > 0f) {
                val alpha = animAlpha.value
                val outline = shape.createOutline(size, layoutDirection, this)
                // Draw background tint
                drawOutline(
                    outline = outline,
                    brush = SolidColor(containerColor.copy(alpha = alpha * 0.5f))
                )
                // Draw accent border
                drawOutline(
                    outline = outline,
                    color = primaryColor.copy(alpha = alpha * 0.85f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
}
