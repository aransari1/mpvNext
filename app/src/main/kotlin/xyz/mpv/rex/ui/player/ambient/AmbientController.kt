package xyz.mpv.rex.ui.player.ambient

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.mpv.rex.preferences.PlayerPreferences

/**
 * Controller for capturing ambient lighting from the video surface using Android's PixelCopy.
 * Decouples ambient glow entirely from mpv video scaling and shaders.
 */
class AmbientController(
  private val playerPreferences: PlayerPreferences,
  private val scope: CoroutineScope,
  private val onShowText: (Boolean) -> Unit
) {
  companion object {
    private const val CAPTURE_INTERVAL_MS = 250L
    private const val BITMAP_WIDTH = 32
    private const val BITMAP_HEIGHT = 18
  }

  private val _isAmbientEnabled = MutableStateFlow(playerPreferences.isAmbientEnabled.get())
  val isAmbientEnabled: StateFlow<Boolean> = _isAmbientEnabled.asStateFlow()

  private val _ambientColors = MutableStateFlow(AmbientColors())
  val ambientColors: StateFlow<AmbientColors> = _ambientColors.asStateFlow()

  private val captureBitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888)
  private val handler = Handler(Looper.getMainLooper())
  private var captureJob: Job? = null

  fun toggleAmbientMode() {
    val newState = !_isAmbientEnabled.value
    _isAmbientEnabled.value = newState
    playerPreferences.isAmbientEnabled.set(newState)
    if (!newState) {
      _ambientColors.value = AmbientColors()
    }
    onShowText(newState)
  }

  fun startCapture(
    surfaceViewProvider: () -> SurfaceView?,
    isPlayingProvider: () -> Boolean,
    isEligibleProvider: () -> Boolean
  ) {
    captureJob?.cancel()
    captureJob = scope.launch {
      while (isActive) {
        delay(CAPTURE_INTERVAL_MS)
        if (!_isAmbientEnabled.value || !isEligibleProvider() || !isPlayingProvider()) {
          continue
        }
        val surfaceView = surfaceViewProvider() ?: continue
        if (!surfaceView.holder.surface.isValid) continue

        runCatching {
          PixelCopy.request(
            surfaceView,
            captureBitmap,
            { copyResult ->
              if (copyResult == PixelCopy.SUCCESS) {
                processBitmapColors(captureBitmap)
              }
            },
            handler
          )
        }
      }
    }
  }

  fun stopCapture() {
    captureJob?.cancel()
    captureJob = null
    _ambientColors.value = AmbientColors()
  }

  private fun processBitmapColors(bitmap: Bitmap) {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return

    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    // Left vertical strip (columns 0..w/4)
    val leftColor = averageColor(pixels, w, h, 0, w / 4, 0, h)
    // Right vertical strip (columns 3*w/4..w)
    val rightColor = averageColor(pixels, w, h, (3 * w) / 4, w, 0, h)
    // Top horizontal strip (rows 0..h/4)
    val topColor = averageColor(pixels, w, h, 0, w, 0, h / 4)
    // Bottom horizontal strip (rows 3*h/4..h)
    val bottomColor = averageColor(pixels, w, h, 0, w, (3 * h) / 4, h)

    _ambientColors.value = AmbientColors(
      left = leftColor,
      right = rightColor,
      top = topColor,
      bottom = bottomColor
    )
  }

  private fun averageColor(
    pixels: IntArray,
    bitmapW: Int,
    bitmapH: Int,
    startX: Int,
    endX: Int,
    startY: Int,
    endY: Int
  ): Color {
    var totalR = 0L
    var totalG = 0L
    var totalB = 0L
    var count = 0

    val safeStartX = startX.coerceIn(0, bitmapW - 1)
    val safeEndX = endX.coerceIn(safeStartX + 1, bitmapW)
    val safeStartY = startY.coerceIn(0, bitmapH - 1)
    val safeEndY = endY.coerceIn(safeStartY + 1, bitmapH)

    for (y in safeStartY until safeEndY) {
      for (x in safeStartX until safeEndX) {
        val pixel = pixels[y * bitmapW + x]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        // Ignore pure black letterbox pixels in the sample
        if (r > 15 || g > 15 || b > 15) {
          totalR += r
          totalG += g
          totalB += b
          count++
        }
      }
    }

    if (count == 0) return Color.Transparent

    val avgR = (totalR / count).toInt()
    val avgG = (totalG / count).toInt()
    val avgB = (totalB / count).toInt()

    return boostColor(avgR, avgG, avgB)
  }

  private fun boostColor(r: Int, g: Int, b: Int): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(r, g, b, hsv)
    // Boost saturation by 35% for vibrant ambient light
    hsv[1] = (hsv[1] * 1.35f).coerceIn(0.25f, 1.0f)
    // Clamp brightness to pleasant ambient range
    hsv[2] = (hsv[2] * 0.95f).coerceIn(0.30f, 0.80f)
    val colorInt = android.graphics.Color.HSVToColor(hsv)
    return Color(colorInt)
  }

  fun cleanup() {
    stopCapture()
  }
}
