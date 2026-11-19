package xyz.mpv.rex.ui.player.delegates

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.util.Log
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.ui.player.PlayerViewModel

/**
 * Controller responsible for MediaSession lifecycle, system media control integration,
 * lockscreen/notification transport controls, and playback metadata.
 */
class PlayerMediaSessionController(
  private val context: Context,
  private val viewModel: PlayerViewModel,
  private val playerPreferences: PlayerPreferences,
) {
  private lateinit var mediaSession: MediaSession
  private lateinit var playbackStateBuilder: PlaybackState.Builder
  private var mediaSessionInitialized = false

  val isInitialized: Boolean
    get() = mediaSessionInitialized

  val sessionToken: MediaSession.Token?
    get() = if (mediaSessionInitialized) mediaSession.sessionToken else null

  /**
   * Initializes MediaSession for integration with system media controls.
   * Supports Android Auto, Wear OS, Bluetooth controls, and notification controls.
   */
  fun setup() {
    runCatching {
      mediaSession =
        MediaSession(context, TAG).apply {
          setCallback(
            object : MediaSession.Callback() {
              private fun canHandle() = !playerPreferences.disableMediaButtons.get()

              override fun onPlay() {
                if (!canHandle()) return
                viewModel.unpause()
                updatePlaybackState(isPlaying = true)
              }

              override fun onPause() {
                if (!canHandle()) return
                viewModel.pause()
                updatePlaybackState(isPlaying = false)
              }

              override fun onSkipToNext() {
                if (!canHandle()) return
                viewModel.handleMediaNext()
              }

              override fun onSkipToPrevious() {
                if (!canHandle()) return
                viewModel.handleMediaPrevious()
              }

              override fun onSeekTo(pos: Long) {
                if (!canHandle()) return
                viewModel.seekTo((pos / 1000).toInt())
                updatePlaybackState(isPlaying = viewModel.paused == false)
              }
            },
          )
          isActive = true
        }
      playbackStateBuilder =
        PlaybackState
          .Builder()
          .setActions(
            PlaybackState.ACTION_PLAY or
              PlaybackState.ACTION_PAUSE or
              PlaybackState.ACTION_PLAY_PAUSE or
              PlaybackState.ACTION_SEEK_TO or
              PlaybackState.ACTION_SKIP_TO_NEXT or
              PlaybackState.ACTION_SKIP_TO_PREVIOUS,
          )
      mediaSessionInitialized = true
    }.onFailure { e ->
      Log.e(TAG, "Failed to initialize MediaSession", e)
      mediaSessionInitialized = false
    }
  }

  /**
   * Updates MediaSession playback state (playing/paused).
   *
   * @param isPlaying true if currently playing, false if paused
   */
  fun updatePlaybackState(isPlaying: Boolean) {
    if (!mediaSessionInitialized) return
    runCatching {
      val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
      val positionMs = (viewModel.pos ?: 0) * 1000L
      mediaSession.setPlaybackState(
        playbackStateBuilder
          .setState(state, positionMs, if (isPlaying) 1.0f else 0f)
          .build(),
      )
    }.onFailure { e -> Log.e(TAG, "Error updating playback state", e) }
  }

  /**
   * Updates MediaSession metadata (title, duration, etc.).
   *
   * @param title The media title
   * @param durationMs The media duration in milliseconds
   */
  fun updateMetadata(
    title: String,
    durationMs: Long,
  ) {
    if (!mediaSessionInitialized) return
    runCatching {
      val metadata =
        MediaMetadata
          .Builder()
          .putString(MediaMetadata.METADATA_KEY_TITLE, title)
          .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
          .build()
      mediaSession.setMetadata(metadata)
    }.onFailure { e -> Log.e(TAG, "Error updating metadata", e) }
  }

  /**
   * Releases MediaSession resources.
   * Called during activity cleanup.
   */
  fun release() {
    if (!mediaSessionInitialized) return
    runCatching {
      mediaSession.isActive = false
      mediaSession.release()
    }.onFailure { e -> Log.e(TAG, "Error releasing MediaSession", e) }
    mediaSessionInitialized = false
  }

  companion object {
    private const val TAG = "mpvex"
  }
}
