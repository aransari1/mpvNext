package xyz.mpv.rex.ui.browser.shorts

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import xyz.mpv.rex.database.dao.ShortsMediaDao
import xyz.mpv.rex.database.entities.ShortsMediaEntity
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.utils.media.MediaFormatter
import xyz.mpv.rex.utils.media.ShortsDiscoveryOps
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ShortsViewModel(
    application: Application
) : AndroidViewModel(application), KoinComponent {

    private val shortsMediaDao: ShortsMediaDao by inject()
    private val browserPreferences: BrowserPreferences by inject()
    private val metadataCache: VideoMetadataCacheRepository by inject()
    private val thumbnailRepository: ThumbnailRepository by inject()

    private val _shorts = MutableStateFlow<List<Video>>(emptyList())
    val shorts: StateFlow<List<Video>> = _shorts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _totalShortsCount = MutableStateFlow(0)
    val totalShortsCount: StateFlow<Int> = _totalShortsCount.asStateFlow()

    val lovedPaths: StateFlow<Set<String>> = shortsMediaDao.observeAllShortsMedia()
        .map { list -> list.filter { it.isLoved }.map { it.path }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val blockedPaths: StateFlow<Set<String>> = shortsMediaDao.observeAllShortsMedia()
        .map { list -> list.filter { it.isBlocked }.map { it.path }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val autoSwipe: StateFlow<Boolean> = browserPreferences.autoSwipeShorts.changes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), browserPreferences.autoSwipeShorts.get())

    private val _currentSpeed = MutableStateFlow(1.0)
    val currentSpeed: StateFlow<Double> = _currentSpeed.asStateFlow()

    private var currentLoadedBlockedOnly: Boolean? = null
    private var loadJob: Job? = null

    fun loadShorts(initialVideoPath: String? = null, blockedOnly: Boolean = false, forceRefresh: Boolean = false) {
        if (!forceRefresh && _shorts.value.isNotEmpty() && currentLoadedBlockedOnly == blockedOnly && initialVideoPath == null) {
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            currentLoadedBlockedOnly = blockedOnly
            _shorts.value = emptyList()
            _totalShortsCount.value = 0
            pendingInitialVideo = null

            if (forceRefresh) {
                browserPreferences.lastWatchedShortPath.set("")
            }

            try {
                val received = mutableListOf<Video>()

                if (blockedOnly) {
                    val blockedShorts = loadBlockedShorts()
                    _totalShortsCount.value = blockedShorts.size
                    _shorts.value = blockedShorts
                    return@launch
                }

                ShortsDiscoveryOps.discoverShortsProgressively(
                    getApplication(),
                    shortsMediaDao,
                    metadataCache,
                    browserPreferences,
                ).collect { video ->
                    // The first item must be able to render before discovery finishes,
                    // otherwise the spinner from #450 comes straight back.
                    if (received.isEmpty()) {
                        _isLoading.value = false
                    }

                    // Honor an explicit start video: hold it back until the rest of the
                    // feed is known so it can be guaranteed to occupy page 0.
                    if (initialVideoPath != null && video.path == initialVideoPath) {
                        pendingInitialVideo = video
                        return@collect
                    }
                    received.add(video)
                    _shorts.value = withInitialFirst(received)
                    _totalShortsCount.value = received.size
                }

                // Discovery done: pin the requested video to the front, then interleave
                // loved items through the remaining feed.
                val initial = pendingInitialVideo
                pendingInitialVideo = null
                val ordered = if (initial != null) {
                    listOf(initial) + interleave(received.filter { it.path != initial.path })
                } else {
                    interleave(received)
                }
                _shorts.value = ordered
                _totalShortsCount.value = ordered.size
            } finally {
                _isLoading.value = false
            }
        }
    }

    private var pendingInitialVideo: Video? = null

    /**
     * Keeps the requested start video in front while more discoveries land, so the
     * pager does not jump once discovery finishes.
     */
    private fun withInitialFirst(received: List<Video>): List<Video> {
        val pending = pendingInitialVideo ?: return received
        return listOf(pending) + received
    }

    /** Applies the loved/other elastic ordering once the feed is complete. */
    private fun interleave(videos: List<Video>): List<Video> {
        val loved = videos.filter { lovedPaths.value.contains(it.path) }.shuffled().toMutableList()
        val others = videos.filter { !lovedPaths.value.contains(it.path) }.shuffled().toMutableList()
        return buildElasticFeed(loved, others)
    }

    /** Blocked-only feed; inherently a full scan, so it stays a single pass. */
    private suspend fun loadBlockedShorts(): List<Video> {
        val blockedInDb = shortsMediaDao.getAllShortsMedia().filter { it.isBlocked }
        val configuredFoldersSet = browserPreferences.shortsSourceFolders.get()
        val flatFolders = xyz.mpv.rex.utils.storage.CoreMediaScanner.getFlatMediaFolders(getApplication())
        val filteredFolders = if (configuredFoldersSet.isNotEmpty()) {
            flatFolders.filter { it.path in configuredFoldersSet }
        } else {
            flatFolders
        }
        val allVideos = filteredFolders.flatMap { folder ->
            xyz.mpv.rex.utils.storage.VideoScanUtils.getVideosInFolder(getApplication(), folder.path)
        }.filter { !it.isAudio }

        val blockedPathsSet = blockedInDb.map { it.path }.toSet()
        val blockedVideos = allVideos.filter { it.path in blockedPathsSet }

        val fileTriples = blockedVideos.mapNotNull { video ->
            val file = java.io.File(video.path)
            if (file.exists()) Triple(file, video.uri, video.displayName) else null
        }
        val metadataMap = metadataCache.getOrExtractMetadataBatch(fileTriples)
        return blockedVideos.map { video ->
            val metadata = metadataMap[video.path]
            if (metadata != null) {
                video.copy(
                    duration = metadata.durationMs,
                    durationFormatted = MediaFormatter.formatDuration(metadata.durationMs),
                    width = metadata.width,
                    height = metadata.height,
                    fps = metadata.fps,
                    resolution = MediaFormatter.formatResolutionWithFps(metadata.width, metadata.height, metadata.fps),
                    hasEmbeddedSubtitles = metadata.hasEmbeddedSubtitles,
                    subtitleCodec = metadata.subtitleCodec
                )
            } else {
                video
            }
        }
    }

    fun getInitialPageIndex(initialVideoPath: String? = null): Int {
        val currentShorts = _shorts.value
        if (currentShorts.isEmpty()) return 0
        if (initialVideoPath != null) {
            val idx = currentShorts.indexOfFirst { it.path == initialVideoPath }
            if (idx != -1) return idx
        }
        val lastWatchedPath = browserPreferences.lastWatchedShortPath.get()
        if (lastWatchedPath.isNotBlank()) {
            val idx = currentShorts.indexOfFirst { it.path == lastWatchedPath }
            if (idx != -1) return idx
        }
        return 0
    }

    private fun buildElasticFeed(
        loved: MutableList<Video>,
        others: MutableList<Video>,
        targetOthersPerLoved: Int = 2
    ): List<Video> {
        if (loved.isEmpty()) return others.shuffled()
        if (others.isEmpty()) return loved.shuffled()

        val interleaved = mutableListOf<Video>()
        
        // Calculate dynamic ratio based on pool sizes
        val effectiveRatio = if (others.size >= loved.size * targetOthersPerLoved) {
            targetOthersPerLoved
        } else {
            // Gracefully degrade to 1:1 if we don't have enough "others"
            maxOf(1, others.size / loved.size)
        }

        while (loved.isNotEmpty() || others.isNotEmpty()) {
            val othersToTake = minOf(effectiveRatio, others.size)
            repeat(othersToTake) { interleaved.add(others.removeAt(0)) }

            if (loved.isNotEmpty()) interleaved.add(loved.removeAt(0))

            // If others are exhausted, dump the rest of the loved videos
            if (others.isEmpty() && loved.isNotEmpty()) {
                interleaved.addAll(loved.shuffled())
                loved.clear()
            }
        }
        return interleaved
    }
    
    fun onShortSettled(video: Video) {
        browserPreferences.lastWatchedShortPath.set(video.path)
    }

    suspend fun getThumbnail(video: Video): Bitmap? {
        return thumbnailRepository.getThumbnail(video, 1080, 1920, forceFirstFrame = true)
    }

    fun syncPlaybackSpeed() {
        MPVLib.setPropertyDouble("speed", _currentSpeed.value)
    }

    fun setPlaybackSpeed(speed: Double) {
        MPVLib.setPropertyDouble("speed", speed)
        _currentSpeed.value = speed
    }

    fun cycleSpeed() {
        val speeds = listOf(1.0, 1.5, 2.0, 0.5)
        val current = _currentSpeed.value
        val nextIndex = (speeds.indexOf(current) + 1) % speeds.size
        setPlaybackSpeed(speeds[nextIndex])
    }

    fun toggleAutoSwipe() {
        val newState = !browserPreferences.autoSwipeShorts.get()
        browserPreferences.autoSwipeShorts.set(newState)
    }

    fun toggleLove(video: Video) {
        viewModelScope.launch {
            val current = shortsMediaDao.getShortsMediaByPath(video.path)
            val isLoved = current?.isLoved ?: false
            val newEntity = current?.copy(isLoved = !isLoved) 
                ?: ShortsMediaEntity(path = video.path, isLoved = true)
            shortsMediaDao.upsert(newEntity)
        }
    }

    fun toggleBlock(video: Video) {
        viewModelScope.launch {
            val current = shortsMediaDao.getShortsMediaByPath(video.path)
            val isBlocked = current?.isBlocked ?: false
            val newEntity = current?.copy(isBlocked = !isBlocked)
                ?: ShortsMediaEntity(path = video.path, isBlocked = true, addedDate = System.currentTimeMillis())
            shortsMediaDao.upsert(newEntity)
        }
    }

    fun deleteShort(video: Video) {
        viewModelScope.launch {
            val currentList = _shorts.value
            val updatedList = currentList.filter { it.path != video.path }
            _shorts.value = updatedList
            _totalShortsCount.value = updatedList.size
            shortsMediaDao.deleteByPath(video.path)

            withContext(NonCancellable + Dispatchers.IO) {
                delay(1000)
                val file = java.io.File(video.path)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ShortsViewModel(application) as T
        }
    }
}
