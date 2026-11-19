package xyz.mpv.rex.database.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import xyz.mpv.rex.database.dao.MediaPlayCountDao
import xyz.mpv.rex.database.entities.MediaPlayCountEntity
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.utils.storage.VideoScanUtils

class MediaPlayCountRepository(
  private val mediaPlayCountDao: MediaPlayCountDao,
) {
  suspend fun incrementPlayCount(filePath: String, timestamp: Long = System.currentTimeMillis()) {
    if (filePath.isBlank()) return
    mediaPlayCountDao.incrementPlayCount(filePath, timestamp)
  }

  suspend fun getMostPlayedEntities(limit: Int = 50): List<MediaPlayCountEntity> =
    mediaPlayCountDao.getMostPlayed(limit)

  fun observeMostPlayedEntities(limit: Int = 50): Flow<List<MediaPlayCountEntity>> =
    mediaPlayCountDao.observeMostPlayed(limit)

  suspend fun getMostPlayedVideos(context: Context, limit: Int = 50): List<Video> {
    val entities = getMostPlayedEntities(limit)
    return entities.mapNotNull { entity ->
      VideoScanUtils.getVideoByPath(context, entity.filePath)
    }
  }

  suspend fun getPlayCount(filePath: String): Int =
    mediaPlayCountDao.getPlayCount(filePath) ?: 0

  suspend fun updateFilePath(oldPath: String, newPath: String) =
    mediaPlayCountDao.updateFilePath(oldPath, newPath)

  suspend fun deleteByFilePath(filePath: String) =
    mediaPlayCountDao.deleteByFilePath(filePath)

  suspend fun clearAll() =
    mediaPlayCountDao.clearAll()
}
