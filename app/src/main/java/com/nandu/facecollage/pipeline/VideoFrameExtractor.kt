package com.nandu.facecollage.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A single decoded frame plus the timestamp it was pulled from. */
data class SampledFrame(
    val index: Int,
    val timestampMs: Long,
    val bitmap: Bitmap
)

/**
 * Pulls frames out of a video at a fixed time interval using
 * [MediaMetadataRetriever]. This runs entirely on [Dispatchers.IO] /
 * [Dispatchers.Default] so it never touches the main thread.
 *
 * We deliberately avoid MediaCodec's full decode pipeline here: for a
 * 20-40s portrait clip, retriever-based sampling at 5-6 fps is simple,
 * robust across OEM decoders, and plenty for face-tracking continuity
 * (a person's face doesn't change pose fast enough to be missed at 5-6fps).
 */
class VideoFrameExtractor(private val context: Context) {

    /** Frames are sampled this often. 180ms ~= 5.5fps. */
    private val sampleIntervalUs = 180_000L

    suspend fun extract(
        videoUri: Uri,
        onProgress: suspend (done: Int, total: Int) -> Unit
    ): Pair<List<SampledFrame>, VideoMeta> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)

        val durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        val rotation = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        val width = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val height = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0

        val meta = VideoMeta(durationMs, rotation, width, height)

        if (durationMs <= 0L) {
            retriever.release()
            return@withContext emptyList<SampledFrame>() to meta
        }

        val timestampsUs = ArrayList<Long>()
        var t = 0L
        val durationUs = durationMs * 1000L
        while (t <= durationUs) {
            timestampsUs.add(t)
            t += sampleIntervalUs
        }

        val frames = ArrayList<SampledFrame>(timestampsUs.size)
        for ((idx, tUs) in timestampsUs.withIndex()) {
            val bmp = retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bmp != null) {
                frames.add(SampledFrame(idx, tUs / 1000L, bmp))
            }
            if (idx % 4 == 0 || idx == timestampsUs.lastIndex) {
                onProgress(idx + 1, timestampsUs.size)
            }
        }
        retriever.release()
        frames to meta
    }
}

data class VideoMeta(
    val durationMs: Long,
    val rotationDegrees: Int,
    val width: Int,
    val height: Int
)
