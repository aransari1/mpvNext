package xyz.mpv.rex.utils.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.Charset

class SubtitleEncodingUtilsTest {

  @Test
  fun isValidUtf8_validUtf8Text_returnsTrue() {
    val utf8Bytes = "Hello World! مرحبا بكم".toByteArray(Charsets.UTF_8)
    assertTrue(SubtitleEncodingUtils.isValidUtf8(utf8Bytes))
  }

  @Test
  fun isValidUtf8_windows1256ArabicText_returnsFalse() {
    val arabicCharset = Charset.forName("windows-1256")
    val arabicBytes = "مرحبا بك في تطبيق ريكس".toByteArray(arabicCharset)
    assertFalse(SubtitleEncodingUtils.isValidUtf8(arabicBytes))
  }

  @Test
  fun detectEncoding_windows1256ArabicBytes_detectsWindows1256() {
    val arabicCharset = Charset.forName("windows-1256")
    val arabicBytes = "قام بالترجمة المهندس كريم رمضان اليوم".toByteArray(arabicCharset)
    val detected = SubtitleEncodingUtils.detectEncoding(arabicBytes)
    assertEquals("windows-1256", detected)
  }

  @Test
  fun detectEncoding_utf8Text_detectsUtf8() {
    val utf8Bytes = "1\n00:00:01,000 --> 00:00:04,000\nHello World\n".toByteArray(Charsets.UTF_8)
    val detected = SubtitleEncodingUtils.detectEncoding(utf8Bytes)
    assertEquals("utf-8", detected)
  }

  @Test
  fun getDisplayName_validCode_returnsFriendlyName() {
    assertEquals("Arabic (Windows-1256)", SubtitleEncodingUtils.getDisplayName("windows-1256"))
    assertEquals("Auto (Detect)", SubtitleEncodingUtils.getDisplayName("auto"))
    assertEquals("UTF-8 (Universal)", SubtitleEncodingUtils.getDisplayName("utf-8"))
  }

  @Test
  fun isTextSubtitle_variousExtensions() {
    assertTrue(SubtitleEncodingUtils.isTextSubtitle("movie.srt"))
    assertTrue(SubtitleEncodingUtils.isTextSubtitle("movie.vtt"))
    assertTrue(SubtitleEncodingUtils.isTextSubtitle("movie.ass"))
    assertTrue(SubtitleEncodingUtils.isTextSubtitle("movie.ssa"))
    assertFalse(SubtitleEncodingUtils.isTextSubtitle("movie.sup"))
    assertFalse(SubtitleEncodingUtils.isTextSubtitle("movie.mkv"))
  }

  @Test
  fun testRealWorldSubtitle01FromIssue344() {
    val testFile = File("tmp/subtitles/01.srt")
    if (!testFile.exists()) return

    val bytes = testFile.readBytes()
    assertFalse(SubtitleEncodingUtils.isValidUtf8(bytes))
    val encoding = SubtitleEncodingUtils.detectEncoding(bytes)
    assertEquals("windows-1256", encoding)

    val decoded = String(bytes, Charset.forName(encoding))
    assertTrue(decoded.contains("قم بالإعلان هنا"))
  }
}
