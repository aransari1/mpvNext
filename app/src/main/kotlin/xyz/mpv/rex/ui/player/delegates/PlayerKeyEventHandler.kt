package xyz.mpv.rex.ui.player.delegates

import android.view.KeyEvent
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.ui.player.MPVView
import xyz.mpv.rex.ui.player.PlayerViewModel
import xyz.mpv.rex.ui.player.Sheets

/**
 * Handles hardware key events (D-pad, media keys, volume keys, keyboard shortcuts)
 * for the player activity.
 */
class PlayerKeyEventHandler(
  private val viewModel: PlayerViewModel,
  private val playerPreferences: PlayerPreferences,
  private val player: MPVView,
  private val onFinishTask: () -> Unit,
) {
  /**
   * Handles hardware key down events for player control.
   * Supports D-pad navigation, media keys, and volume controls.
   *
   * @param keyCode The key code
   * @param event The key event
   * @param fallback Fallback lambda when event is not handled by this delegate
   * @return true if event was handled, false otherwise
   */
  @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
  fun onKeyDown(
    keyCode: Int,
    event: KeyEvent?,
    fallback: () -> Boolean,
  ): Boolean {
    val isTrackSheetOpen =
      viewModel.sheetShown.value == Sheets.SubtitleTracks ||
        viewModel.sheetShown.value == Sheets.AudioTracks
    val isNoSheetOpen = viewModel.sheetShown.value == Sheets.None

    when (keyCode) {
      KeyEvent.KEYCODE_DPAD_UP -> {
        return fallback()
      }

      KeyEvent.KEYCODE_DPAD_DOWN,
      KeyEvent.KEYCODE_DPAD_RIGHT,
      KeyEvent.KEYCODE_DPAD_LEFT,
        -> {
        if (isTrackSheetOpen) {
          return fallback()
        }

        if (isNoSheetOpen) {
          when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
              viewModel.handleRightDoubleTap()
              return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
              viewModel.handleLeftDoubleTap()
              return true
            }
          }
        }
        return fallback()
      }

      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
        if (isTrackSheetOpen) {
          return fallback()
        }
        return fallback()
      }

      KeyEvent.KEYCODE_SPACE -> {
        viewModel.pauseUnpause()
        return true
      }

      KeyEvent.KEYCODE_VOLUME_UP -> {
        viewModel.changeVolumeBy(1)
        viewModel.displayVolumeSlider()
        return true
      }

      KeyEvent.KEYCODE_VOLUME_DOWN -> {
        viewModel.changeVolumeBy(-1)
        viewModel.displayVolumeSlider()
        return true
      }

      KeyEvent.KEYCODE_MEDIA_STOP -> {
        if (playerPreferences.disableMediaButtons.get()) return true
        onFinishTask()
        return true
      }

      KeyEvent.KEYCODE_MEDIA_REWIND -> {
        if (playerPreferences.disableMediaButtons.get()) return true
        viewModel.handleLeftDoubleTap()
        return true
      }

      KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
        if (playerPreferences.disableMediaButtons.get()) return true
        viewModel.handleRightDoubleTap()
        return true
      }

      KeyEvent.KEYCODE_MEDIA_PLAY,
      KeyEvent.KEYCODE_MEDIA_PAUSE,
      KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
      KeyEvent.KEYCODE_MEDIA_NEXT,
      KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
        if (playerPreferences.disableMediaButtons.get()) return true

        event?.let { player.onKey(it) }
        return fallback()
      }

      else -> {
        event?.let { player.onKey(it) }
        return fallback()
      }
    }
  }

  /**
   * Handles hardware key up events for player control.
   *
   * @param keyCode The key code
   * @param event The key event
   * @param fallback Fallback lambda when event is not handled by this delegate
   * @return true if event was handled, false otherwise
   */
  fun onKeyUp(
    keyCode: Int,
    event: KeyEvent?,
    fallback: () -> Boolean,
  ): Boolean {
    event?.let {
      if (player.onKey(it)) return true
    }
    return fallback()
  }
}
