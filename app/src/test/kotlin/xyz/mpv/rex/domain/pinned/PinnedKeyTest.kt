package xyz.mpv.rex.domain.pinned

import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.ui.browser.components.pinnedKeyOf
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedItem
import xyz.mpv.rex.ui.browser.videolist.VideoWithPlaybackInfo

/**
 * The pin badge is shown when an item's [pinnedKeyOf] is in the pinned path set.
 * Every card type the browser renders must resolve to the same key that the
 * repository stores, otherwise the item gets no badge (issue #466 regression:
 * the media library and per-folder video list render [VideoWithPlaybackInfo],
 * and that branch silently dropped the pin for every media item).
 */
class PinnedKeyTest {

  private val mediaUri: Uri = mockk(relaxed = true)

  private fun video(path: String) = Video(
    id = 1L,
    title = "title",
    displayName = path.substringAfterLast('/'),
    path = path,
    uri = mediaUri,
    duration = 0L,
    durationFormatted = "0:00",
    size = 0L,
    sizeFormatted = "0 B",
    dateModified = 0L,
    dateAdded = 0L,
    mimeType = "video/mp4",
    bucketId = "",
    bucketDisplayName = "",
    width = 0,
    height = 0,
    fps = 0f,
    resolution = "",
  )

  private fun folder(path: String) = FileSystemItem.Folder(
    name = path.substringAfterLast('/'),
    path = path,
    lastModified = 0L,
    videoCount = 1,
  )

  private fun videoFile(video: Video) = FileSystemItem.VideoFile(
    name = video.displayName,
    path = video.path,
    lastModified = 0L,
    video = video,
  )

  private fun videoFolder(path: String) = VideoFolder(
    bucketId = "1",
    name = path.substringAfterLast('/'),
    path = path,
    videoCount = 1,
  )

  @Test
  fun `video folder and filesystem folder use their path`() {
    assertEquals("/storage/Movies", pinnedKeyOf(videoFolder("/storage/Movies")))
    assertEquals("/storage/Movies", pinnedKeyOf(folder("/storage/Movies")))
  }

  @Test
  fun `plain video and filesystem video file use their path`() {
    val file = video("/storage/Movies/a.mp4")
    assertEquals("/storage/Movies/a.mp4", pinnedKeyOf(file))
    assertEquals("/storage/Movies/a.mp4", pinnedKeyOf(videoFile(file)))
  }

  @Test
  fun `video with playback info resolves to the underlying video path`() {
    // This is the shape rendered by the media library and the per-folder video list.
    val wrapped = VideoWithPlaybackInfo(video("/storage/Movies/a.mp4"), progressPercentage = 0.5f)
    assertEquals("/storage/Movies/a.mp4", pinnedKeyOf(wrapped))
  }

  @Test
  fun `recently played video resolves to the underlying video path`() {
    val item = RecentlyPlayedItem.VideoItem(video("/storage/Movies/a.mp4"), timestamp = 1L)
    assertEquals("/storage/Movies/a.mp4", pinnedKeyOf(item))
  }

  @Test
  fun `blank path falls back to the uri string`() {
    val noPath = video(path = "")
    assertEquals(mediaUri.toString(), pinnedKeyOf(noPath))
    assertEquals(mediaUri.toString(), pinnedKeyOf(VideoWithPlaybackInfo(noPath)))
  }

  @Test
  fun `unpinnable items resolve to null`() {
    assertNull(pinnedKeyOf(Any()))
    assertNull(pinnedKeyOf("just a string"))
  }

  @Test
  fun `every pinnable card shape shares one key for the same media file`() {
    val path = "/storage/Movies/a.mp4"
    val file = video(path)
    val keys = setOf(
      pinnedKeyOf(file),
      pinnedKeyOf(videoFile(file)),
      pinnedKeyOf(VideoWithPlaybackInfo(file)),
      pinnedKeyOf(RecentlyPlayedItem.VideoItem(file, timestamp = 0L)),
    )
    assertEquals(setOf<String?>(path), keys)
  }
}
