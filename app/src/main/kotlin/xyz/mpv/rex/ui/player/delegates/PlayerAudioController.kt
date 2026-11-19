package xyz.mpv.rex.ui.player.delegates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import `is`.xyz.mpv.MPVLib

/**
 * Controller responsible for audio focus management and handling the
 * ACTION_AUDIO_BECOMING_NOISY broadcast receiver.
 */
class PlayerAudioController(
  private val context: Context,
  private val audioManager: AudioManager,
  private val onPausePlayback: () -> Unit,
  private val onUnpausePlayback: () -> Unit,
  private val isPlayerPaused: () -> Boolean,
  private val onDuckVolume: (factor: Float) -> Unit = { factor ->
    MPVLib.command("multiply", "volume", factor.toString())
  },
  private val onClearKeepScreenOn: () -> Unit = {},
) {
  private var audioFocusRequest: AudioFocusRequest? = null
  private var restoreAudioFocus: () -> Unit = {}
  private var noisyReceiverRegistered = false

  private val noisyReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(
        context: Context?,
        intent: Intent?,
      ) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
          onPausePlayback()
          onClearKeepScreenOn()
        }
      }
    }

  private val audioFocusChangeListener =
    AudioManager.OnAudioFocusChangeListener { focusChange ->
      when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
          -> {
          // Save current state to restore later
          val oldRestore = restoreAudioFocus
          val wasPlayerPaused = isPlayerPaused()
          onPausePlayback()
          restoreAudioFocus = {
            oldRestore()
            if (!wasPlayerPaused) onUnpausePlayback()
          }
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
          // Lower volume temporarily
          onDuckVolume(0.5f)
          restoreAudioFocus = {
            onDuckVolume(2.0f)
          }
        }

        AudioManager.AUDIOFOCUS_GAIN -> {
          // Restore previous audio state
          restoreAudioFocus()
          restoreAudioFocus = {}
        }

        AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
          Log.d(TAG, "Audio focus request failed")
        }
      }
    }

  /**
   * Initializes audio focus request if background service is not bound.
   */
  fun setupAudioFocus(serviceBound: Boolean, autoplayOnOpen: Boolean) {
    if (!serviceBound) {
      audioFocusRequest =
        AudioFocusRequest
          .Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(
            AudioAttributes
              .Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
              .build(),
          ).setOnAudioFocusChangeListener(audioFocusChangeListener)
          .setAcceptsDelayedFocusGain(true)
          .setWillPauseWhenDucked(true)
          .build()
      if (autoplayOnOpen) {
        requestAudioFocus()
      }
    }
  }

  /**
   * Requests audio focus.
   *
   * @return true if audio focus was granted immediately, false otherwise
   */
  fun requestAudioFocus(): Boolean {
    val req = audioFocusRequest ?: return false
    val result = audioManager.requestAudioFocus(req)
    return when (result) {
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        restoreAudioFocus = {}
        true
      }

      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
        restoreAudioFocus = { requestAudioFocus() }
        false
      }

      else -> {
        restoreAudioFocus = {}
        false
      }
    }
  }

  /**
   * Abandons audio focus if requested.
   */
  fun abandonAudioFocus() {
    if (restoreAudioFocus != {}) {
      audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
      restoreAudioFocus = {}
    }
  }

  /**
   * Registers the noisy broadcast receiver.
   */
  fun registerNoisyReceiver() {
    if (!noisyReceiverRegistered) {
      val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
      context.registerReceiver(noisyReceiver, filter)
      noisyReceiverRegistered = true
    }
  }

  /**
   * Unregisters the noisy broadcast receiver.
   */
  fun unregisterNoisyReceiver() {
    if (noisyReceiverRegistered) {
      runCatching {
        context.unregisterReceiver(noisyReceiver)
        noisyReceiverRegistered = false
      }
    }
  }

  companion object {
    private const val TAG = "mpvex"
  }
}
