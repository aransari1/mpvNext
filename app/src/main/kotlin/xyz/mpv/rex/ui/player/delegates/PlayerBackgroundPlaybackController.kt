package xyz.mpv.rex.ui.player.delegates

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Log
import `is`.xyz.mpv.MPVLib
import xyz.mpv.rex.ui.player.MediaPlaybackService

/**
 * Controller responsible for managing background playback service binding,
 * unbinding, notification updates, and disabling/enabling video tracks during background playback.
 */
class PlayerBackgroundPlaybackController(
  private val activity: Activity,
  private val serviceListener: MediaPlaybackService.ServiceListener,
) {
  var mediaPlaybackService: MediaPlaybackService? = null
  var serviceBound: Boolean = false
  var isManualBackgroundPlayback: Boolean = false
  var isInBackgroundPlayback: Boolean = false
  var lastVid: Int = -1

  val serviceConnection: ServiceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(
        name: ComponentName?,
        service: IBinder?,
      ) {
        val binder = service as? MediaPlaybackService.MediaPlaybackBinder ?: return
        mediaPlaybackService = binder.getService()
        mediaPlaybackService?.setListener(serviceListener)
        serviceBound = true
        Log.d(TAG, "Service connected")
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        Log.d(TAG, "Service disconnected")
        mediaPlaybackService?.setListener(null)
        mediaPlaybackService = null
        serviceBound = false
      }
    }

  /**
   * Starts the background playback service and binds to it.
   */
  fun startBackgroundPlayback(
    fileName: String,
    isReady: Boolean,
    onExtractThumbnail: (suspend () -> Bitmap?)? = null,
    onThumbnailExtracted: ((Bitmap?) -> Unit)? = null,
  ) {
    if (fileName.isBlank() || !isReady) {
      Log.w(TAG, "Cannot start background playback: video not ready")
      return
    }

    if (serviceBound) {
      Log.d(TAG, "Service already bound, skipping start")
      return
    }

    Log.d(TAG, "Starting background playback for: $fileName")

    MediaPlaybackService.createNotificationChannel(activity)

    val artist = runCatching { MPVLib.getPropertyString("metadata/artist") }.getOrNull() ?: ""

    val intent =
      Intent(activity, MediaPlaybackService::class.java).apply {
        putExtra("media_title", fileName)
        putExtra("media_artist", artist)
      }

    try {
      activity.startForegroundService(intent)
      activity.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
      Log.d(TAG, "Service start and bind initiated")
    } catch (e: Exception) {
      Log.e(TAG, "Error starting/binding service", e)
    }
  }

  /**
   * Stops the background playback service and unbinds from it.
   */
  fun endBackgroundPlayback() {
    Log.d(TAG, "Ending background playback service")

    if (serviceBound) {
      try {
        activity.unbindService(serviceConnection)
        Log.d(TAG, "Service unbound successfully")
      } catch (e: Exception) {
        Log.e(TAG, "Error unbinding service", e)
      }
      serviceBound = false
    }

    try {
      activity.stopService(Intent(activity, MediaPlaybackService::class.java))
      Log.d(TAG, "Stop service command sent")
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping service", e)
    }

    mediaPlaybackService = null
  }

  /**
   * Triggers manual background playback by setting the flag, starting the service,
   * restoring system UI, and transitioning to the home screen.
   */
  fun triggerBackgroundPlayback(
    fileName: String,
    isReady: Boolean,
    onRestoreSystemUI: () -> Unit,
    onStartService: () -> Unit,
  ) {
    if (fileName.isBlank() || !isReady) {
      Log.w(TAG, "Cannot trigger background playback: video not ready")
      return
    }

    Log.d(TAG, "User triggered background playback")
    isManualBackgroundPlayback = true

    onStartService()
    onRestoreSystemUI()

    val intent =
      Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
    activity.startActivity(intent)
  }

  /**
   * Disables video decoding to save battery when moving to background playback.
   */
  fun disableVideoForBackground(
    isReady: Boolean,
    fileName: String,
    hasVideoTrack: () -> Boolean,
  ) {
    if (!isReady || fileName.isBlank()) return

    if (!hasVideoTrack()) {
      isInBackgroundPlayback = true
      Log.d(TAG, "Audio-only playback in background")
      return
    }

    val currentVid = MPVLib.getPropertyInt("vid") ?: -1
    if (currentVid > 0) {
      if (lastVid <= 0) {
        lastVid = currentVid
      }
      MPVLib.setPropertyString("vid", "no")
      isInBackgroundPlayback = true
      Log.d(TAG, "Video disabled for background playback (saved vid: $lastVid)")
    } else {
      if (MPVLib.getPropertyString("vid") != "no") {
        MPVLib.setPropertyString("vid", "no")
      }
      isInBackgroundPlayback = true
    }
  }

  /**
   * Restores video decoding when returning from background playback.
   */
  fun enableVideoAfterBackground(
    mpvInitialized: Boolean,
    onSetPropertyString: (String, String) -> Unit,
  ) {
    isInBackgroundPlayback = false
    if (lastVid > 0) {
      Log.d(TAG, "Restoring video after background playback (vid: $lastVid)")
      MPVLib.setPropertyInt("vid", lastVid)
      lastVid = -1
    } else if (mpvInitialized && MPVLib.getPropertyString("vid") == "no") {
      Log.d(TAG, "Restoring video after background playback (setting vid to auto)")
      onSetPropertyString("vid", "auto")
      lastVid = -1
    }
  }

  /**
   * Updates notification media info on the bound service.
   */
  fun setMediaInfo(
    title: String,
    artist: String? = null,
    thumbnail: Bitmap? = null,
  ) {
    mediaPlaybackService?.setMediaInfo(title = title, artist = artist ?: "", thumbnail = thumbnail)
  }

  /**
   * Updates MediaSession on the bound service.
   */
  fun updateMediaSession() {
    mediaPlaybackService?.updateMediaSession()
  }

  companion object {
    private const val TAG = "mpvex"
  }
}
