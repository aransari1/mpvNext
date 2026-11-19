package xyz.mpv.rex.domain.bookmark.repository

import kotlinx.coroutines.flow.Flow
import xyz.mpv.rex.database.entities.BookmarkEntity

interface BookmarkRepository {
  fun observeByMediaKey(mediaKey: String): Flow<List<BookmarkEntity>>

  suspend fun getByMediaKey(mediaKey: String): List<BookmarkEntity>

  /** Inserts a bookmark. Returns the new row id, or 0 when a duplicate position was ignored. */
  suspend fun add(
    mediaKey: String,
    positionMs: Long,
    title: String,
  ): Long

  suspend fun rename(
    id: Long,
    title: String,
  )

  suspend fun delete(id: Long)

  suspend fun updateMediaKey(
    oldKey: String,
    newKey: String,
  )
}
