package com.nandu.facecollage.pipeline

import android.content.Context
import android.net.Uri
import com.nandu.facecollage.pipeline.models.Appearance
import com.nandu.facecollage.pipeline.models.FrameFaceDetection
import com.nandu.facecollage.pipeline.models.PersonCluster
import com.nandu.facecollage.pipeline.models.ProcessingPhase
import com.nandu.facecollage.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs the whole "video in -> distinct people out" pipeline. Every step is
 * dispatched on background threads ([Dispatchers.IO]/[Dispatchers.Default]);
 * nothing in this class ever touches the main thread, so
 * [com.nandu.facecollage.ui.processing.ProcessingViewModel] can safely launch
 * this from a coroutine and keep the UI responsive while it runs.
 *
 * Pipeline stages, matching the assignment's requirements one-to-one:
 *   1. [VideoFrameExtractor]  - sample frames off the video
 *   2. [FaceDetectorEngine]   - ML Kit face detection per sampled frame
 *   3. [AppearanceTracker]    - group detections into continuous appearances
 *   4. [FaceEmbedder]         - FaceNet embedding per appearance (stage: embeddings)
 *   5. [IdentityClusterer]    - cluster appearances into distinct people (stage: clustering)
 */
class VideoProcessingPipeline(private val context: Context) {

    private val frameExtractor = VideoFrameExtractor(context)
    private val faceDetector = FaceDetectorEngine()
    private val embedder = FaceEmbedder(context)
    private val appearanceTracker = AppearanceTracker()
    private val identityClusterer = IdentityClusterer(embedder)

    suspend fun process(
        videoUri: Uri,
        onPhase: suspend (ProcessingPhase) -> Unit
    ): List<PersonCluster> = withContext(Dispatchers.Default) {
        onPhase(ProcessingPhase.LoadingVideo)

        val (frames, meta) = frameExtractor.extract(videoUri) { done, total ->
            onPhase(ProcessingPhase.ExtractingFrames(done, total))
        }

        val rotated = if (meta.rotationDegrees != 0) {
            frames.map { it.copy(bitmap = BitmapUtils.rotate(it.bitmap, meta.rotationDegrees)) }
        } else frames

        val frameByIndex = rotated.associateBy { it.index }

        val allDetections = ArrayList<FrameFaceDetection>()
        for ((i, frame) in rotated.withIndex()) {
            allDetections.addAll(faceDetector.detectInFrame(frame))
            if (i % 5 == 0 || i == rotated.lastIndex) {
                onPhase(ProcessingPhase.DetectingFaces(i + 1, rotated.size))
            }
        }
        faceDetector.close()

        val segments = appearanceTracker.buildSegments(allDetections)
        onPhase(ProcessingPhase.BuildingAppearances(segments.size))

        val appearances = ArrayList<Appearance>(segments.size)
        for ((segIdx, seg) in segments.withIndex()) {
            val topDetections = seg.sortedByDescending { it.qualityScore() }.take(3)
            val crops = topDetections.mapNotNull { d ->
                frameByIndex[d.frameIndex]?.bitmap?.let { bmp ->
                    BitmapUtils.generousFaceCrop(bmp, d.box)
                }
            }
            if (crops.isEmpty()) continue

            val embeddings = crops.map { embedder.embed(it) }
            val meanEmbedding = embedder.average(embeddings)

            val best = seg.maxBy { it.qualityScore() }
            val bestShotSource = frameByIndex[best.frameIndex]?.bitmap ?: continue
            val bestShot = BitmapUtils.generousFaceCrop(bestShotSource, best.box)

            appearances.add(
                Appearance(
                    appearanceId = segIdx,
                    trackingId = seg.first().trackingId,
                    detections = seg,
                    embedding = meanEmbedding,
                    startMs = seg.first().timestampMs,
                    endMs = seg.last().timestampMs,
                    bestDetection = best,
                    bestShot = bestShot
                )
            )
            onPhase(ProcessingPhase.EmbeddingFaces(segIdx + 1, segments.size))
        }

        onPhase(ProcessingPhase.ClusteringIdentities)
        val people = identityClusterer.cluster(appearances)

        onPhase(ProcessingPhase.Done)
        people
    }

    fun close() {
        embedder.close()
    }
}
