package xyz.mpv.rex.utils.media

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

data class SubtitleEncoding(
  val code: String,
  val displayName: String,
)

object SubtitleEncodingUtils {
  private const val TAG = "SubtitleEncodingUtils"
  private const val MAX_SUBTITLE_BYTES = 15 * 1024 * 1024 // 15 MB limit to prevent OOM
  private const val CONVERTED_DIR_NAME = "converted_subtitles"

  val ENCODINGS: List<SubtitleEncoding> = listOf(
    SubtitleEncoding("auto", "Auto (Detect)"),
    SubtitleEncoding("utf-8", "UTF-8 (Universal)"),
    SubtitleEncoding("windows-1256", "Arabic (Windows-1256)"),
    SubtitleEncoding("iso-8859-6", "Arabic (ISO-8859-6)"),
    SubtitleEncoding("windows-1250", "Central European (Windows-1250)"),
    SubtitleEncoding("iso-8859-2", "Central European (ISO-8859-2)"),
    SubtitleEncoding("windows-1251", "Cyrillic (Windows-1251)"),
    SubtitleEncoding("iso-8859-5", "Cyrillic (ISO-8859-5)"),
    SubtitleEncoding("koi8-r", "Russian (KOI8-R)"),
    SubtitleEncoding("windows-1252", "Western European (Windows-1252)"),
    SubtitleEncoding("iso-8859-1", "Western European (ISO-8859-1)"),
    SubtitleEncoding("windows-1253", "Greek (Windows-1253)"),
    SubtitleEncoding("iso-8859-7", "Greek (ISO-8859-7)"),
    SubtitleEncoding("windows-1255", "Hebrew (Windows-1255)"),
    SubtitleEncoding("iso-8859-8", "Hebrew (ISO-8859-8)"),
    SubtitleEncoding("windows-1254", "Turkish (Windows-1254)"),
    SubtitleEncoding("iso-8859-9", "Turkish (ISO-8859-9)"),
    SubtitleEncoding("gb18030", "Chinese Simplified (GB18030 / GBK)"),
    SubtitleEncoding("big5", "Chinese Traditional (Big5)"),
    SubtitleEncoding("shift_jis", "Japanese (Shift-JIS)"),
    SubtitleEncoding("euc-jp", "Japanese (EUC-JP)"),
    SubtitleEncoding("euc-kr", "Korean (EUC-KR)"),
    SubtitleEncoding("windows-874", "Thai (Windows-874)"),
    SubtitleEncoding("tis-620", "Thai (TIS-620)"),
    SubtitleEncoding("windows-1258", "Vietnamese (Windows-1258)"),
  )

  private val ENCODING_MAP: Map<String, String> = ENCODINGS.associate { it.code to it.displayName }

  fun getDisplayName(code: String): String {
    return ENCODING_MAP[code.lowercase(Locale.ROOT)] ?: code
  }

  fun isTextSubtitle(fileNameOrPath: String): Boolean {
    val ext = fileNameOrPath.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return ext in setOf("srt", "vtt", "ass", "ssa", "sub", "smi", "sami", "txt", "lrc")
  }

  /**
   * Fast validation checking whether the byte array contains valid UTF-8 sequences.
   */
  fun isValidUtf8(bytes: ByteArray): Boolean {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
      return true
    }
    val decoder = Charsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)

    return try {
      decoder.decode(ByteBuffer.wrap(bytes))
      true
    } catch (_: Exception) {
      false
    }
  }

  private fun countBigrams(bytes: ByteArray, b1: Int, b2: Int, sampleLimit: Int): Int {
    val limit = minOf(bytes.size - 1, sampleLimit)
    var count = 0
    val byte1 = b1.toByte()
    val byte2 = b2.toByte()
    for (i in 0 until limit) {
      if (bytes[i] == byte1 && bytes[i + 1] == byte2) {
        count++
      }
    }
    return count
  }

  /**
   * Detects the character encoding of the given bytes using byte markers and script frequency analysis.
   */
  fun detectEncoding(bytes: ByteArray): String {
    // 1. Check if UTF-16 BOM is present
    if (bytes.size >= 2) {
      if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return "utf-16be"
      if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return "utf-16le"
    }

    // 2. Check if valid UTF-8
    if (isValidUtf8(bytes)) {
      return "utf-8"
    }

    // 3. Script frequency analysis for single-byte legacy encodings
    val sampleLimit = 64 * 1024 // Analyze up to first 64KB
    val sampleLength = minOf(bytes.size, sampleLimit)

    val arabicChars = runCatching {
      String(bytes, 0, sampleLength, Charset.forName("windows-1256")).count { it in '\u0600'..'\u06FF' }
    }.getOrDefault(0)

    val cyrillicChars = runCatching {
      String(bytes, 0, sampleLength, Charset.forName("windows-1251")).count { it in '\u0400'..'\u04FF' }
    }.getOrDefault(0)

    val greekChars = runCatching {
      String(bytes, 0, sampleLength, Charset.forName("windows-1253")).count { it in '\u0370'..'\u03FF' }
    }.getOrDefault(0)

    val hebrewChars = runCatching {
      String(bytes, 0, sampleLength, Charset.forName("windows-1255")).count { it in '\u0590'..'\u05FF' }
    }.getOrDefault(0)

    // Arabic in Windows-1256:
    // Common bigrams: ال (C7 E1), لا (E1 C7), في (DA ED), من (E3 E4), ما (E3 C7), ان (C7 E4), ون (E6 E4), ين (ED E4)
    val arabicBigrams = listOf(
      0xC7 to 0xE1, // ال
      0xE1 to 0xC7, // لا
      0xDA to 0xED, // في
      0xE3 to 0xE4, // من
      0xE3 to 0xC7, // ما
      0xC7 to 0xE4, // ان
      0xE6 to 0xE4, // ون
      0xED to 0xE4, // ين
    )
    var arabicScore = 0
    for ((b1, b2) in arabicBigrams) {
      arabicScore += countBigrams(bytes, b1, b2, sampleLimit)
    }

    // Cyrillic in Windows-1251:
    // Common bigrams: ст (F1 F2), но (ED EE), ен (E5 ED), то (F2 EE), на (ED E0), ов (EE E2), ни (ED E8), ра (F0 E0), ко (EA EE)
    val cyrillicBigrams = listOf(
      0xF1 to 0xF2, // ст
      0xED to 0xEE, // но
      0xE5 to 0xED, // ен
      0xF2 to 0xEE, // то
      0xED to 0xE0, // на
      0xEE to 0xE2, // ов
      0xED to 0xE8, // ни
      0xF0 to 0xE0, // ра
      0xEA to 0xEE, // ко
    )
    var cyrillicScore = 0
    for ((b1, b2) in cyrillicBigrams) {
      cyrillicScore += countBigrams(bytes, b1, b2, sampleLimit)
    }

    // Greek in Windows-1253:
    // Common bigrams: κα (EA E1), αι (E1 E9), το (F4 EF), να (ED E1), στ (F3 F4), τη (F4 E7)
    val greekBigrams = listOf(
      0xEA to 0xE1, // κα
      0xE1 to 0xE9, // αι
      0xF4 to 0xEF, // το
      0xED to 0xE1, // να
      0xF3 to 0xF4, // στ
      0xF4 to 0xE7, // τη
    )
    var greekScore = 0
    for ((b1, b2) in greekBigrams) {
      greekScore += countBigrams(bytes, b1, b2, sampleLimit)
    }

    // Hebrew in Windows-1255:
    // Common bigrams: של (F9 EC), את (E0 FA), על (F2 EC), זה (E6 E4), לא (EC E0), כי (EB E9)
    val hebrewBigrams = listOf(
      0xF9 to 0xEC, // של
      0xE0 to 0xFA, // את
      0xF2 to 0xEC, // על
      0xE6 to 0xE4, // זה
      0xEC to 0xE0, // לא
      0xEB to 0xE9, // כי
    )
    var hebrewScore = 0
    for ((b1, b2) in hebrewBigrams) {
      hebrewScore += countBigrams(bytes, b1, b2, sampleLimit)
    }

    Log.d(TAG, "Detection - Arabic: chars=$arabicChars, bg=$arabicScore; Cyrillic: chars=$cyrillicChars, bg=$cyrillicScore")

    if (arabicChars >= 5 || cyrillicChars >= 5 || greekChars >= 5 || hebrewChars >= 5) {
      if (arabicChars >= cyrillicChars && (arabicScore > cyrillicScore || cyrillicScore == 0)) {
        return "windows-1256"
      }
      if (cyrillicChars >= arabicChars && (cyrillicScore > arabicScore || arabicScore == 0)) {
        return "windows-1251"
      }
      if (greekChars >= 5 && greekChars >= arabicChars) {
        return "windows-1253"
      }
      if (hebrewChars >= 5 && hebrewChars >= arabicChars) {
        return "windows-1255"
      }
    }

    val maxScore = maxOf(arabicScore, cyrillicScore, greekScore, hebrewScore)
    if (maxScore >= 10) {
      return when (maxScore) {
        arabicScore -> "windows-1256"
        cyrillicScore -> "windows-1251"
        greekScore -> "windows-1253"
        hebrewScore -> "windows-1255"
        else -> "windows-1252"
      }
    }

    // Central European Windows-1250 check: Polish, Czech, Hungarian letters
    // Ą (A5), ą (B9), Ć (C6), ć (E6), Ę (CA), ę (EA), Ł (A3), ł (B3), Ń (D1), ń (F1), Ś (8C), ś (9C), Ź (8F), ź (9F), Ż (AF), ż (BF)
    var ceCount = 0
    val limit = minOf(bytes.size, sampleLimit)
    for (i in 0 until limit) {
      val b = bytes[i].toInt() and 0xFF
      if (b in listOf(0xA5, 0xB9, 0xC6, 0xE6, 0xCA, 0xEA, 0xA3, 0xB3, 0xD1, 0xF1, 0x8C, 0x9C, 0x8F, 0x9F, 0xAF, 0xBF)) {
        ceCount++
      }
    }
    if (ceCount >= 15) {
      return "windows-1250"
    }

    // Default fallback for legacy single-byte encoding
    return "windows-1252"
  }

  private val originalBytesMap = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

  fun registerOriginalBytes(pathOrUri: String, bytes: ByteArray) {
    originalBytesMap[pathOrUri] = bytes
  }

  fun getOriginalBytes(pathOrUri: String): ByteArray? {
    return originalBytesMap[pathOrUri]
  }

  /**
   * Normalizes a subtitle from bytes, converting to UTF-8 if necessary.
   * Returns the converted file path if conversion was performed, or null if already valid UTF-8.
   */
  fun convertToUtf8IfNecessary(
    context: Context,
    bytes: ByteArray,
    originalName: String,
    preferredEncoding: String = "auto",
  ): File? {
    if (bytes.size > MAX_SUBTITLE_BYTES) {
      Log.w(TAG, "Subtitle file exceeds max size ($MAX_SUBTITLE_BYTES bytes), skipping conversion")
      return null
    }

    val isAuto = preferredEncoding.isBlank() || preferredEncoding.equals("auto", ignoreCase = true)

    // If auto and already valid UTF-8, no conversion needed
    if (isAuto && isValidUtf8(bytes)) {
      return null
    }

    val targetEncoding = if (!isAuto) {
      preferredEncoding
    } else {
      detectEncoding(bytes)
    }

    if (targetEncoding.equals("utf-8", ignoreCase = true)) {
      return null
    }

    Log.i(TAG, "Transcoding subtitle '$originalName' from encoding '$targetEncoding' to UTF-8")

    return runCatching {
      val charset = Charset.forName(targetEncoding)
      val decodedText = String(bytes, charset)

      val cacheDir = File(context.cacheDir, CONVERTED_DIR_NAME).apply { mkdirs() }
      val safeBaseName = originalName.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9._-]"), "_")
      val ext = originalName.substringAfterLast('.', "srt")
      val outFile = File(cacheDir, "${safeBaseName}_converted.$ext")
      val rawFile = File(cacheDir, "${outFile.nameWithoutExtension}.raw")

      outFile.writeText(decodedText, Charsets.UTF_8)
      rawFile.writeBytes(bytes)
      registerOriginalBytes(outFile.absolutePath, bytes)
      Log.i(TAG, "Successfully converted subtitle to: ${outFile.absolutePath}")
      outFile
    }.onFailure { e ->
      Log.e(TAG, "Failed to transcode subtitle from $targetEncoding", e)
    }.getOrNull()
  }

  /**
   * Normalizes a local subtitle file.
   * Returns the path to the converted UTF-8 file, or the original file path if no conversion was needed.
   */
  fun normalizeLocalSubtitleFile(
    context: Context,
    file: File,
    preferredEncoding: String = "auto",
  ): String {
    if (!file.exists() || !file.canRead() || !isTextSubtitle(file.name)) {
      return file.absolutePath
    }

    return runCatching {
      val bytes = file.readBytes()
      registerOriginalBytes(file.absolutePath, bytes)
      val converted = convertToUtf8IfNecessary(context, bytes, file.name, preferredEncoding)
      if (converted != null) {
        registerOriginalBytes(converted.absolutePath, bytes)
        converted.absolutePath
      } else {
        file.absolutePath
      }
    }.getOrDefault(file.absolutePath)
  }

  /**
   * Normalizes a subtitle from Uri (file:// or content://).
   * Returns a pair of (resolvedPathForMpv, convertedFileOrNull).
   */
  fun normalizeSubtitleUri(
    context: Context,
    uri: Uri,
    preferredEncoding: String = "auto",
  ): Pair<String, File?> {
    val fileName = uri.lastPathSegment ?: "subtitle.srt"
    if (!isTextSubtitle(fileName)) {
      return (uri.toString()) to null
    }

    return runCatching {
      val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return (uri.toString()) to null

      registerOriginalBytes(uri.toString(), bytes)
      val converted = convertToUtf8IfNecessary(context, bytes, fileName, preferredEncoding)
      if (converted != null) {
        registerOriginalBytes(converted.absolutePath, bytes)
        converted.absolutePath to converted
      } else {
        uri.toString() to null
      }
    }.getOrElse { e ->
      Log.e(TAG, "Error normalizing subtitle URI: $uri", e)
      uri.toString() to null
    }
  }

  /**
   * Immediately reloads all active external subtitle tracks in MPV with the given encoding.
   */
  fun applyEncodingChange(context: Context, newEncoding: String) {
    runCatching {
      val trackCount = runCatching { `is`.xyz.mpv.MPVLib.getPropertyInt("track-list/count") ?: 0 }.getOrDefault(0)
      val primarySid = runCatching { `is`.xyz.mpv.MPVLib.getPropertyInt("sid") ?: 0 }.getOrDefault(0)
      val secondarySid = runCatching { `is`.xyz.mpv.MPVLib.getPropertyInt("secondary-sid") ?: 0 }.getOrDefault(0)
      val cacheDir = File(context.cacheDir, CONVERTED_DIR_NAME).apply { mkdirs() }

      data class ExternalTrack(
        val id: Int,
        val isPrimary: Boolean,
        val isSecondary: Boolean,
        val externalFilename: String,
        val title: String?,
      )

      val externalTracks = mutableListOf<ExternalTrack>()
      for (i in 0 until trackCount) {
        val type = `is`.xyz.mpv.MPVLib.getPropertyString("track-list/$i/type") ?: continue
        if (type != "sub") continue
        val isExternal = `is`.xyz.mpv.MPVLib.getPropertyBoolean("track-list/$i/external") ?: false
        if (!isExternal) continue
        val id = `is`.xyz.mpv.MPVLib.getPropertyInt("track-list/$i/id") ?: continue
        val isSelected = `is`.xyz.mpv.MPVLib.getPropertyBoolean("track-list/$i/selected") ?: false
        val externalFilename = `is`.xyz.mpv.MPVLib.getPropertyString("track-list/$i/external-filename") ?: continue
        val title = `is`.xyz.mpv.MPVLib.getPropertyString("track-list/$i/title")
        externalTracks.add(
          ExternalTrack(
            id = id,
            isPrimary = isSelected || (id == primarySid),
            isSecondary = (id == secondarySid),
            externalFilename = externalFilename,
            title = title,
          )
        )
      }

      for (track in externalTracks) {
        val file = File(track.externalFilename)
        val rawFile = File(cacheDir, "${file.nameWithoutExtension}.raw")

        val originalBytes = originalBytesMap[track.externalFilename]
          ?: (if (rawFile.exists()) rawFile.readBytes() else null)
          ?: (if (file.exists()) file.readBytes() else null)
          ?: continue

        val targetEncoding = if (newEncoding.isBlank() || newEncoding.equals("auto", ignoreCase = true)) {
          detectEncoding(originalBytes)
        } else {
          newEncoding
        }

        val charset = runCatching { Charset.forName(targetEncoding) }.getOrDefault(Charsets.UTF_8)
        val newText = String(originalBytes, charset)

        val isInsideCache = runCatching {
          file.canonicalPath.startsWith(cacheDir.canonicalPath)
        }.getOrDefault(false)

        if (isInsideCache && file.exists()) {
          file.writeText(newText, Charsets.UTF_8)
          `is`.xyz.mpv.MPVLib.command("sub-reload", track.id.toString())
          Log.i(TAG, "Re-encoded in-place cache subtitle ${track.externalFilename} with $targetEncoding and reloaded")
        } else {
          val safeBaseName = file.nameWithoutExtension.replace(Regex("[^a-zA-Z0-9._-]"), "_")
          val ext = file.extension.ifBlank { "srt" }
          val newCacheFile = File(cacheDir, "${safeBaseName}_${track.id}_converted.$ext")
          val newRawFile = File(cacheDir, "${newCacheFile.nameWithoutExtension}.raw")
          newCacheFile.writeText(newText, Charsets.UTF_8)
          newRawFile.writeBytes(originalBytes)

          registerOriginalBytes(newCacheFile.absolutePath, originalBytes)

          `is`.xyz.mpv.MPVLib.command("sub-remove", track.id.toString())
          val mode = if (track.isPrimary) "select" else "auto"
          if (track.title != null) {
            `is`.xyz.mpv.MPVLib.command("sub-add", newCacheFile.absolutePath, mode, track.title)
          } else {
            `is`.xyz.mpv.MPVLib.command("sub-add", newCacheFile.absolutePath, mode)
          }

          if (track.isSecondary) {
            val countAfter = `is`.xyz.mpv.MPVLib.getPropertyInt("track-list/count") ?: 0
            if (countAfter > 0) {
              val newId = `is`.xyz.mpv.MPVLib.getPropertyInt("track-list/${countAfter - 1}/id")
              if (newId != null) {
                `is`.xyz.mpv.MPVLib.setPropertyInt("secondary-sid", newId)
              }
            }
          }
          Log.i(TAG, "Replaced external subtitle track ${track.id} with converted file ${newCacheFile.absolutePath}")
        }
      }
      `is`.xyz.mpv.MPVLib.command("sub-reload")
    }.onFailure { e ->
      Log.e(TAG, "Failed to apply encoding change: $newEncoding", e)
    }
  }

  /**
   * Cleans up all converted subtitle files from the cache directory and clears memory maps.
   */
  fun cleanupConvertedSubtitles(context: Context) {
    originalBytesMap.clear()
    runCatching {
      val cacheDir = File(context.cacheDir, CONVERTED_DIR_NAME)
      if (cacheDir.exists() && cacheDir.isDirectory) {
        cacheDir.listFiles()?.forEach { it.delete() }
      }
    }.onFailure { e ->
      Log.w(TAG, "Failed to cleanup converted subtitles", e)
    }
  }
}
