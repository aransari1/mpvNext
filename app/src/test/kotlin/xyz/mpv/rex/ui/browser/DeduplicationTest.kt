package xyz.mpv.rex.ui.browser

import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.media.model.VideoFolder

class DeduplicationTest {

  private fun createVideo(
    id: Long,
    path: String,
    bucketId: String,
    uri: Uri = mockk(relaxed = true),
  ) = Video(
    id = id,
    title = "Video $id",
    displayName = "video$id.mp4",
    path = path,
    uri = uri,
    duration = 1000L,
    durationFormatted = "00:01",
    size = 1024L,
    sizeFormatted = "1 KB",
    dateModified = 100L,
    dateAdded = 100L,
    mimeType = "video/mp4",
    bucketId = bucketId,
    bucketDisplayName = "Download",
    width = 1920,
    height = 1080,
    fps = 30f,
    resolution = "1920x1080",
  )

  @Test
  fun `videos with duplicate paths are deduplicated by path or uri`() {
    val video1 = createVideo(
      id = 1L,
      path = "/storage/emulated/0/download/YTDLnis/Video/test.mp4",
      bucketId = "download",
    )
    val video2 = createVideo(
      id = 2L,
      path = "/storage/emulated/0/download/YTDLnis/Video/test.mp4",
      bucketId = "Download",
    )

    val list = listOf(video1, video2)
    val deduplicated = list.distinctBy { it.path.ifBlank { it.uri.toString() } }

    assertEquals(1, deduplicated.size)
    assertEquals("/storage/emulated/0/download/YTDLnis/Video/test.mp4", deduplicated.first().path)
  }

  @Test
  fun `folders with duplicate bucketIds are deduplicated`() {
    val folder1 = VideoFolder(
      bucketId = "/storage/emulated/0/Download",
      name = "Download",
      path = "/storage/emulated/0/Download",
      videoCount = 5,
      audioCount = 0,
      totalSize = 5000L,
      totalDuration = 10000L,
      lastModified = 100L,
    )
    val folder2 = VideoFolder(
      bucketId = "/storage/emulated/0/Download",
      name = "Download",
      path = "/storage/emulated/0/Download",
      videoCount = 5,
      audioCount = 0,
      totalSize = 5000L,
      totalDuration = 10000L,
      lastModified = 100L,
    )

    val list = listOf(folder1, folder2)
    val deduplicated = list.distinctBy { it.bucketId }

    assertEquals(1, deduplicated.size)
  }
}
