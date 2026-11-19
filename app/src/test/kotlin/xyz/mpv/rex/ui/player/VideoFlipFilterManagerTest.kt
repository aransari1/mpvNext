package xyz.mpv.rex.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.ui.player.managers.VideoFlipFilterManager

class VideoFlipFilterManagerTest {

  @Test
  fun `when hwdec is mediacodec, enabling flip switches hwdec to mediacodec-copy`() {
    val properties = mutableMapOf("hwdec" to "mediacodec", "hwdec-current" to "mediacodec")
    val commands = mutableListOf<List<String>>()

    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value -> properties[prop] = value },
      executeCommand = { commands.add(it.toList()) },
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)

    assertEquals("mediacodec-copy", properties["hwdec"])
    assertEquals("mediacodec", manager.preFlipHwDec)
    assertTrue(manager.isFlipSessionActive)
    assertTrue(commands.contains(listOf("vf", "add", "@mpvex_hflip:hflip")))
    assertTrue(commands.contains(listOf("vf", "remove", "@mpvex_vflip")))
  }

  @Test
  fun `when flip is disabled after being switched by flip, hwdec is restored to previous pre-flip state`() {
    val properties = mutableMapOf("hwdec" to "mediacodec", "hwdec-current" to "mediacodec")
    val commands = mutableListOf<List<String>>()

    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value -> properties[prop] = value },
      executeCommand = { commands.add(it.toList()) },
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals("mediacodec-copy", properties["hwdec"])

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals("mediacodec", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
    assertTrue(commands.contains(listOf("vf", "remove", "@mpvex_hflip")))
    assertTrue(commands.contains(listOf("vf", "remove", "@mpvex_vflip")))
  }

  @Test
  fun `if hwdec was already mediacodec-copy prior to enabling flip, disabling flip leaves hwdec as mediacodec-copy`() {
    val properties = mutableMapOf("hwdec" to "mediacodec-copy", "hwdec-current" to "mediacodec-copy")
    var setPropertyCallCount = 0

    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value ->
        setPropertyCallCount++
        properties[prop] = value
      },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals("mediacodec-copy", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertEquals(0, setPropertyCallCount)

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals("mediacodec-copy", properties["hwdec"])
    assertEquals(0, setPropertyCallCount)
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
  }

  @Test
  fun `if hwdec was no prior to enabling flip, disabling flip leaves hwdec as no`() {
    val properties = mutableMapOf("hwdec" to "no", "hwdec-current" to "no")
    var setPropertyCallCount = 0

    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value ->
        setPropertyCallCount++
        properties[prop] = value
      },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = false, isFlipped = true)
    assertEquals("no", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertEquals(0, setPropertyCallCount)

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals("no", properties["hwdec"])
    assertEquals(0, setPropertyCallCount)
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
  }

  @Test
  fun `if flip is toggled multiple times, pre-flip baseline decoder state is preserved across session and restored`() {
    val properties = mutableMapOf("hwdec" to "mediacodec", "hwdec-current" to "mediacodec")
    val setPropertyCalls = mutableListOf<Pair<String, String>>()
    val commands = mutableListOf<List<String>>()

    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value ->
        setPropertyCalls.add(prop to value)
        properties[prop] = value
      },
      executeCommand = { commands.add(it.toList()) },
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals("mediacodec-copy", properties["hwdec"])
    assertEquals("mediacodec", manager.preFlipHwDec)
    assertTrue(manager.isFlipSessionActive)
    assertEquals(1, setPropertyCalls.size)
    assertEquals("hwdec" to "mediacodec-copy", setPropertyCalls[0])
    assertTrue(commands.contains(listOf("vf", "add", "@mpvex_hflip:hflip")))

    manager.updateFilters(isMirrored = true, isFlipped = true)
    assertEquals("mediacodec", manager.preFlipHwDec)
    assertEquals(1, setPropertyCalls.size)
    assertTrue(commands.contains(listOf("vf", "add", "@mpvex_vflip:vflip")))

    manager.updateFilters(isMirrored = false, isFlipped = true)
    assertEquals("mediacodec", manager.preFlipHwDec)
    assertEquals(1, setPropertyCalls.size)
    assertTrue(commands.contains(listOf("vf", "remove", "@mpvex_hflip")))

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals("mediacodec", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
    assertEquals(2, setPropertyCalls.size)
    assertEquals("hwdec" to "mediacodec", setPropertyCalls[1])
    assertTrue(commands.contains(listOf("vf", "remove", "@mpvex_vflip")))
  }

  @Test
  fun `preserves complex pre-flip option string like mediacodec,no`() {
    val properties = mutableMapOf("hwdec" to "mediacodec,no", "hwdec-current" to "mediacodec")

    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value -> properties[prop] = value },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals("mediacodec-copy", properties["hwdec"])
    assertEquals("mediacodec,no", manager.preFlipHwDec)

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals("mediacodec,no", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
  }

  @Test
  fun `when hwdec-current is null or empty, falls back to hwdec property`() {
    val properties = mutableMapOf("hwdec" to "mediacodec", "hwdec-current" to "")

    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value -> properties[prop] = value },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals("mediacodec-copy", properties["hwdec"])
    assertEquals("mediacodec", manager.preFlipHwDec)

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals("mediacodec", properties["hwdec"])
  }

  @Test
  fun `reset clears session state without restoring hwdec`() {
    val properties = mutableMapOf("hwdec" to "mediacodec", "hwdec-current" to "mediacodec")
    var setPropertyCount = 0

    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value ->
        setPropertyCount++
        properties[prop] = value
      },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals(1, setPropertyCount)
    assertEquals("mediacodec", manager.preFlipHwDec)
    assertTrue(manager.isFlipSessionActive)

    manager.reset()
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals(1, setPropertyCount)
  }

  @Test
  fun `when hwdec-current is empty and hwdec is complex mediacodec,no, enabling flip switches to mediacodec-copy and restores mediacodec,no on disable`() {
    val properties = mutableMapOf("hwdec" to "mediacodec,no", "hwdec-current" to "")
    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value -> properties[prop] = value },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals("mediacodec-copy", properties["hwdec"])
    assertEquals("mediacodec,no", manager.preFlipHwDec)
    assertTrue(manager.isFlipSessionActive)

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals("mediacodec,no", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
  }

  @Test
  fun `when user manually changes hwdec to SW mid-session, disabling flip preserves user selection and does not force mediacodec`() {
    val properties = mutableMapOf("hwdec" to "mediacodec", "hwdec-current" to "mediacodec")
    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value -> properties[prop] = value },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals("mediacodec-copy", properties["hwdec"])
    assertEquals("mediacodec", manager.preFlipHwDec)

    properties["hwdec"] = "no"
    properties["hwdec-current"] = "no"

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals("no", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
  }

  @Test
  fun `when user manually changes hwdec to auto mid-session, disabling flip preserves user selection`() {
    val properties = mutableMapOf("hwdec" to "mediacodec", "hwdec-current" to "mediacodec")
    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value -> properties[prop] = value },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = false, isFlipped = true)
    assertEquals("mediacodec-copy", properties["hwdec"])

    properties["hwdec"] = "auto"

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals("auto", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
  }

  @Test
  fun `when hwdec-current is no and hwdec is mediacodec,no, enabling flip does not switch hwdec because active decoding is software`() {
    val properties = mutableMapOf("hwdec" to "mediacodec,no", "hwdec-current" to "no")
    var setPropertyCount = 0
    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value ->
        setPropertyCount++
        properties[prop] = value
      },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals(0, setPropertyCount)
    assertEquals("mediacodec,no", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertTrue(manager.isFlipSessionActive)

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals(0, setPropertyCount)
    assertEquals("mediacodec,no", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
  }

  @Test
  fun `when pre-flip hwdec is auto and hwdec-current is mediacodec, enabling flip sets mediacodec-copy and disabling restores auto`() {
    val properties = mutableMapOf("hwdec" to "auto", "hwdec-current" to "mediacodec")
    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value -> properties[prop] = value },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals("mediacodec-copy", properties["hwdec"])
    assertEquals("auto", manager.preFlipHwDec)

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals("auto", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
  }

  @Test
  fun `when pre-flip hwdec is auto-copy and hwdec-current is mediacodec-copy, enabling flip does not change hwdec`() {
    val properties = mutableMapOf("hwdec" to "auto-copy", "hwdec-current" to "mediacodec-copy")
    var setPropertyCount = 0
    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value ->
        setPropertyCount++
        properties[prop] = value
      },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = false, isFlipped = true)
    assertEquals(0, setPropertyCount)
    assertEquals("auto-copy", properties["hwdec"])
    assertNull(manager.preFlipHwDec)

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals(0, setPropertyCount)
    assertEquals("auto-copy", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
  }

  @Test
  fun `when hwdec contains leading and trailing spaces, enabling flip switches to mediacodec-copy and restores original string`() {
    val properties = mutableMapOf("hwdec" to "  mediacodec , no  ", "hwdec-current" to "mediacodec")
    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value -> properties[prop] = value },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals("mediacodec-copy", properties["hwdec"])
    assertEquals("  mediacodec , no  ", manager.preFlipHwDec)

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals("  mediacodec , no  ", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
  }

  @Test
  fun `when updateFilters is called with no flip active repeatedly, hwdec is never modified`() {
    val properties = mutableMapOf("hwdec" to "mediacodec")
    var setPropertyCount = 0
    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value ->
        setPropertyCount++
        properties[prop] = value
      },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = false, isFlipped = false)
    manager.updateFilters(isMirrored = false, isFlipped = false)

    assertEquals(0, setPropertyCount)
    assertEquals("mediacodec", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
  }

  @Test
  fun `when both properties are null or empty, enabling and disabling flip does not crash and leaves properties untouched`() {
    val properties = mutableMapOf<String, String>()
    var setPropertyCount = 0
    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value ->
        setPropertyCount++
        properties[prop] = value
      },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals(0, setPropertyCount)
    assertNull(manager.preFlipHwDec)
    assertTrue(manager.isFlipSessionActive)

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals(0, setPropertyCount)
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
  }

  @Test
  fun `when pre-flip hwdec is mediacodec-copy and hwdec-current is empty, enabling flip does not change hwdec`() {
    val properties = mutableMapOf("hwdec" to "mediacodec-copy", "hwdec-current" to "")
    var setPropertyCount = 0
    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value ->
        setPropertyCount++
        properties[prop] = value
      },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals(0, setPropertyCount)
    assertEquals("mediacodec-copy", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertTrue(manager.isFlipSessionActive)

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals(0, setPropertyCount)
    assertEquals("mediacodec-copy", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
  }

  @Test
  fun `when user manually changes hwdec to mediacodec mid-session, disabling flip preserves user selection and does not re-write`() {
    val properties = mutableMapOf("hwdec" to "mediacodec,no", "hwdec-current" to "mediacodec")
    var setPropertyCount = 0
    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value ->
        setPropertyCount++
        properties[prop] = value
      },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals(1, setPropertyCount)
    assertEquals("mediacodec-copy", properties["hwdec"])
    assertEquals("mediacodec,no", manager.preFlipHwDec)

    properties["hwdec"] = "mediacodec"

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals(1, setPropertyCount)
    assertEquals("mediacodec", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
  }

  @Test
  fun `when flip is toggled off and back on while other flip remains active, baseline state is preserved`() {
    val properties = mutableMapOf("hwdec" to "mediacodec", "hwdec-current" to "mediacodec")
    val setPropertyCalls = mutableListOf<Pair<String, String>>()
    val manager = VideoFlipFilterManager(
      getPropertyString = { properties[it] },
      setPropertyString = { prop, value ->
        setPropertyCalls.add(prop to value)
        properties[prop] = value
      },
      executeCommand = {},
    )

    manager.updateFilters(isMirrored = true, isFlipped = false)
    assertEquals("mediacodec-copy", properties["hwdec"])
    assertEquals("mediacodec", manager.preFlipHwDec)
    assertEquals(1, setPropertyCalls.size)

    manager.updateFilters(isMirrored = true, isFlipped = true)
    assertEquals("mediacodec", manager.preFlipHwDec)
    assertEquals(1, setPropertyCalls.size)

    manager.updateFilters(isMirrored = false, isFlipped = true)
    assertEquals("mediacodec", manager.preFlipHwDec)
    assertTrue(manager.isFlipSessionActive)
    assertEquals(1, setPropertyCalls.size)

    manager.updateFilters(isMirrored = true, isFlipped = true)
    assertEquals("mediacodec", manager.preFlipHwDec)
    assertTrue(manager.isFlipSessionActive)
    assertEquals(1, setPropertyCalls.size)

    manager.updateFilters(isMirrored = false, isFlipped = false)
    assertEquals("mediacodec", properties["hwdec"])
    assertNull(manager.preFlipHwDec)
    assertFalse(manager.isFlipSessionActive)
    assertEquals(2, setPropertyCalls.size)
    assertEquals("hwdec" to "mediacodec", setPropertyCalls[1])
  }
}
