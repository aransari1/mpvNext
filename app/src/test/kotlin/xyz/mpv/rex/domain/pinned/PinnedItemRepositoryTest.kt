package xyz.mpv.rex.domain.pinned

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mpv.rex.domain.browser.FileSystemItem
import xyz.mpv.rex.preferences.preference.Preference
import xyz.mpv.rex.preferences.preference.PreferenceStore
import xyz.mpv.rex.ui.browser.components.pinnedKeyOf
import xyz.mpv.rex.utils.sort.PinnedSortUtils
import java.nio.file.Files
import java.nio.file.Paths

class PinnedItemRepositoryTest {

  private class InMemoryPreference<T>(
    private val key: String,
    private val defaultValue: T,
    private val storage: MutableMap<String, Any?>,
  ) : Preference<T> {
    private val flow = MutableStateFlow(get())

    override fun key(): String = key
    @Suppress("UNCHECKED_CAST")
    override fun get(): T = (storage[key] as? T) ?: defaultValue
    override fun set(value: T) {
      storage[key] = value
      flow.value = value
    }
    override fun isSet(): Boolean = storage.containsKey(key)
    override fun delete() {
      storage.remove(key)
      flow.value = defaultValue
    }
    override fun defaultValue(): T = defaultValue
    override fun changes(): Flow<T> = flow.asStateFlow()
    override fun stateIn(scope: CoroutineScope) = flow.asStateFlow()
  }

  private class InMemoryPreferenceStore : PreferenceStore {
    private val storage = mutableMapOf<String, Any?>()

    override fun getString(key: String, defaultValue: String): Preference<String> =
      InMemoryPreference(key, defaultValue, storage)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
      InMemoryPreference(key, defaultValue, storage)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
      InMemoryPreference(key, defaultValue, storage)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
      InMemoryPreference(key, defaultValue, storage)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
      InMemoryPreference(key, defaultValue, storage)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
      InMemoryPreference(key, defaultValue, storage)

    override fun <T> getObject(
      key: String,
      defaultValue: T,
      serializer: (T) -> String,
      deserializer: (String) -> T,
    ): Preference<T> = InMemoryPreference(key, defaultValue, storage)

    override fun getAll(): Map<String, *> = storage
  }

  private fun repository() = PinnedItemRepositoryImpl(InMemoryPreferenceStore())

  @Test
  fun `starts empty`() = runTest {
    assertEquals(emptyList<String>(), repository().getPinnedPaths(PinScope.TREE))
  }

  @Test
  fun `pin preserves insertion order and ignores duplicates`() = runTest {
    val repo = repository()
    repo.pin(PinScope.TREE, "/storage/a")
    repo.pin(PinScope.TREE, "/storage/b")
    repo.pin(PinScope.TREE, "/storage/a")

    assertEquals(listOf("/storage/a", "/storage/b"), repo.getPinnedPaths(PinScope.TREE))
    assertTrue(repo.isPinned(PinScope.TREE, "/storage/a"))
    assertFalse(repo.isPinned(PinScope.TREE, "/storage/c"))
  }

  @Test
  fun `pinAll and unpinAll work on multiple paths`() = runTest {
    val repo = repository()
    repo.pinAll(PinScope.TREE, listOf("/a", "/b", "/c"))
    repo.unpinAll(PinScope.TREE, listOf("/b", "/missing"))

    assertEquals(listOf("/a", "/c"), repo.getPinnedPaths(PinScope.TREE))
  }

  @Test
  fun `renamePin moves the pin`() = runTest {
    val repo = repository()
    repo.pin(PinScope.TREE, "/storage/Folder")
    repo.renamePin("/storage/Folder", "/storage/Renamed")

    assertEquals(listOf("/storage/Renamed"), repo.getPinnedPaths(PinScope.TREE))
  }

  @Test
  fun `removePinsUnder removes the folder and its descendants only`() = runTest {
    val repo = repository()
    repo.pinAll(PinScope.TREE, listOf("/storage/Folder", "/storage/Folder/sub/video.mp4", "/storage/Folder2"))

    repo.removePinsUnder("/storage/Folder")

    assertEquals(listOf("/storage/Folder2"), repo.getPinnedPaths(PinScope.TREE))
  }

  @Test
  fun `pinnedPaths flow emits updates`() = runTest {
    val repo = repository()
    repo.pin(PinScope.TREE, "/x")
    assertEquals(listOf("/x"), repo.pinnedPaths(PinScope.TREE).first { it.isNotEmpty() })
  }

  @Test
  fun `encode and decode round trip`() {
    val paths = listOf("/a b/c.mp4", "/d")
    assertEquals(paths, PinnedItemRepositoryImpl.decode(PinnedItemRepositoryImpl.encode(paths)))
    assertEquals(emptyList<String>(), PinnedItemRepositoryImpl.decode(""))
  }

  @Test
  fun `tree and album pins are isolated by default`() = runTest {
    val repo = repository()
    assertTrue(repo.getIsIsolated())

    repo.pin(PinScope.TREE, "/tree/folder")
    repo.pin(PinScope.ALBUM, "/album/folder")

    assertEquals(listOf("/tree/folder"), repo.getPinnedPaths(PinScope.TREE))
    assertEquals(listOf("/album/folder"), repo.getPinnedPaths(PinScope.ALBUM))
  }

  @Test
  fun `disabling isolation merges both pin sets into the shared list`() = runTest {
    val repo = repository()
    repo.pin(PinScope.TREE, "/tree/folder")
    repo.pin(PinScope.ALBUM, "/album/folder")

    repo.setIsIsolated(false)

    assertFalse(repo.getIsIsolated())
    // Both views now see the merged set.
    assertEquals(listOf("/tree/folder", "/album/folder"), repo.getPinnedPaths(PinScope.TREE))
    assertEquals(listOf("/tree/folder", "/album/folder"), repo.getPinnedPaths(PinScope.ALBUM))
  }

  @Test
  fun `enabling isolation copies the shared list into both views`() = runTest {
    val repo = repository()
    repo.setIsIsolated(false)
    repo.pin(PinScope.TREE, "/shared/folder")

    repo.setIsIsolated(true)

    assertTrue(repo.getIsIsolated())
    assertEquals(listOf("/shared/folder"), repo.getPinnedPaths(PinScope.TREE))
    assertEquals(listOf("/shared/folder"), repo.getPinnedPaths(PinScope.ALBUM))
  }

  @Test
  fun `unpinning in one view leaves the other untouched`() = runTest {
    val repo = repository()
    repo.pinAll(PinScope.TREE, listOf("/a", "/b"))
    repo.pinAll(PinScope.ALBUM, listOf("/a"))

    repo.unpinAll(PinScope.TREE, listOf("/a"))

    assertEquals(listOf("/b"), repo.getPinnedPaths(PinScope.TREE))
    assertEquals(listOf("/a"), repo.getPinnedPaths(PinScope.ALBUM))
  }

  // ---- MEDIA scope: always shared, never affected by the isolation toggle ----

  @Test
  fun `media pins are independent of tree and album pins`() = runTest {
    val repo = repository()

    repo.pin(PinScope.TREE, "/tree/folder")
    repo.pin(PinScope.ALBUM, "/album/folder")
    repo.pin(PinScope.MEDIA, "/videos/movie.mp4")

    assertEquals(listOf("/tree/folder"), repo.getPinnedPaths(PinScope.TREE))
    assertEquals(listOf("/album/folder"), repo.getPinnedPaths(PinScope.ALBUM))
    assertEquals(listOf("/videos/movie.mp4"), repo.getPinnedPaths(PinScope.MEDIA))
  }

  @Test
  fun `media pins survive disabling isolation and are not merged into the shared folder list`() = runTest {
    val repo = repository()
    repo.pin(PinScope.TREE, "/tree/folder")
    repo.pin(PinScope.MEDIA, "/videos/movie.mp4")

    repo.setIsIsolated(false)

    assertEquals(listOf("/tree/folder"), repo.getPinnedPaths(PinScope.TREE))
    assertEquals(listOf("/tree/folder"), repo.getPinnedPaths(PinScope.ALBUM))
    assertEquals(listOf("/videos/movie.mp4"), repo.getPinnedPaths(PinScope.MEDIA))
  }

  @Test
  fun `media pins survive re-enabling isolation`() = runTest {
    val repo = repository()
    repo.setIsIsolated(false)
    repo.pin(PinScope.TREE, "/tree/folder")
    repo.pin(PinScope.MEDIA, "/videos/movie.mp4")

    repo.setIsIsolated(true)

    assertEquals(listOf("/tree/folder"), repo.getPinnedPaths(PinScope.TREE))
    assertEquals(listOf("/tree/folder"), repo.getPinnedPaths(PinScope.ALBUM))
    assertEquals(listOf("/videos/movie.mp4"), repo.getPinnedPaths(PinScope.MEDIA))
  }

  @Test
  fun `toggling isolation never leaks media pins into the album folder list`() = runTest {
    val repo = repository()
    repo.pin(PinScope.MEDIA, "/videos/a.mp4")
    repo.pin(PinScope.MEDIA, "/music/song.flac")

    repo.setIsIsolated(false)
    val shared = repo.getPinnedPaths(PinScope.ALBUM)
    repo.setIsIsolated(true)
    val isolated = repo.getPinnedPaths(PinScope.ALBUM)

    assertFalse("/videos/a.mp4" in shared)
    assertFalse("/music/song.flac" in shared)
    assertFalse("/videos/a.mp4" in isolated)
    assertFalse("/music/song.flac" in isolated)
    assertEquals(listOf("/videos/a.mp4", "/music/song.flac"), repo.getPinnedPaths(PinScope.MEDIA))
  }

  @Test
  fun `media pin order is preserved regardless of isolation`() = runTest {
    val repo = repository()
    repo.pin(PinScope.MEDIA, "/videos/b.mp4")
    repo.pin(PinScope.MEDIA, "/videos/a.mp4")
    repo.pin(PinScope.MEDIA, "/videos/b.mp4")

    repo.setIsIsolated(false)
    repo.setIsIsolated(true)

    assertEquals(listOf("/videos/b.mp4", "/videos/a.mp4"), repo.getPinnedPaths(PinScope.MEDIA))
  }

  @Test
  fun `media pin flow emits updates while isolated and shared`() = runTest {
    val repo = repository()
    repo.pin(PinScope.MEDIA, "/videos/a.mp4")
    assertEquals(listOf("/videos/a.mp4"), repo.pinnedPaths(PinScope.MEDIA).first { it.isNotEmpty() })

    repo.setIsIsolated(false)
    repo.pin(PinScope.MEDIA, "/videos/b.mp4")
    assertEquals(
      listOf("/videos/a.mp4", "/videos/b.mp4"),
      repo.pinnedPaths(PinScope.MEDIA).first { it.size == 2 },
    )
  }

  // ---- maintenance operations (rename/delete/prune) cover MEDIA ----

  @Test
  fun `renamePin follows a renamed media file`() = runTest {
    val repo = repository()
    repo.pin(PinScope.MEDIA, "/videos/old.mp4")

    repo.renamePin("/videos/old.mp4", "/videos/new.mp4")

    assertEquals(listOf("/videos/new.mp4"), repo.getPinnedPaths(PinScope.MEDIA))
  }

  @Test
  fun `renaming an unpinned folder still moves the pins beneath it`() = runTest {
    val repo = repository()
    // The folder itself is not pinned, only a file inside it.
    repo.pin(PinScope.MEDIA, "/videos/Show/ep1.mp4")

    repo.renamePin("/videos/Show", "/videos/Renamed")

    assertEquals(listOf("/videos/Renamed/ep1.mp4"), repo.getPinnedPaths(PinScope.MEDIA))
  }

  @Test
  fun `renaming a pinned folder moves media pins beneath it`() = runTest {
    val repo = repository()
    repo.pin(PinScope.TREE, "/videos/Show")
    repo.pin(PinScope.MEDIA, "/videos/Show/ep1.mp4")

    repo.renamePin("/videos/Show", "/videos/Renamed")

    assertEquals(listOf("/videos/Renamed"), repo.getPinnedPaths(PinScope.TREE))
    assertEquals(listOf("/videos/Renamed/ep1.mp4"), repo.getPinnedPaths(PinScope.MEDIA))
  }

  @Test
  fun `removePinsUnder drops media pins inside a deleted folder`() = runTest {
    val repo = repository()
    repo.pinAll(
      PinScope.MEDIA,
      listOf("/videos/Show", "/videos/Show/ep1.mp4", "/videos/Show2/ep1.mp4"),
    )

    repo.removePinsUnder("/videos/Show")

    // Prefix match must not eat the sibling "Show2" folder.
    assertEquals(listOf("/videos/Show2/ep1.mp4"), repo.getPinnedPaths(PinScope.MEDIA))
  }

  @Test
  fun `pruneMissingPins drops missing media pins but keeps readable ones`() = runTest {
    val repo = repository()
    val keep = createTempFile()
    val drop = createTempFile()
    try {
      repo.pinAll(PinScope.MEDIA, listOf(keep, drop, "content://media/external/video/42"))

      Files.delete(Paths.get(drop))
      repo.pruneMissingPins()

      assertEquals(
        listOf(keep, "content://media/external/video/42"),
        repo.getPinnedPaths(PinScope.MEDIA),
      )
    } finally {
      Files.deleteIfExists(Paths.get(keep))
      Files.deleteIfExists(Paths.get(drop))
    }
  }

  @Test
  fun `pruneMissingPins also prunes after isolation is toggled`() = runTest {
    val repo = repository()
    val missing = createTempFile()
    repo.pin(PinScope.MEDIA, missing)
    repo.setIsIsolated(false)

    Files.delete(Paths.get(missing))
    repo.pruneMissingPins()

    assertEquals(emptyList<String>(), repo.getPinnedPaths(PinScope.MEDIA))
  }

  @Test
  fun `clearAll removes media pins in every isolation mode`() = runTest {
    val repo = repository()
    repo.pin(PinScope.TREE, "/tree/folder")
    repo.pin(PinScope.MEDIA, "/videos/a.mp4")

    repo.clearAll()
    repo.setIsIsolated(false)
    repo.pin(PinScope.MEDIA, "/videos/b.mp4")
    repo.clearAll()

    assertEquals(emptyList<String>(), repo.getPinnedPaths(PinScope.MEDIA))
    assertEquals(emptyList<String>(), repo.getPinnedPaths(PinScope.TREE))
    assertEquals(emptyList<String>(), repo.getPinnedPaths(PinScope.ALBUM))
  }

  private fun createTempFile(): String =
    Files.createTempFile("pin-test", ".mp4").toFile().absolutePath
}

class PinnedSortUtilsTest {

  private fun folder(path: String) = FileSystemItem.Folder(
    name = path.substringAfterLast('/'),
    path = path,
    lastModified = 0L,
    videoCount = 1,
    totalSize = 0L,
    totalDuration = 0L,
    videoSize = 0L,
    audioSize = 0L,
    videoDuration = 0L,
    audioDuration = 0L,
  )

  @Test
  fun `pinned items move to the front in pin order`() {
    val items = listOf(folder("/a"), folder("/b"), folder("/c"), folder("/d"))
    val result = PinnedSortUtils.sortPinnedFirst(items, listOf("/d", "/b")) { it.path }
    assertEquals(listOf("/d", "/b", "/a", "/c"), result.map { it.path })
  }

  @Test
  fun `unpinned order is preserved`() {
    val items = listOf(folder("/c"), folder("/a"), folder("/b"))
    val result = PinnedSortUtils.sortPinnedFirst(items, emptyList()) { it.path }
    assertEquals(items, result)
  }

  @Test
  fun `missing pins are ignored`() {
    val items = listOf(folder("/a"), folder("/b"))
    val result = PinnedSortUtils.sortPinnedFirst(items, listOf("/other", "/b")) { it.path }
    assertEquals(listOf("/b", "/a"), result.map { it.path })
  }

  @Test
  fun `pinned entries are reordered without duplicating them`() {
    // The tree view no longer hoists pins into a separate section: pinned items
    // must stay in the list, just moved to the front in pin order.
    val items = listOf(folder("/c"), folder("/b"), folder("/a"))
    val pinned = listOf("/b", "/a")

    val result = PinnedSortUtils.sortPinnedFirst(items, pinned) { it.path }

    assertEquals(listOf("/b", "/a", "/c"), result.map { it.path })
    assertEquals(items.size, result.size)
  }
}
