package xyz.mpv.rex.ui.player.delegates

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.ui.player.MPVView
import xyz.mpv.rex.ui.player.PlayerOrientation

/**
 * Controller responsible for managing screen orientation and rotation states
 * based on user preferences and video aspect ratios.
 */
class PlayerOrientationController(
  private val activity: Activity,
  private val playerPreferences: PlayerPreferences,
  private val player: MPVView,
) : DefaultLifecycleObserver {

  /**
   * Tracks whether orientation was restored from DB or intent.
   */
  var isOrientationRestored: Boolean = false

  /**
   * Tracks whether user manually cycled/overrode orientation for the current video.
   */
  var isUserOverridden: Boolean = false

  /**
   * Starts orientation tracking / lifecycle observation.
   */
  fun start() {
    // Hook for start lifecycle
  }

  /**
   * Stops orientation tracking / lifecycle observation.
   */
  fun stop() {
    // Hook for stop lifecycle
  }

  override fun onStart(owner: LifecycleOwner) {
    start()
  }

  override fun onStop(owner: LifecycleOwner) {
    stop()
  }

  /**
   * Sets the screen orientation based on user preferences.
   *
   * IMPORTANT: Preferences are the single source of truth for orientation.
   * This method applies the preference value when videos load.
   * The rotation button temporarily overrides this without changing preferences.
   *
   * For "Video" orientation mode, this will wait for video-params/aspect to update
   * to the correct orientation, starting with landscape as fallback.
   *
   * @param width Optional video width from metadata to set orientation before video loads
   * @param height Optional video height from metadata to set orientation before video loads
   * @param rotation Optional video rotation from metadata to correctly determine aspect ratio
   */
  fun setOrientation(width: Int = -1, height: Int = -1, rotation: Int = 0) {
    val orientationPref = playerPreferences.orientation.get()

    activity.requestedOrientation =
      when (orientationPref) {
        PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        PlayerOrientation.Smart, PlayerOrientation.Video -> {
          // If the user has manually overridden orientation for this video, don't auto-reset it
          if (isUserOverridden) {
            Log.d(TAG, "setOrientation - User manual override active: keeping ${activity.requestedOrientation}")
            return
          }

          // For Smart mode, check if orientation was already restored from database
          val isSmartMode = orientationPref == PlayerOrientation.Smart

          // If in Smart mode and we've already restored a choice from the database,
          // keep using it and don't re-calculate from aspect ratio.
          if (isSmartMode && isOrientationRestored) {
            Log.d(TAG, "setOrientation - Smart mode: using restored orientation ${activity.requestedOrientation}")
            return
          }

          // 1. Try provided width/height from metadata first (to avoid jumpy transition)
          if (width > 0 && height > 0) {
            // Swap dimensions if video has 90 or 270 degree rotation
            val isRotated = rotation == 90 || rotation == 270
            val effectiveWidth = if (isRotated) height else width
            val effectiveHeight = if (isRotated) width else height

            val aspect = effectiveWidth.toDouble() / effectiveHeight.toDouble()
            Log.d(TAG, "setOrientation - Using metadata: ${width}x${height}, rot=$rotation, aspect=$aspect")
            if (aspect > 1.0) {
              ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
              ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
          } else {
            // 2. Fallback to current player aspect ratio
            val aspect = runCatching { player.getVideoOutAspect() }.getOrNull()
            Log.d(TAG, "setOrientation - ${if (isSmartMode) "Smart (fallback)" else "Video"} mode: aspect=$aspect")
            if (aspect == null || aspect <= 0.0) {
              // Aspect not available yet or audio-only file - do not force orientation change
              Log.d(TAG, "setOrientation - Aspect not available or audio-only file")
              activity.requestedOrientation
            } else {
              // Aspect available - set correct orientation now
              val orientation = if (aspect > 1.0) {
                Log.d(TAG, "setOrientation - Aspect $aspect > 1.0, setting landscape")
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
              } else {
                Log.d(TAG, "setOrientation - Aspect $aspect <= 1.0, setting portrait")
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
              }
              orientation
            }
          }
        }
        PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      }
  }

  companion object {
    private const val TAG = "mpvex"
  }
}
