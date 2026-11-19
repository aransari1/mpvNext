package xyz.mpv.rex.ui.player.managers

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import `is`.xyz.mpv.MPVLib
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.PlayerPreferences

/**
 * Manages video screenshot and snapshot capture, subtitle inclusion configuration,
 * temporary caching, and MediaStore / external storage persistence.
 */
class PlayerSnapshotManager(
  private val playerPreferences: PlayerPreferences,
) {
  private val _isSnapshotLoading = MutableStateFlow(false)
  val isSnapshotLoading: StateFlow<Boolean> = _isSnapshotLoading.asStateFlow()

  fun takeSnapshot(context: Context, scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
      _isSnapshotLoading.value = true
      try {
        val includeSubtitles = playerPreferences.includeSubtitlesInSnapshot.get()

        // Generate filename with timestamp
        val timestamp =
          SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "mpv_snapshot_$timestamp.png"

        // Create a temporary file first
        val tempFile = File(context.cacheDir, filename)

        // Take screenshot using MPV to temp file, with or without subtitles
        if (includeSubtitles) {
          MPVLib.command("screenshot-to-file", tempFile.absolutePath, "subtitles")
        } else {
          MPVLib.command("screenshot-to-file", tempFile.absolutePath, "video")
        }

        // Wait a bit for MPV to finish writing the file
        delay(200)

        // Check if file was created
        if (!tempFile.exists() || tempFile.length() == 0L) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to create screenshot", Toast.LENGTH_SHORT).show()
          }
          return@launch
        }

        // Use different methods based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          // Android 10+ - Use MediaStore with RELATIVE_PATH
          val contentValues =
            ContentValues().apply {
              put(MediaStore.Images.Media.DISPLAY_NAME, filename)
              put(MediaStore.Images.Media.MIME_TYPE, "image/png")
              put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/mpvSnaps",
              )
              put(MediaStore.Images.Media.IS_PENDING, 1)
            }

          val resolver = context.contentResolver
          val imageUri =
            resolver.insert(
              MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
              contentValues,
            )

          if (imageUri != null) {
            // Copy temp file to MediaStore
            resolver.openOutputStream(imageUri)?.use { outputStream ->
              tempFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
              }
            }

            // Mark as finished
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)

            // Delete temp file
            tempFile.delete()

            // Show success toast
            withContext(Dispatchers.Main) {
              Toast
                .makeText(
                  context,
                  context.getString(R.string.player_sheets_frame_navigation_snapshot_saved),
                  Toast.LENGTH_SHORT,
                ).show()
            }
          } else {
            throw Exception("Failed to create MediaStore entry")
          }
        } else {
          // Android 9 and below - Use legacy external storage
          @Suppress("DEPRECATION")
          val picturesDir =
            Environment.getExternalStoragePublicDirectory(
              Environment.DIRECTORY_PICTURES,
            )
          val snapshotsDir = File(picturesDir, "mpvSnaps")

          // Create directory if it doesn't exist
          if (!snapshotsDir.exists()) {
            val created = snapshotsDir.mkdirs()
            if (!created && !snapshotsDir.exists()) {
              throw Exception("Failed to create mpvSnaps directory")
            }
          }

          val destFile = File(snapshotsDir, filename)
          tempFile.copyTo(destFile, overwrite = true)
          tempFile.delete()

          // Notify media scanner about the new file
          MediaScannerConnection.scanFile(
            context,
            arrayOf(destFile.absolutePath),
            arrayOf("image/png"),
            null,
          )

          withContext(Dispatchers.Main) {
            Toast
              .makeText(
                context,
                context.getString(R.string.player_sheets_frame_navigation_snapshot_saved),
                Toast.LENGTH_SHORT,
              ).show()
          }
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          Toast.makeText(context, "Failed to save snapshot: ${e.message}", Toast.LENGTH_LONG).show()
        }
      } finally {
        _isSnapshotLoading.value = false
      }
    }
  }
}
