package com.nandu.facecollage.pipeline

import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.nandu.facecollage.pipeline.models.FrameFaceDetection
import com.nandu.facecollage.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps ML Kit's on-device [FaceDetector]. Stage 1 of the 3 required stages
 * (detection -> embedding -> clustering).
 *
 * Tracking is enabled and a SINGLE detector instance is reused across every
 * sampled frame of a video, processed strictly in timestamp order. ML Kit
 * assigns a stable `trackingId` to a face as long as it keeps seeing it in
 * consecutive frames, which is exactly the primitive we need to build
 * "continuous visible appearances" in [AppearanceTracker] - detection alone
 * does not know identity, tracking alone does not know identity either, it
 * just tells us "same blob, frame to frame" until the detector loses it.
 */
class FaceDetectorEngine {

    private val detector: FaceDetector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // smiling + eyes-open
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(0.08f)
            .enableTracking()
            .build()
        FaceDetection.getClient(opts)
    }

    suspend fun detectInFrame(frame: SampledFrame): List<FrameFaceDetection> =
        withContext(Dispatchers.Default) {
            val input = InputImage.fromBitmap(frame.bitmap, 0)
            val faces: List<Face> = try {
                Tasks.await(detector.process(input))
            } catch (t: Throwable) {
                emptyList()
            }

            faces.map { face -> face.toDetection(frame) }
        }

    fun close() = detector.close()

    private fun Face.toDetection(frame: SampledFrame): FrameFaceDetection {
        val box = RectF(boundingBox)
        val frameW = frame.bitmap.width
        val frameH = frame.bitmap.height
        val visible = BitmapUtils.visibleFraction(box, frameW, frameH)
        val sharpness = BitmapUtils.sharpnessScore(frame.bitmap, box)

        return FrameFaceDetection(
            frameIndex = frame.index,
            timestampMs = frame.timestampMs,
            box = box,
            trackingId = if (trackingId != -1) trackingId else null,
            headEulerAngleX = headEulerAngleX,
            headEulerAngleY = headEulerAngleY,
            headEulerAngleZ = headEulerAngleZ,
            leftEyeOpenProbability = leftEyeOpenProbability,
            rightEyeOpenProbability = rightEyeOpenProbability,
            smilingProbability = smilingProbability,
            visibleFraction = visible,
            sharpness = sharpness
        )
    }
}
