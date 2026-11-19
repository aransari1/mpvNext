package xyz.mpv.rex.ui.player.ambient

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import xyz.mpv.rex.ui.player.PlayerViewModel
import xyz.mpv.rex.ui.player.VideoAspect

@Composable
fun AmbientGlowOverlay(
  viewModel: PlayerViewModel,
  modifier: Modifier = Modifier
) {
  val isEnabled by viewModel.ambientController.isAmbientEnabled.collectAsState()
  if (!isEnabled) return

  val aspectMode by viewModel.videoAspect.collectAsState()
  val customAspect by viewModel.currentAspectRatio.collectAsState()

  // Ambient glow is designed for letterbox/pillarbox margins in Fit mode or custom aspect ratio
  if (aspectMode != VideoAspect.Fit && customAspect <= 0.0) {
    return
  }

  val zoom by viewModel.videoZoom.collectAsState()
  val panX by viewModel.videoPanX.collectAsState()
  val panY by viewModel.videoPanY.collectAsState()
  val advancedZoom by viewModel.advancedZoomEnabled.collectAsState()
  val scaleXState by viewModel.videoScaleX.collectAsState()
  val scaleYState by viewModel.videoScaleY.collectAsState()

  val ambientColors by viewModel.ambientController.ambientColors.collectAsState()

  val animLeft by animateColorAsState(
    targetValue = ambientColors.left,
    animationSpec = tween(400),
    label = "ambient_left"
  )
  val animRight by animateColorAsState(
    targetValue = ambientColors.right,
    animationSpec = tween(400),
    label = "ambient_right"
  )
  val animTop by animateColorAsState(
    targetValue = ambientColors.top,
    animationSpec = tween(400),
    label = "ambient_top"
  )
  val animBottom by animateColorAsState(
    targetValue = ambientColors.bottom,
    animationSpec = tween(400),
    label = "ambient_bottom"
  )

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val screenW = constraints.maxWidth.toFloat()
    val screenH = constraints.maxHeight.toFloat()
    if (screenW <= 0f || screenH <= 0f) return@BoxWithConstraints

    val rawVidAr = if (customAspect > 0.0) {
      customAspect
    } else {
      viewModel.getVideoOutAspect() ?: (16.0 / 9.0)
    }
    val vidAr = rawVidAr.toFloat().coerceAtLeast(0.01f)
    val screenAr = screenW / screenH

    // Unzoomed base video size (Fit mode geometry)
    val baseW = if (screenAr > vidAr) screenH * vidAr else screenW
    val baseH = if (screenAr > vidAr) screenH else screenW / vidAr

    val (scaleX, scaleY) = if (advancedZoom) {
      scaleXState.coerceAtLeast(0.01f) to scaleYState.coerceAtLeast(0.01f)
    } else {
      val scale = 2f.pow(zoom).coerceAtLeast(0.01f)
      scale to scale
    }

    val renderedW = baseW * scaleX
    val renderedH = baseH * scaleY

    // In mpv, video-pan-x / y shifts relative to the scaled video size
    val centerX = (screenW / 2f) + (panX * renderedW)
    val centerY = (screenH / 2f) + (panY * renderedH)

    val videoLeft = centerX - (renderedW / 2f)
    val videoRight = centerX + (renderedW / 2f)
    val videoTop = centerY - (renderedH / 2f)
    val videoBottom = centerY + (renderedH / 2f)

    // Clamped margin boundaries
    val leftBarWidth = videoLeft.coerceIn(0f, screenW)
    val rightBarStart = videoRight.coerceIn(0f, screenW)
    val topBarHeight = videoTop.coerceIn(0f, screenH)
    val bottomBarStart = videoBottom.coerceIn(0f, screenH)

    // Active video span clamped to screen bounds (prevents perpendicular strips from crossing into corners)
    val videoSpanXStart = videoLeft.coerceIn(0f, screenW)
    val videoSpanXEnd = videoRight.coerceIn(0f, screenW)
    val videoSpanWidth = (videoSpanXEnd - videoSpanXStart).coerceAtLeast(0f)

    val videoSpanYStart = videoTop.coerceIn(0f, screenH)
    val videoSpanYEnd = videoBottom.coerceIn(0f, screenH)
    val videoSpanHeight = (videoSpanYEnd - videoSpanYStart).coerceAtLeast(0f)

    // Smoothly fade out ambient glow if zoomed out below 0
    val alphaFactor = if (zoom < 0f) {
      ((zoom + 0.2f) / 0.2f).coerceIn(0f, 1f)
    } else {
      1f
    }
    if (alphaFactor <= 0.01f) return@BoxWithConstraints
    val glowAlpha = 0.55f * alphaFactor

    Canvas(modifier = Modifier.fillMaxSize()) {
      // Left bar ambient glow (spans video height)
      if (leftBarWidth > 1f && videoSpanHeight > 1f && animLeft != Color.Transparent) {
        val leftBrush = Brush.horizontalGradient(
          colors = listOf(Color.Transparent, animLeft.copy(alpha = glowAlpha)),
          startX = 0f,
          endX = leftBarWidth
        )
        drawRect(
          brush = leftBrush,
          topLeft = Offset(0f, videoSpanYStart),
          size = Size(leftBarWidth, videoSpanHeight)
        )
      }

      // Right bar ambient glow (spans video height)
      if (rightBarStart < screenW - 1f && videoSpanHeight > 1f && animRight != Color.Transparent) {
        val rightBrush = Brush.horizontalGradient(
          colors = listOf(animRight.copy(alpha = glowAlpha), Color.Transparent),
          startX = rightBarStart,
          endX = screenW
        )
        drawRect(
          brush = rightBrush,
          topLeft = Offset(rightBarStart, videoSpanYStart),
          size = Size(screenW - rightBarStart, videoSpanHeight)
        )
      }

      // Top bar ambient glow (spans video width)
      if (topBarHeight > 1f && videoSpanWidth > 1f && animTop != Color.Transparent) {
        val topBrush = Brush.verticalGradient(
          colors = listOf(Color.Transparent, animTop.copy(alpha = glowAlpha)),
          startY = 0f,
          endY = topBarHeight
        )
        drawRect(
          brush = topBrush,
          topLeft = Offset(videoSpanXStart, 0f),
          size = Size(videoSpanWidth, topBarHeight)
        )
      }

      // Bottom bar ambient glow (spans video width)
      if (bottomBarStart < screenH - 1f && videoSpanWidth > 1f && animBottom != Color.Transparent) {
        val bottomBrush = Brush.verticalGradient(
          colors = listOf(animBottom.copy(alpha = glowAlpha), Color.Transparent),
          startY = bottomBarStart,
          endY = screenH
        )
        drawRect(
          brush = bottomBrush,
          topLeft = Offset(videoSpanXStart, bottomBarStart),
          size = Size(videoSpanWidth, screenH - bottomBarStart)
        )
      }
    }
  }
}
