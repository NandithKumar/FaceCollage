package com.nandu.facecollage.pipeline.models

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * One face detection result on a single sampled video frame, before any
 * temporal grouping has happened. Kept intentionally lightweight: we do NOT
 * hold a bitmap here, only a reference to which sampled frame it came from
 * plus everything ML Kit gave us about the face in that frame.
 */
data class FrameFaceDetection(
    val frameIndex: Int,
    val timestampMs: Long,
    /** Face bounding box in the coordinate space of the sampled frame bitmap. */
    val box: RectF,
    val trackingId: Int?,
    val headEulerAngleX: Float, // pitch (nod up/down)
    val headEulerAngleY: Float, // yaw (turn left/right) -> frontality signal
    val headEulerAngleZ: Float, // roll (tilt)
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val smilingProbability: Float?,
    /** Fraction of the box that lies inside the frame bounds, 0..1. 1 = fully visible. */
    val visibleFraction: Float,
    /** Laplacian-variance based sharpness score computed on the face crop, higher = sharper. */
    val sharpness: Float
) {
    /** Composite 0..1 "how good a headshot is this" score. Higher is better. */
    fun qualityScore(): Float {
        val frontality = frontalityScore()
        val eyes = eyesOpenScore()
        val smile = smilingProbability ?: 0.5f
        val sharp = sharpness.coerceIn(0f, 1f)
        val clipPenalty = visibleFraction // already 0..1, multiplied in

        return (0.32f * frontality +
            0.28f * sharp +
            0.22f * eyes +
            0.10f * smile +
            0.08f * 1f) * clipPenalty
    }

    private fun frontalityScore(): Float {
        // 0 degrees yaw/pitch = perfectly frontal. Penalize deviation, allow up to ~35 degrees.
        val yaw = kotlin.math.abs(headEulerAngleY)
        val pitch = kotlin.math.abs(headEulerAngleX)
        val yawScore = (1f - (yaw / 35f)).coerceIn(0f, 1f)
        val pitchScore = (1f - (pitch / 30f)).coerceIn(0f, 1f)
        return (yawScore * 0.7f + pitchScore * 0.3f)
    }

    private fun eyesOpenScore(): Float {
        val l = leftEyeOpenProbability
        val r = rightEyeOpenProbability
        return when {
            l != null && r != null -> (l + r) / 2f
            l != null -> l
            r != null -> r
            else -> 0.6f // unknown, don't punish too hard
        }
    }
}

/**
 * A continuous run of frames in which the SAME face was tracked without a
 * visibility gap. This is what the assignment calls an "appearance":
 * it starts when a face becomes clearly visible and ends when it's no
 * longer clearly visible.
 */
data class Appearance(
    val appearanceId: Int,
    val trackingId: Int?,
    val detections: List<FrameFaceDetection>,
    /** Mean FaceNet embedding across the sampled detections in this appearance, L2-normalized. */
    val embedding: FloatArray,
    val startMs: Long,
    val endMs: Long,
    /** Best single detection in this appearance by quality score. */
    val bestDetection: FrameFaceDetection,
    /** Generously-cropped bitmap of the best frame (NOT a tight face crop). */
    val bestShot: Bitmap
) {
    val durationMs: Long get() = endMs - startMs
}

/** Final grouping of appearances that the identity clusterer decided belong to one person. */
data class PersonCluster(
    val personIndex: Int, // 1-based, stable ordering for display
    val appearances: List<Appearance>,
    val centroidEmbedding: FloatArray
) {
    val appearanceCount: Int get() = appearances.size

    /** The single best shot across ALL of this person's appearances. */
    val representativeShot: Bitmap
        get() = appearances.maxBy { it.bestDetection.qualityScore() }.bestShot
}

/** What ProcessingScreen renders, updated as the pipeline runs. */
sealed class ProcessingPhase(val label: String) {
    data object Idle : ProcessingPhase("Idle")
    data object LoadingVideo : ProcessingPhase("Loading video")
    data class ExtractingFrames(val done: Int, val total: Int) :
        ProcessingPhase("Extracting frames")
    data class DetectingFaces(val done: Int, val total: Int) :
        ProcessingPhase("Detecting faces")
    data class BuildingAppearances(val count: Int) :
        ProcessingPhase("Grouping continuous appearances")
    data class EmbeddingFaces(val done: Int, val total: Int) :
        ProcessingPhase("Computing face embeddings")
    data object ClusteringIdentities : ProcessingPhase("Clustering identities")
    data object BuildingCollage : ProcessingPhase("Building collage")
    data object Done : ProcessingPhase("Done")

    /** 0f..1f overall progress estimate across the whole pipeline. */
    fun overallProgress(): Float = when (this) {
        is Idle -> 0f
        is LoadingVideo -> 0.02f
        is ExtractingFrames -> 0.05f + 0.25f * safeFrac(done, total)
        is DetectingFaces -> 0.30f + 0.35f * safeFrac(done, total)
        is BuildingAppearances -> 0.66f
        is EmbeddingFaces -> 0.68f + 0.17f * safeFrac(done, total)
        is ClusteringIdentities -> 0.90f
        is BuildingCollage -> 0.95f
        is Done -> 1f
    }

    private fun safeFrac(done: Int, total: Int) =
        if (total <= 0) 0f else (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

data class ProcessingResult(
    val people: List<PersonCluster>,
    val totalAppearances: Int,
    val collageBitmap: Bitmap,
    val processingTimeMs: Long
)
