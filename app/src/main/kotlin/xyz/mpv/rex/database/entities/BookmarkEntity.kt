package xyz.mpv.rex.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-created bookmark (pin) at a specific position inside a media file.
 *
 * These are intentionally kept apart from mpv's embedded chapter list, which is read-only
 * and owned by the demuxer. Bookmarks survive playback restarts and are keyed by the
 * same stable media identifier used for playback state (see PlayerPlaylistLoader#getMediaIdentifier).
 */
@Entity(
  tableName = "media_bookmarks",
  indices = [Index(value = ["mediaKey", "positionMs"], unique = true)],
)
data class BookmarkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  /** Stable media identity; falls back to media title when no identifier is available. */
  val mediaKey: String,
  /** Bookmark position in milliseconds from the start of the media. */
  val positionMs: Long,
  /** User-editable label. */
  val title: String,
  val createdAt: Long,
)
