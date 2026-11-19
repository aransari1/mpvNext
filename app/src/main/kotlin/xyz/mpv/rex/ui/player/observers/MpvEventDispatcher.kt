package xyz.mpv.rex.ui.player.observers

import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode

/**
 * Dispatches MPV property change callbacks and core player events
 * to their corresponding listeners and state handlers.
 */
class MpvEventDispatcher(
  private val listener: EventListener,
) {
  interface EventListener {
    fun onVideoDimensionChanged(property: String, value: Long)
    fun onVideoAspectChanged(aspect: Double)
    fun onPauseStateChanged(isPaused: Boolean)
    fun onEofReached(isEof: Boolean)
    fun onLuaInvocation(property: String, value: String)
    fun onStartFile()
    fun onFileLoaded()
    fun onPlaybackRestart()
  }

  fun dispatchProperty(property: String) {
    // Currently no properties use this signature
  }

  fun dispatchProperty(property: String, value: Long) {
    when (property) {
      "video-params/w",
      "video-params/h" -> listener.onVideoDimensionChanged(property, value)
    }
  }

  fun dispatchProperty(property: String, value: Boolean) {
    when (property) {
      "pause" -> listener.onPauseStateChanged(value)
      "eof-reached" -> listener.onEofReached(value)
    }
  }

  fun dispatchProperty(property: String, value: String) {
    when (property.substringBeforeLast("/")) {
      "user-data/mpvex" -> listener.onLuaInvocation(property, value)
    }
  }

  fun dispatchProperty(property: String, value: Double) {
    when (property) {
      "video-params/aspect" -> listener.onVideoAspectChanged(value)
    }
  }

  @Suppress("UnusedParameter")
  fun dispatchProperty(property: String, value: MPVNode) {
    // Currently no MPVNode properties are handled
  }

  fun dispatchEvent(eventId: Int) {
    when (eventId) {
      MPVLib.MpvEvent.MPV_EVENT_START_FILE -> listener.onStartFile()
      MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> listener.onFileLoaded()
      MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> listener.onPlaybackRestart()
    }
  }

  companion object {
    private const val TAG = "MpvEventDispatcher"
  }
}
