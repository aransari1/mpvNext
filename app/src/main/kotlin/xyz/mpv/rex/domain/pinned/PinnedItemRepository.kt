package xyz.mpv.rex.domain.pinned

import kotlinx.coroutines.flow.Flow
import xyz.mpv.rex.preferences.preference.Preference

/**
 * Persists the user's pinned folders and files (issue #466).
 *
 * Pinned entries are stored as absolute filesystem paths, which is the identity
 * used by both the tree view (`FileSystemItem`) and the album view (`VideoFolder`).
 * The pin order is preserved so pinned items keep their position in the list
 * regardless of the active sort type/order.
 *
 * Every operation is scoped by [PinScope]: the tree and album views can either
 * share one pin set or keep isolated ones (see [isolatedPreference]), while media
 * pins ([PinScope.MEDIA]) are always shared across all views.
 */
interface PinnedItemRepository {
  /** Pinned paths for [scope], in insertion order (oldest first). */
  fun pinnedPaths(scope: PinScope): Flow<List<String>>

  /** Current snapshot of pinned paths for [scope], in insertion order. */
  fun getPinnedPaths(scope: PinScope): List<String>

  suspend fun isPinned(scope: PinScope, path: String): Boolean

  /** Pins [path] if not already pinned; already pinned paths keep their position. */
  suspend fun pin(scope: PinScope, path: String)

  /** Pins every path in [paths] that is not pinned yet. */
  suspend fun pinAll(scope: PinScope, paths: Collection<String>)

  suspend fun unpin(scope: PinScope, path: String)

  suspend fun unpinAll(scope: PinScope, paths: Collection<String>)

  /** Removes every pin located at or below [path] (used when a folder is deleted). */
  suspend fun removePinsUnder(path: String)

  /** Moves a pin from [oldPath] to [newPath] after a rename (no-op if [oldPath] isn't pinned). */
  suspend fun renamePin(oldPath: String, newPath: String)

  /** Drops pins for files that no longer exist. Safe to call off the main thread. */
  suspend fun pruneMissingPins()

  suspend fun clearAll()

  /** Whether tree and album views keep separate pin sets. Backed by a settings preference. */
  val isolatedPreference: Preference<Boolean>

  fun getIsIsolated(): Boolean

  /**
   * Switches between isolated and shared pin sets.
   *
   * When isolating, the shared list is copied into both scopes so nothing the user
   * pinned disappears. When un-sharing, the two lists are merged (album pins follow
   * tree pins) so no pin is lost.
   */
  suspend fun setIsIsolated(isolated: Boolean)
}
