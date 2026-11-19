package xyz.mpv.rex.domain.pinned

/**
 * Which browser surface a pin belongs to (issue #466 follow-up).
 *
 * The tree (file manager) view and the album (folder) view can keep **separate**
 * pin sets, so pinning many folders inside one root in tree view doesn't flood
 * the album view with pins. When isolation is disabled both scopes resolve to the
 * same storage, i.e. the two views share one pin set (issue #466 behaviour).
 */
enum class PinScope {
  /** Folder pins in the tree / file-manager view (`FileSystemBrowserScreen`). */
  TREE,

  /** Folder pins in the album / folder view (`FolderListScreen`). */
  ALBUM,

  /**
   * Video/audio pins. Unlike [TREE]/[ALBUM] this scope is **always shared** across
   * tree, folder and media-library views and is never affected by the
   * "separate pins per view" preference.
   */
  MEDIA,
}
