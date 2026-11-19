package xyz.mpv.rex.utils.media

import android.content.Context
import android.util.Log
import xyz.mpv.rex.database.dao.ShortsMediaDao
import xyz.mpv.rex.database.repository.VideoMetadataCacheRepository
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.utils.storage.CoreMediaScanner
import xyz.mpv.rex.utils.storage.VideoScanUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Operations for discovering and filtering vertical videos (Shorts).
 */
object ShortsDiscoveryOps {
    private const val TAG = "ShortsDiscoveryOps"

    /**
     * Discovers all vertical videos across all scanned media folders.
     *
     * Kept for callers that need the complete list at once; prefer
     * [discoverShortsProgressively] when the result feeds UI, so the first item can be
     * shown before the whole library has been probed.
     */
    suspend fun discoverShorts(
        context: Context,
        shortsMediaDao: ShortsMediaDao,
        metadataCache: VideoMetadataCacheRepository,
        browserPreferences: BrowserPreferences
    ): List<Video> {
        val result = mutableListOf<Video>()
        discoverShortsProgressively(context, shortsMediaDao, metadataCache, browserPreferences)
            .collect { result.add(it) }
        return result
    }

    /**
     * Discovers vertical videos, emitting each one as soon as its metadata is known.
     *
     * Previously the entire library was probed with MediaInfo before the first page
     * could render, so a cold cache meant a spinner for minutes. Results are now
     * produced in three passes, cheapest first:
     *
     * 1. rows whose MediaStore resolution already proves they are shorts,
     * 2. rows with a store duration but no usable resolution (cheap retriever probe),
     * 3. everything else, via MediaInfo.
     */
    fun discoverShortsProgressively(
        context: Context,
        shortsMediaDao: ShortsMediaDao,
        metadataCache: VideoMetadataCacheRepository,
        browserPreferences: BrowserPreferences,
    ): Flow<Video> = channelFlow {
        val configuredFoldersSet = browserPreferences.shortsSourceFolders.get()
        val flatFolders = CoreMediaScanner.getFlatMediaFolders(context)
        val filteredFolders = if (configuredFoldersSet.isNotEmpty()) {
            flatFolders.filter { it.path in configuredFoldersSet }
        } else {
            flatFolders
        }

        val allVideos = filteredFolders.flatMap { folder ->
            VideoScanUtils.getVideosInFolder(context, folder.path)
        }.filter { !it.isAudio }

        val elimination = SelectionRules(
            includeHorizontal = browserPreferences.includeShortHorizontalVideos.get(),
            maxHorizontalMs = browserPreferences.maxHorizontalVideoDurationMinutes.get() * 60 * 1000L,
            manuallyAdded = shortsMediaDao.getAllShortsMedia()
                .filter { it.isManuallyAdded }
                .map { it.path }
                .toSet(),
            blocked = shortsMediaDao.getAllShortsMedia()
                .filter { it.isBlocked }
                .map { it.path }
                .toSet(),
        )

        // Paths already emitted or already disproved. Anything in here is skipped by
        // later passes, which is what keeps the total MediaInfo work bounded by the
        // files whose orientation genuinely could not be determined cheaply.
        val resolved = HashSet<String>(allVideos.size)

        fun emitResolved(video: Video, width: Int, height: Int, durationMs: Long) {
            if (!resolved.add(video.path)) return
            if (elimination.isAccepted(video.path, width, height, durationMs)) {
                trySend(video)
            }
        }

        try {
            // --- Pass 1: orientation already known from MediaStore ---
            for (video in allVideos) {
                val (w, h) = displayDimensions(video.width, video.height, video.rotation)
                if (w > 0 && h > 0) {
                    emitResolved(video, w, h, video.duration)
                }
            }

            // --- Pass 2: cheap probe on rows MediaStore already timed ---
            val toProbe = allVideos.filter { it.path !in resolved && it.duration > 0 }
            for (video in toProbe) {
                val probed = metadataCache.probeResolution(video)
                if (probed != null) {
                    emitResolved(video, probed.first, probed.second, video.duration)
                }
            }

            // --- Pass 3: MediaInfo for everything still undecided ---
            val undecided = allVideos.filter { it.path !in resolved }
            val files = undecided.mapNotNull { video ->
                val file = File(video.path)
                if (file.exists()) Triple(file, video.uri, video.displayName) else null
            }
            val byPath = undecided.associateBy { it.path }

            metadataCache.walkMetadata(files) { path, metadata ->
                val video = byPath[path] ?: return@walkMetadata
                if (path in resolved) return@walkMetadata

                // If MediaInfo could not report a resolution but MediaStore already said
                // portrait, keep the video instead of losing a valid short.
                val w: Int
                val h: Int
                if (metadata.width > 0 && metadata.height > 0) {
                    val rotated = displayDimensions(metadata.width, metadata.height, metadata.rotation)
                    w = rotated.first
                    h = rotated.second
                } else if (video.width > 0 && video.height > 0) {
                    val rotated = displayDimensions(video.width, video.height, video.rotation)
                    w = rotated.first
                    h = rotated.second
                } else {
                    resolved.add(path)
                    return@walkMetadata
                }

                val durationMs = if (metadata.durationMs > 0) metadata.durationMs else video.duration
                resolved.add(path)

                if (elimination.isAccepted(path, w, h, durationMs)) {
                    trySend(enrich(video, metadata))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering shorts", e)
        }
    }.flowOn(Dispatchers.IO)

    /** Applies extracted metadata to a store-scanned row. */
    private fun enrich(video: Video, metadata: MediaInfoOps.VideoMetadata): Video = video.copy(
        duration = metadata.durationMs,
        durationFormatted = MediaFormatter.formatDuration(metadata.durationMs),
        width = metadata.width,
        height = metadata.height,
        fps = metadata.fps,
        resolution = MediaFormatter.formatResolutionWithFps(metadata.width, metadata.height, metadata.fps),
        hasEmbeddedSubtitles = metadata.hasEmbeddedSubtitles,
        subtitleCodec = metadata.subtitleCodec,
    )

    /** Applies display rotation: 90/270 degree rotations swap the axes. */
    private fun displayDimensions(width: Int, height: Int, rotation: Int): Pair<Int, Int> =
        if (rotation % 180 == 0) width to height else height to width

    /** Pure eligibility rules shared by every discovery pass. */
    private class SelectionRules(
        private val includeHorizontal: Boolean,
        private val maxHorizontalMs: Long,
        private val manuallyAdded: Set<String>,
        private val blocked: Set<String>,
    ) {
        fun isAccepted(path: String, width: Int, height: Int, durationMs: Long): Boolean {
            if (path in blocked) return false

            val isVertical = height > width && height > 0
            val isShortHorizontal = width > 0 &&
                height <= width &&
                includeHorizontal &&
                durationMs > 0 &&
                durationMs <= maxHorizontalMs

            return isVertical || path in manuallyAdded || isShortHorizontal
        }
    }
}
