package xyz.mpv.rex.domain.ytdl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.domain.ytdl.model.ResolvedStream
import xyz.mpv.rex.domain.ytdl.model.VideoQuality

class YtdlVideoQualityTest {

  @Test
  fun `VideoQuality DASH and AudioOnly properties evaluate correctly`() {
    val dashQuality = VideoQuality(
      id = "137",
      label = "1080p",
      height = 1080,
      width = 1920,
      fps = 30,
      codec = "AVC",
      videoUrl = "https://example.com/video.mp4",
      audioUrl = "https://example.com/audio.m4a",
      isDASH = true,
      isAudioOnly = false,
    )
    assertTrue(dashQuality.isDASH)
    assertFalse(dashQuality.isAudioOnly)
    assertEquals(1080, dashQuality.height)
    assertEquals("AVC", dashQuality.codec)

    val audioOnlyQuality = VideoQuality(
      id = "audio_only",
      label = "Audio Only",
      videoUrl = "https://example.com/audio.m4a",
      audioUrl = null,
      isDASH = false,
      isAudioOnly = true,
    )
    assertFalse(audioOnlyQuality.isDASH)
    assertTrue(audioOnlyQuality.isAudioOnly)
    assertNull(audioOnlyQuality.audioUrl)
  }

  @Test
  fun `ResolvedStream default availableQualities is empty list for backwards compatibility`() {
    val stream = ResolvedStream(
      isSuccess = true,
      videoUrl = "https://example.com/stream.mp4",
      audioUrl = null,
      title = "Test Video",
    )
    assertTrue(stream.isSuccess)
    assertTrue(stream.availableQualities.isEmpty())
    assertFalse(stream.isDASH)
  }

  @Test
  fun `ResolvedStream with DASH and available qualities operates correctly`() {
    val qualities = listOf(
      VideoQuality(
        id = "137",
        label = "1080p",
        height = 1080,
        fps = 30,
        videoUrl = "https://example.com/1080.mp4",
        audioUrl = "https://example.com/audio.m4a",
        isDASH = true,
      ),
      VideoQuality(
        id = "136",
        label = "720p",
        height = 720,
        fps = 30,
        videoUrl = "https://example.com/720.mp4",
        audioUrl = "https://example.com/audio.m4a",
        isDASH = true,
      ),
    )

    val stream = ResolvedStream(
      isSuccess = true,
      videoUrl = "https://example.com/1080.mp4",
      audioUrl = "https://example.com/audio.m4a",
      title = "DASH Video",
      availableQualities = qualities,
    )

    assertTrue(stream.isSuccess)
    assertTrue(stream.isDASH)
    assertEquals(2, stream.availableQualities.size)
    assertEquals("1080p", stream.availableQualities[0].label)
    assertEquals("720p", stream.availableQualities[1].label)
  }

  @Test
  fun `VideoQuality shortLabel formats to height with p correctly`() {
    val q1 = VideoQuality(id = "1", label = "1440p60 (2k)", height = 1440)
    assertEquals("1440p", q1.shortLabel)

    val q2 = VideoQuality(id = "2", label = "2160p60 (4K)", height = 2160)
    assertEquals("2160p", q2.shortLabel)

    val q3 = VideoQuality(id = "3", label = "1080p60", height = 1080)
    assertEquals("1080p", q3.shortLabel)

    val q4 = VideoQuality(id = "4", label = "720p", height = 720)
    assertEquals("720p", q4.shortLabel)

    val q5 = VideoQuality(id = "5", label = "1440p60 (2k)", height = 0)
    assertEquals("1440p", q5.shortLabel)

    val q6 = VideoQuality(id = "6", label = "Audio Only", isAudioOnly = true)
    assertEquals("Audio Only", q6.shortLabel)
  }
}
