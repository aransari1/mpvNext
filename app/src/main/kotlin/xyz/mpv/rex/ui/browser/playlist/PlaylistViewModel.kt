package xyz.mpv.rex.ui.browser.playlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import xyz.mpv.rex.R
import xyz.mpv.rex.database.entities.PlaylistEntity
import xyz.mpv.rex.database.repository.MediaPlayCountRepository
import xyz.mpv.rex.database.repository.PlaylistRepository
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.ui.browser.base.BaseBrowserViewModel
import xyz.mpv.rex.utils.storage.VideoScanUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

data class PlaylistWithCount(
  val playlist: PlaylistEntity,
  val itemCount: Int,
  val firstItemPath: String? = null,
  val isAutoPlaylist: Boolean = false,
)

class PlaylistViewModel(
  application: Application,
) : BaseBrowserViewModel<PlaylistWithCount>(application),
  KoinComponent {
  private val repository: PlaylistRepository by inject()
  private val mediaPlayCountRepository: MediaPlayCountRepository by inject()

  val playlistsWithCount: StateFlow<List<PlaylistWithCount>> = items

  // Track if initial load has completed to prevent empty state flicker
  val hasCompletedInitialLoad: StateFlow<Boolean> = hasLoaded

  companion object {
    private const val TAG = "PlaylistViewModel"
    const val ID_RECENTLY_ADDED = -1
    const val ID_MOST_PLAYED = -2

    fun factory(application: Application) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PlaylistViewModel(application) as T
      }
  }

  init {
    loadData()

    // Observe all playlists and update items
    viewModelScope.launch(Dispatchers.IO) {
      repository.observeAllPlaylists().collectLatest {
        loadData()
      }
    }

    // Observe preference changes for auto playlists
    viewModelScope.launch(Dispatchers.IO) {
      merge(
        browserPreferences.showRecentlyAddedPlaylist.changes(),
        browserPreferences.showMostPlayedPlaylist.changes(),
      ).collectLatest {
        loadData()
      }
    }

    // Observe play count changes to keep Most Played updated
    viewModelScope.launch(Dispatchers.IO) {
      mediaPlayCountRepository.observeMostPlayedEntities().collectLatest {
        if (browserPreferences.showMostPlayedPlaylist.get()) {
          loadData()
        }
      }
    }
  }

  override fun loadData() {
    viewModelScope.launch(Dispatchers.IO) {
      _isLoading.value = true
      try {
        val context = getApplication<Application>()
        val autoPlaylists = mutableListOf<PlaylistWithCount>()

        if (browserPreferences.showRecentlyAddedPlaylist.get()) {
          val recentVideos = VideoScanUtils.getRecentlyAddedVideos(context, limit = 50)
          val recentlyAddedPlaylist = PlaylistEntity(
            id = ID_RECENTLY_ADDED,
            name = context.getString(R.string.playlist_recently_added),
            createdAt = 0L,
            updatedAt = System.currentTimeMillis(),
          )
          autoPlaylists.add(
            PlaylistWithCount(
              playlist = recentlyAddedPlaylist,
              itemCount = recentVideos.size,
              firstItemPath = recentVideos.firstOrNull()?.path,
              isAutoPlaylist = true,
            )
          )
        }

        if (browserPreferences.showMostPlayedPlaylist.get()) {
          val mostPlayedVideos = mediaPlayCountRepository.getMostPlayedVideos(context, limit = 50)
          val mostPlayedPlaylist = PlaylistEntity(
            id = ID_MOST_PLAYED,
            name = context.getString(R.string.playlist_most_played),
            createdAt = 0L,
            updatedAt = System.currentTimeMillis(),
          )
          autoPlaylists.add(
            PlaylistWithCount(
              playlist = mostPlayedPlaylist,
              itemCount = mostPlayedVideos.size,
              firstItemPath = mostPlayedVideos.firstOrNull()?.path,
              isAutoPlaylist = true,
            )
          )
        }

        val playlists = repository.getAllPlaylists()
        val playlistsWithCounts = playlists.map { playlist ->
          val count = repository.getPlaylistItemCount(playlist.id)
          val thumbnailPath = repository.getEffectiveThumbnailPath(playlist.id)
          PlaylistWithCount(playlist, count, thumbnailPath)
        }.sortedByDescending { it.playlist.updatedAt }

        _items.value = autoPlaylists + playlistsWithCounts
        _hasLoaded.value = true
      } finally {
        _isLoading.value = false
      }
    }
  }

  override fun refresh(silent: Boolean) {
    loadData()
  }

  suspend fun createPlaylist(name: String): Long {
    return repository.createPlaylist(name)
  }

  suspend fun deletePlaylists(playlistsToDelete: List<PlaylistWithCount>) {
    playlistsToDelete.filter { !it.isAutoPlaylist && it.playlist.id >= 0 }.forEach {
      repository.deletePlaylist(it.playlist)
    }
    loadData()
  }
}
