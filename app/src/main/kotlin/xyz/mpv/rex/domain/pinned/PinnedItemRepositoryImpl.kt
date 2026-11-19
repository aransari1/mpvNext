package xyz.mpv.rex.domain.pinned

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import xyz.mpv.rex.preferences.preference.Preference
import xyz.mpv.rex.preferences.preference.PreferenceStore
import java.io.File

/**
 * [PinnedItemRepository] backed by [PreferenceStore].
 *
 * Only the pin *order* is persisted (a newline-separated [String] preference).
 * A `StringSet` would lose ordering, which we need so pinned items don't jump
 * around when the user changes the sort type.
 *
 * Storage keys:
 * - [KEY_SHARED_ORDER] — used when isolation is disabled (both views share it).
 * - [KEY_TREE_ORDER] / [KEY_ALBUM_ORDER] — used when isolation is enabled.
 *
 * Writes are serialized behind a [Mutex] so concurrent read-modify-write cycles
 * (e.g. "Pin" tapped twice quickly) can't drop entries.
 */
class PinnedItemRepositoryImpl(
  preferenceStore: PreferenceStore,
) : PinnedItemRepository {

  private val sharedOrder: Preference<String> = preferenceStore.getString(KEY_SHARED_ORDER, "")
  private val treeOrder: Preference<String> = preferenceStore.getString(KEY_TREE_ORDER, "")
  private val albumOrder: Preference<String> = preferenceStore.getString(KEY_ALBUM_ORDER, "")
  private val mediaOrder: Preference<String> = preferenceStore.getString(KEY_MEDIA_ORDER, "")
  private val isolated: Preference<Boolean> = preferenceStore.getBoolean(KEY_ISOLATED, true)

  private val writeMutex = Mutex()

  override val isolatedPreference: Preference<Boolean> = isolated

  override fun getIsIsolated(): Boolean = isolated.get()

  override fun pinnedPaths(scope: PinScope): Flow<List<String>> =
    combine(
      isolated.changes(),
      sharedOrder.changes(),
      treeOrder.changes(),
      albumOrder.changes(),
      mediaOrder.changes(),
    ) { _, shared, tree, album, media ->
      decode(readRaw(scope, shared, tree, album, media))
    }

  override fun getPinnedPaths(scope: PinScope): List<String> =
    decode(readRaw(scope, sharedOrder.get(), treeOrder.get(), albumOrder.get(), mediaOrder.get()))

  override suspend fun isPinned(scope: PinScope, path: String): Boolean =
    path.isNotBlank() && path in getPinnedPaths(scope)

  override suspend fun pin(scope: PinScope, path: String) = pinAll(scope, listOf(path))

  override suspend fun pinAll(scope: PinScope, paths: Collection<String>) = mutate(scope) { current ->
    val additions = paths.filter { it.isNotBlank() && it !in current }
    if (additions.isEmpty()) current else current + additions
  }

  override suspend fun unpin(scope: PinScope, path: String) = unpinAll(scope, listOf(path))

  override suspend fun unpinAll(scope: PinScope, paths: Collection<String>) {
    val removals = paths.toSet()
    if (removals.isEmpty()) return
    mutate(scope) { current -> current.filterNot { it in removals } }
  }

  override suspend fun removePinsUnder(path: String) {
    if (path.isBlank()) return
    val prefix = path.trimEnd('/') + "/"
    val predicate = { pinned: String -> !(pinned == path || pinned.startsWith(prefix)) }
    mutateAllScopes { current -> current.filter(predicate) }
  }

  override suspend fun renamePin(oldPath: String, newPath: String) {
    if (oldPath.isBlank() || newPath.isBlank() || oldPath == newPath) return
    val oldPrefix = oldPath.trimEnd('/') + "/"
    val newPrefix = newPath.trimEnd('/') + "/"
    // Rewrite the entries under the folder in whatever list holds them, rather
    // than only acting when the folder itself is pinned: renaming an unpinned
    // folder that contains pinned media must still move those media pins.
    mutateAllScopes { current ->
      current.map { pinned ->
        when {
          pinned == oldPath -> newPath
          pinned.startsWith(oldPrefix) -> newPrefix + pinned.removePrefix(oldPrefix)
          else -> pinned
        }
      }.distinct()
    }
  }

  override suspend fun pruneMissingPins() {
    // Skip when the app can't read the filesystem right now (permission revoked,
    // storage unmounted): otherwise a transient failure would wipe every pin.
    if (!File("/").canRead()) return
    mutateAllScopes { current -> current.filter(::existsOrUnknown) }
  }

  override suspend fun clearAll() {
    mutateAllScopes { emptyList() }
  }

  override suspend fun setIsIsolated(isolated: Boolean) = withContext(Dispatchers.IO) {
    writeMutex.withLock {
      if (isolated == this@PinnedItemRepositoryImpl.isolated.get()) return@withLock

      if (isolated) {
        // Split the shared list into both scopes so nothing disappears.
        val shared = decode(sharedOrder.get())
        treeOrder.set(encode(shared))
        albumOrder.set(encode(shared))
      } else {
        // Merge both scoped lists into the shared one so nothing is lost.
        val merged = (decode(treeOrder.get()) + decode(albumOrder.get())).distinct()
        sharedOrder.set(encode(merged))
      }
      this@PinnedItemRepositoryImpl.isolated.set(isolated)
    }
  }

  /** Reads the raw (encoded) value backing [scope] for the given isolation state. */
  private fun readRaw(
    scope: PinScope,
    shared: String,
    tree: String,
    album: String,
    media: String,
  ): String =
    if (scope == PinScope.MEDIA) {
      // Media pins are always shared across views, independent of isolation.
      media
    } else if (!isolated.get()) {
      shared
    } else {
      when (scope) {
        PinScope.TREE -> tree
        PinScope.ALBUM -> album
        PinScope.MEDIA -> media
      }
    }

  private fun preferenceFor(scope: PinScope): Preference<String> =
    if (scope == PinScope.MEDIA) {
      mediaOrder
    } else if (!isolated.get()) {
      sharedOrder
    } else {
      when (scope) {
        PinScope.TREE -> treeOrder
        PinScope.ALBUM -> albumOrder
        PinScope.MEDIA -> mediaOrder
      }
    }

  private suspend fun mutate(
    scope: PinScope,
    transform: (List<String>) -> List<String>,
  ) = withContext(Dispatchers.IO) {
    writeMutex.withLock {
      val current = getPinnedPaths(scope)
      val updated = transform(current)
      if (updated != current) {
        preferenceFor(scope).set(encode(updated))
      }
    }
  }

  /**
   * Applies [transform] to every storage list that can currently hold pins.
   * Used by maintenance operations (rename/delete/prune) which must stay correct
   * regardless of the isolation setting.
   */
  private suspend fun mutateAllScopes(transform: (List<String>) -> List<String>) =
    withContext(Dispatchers.IO) {
      writeMutex.withLock {
        val scopes = buildList {
          add(PinScope.MEDIA)
          if (isolated.get()) {
            add(PinScope.TREE)
            add(PinScope.ALBUM)
          } else {
            add(PinScope.TREE)
          }
        }
        scopes.forEach { scope ->
          val current = getPinnedPaths(scope)
          val updated = transform(current)
          if (updated != current) {
            preferenceFor(scope).set(encode(updated))
          }
        }
      }
    }

  private fun existsOrUnknown(path: String): Boolean {
    if (path.startsWith("content://") || path.startsWith("http://") || path.startsWith("https://")) {
      // Non-filesystem entries can't be verified cheaply — trust them.
      return true
    }
    return try {
      File(path).exists()
    } catch (_: SecurityException) {
      true
    }
  }

  companion object {
    const val KEY_SHARED_ORDER = "pinned_paths_order"
    const val KEY_TREE_ORDER = "pinned_paths_order_tree"
    const val KEY_ALBUM_ORDER = "pinned_paths_order_album"
    const val KEY_MEDIA_ORDER = "pinned_paths_order_media"
    const val KEY_ISOLATED = "pinned_paths_isolated_by_view"

    private const val SEPARATOR = "\n"

    fun encode(paths: List<String>): String =
      paths.filter { it.isNotBlank() }.joinToString(SEPARATOR)

    fun decode(raw: String): List<String> =
      if (raw.isBlank()) {
        emptyList()
      } else {
        raw.split(SEPARATOR).filter { it.isNotBlank() }.distinct()
      }
  }
}
