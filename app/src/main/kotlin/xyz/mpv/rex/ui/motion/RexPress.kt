package xyz.mpv.rex.ui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * One source of truth for expressive press feedback.
 *
 * Before this existed the same gesture shipped at four magnitudes (0.97, 0.98, 0.90, 0.85), each
 * with its own spring, re-implemented file by file. Components should reach for
 * [pressScale] / [pressShape] instead of reading an [InteractionSource] themselves, so a change
 * to the feel lands everywhere at once.
 *
 * Two behaviours are kept separate, because they answer different questions:
 *  - [pressScale]: the transient "pushed in" scale for tappable surfaces.
 *  - [pressShape]: the resting -> pressed shape morph, for controls that change silhouette.
 *
 * Do not add navigation, loading or header motion here. Those are load-bearing for the jank fix
 * documented in `docs/ui-design/NAV_MOTION.md` and are owned by [RexMotion].
 */
object RexPress {
  /**
   * The single press scale used by content-surface cards, settings rows and navigation items.
   *
   * Small enough to read as pressure rather than movement, large enough to register on a
   * 40dp control. The bouncy damping on release is deliberate: the overshoot is what makes the
   * gesture feel alive.
   */
  const val SCALE = 0.96f

  /**
   * Deeper scale for small, chunky targets (shorts controls, dense mini controls) where
   * [SCALE] is too subtle to notice.
   */
  const val SCALE_SMALL_TARGET = 0.90f

  /**
   * Spring for press feedback, derived from the theme's [MotionScheme] so it inherits
   * `MotionScheme.expressive()` instead of hardcoding a spec per call site.
   */
  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  fun scaleSpec(): SpringSpec<Float> {
    val scheme = MotionScheme.expressive()
    val spring = scheme.defaultSpatialSpec<Float>() as? SpringSpec<Float>
    return spring ?: spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMediumLow,
    )
  }

  /** Spacing spring: softer than [scaleSpec], used for shape morphs so they do not snap. */
  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  fun shapeSpec(): SpringSpec<Float> {
    val scheme = MotionScheme.expressive()
    val spring = scheme.defaultSpatialSpec<Float>() as? SpringSpec<Float>
    return spring ?: spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMediumLow,
    )
  }

  /**
   * Animated press scale for [interactionSource], as a [State] so callers can read it inside
   * `graphicsLayer` without recomposing.
   */
  @Composable
  fun pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = SCALE,
  ): State<Float> = animateFloatAsState(
    targetValue = if (isPressed(interactionSource)) pressedScale else 1f,
    animationSpec = scaleSpec(),
    label = "rex_press_scale",
  )

  /**
   * Animated [Float] in 0..1 describing how pressed [interactionSource] is (1 = fully pressed).
   * Use it to drive a shape morph or a colour crossfade.
   */
  @Composable
  fun pressProgress(interactionSource: InteractionSource): State<Float> = animateFloatAsState(
    targetValue = if (isPressed(interactionSource)) 1f else 0f,
    animationSpec = shapeSpec(),
    label = "rex_press_progress",
  )

  /**
   * Morphs between [resting] and [pressed] shapes as [interactionSource] changes.
   *
   * Corner-based shapes (e.g. `RoundedCornerShape`) interpolate their radii; anything else falls
   * back to the resting outline rather than attempting a polygon match mid-press.
   */
  @Composable
  fun pressShape(
    interactionSource: InteractionSource,
    resting: Shape,
    pressed: Shape = resting,
  ): Shape {
    if (resting == pressed) return resting
    val progress by pressProgress(interactionSource)
    return remember(resting, pressed, progress) { morphShape(resting, pressed, progress) }
  }

  /**
   * Convenience [Modifier] applying [pressScale] through `graphicsLayer`.
   *
   * Reads the scale only in the draw phase, so a press never recomposes the subtree.
   */
  @Composable
  fun pressScaleModifier(
    interactionSource: InteractionSource,
    pressedScale: Float = SCALE,
  ): Modifier {
    val scale by pressScale(interactionSource, pressedScale)
    return Modifier.graphicsLayer {
      scaleX = scale
      scaleY = scale
    }
  }

  /** True while [interactionSource] is held down. Shared by every accessor above. */
  @Composable
  fun isPressed(interactionSource: InteractionSource): Boolean {
    val pressed = remember(interactionSource) { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
      interactionSource.interactions.collect { interaction ->
        when (interaction) {
          is PressInteraction.Press -> pressed.value = true
          is PressInteraction.Release, is PressInteraction.Cancel -> pressed.value = false
        }
      }
    }
    return pressed.value
  }

  private fun morphShape(resting: Shape, pressed: Shape, progress: Float): Shape =
    when {
      progress <= 0f -> resting
      progress >= 1f -> pressed
      else -> MorphingShape(resting, pressed, progress)
    }
}

/**
 * A [Shape] that interpolates between two static outlines.
 *
 * Expressive controls keep their resting shape type (rounded rectangle), so corner-based morphs
 * are the common case. Anything else falls back to the resting outline, which is preferable to
 * a crash mid-gesture.
 */
@Immutable
private class MorphingShape(
  private val resting: Shape,
  private val pressed: Shape,
  private val progress: Float,
) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val from = resting.createOutline(size, layoutDirection, density)
    val to = pressed.createOutline(size, layoutDirection, density)
    return when {
      from is Outline.Rounded && to is Outline.Rounded -> Outline.Rounded(
        RoundRect(
          left = lerpFloat(from.roundRect.left, to.roundRect.left, progress),
          top = lerpFloat(from.roundRect.top, to.roundRect.top, progress),
          right = lerpFloat(from.roundRect.right, to.roundRect.right, progress),
          bottom = lerpFloat(from.roundRect.bottom, to.roundRect.bottom, progress),
          topLeftCornerRadius = lerpCorner(from.roundRect.topLeftCornerRadius, to.roundRect.topLeftCornerRadius, progress),
          topRightCornerRadius = lerpCorner(from.roundRect.topRightCornerRadius, to.roundRect.topRightCornerRadius, progress),
          bottomRightCornerRadius = lerpCorner(from.roundRect.bottomRightCornerRadius, to.roundRect.bottomRightCornerRadius, progress),
          bottomLeftCornerRadius = lerpCorner(from.roundRect.bottomLeftCornerRadius, to.roundRect.bottomLeftCornerRadius, progress),
        ),
      )
      else -> from
    }
  }
}

private fun lerpCorner(from: CornerRadius, to: CornerRadius, progress: Float): CornerRadius =
  CornerRadius(
    x = lerpFloat(from.x, to.x, progress),
    y = lerpFloat(from.y, to.y, progress),
  )

private fun lerpFloat(from: Float, to: Float, progress: Float): Float =
  from + (to - from) * progress
