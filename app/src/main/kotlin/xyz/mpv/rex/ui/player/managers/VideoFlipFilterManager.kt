package xyz.mpv.rex.ui.player.managers

import `is`.xyz.mpv.MPVLib

class VideoFlipFilterManager(
  private val getPropertyString: (String) -> String? = { MPVLib.getPropertyString(it) },
  private val setPropertyString: (String, String) -> Unit = { prop, value -> MPVLib.setPropertyString(prop, value) },
  private val executeCommand: (Array<out String>) -> Unit = { cmd -> MPVLib.command(*cmd) },
) {
  var preFlipHwDec: String? = null
    internal set
  var isFlipSessionActive: Boolean = false
    internal set

  private fun isMediacodecDirect(decoder: String?): Boolean {
    if (decoder.isNullOrEmpty()) return false
    val firstToken = decoder.split(',').firstOrNull()?.trim()
    return firstToken == "mediacodec"
  }

  fun updateFilters(isMirrored: Boolean, isFlipped: Boolean) {
    val isAnyFlipActive = isMirrored || isFlipped

    if (isAnyFlipActive) {
      if (!isFlipSessionActive) {
        isFlipSessionActive = true
        val activeHwDec = getPropertyString("hwdec-current").takeUnless { it.isNullOrEmpty() }
          ?: getPropertyString("hwdec").orEmpty()
        if (isMediacodecDirect(activeHwDec)) {
          preFlipHwDec = getPropertyString("hwdec")?.takeIf { it.isNotEmpty() } ?: activeHwDec
          setPropertyString("hwdec", "mediacodec-copy")
        }
      }

      if (isMirrored) {
        executeCommand(arrayOf("vf", "add", "@mpvex_hflip:hflip"))
      } else {
        executeCommand(arrayOf("vf", "remove", "@mpvex_hflip"))
      }

      if (isFlipped) {
        executeCommand(arrayOf("vf", "add", "@mpvex_vflip:vflip"))
      } else {
        executeCommand(arrayOf("vf", "remove", "@mpvex_vflip"))
      }
    } else {
      executeCommand(arrayOf("vf", "remove", "@mpvex_hflip"))
      executeCommand(arrayOf("vf", "remove", "@mpvex_vflip"))

      val savedHwDec = preFlipHwDec
      if (savedHwDec != null) {
        val currentHwDec = getPropertyString("hwdec")?.trim()
        if (currentHwDec == "mediacodec-copy") {
          setPropertyString("hwdec", savedHwDec)
        }
      }
      preFlipHwDec = null
      isFlipSessionActive = false
    }
  }

  fun reset() {
    preFlipHwDec = null
    isFlipSessionActive = false
  }
}
