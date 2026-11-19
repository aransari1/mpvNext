package xyz.mpv.rex.utils.sort

/**
 * Helpers for pinning folders/files to the top of browser lists (issue #466).
 *
 * Pinning works the same way in the tree view (`FileSystemItem`) and the album
 * view (`VideoFolder`): the pinned items are pulled out of the current list,
 * ordered by their pin order (not by the active sort), and rendered above the
 * regular sections.
 */
object PinnedSortUtils {

  /**
   * Moves items whose [pathOf] is in [pinnedPaths] to the front, ordered by their
   * position in [pinnedPaths] (oldest pin first). The relative order of all other
   * items is left untouched.
   *
   * Applied *after* sorting so that changing the sort type/order never shuffles
   * the pinned block.
   */
  fun <T> sortPinnedFirst(
    items: List<T>,
    pinnedPaths: List<String>,
    pathOf: (T) -> String,
  ): List<T> {
    if (items.isEmpty() || pinnedPaths.isEmpty()) return items

    val byPath = HashMap<String, T>(items.size)
    items.forEach { byPath.putIfAbsent(pathOf(it), it) }

    val pinned = ArrayList<T>(pinnedPaths.size)
    val pinnedSet = HashSet<String>(pinnedPaths.size)
    pinnedPaths.forEach { path ->
      val item = byPath[path] ?: return@forEach
      pinned.add(item)
      pinnedSet.add(path)
    }
    if (pinned.isEmpty()) return items

    val rest = ArrayList<T>(items.size - pinned.size)
    items.forEach { if (pathOf(it) !in pinnedSet) rest.add(it) }

    return pinned + rest
  }
}
