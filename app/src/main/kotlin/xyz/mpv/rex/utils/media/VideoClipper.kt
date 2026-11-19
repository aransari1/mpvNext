package xyz.mpv.rex.utils.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import `is`.xyz.mpv.FastClipper
import `is`.xyz.mpv.FastClipper.ClipMode
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object VideoClipper {
    private const val TAG = "VideoClipper"
    private const val DEFAULT_BUFFER_SIZE = 2 * 1024 * 1024 // 2 MB

    suspend fun cutClip(
        context: Context,
        inputPath: String,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        mode: ClipMode = ClipMode.FAST_COPY
    ): Result<File> = withContext(Dispatchers.IO) {
        val startSec = startMs / 1000.0
        val endSec = endMs / 1000.0
        val startUs = startMs * 1000L
        val endUs = endMs * 1000L

        outputFile.parentFile?.mkdirs()

        // 1. Primary Strategy: Native FFmpeg Fast Copy or Frame-Accurate Hardware Transcoding
        val ffmpegResult = runCatching {
            cutWithNativeFFmpeg(context, inputPath, outputFile, startSec, endSec, mode)
        }

        if (ffmpegResult.isSuccess && outputFile.exists() && outputFile.length() > 0) {
            Log.d(TAG, "Clip cut successfully via Native FFmpeg ($mode): ${outputFile.absolutePath}")
            scanFile(context, outputFile)
            return@withContext Result.success(outputFile)
        }

        Log.w(TAG, "Native FFmpeg cut failed or unavailable, falling back...", ffmpegResult.exceptionOrNull())

        // 2. Fallback for MKV: mpv dump-cache if output container is MKV (only in fast copy mode)
        val isMkv = inputPath.endsWith(".mkv", ignoreCase = true) || outputFile.name.endsWith(".mkv", ignoreCase = true)
        if (isMkv && mode == ClipMode.FAST_COPY) {
            val dumpResult = tryDumpCache(startMs, endMs, outputFile)
            if (dumpResult.isSuccess && outputFile.exists() && outputFile.length() > 100 * 1024) {
                Log.d(TAG, "Clip dumped via mpv dump-cache fallback: ${outputFile.absolutePath}")
                scanFile(context, outputFile)
                return@withContext Result.success(outputFile)
            }
        }

        // 3. Fallback for MP4 / WebM: Android native MediaExtractor + MediaMuxer
        val mediaMuxerOutputFile = if (isMkv && outputFile.name.endsWith(".mkv", ignoreCase = true)) {
            File(outputFile.parentFile, outputFile.name.removeSuffix(".mkv") + ".mp4")
        } else {
            outputFile
        }

        val muxerResult = runCatching {
            cutWithMediaMuxer(context, inputPath, mediaMuxerOutputFile, startUs, endUs)
        }

        if (muxerResult.isSuccess && mediaMuxerOutputFile.exists() && mediaMuxerOutputFile.length() > 0) {
            Log.d(TAG, "Clip cut successfully via MediaMuxer fallback: ${mediaMuxerOutputFile.absolutePath}")
            scanFile(context, mediaMuxerOutputFile)
            return@withContext Result.success(mediaMuxerOutputFile)
        }

        val finalError = ffmpegResult.exceptionOrNull()
            ?: muxerResult.exceptionOrNull()
            ?: Exception("Failed to generate clip file")

        if (outputFile.exists() && outputFile.length() == 0L) {
            outputFile.delete()
        }
        if (mediaMuxerOutputFile.exists() && mediaMuxerOutputFile.length() == 0L) {
            mediaMuxerOutputFile.delete()
        }

        Result.failure(finalError)
    }

    private fun cutWithNativeFFmpeg(
        context: Context,
        inputPath: String,
        outputFile: File,
        startSec: Double,
        endSec: Double,
        mode: ClipMode
    ) {
        var pfd: ParcelFileDescriptor? = null
        val resolvedPath = if (inputPath.startsWith("content://")) {
            try {
                pfd = context.contentResolver.openFileDescriptor(Uri.parse(inputPath), "r")
                pfd?.fd?.let { fd -> "/proc/self/fd/$fd" } ?: inputPath
            } catch (e: Exception) {
                Log.w(TAG, "Unable to get file descriptor for $inputPath", e)
                inputPath
            }
        } else {
            inputPath
        }

        try {
            val result = FastClipper.cutClip(resolvedPath, outputFile.absolutePath, startSec, endSec, mode)
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: IllegalStateException("Native FFmpeg clipping failed")
            }
        } finally {
            try {
                pfd?.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun tryDumpCache(startMs: Long, endMs: Long, outputFile: File): Result<Unit> {
        val startSec = startMs / 1000.0
        val endSec = endMs / 1000.0
        return runCatching {
            MPVLib.command(
                "dump-cache",
                String.format(java.util.Locale.US, "%.3f", startSec),
                String.format(java.util.Locale.US, "%.3f", endSec),
                outputFile.absolutePath
            )
        }
    }

    private fun cutWithMediaMuxer(
        context: Context,
        inputPath: String,
        outputFile: File,
        startUs: Long,
        endUs: Long
    ) {
        val extractor = MediaExtractor()
        if (inputPath.startsWith("content://")) {
            extractor.setDataSource(context, Uri.parse(inputPath), null)
        } else {
            extractor.setDataSource(inputPath)
        }

        val trackCount = extractor.trackCount
        val muxerFormat = when {
            outputFile.name.endsWith(".webm", ignoreCase = true) -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            else -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        }
        val muxer = MediaMuxer(outputFile.absolutePath, muxerFormat)
        val indexMap = HashMap<Int, Int>()
        var maxBufferSize = DEFAULT_BUFFER_SIZE
        var videoTrackIndex = -1

        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                extractor.selectTrack(i)
                val newTrackIndex = muxer.addTrack(format)
                indexMap[i] = newTrackIndex

                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                }

                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    if (size > maxBufferSize) maxBufferSize = size
                }
            }
        }

        if (indexMap.isEmpty()) {
            extractor.release()
            muxer.release()
            throw IllegalStateException("No extractable video or audio tracks found")
        }

        if (videoTrackIndex != -1) {
            extractor.selectTrack(videoTrackIndex)
        }
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        var baseTimeUs = extractor.sampleTime
        if (baseTimeUs < 0) baseTimeUs = startUs

        muxer.start()

        val buffer = ByteBuffer.allocateDirect(maxBufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val trackIndex = extractor.sampleTrackIndex
            if (trackIndex < 0) break

            val sampleTime = extractor.sampleTime
            if (sampleTime > endUs) break

            val muxerTrackIndex = indexMap[trackIndex]
            if (muxerTrackIndex != null && sampleTime >= baseTimeUs) {
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size > 0) {
                    bufferInfo.presentationTimeUs = sampleTime - baseTimeUs
                    bufferInfo.offset = 0
                    val flags = extractor.sampleFlags
                    var muxerFlags = 0
                    if ((flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        muxerFlags = muxerFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                    }
                    bufferInfo.flags = muxerFlags

                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                }
            }

            extractor.advance()
        }

        try {
            muxer.stop()
        } finally {
            muxer.release()
            extractor.release()
        }
    }

    private fun scanFile(context: Context, file: File) {
        MediaScannerConnection.scanFile(
            context.applicationContext,
            arrayOf(file.absolutePath),
            null
        ) { _, _ ->
            MediaLibraryEvents.notifyChanged()
        }
    }

    fun getOutputClipFile(
        inputPath: String,
        startMs: Long,
        endMs: Long,
        mode: ClipMode = ClipMode.FAST_COPY
    ): File {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val clipsDir = File(moviesDir, "REX Player")
        if (!clipsDir.exists()) {
            clipsDir.mkdirs()
        }

        val originalName = Uri.parse(inputPath).lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() } ?: "video"

        val extension = when {
            mode == ClipMode.FRAME_ACCURATE -> "mp4"
            inputPath.endsWith(".webm", ignoreCase = true) -> "webm"
            inputPath.endsWith(".mkv", ignoreCase = true) -> "mkv"
            inputPath.endsWith(".ts", ignoreCase = true) -> "ts"
            inputPath.endsWith(".mov", ignoreCase = true) -> "mov"
            inputPath.endsWith(".flv", ignoreCase = true) -> "mkv"
            inputPath.endsWith(".avi", ignoreCase = true) -> "mkv"
            else -> "mp4"
        }

        val startSec = startMs / 1000
        val endSec = endMs / 1000
        val modeSuffix = if (mode == ClipMode.FRAME_ACCURATE) "_exact" else ""
        val baseFileName = "${originalName}_clip_${startSec}s-${endSec}s$modeSuffix"

        var candidate = File(clipsDir, "$baseFileName.$extension")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(clipsDir, "${baseFileName}_$counter.$extension")
            counter++
        }

        return candidate
    }
}
