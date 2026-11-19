package xyz.mpv.rex.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import xyz.mpv.rex.database.entities.BookmarkEntity

@Dao
interface BookmarkDao {
  @Upsert
  suspend fun upsert(bookmark: BookmarkEntity): Long

  @Query("SELECT * FROM media_bookmarks WHERE mediaKey = :mediaKey ORDER BY positionMs ASC")
  fun observeByMediaKey(mediaKey: String): Flow<List<BookmarkEntity>>

  @Query("SELECT * FROM media_bookmarks WHERE mediaKey = :mediaKey ORDER BY positionMs ASC")
  suspend fun getByMediaKey(mediaKey: String): List<BookmarkEntity>

  @Query("UPDATE media_bookmarks SET title = :title WHERE id = :id")
  suspend fun rename(id: Long, title: String)

  @Query("DELETE FROM media_bookmarks WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM media_bookmarks WHERE mediaKey = :mediaKey")
  suspend fun deleteByMediaKey(mediaKey: String)

  @Query("UPDATE media_bookmarks SET mediaKey = :newKey WHERE mediaKey = :oldKey")
  suspend fun updateMediaKey(oldKey: String, newKey: String)

  @Query("DELETE FROM media_bookmarks")
  suspend fun clearAll()
}
