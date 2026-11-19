package xyz.mpv.rex.utils.media

import android.net.Uri

/**
 * Header parsing, serialization, and sanitization for MPV HTTP playback.
 */
object PlaybackHttpHeaders {
  private val headerNamePattern = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

  fun fromFlatPairs(values: Array<String>?): Map<String, String> {
    if (values == null) return emptyMap()
    val headers = linkedMapOf<String, String>()
    values.asList().chunked(2).forEach { pair ->
      if (pair.size == 2) put(headers, pair[0], pair[1])
    }
    return headers
  }

  fun merge(vararg sources: Map<String, String>): Map<String, String> {
    val headers = linkedMapOf<String, String>()
    sources.forEach { source -> source.forEach { (name, value) -> put(headers, name, value) } }
    return headers
  }

  fun value(
    headers: Map<String, String>,
    name: String,
  ): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

  fun userAgent(headers: Map<String, String>): String? = value(headers, "User-Agent")

  fun toMpvHeaderFields(headers: Map<String, String>): String =
    headers.entries
      .filterNot { it.key.equals("User-Agent", ignoreCase = true) }
      .joinToString(",") { (name, value) -> "$name: ${escapeMpvListValue(value)}" }

  fun put(
    target: MutableMap<String, String>,
    rawName: String,
    rawValue: String,
  ) {
    val name = rawName.trim()
    val value = rawValue.trim()
    if (!headerNamePattern.matches(name) || value.any { it == '\r' || it == '\n' || it == '\u0000' }) return
    target.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let { existing -> target.remove(existing) }
    target[name] = value
  }

  fun escapeMpvListValue(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace(",", "\\,")

  fun withFallbackHeaders(headers: Map<String, String>, playableUriStr: String?): Map<String, String> {
    if (playableUriStr.isNullOrBlank()) return headers
    val uri = runCatching { Uri.parse(playableUriStr) }.getOrNull()
    return withFallbackHeaders(headers, uri)
  }

  /**
   * Ensures essential headers like Referer are populated for known streaming domains
   * if omitted by the extractor.
   */
  fun withFallbackHeaders(headers: Map<String, String>, playableUri: Uri?): Map<String, String> {
    if (playableUri == null) return headers
    val result = headers.toMutableMap()
    val host = playableUri.host?.lowercase().orEmpty()
    val hasReferer = result.keys.any { it.equals("Referer", ignoreCase = true) }
    if (!hasReferer) {
      if (host.contains("youtube.com") || host.contains("youtu.be")) {
        result["Referer"] = "https://www.youtube.com/"
      } else if (host.isNotBlank()) {
        HttpUtils.extractRefererDomain(playableUri)?.let { ref ->
          result["Referer"] = "$ref/"
        }
      }
    }
    return result
  }
}
