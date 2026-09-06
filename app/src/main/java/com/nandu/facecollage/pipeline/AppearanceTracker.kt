package com.nandu.facecollage.pipeline

import com.nandu.facecollage.pipeline.models.FrameFaceDetection

/**
 * Turns the raw per-frame detections from [FaceDetectorEngine] into
 * "appearances": continuous runs where the same tracked face stayed clearly
 * visible. This is purely temporal - it knows nothing about WHO the person
 * is, only "same tracked blob, uninterrupted". Cross-appearance identity
 * (stage 3) is handled separately by [IdentityClusterer].
 *
 * Two quality gates decide whether a detection counts as "clearly visible"
 * at all:
 *  - [minSharpness]: filters out the blurred frames of a whip-pan. A blurry
 *    detection neither starts nor extends an appearance, and a segment made
 *    up only of blurry detections is dropped entirely - "blurred whip-pan
 *    passes count for nobody", per spec.
 *  - [minVisibleFraction]: filters out faces mostly clipped by the frame
 *    edge, so a sliver of cheek at the frame boundary doesn't count as an
 *    appearance either.
 *
 * A gap of more than [maxMissedFrames] consecutive sampled frames without a
 * qualifying detection for a given trackingId ends that appearance; if the
 * same trackingId is seen again later it starts a NEW appearance (which is
 * exactly the "4 separate appearances of the same person" behaviour the
 * worked example describes - re-identifying them as the same PERSON is
 * IdentityClusterer's job).
 */
class AppearanceTracker(
    private val minSharpness: Float = 0.10f,
    private val minVisibleFraction: Float = 0.55f,
    private val maxMissedFrames: Int = 1,
    private val minSegmentDetections: Int = 2,
    private val minSegmentDurationMs: Long = 260L
) {

    fun buildSegments(allDetections: List<FrameFaceDetection>): List<List<FrameFaceDetection>> {
        val qualifying = allDetections.filter {
            it.sharpness >= minSharpness && it.visibleFraction >= minVisibleFraction
        }

        // Detections with a stable trackingId are grouped by that id; ML Kit
        // occasionally fails to assign one (-1 -> null here), in which case we
        // fall back to treating each such detection as its own 1-frame group -
        // it will only survive the length/duration filter below if it somehow
        // recurs, which in practice it won't, so it's effectively discarded
        // rather than falsely inflating appearance counts.
        val byTrack = qualifying.filter { it.trackingId != null }.groupBy { it.trackingId }
        val untracked = qualifying.filter { it.trackingId == null }

        val segments = ArrayList<List<FrameFaceDetection>>()

        for ((_, dets) in byTrack) {
            val sorted = dets.sortedBy { it.frameIndex }
            var current = ArrayList<FrameFaceDetection>()
            for (d in sorted) {
                if (current.isEmpty()) {
                    current.add(d)
                    continue
                }
                val gap = d.frameIndex - current.last().frameIndex
                if (gap <= maxMissedFrames + 1) {
                    current.add(d)
                } else {
                    segments.add(current)
                    current = arrayListOf(d)
                }
            }
            if (current.isNotEmpty()) segments.add(current)
        }

        for (d in untracked) segments.add(listOf(d))

        return segments.filter { seg ->
            seg.size >= minSegmentDetections &&
                (seg.last().timestampMs - seg.first().timestampMs) >= minSegmentDurationMs
        }.sortedBy { it.first().timestampMs }
    }
}
