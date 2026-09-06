package com.nandu.facecollage.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

object BitmapUtils {

    /** Rotate a bitmap by [degrees] (as reported by the video's rotation metadata). */
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    /**
     * Crop generously AROUND a face box rather than tightly to it, so the tile
     * used in the collage has real resolution and context (hair, shoulders)
     * instead of a low-res, jaw-clipping mugshot. Expands the box by [expand]x
     * on each side (clamped to the frame), then makes the crop square-ish by
     * padding the shorter side, and finally resizes down to [targetSize] if the
     * crop is larger (keeps memory bounded when building the final collage).
     */
    fun generousFaceCrop(
        source: Bitmap,
        box: RectF,
        expand: Float = 1.9f,
        targetSize: Int = 640
    ): Bitmap {
        val cx = box.centerX()
        val cy = box.centerY()
        val halfW = (box.width() * expand) / 2f
        val halfH = (box.height() * expand) / 2f
        // Use the larger half-extent for both axes so the crop is roughly square,
        // which keeps people's faces comparably framed across different tiles.
        val half = max(halfW, halfH)

        var left = cx - half
        var top = cy - half * 1.05f // slightly more headroom above than below
        var right = cx + half
        var bottom = cy + half * 0.95f

        left = left.coerceIn(0f, source.width.toFloat())
        top = top.coerceIn(0f, source.height.toFloat())
        right = right.coerceIn(0f, source.width.toFloat())
        bottom = bottom.coerceIn(0f, source.height.toFloat())

        val w = (right - left).toInt().coerceAtLeast(1)
        val h = (bottom - top).toInt().coerceAtLeast(1)
        val safeW = min(w, source.width - left.toInt())
        val safeH = min(h, source.height - top.toInt())

        val cropped = Bitmap.createBitmap(
            source,
            left.toInt(),
            top.toInt(),
            safeW.coerceAtLeast(1),
            safeH.coerceAtLeast(1)
        )

        if (cropped.width <= targetSize && cropped.height <= targetSize) return cropped
        val scale = targetSize.toFloat() / max(cropped.width, cropped.height)
        val newW = (cropped.width * scale).toInt().coerceAtLeast(1)
        val newH = (cropped.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(cropped, newW, newH, true)
    }

    /**
     * Variance-of-Laplacian sharpness estimate over a face region, computed on
     * a small downsampled grayscale patch (cheap enough to run per-detection).
     * Returns a value normalized to roughly 0..1 via [normalizeCap].
     */
    fun sharpnessScore(source: Bitmap, box: RectF, normalizeCap: Float = 900f): Float {
        val left = box.left.toInt().coerceIn(0, source.width - 1)
        val top = box.top.toInt().coerceIn(0, source.height - 1)
        val right = box.right.toInt().coerceIn(left + 1, source.width)
        val bottom = box.bottom.toInt().coerceIn(top + 1, source.height)
        val w = right - left
        val h = bottom - top
        if (w < 8 || h < 8) return 0f

        // Downsample to a fixed small patch for a fast, size-independent Laplacian.
        val patchSize = 64
        val patch = Bitmap.createScaledBitmap(
            Bitmap.createBitmap(source, left, top, w, h),
            patchSize,
            patchSize,
            true
        )

        val gray = IntArray(patchSize * patchSize)
        val pixels = IntArray(patchSize * patchSize)
        patch.getPixels(pixels, 0, patchSize, 0, 0, patchSize, patchSize)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        // 3x3 Laplacian kernel convolution, then variance of the response.
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until patchSize - 1) {
            for (x in 1 until patchSize - 1) {
                val idx = y * patchSize + x
                val lap = -4 * gray[idx] + gray[idx - 1] + gray[idx + 1] +
                    gray[idx - patchSize] + gray[idx + patchSize]
                sum += lap
                sumSq += lap.toDouble() * lap.toDouble()
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return (variance.toFloat() / normalizeCap).coerceIn(0f, 1f)
    }

    /** Fraction of [box] that lies within the [frameW]x[frameH] bounds. */
    fun visibleFraction(box: RectF, frameW: Int, frameH: Int): Float {
        val totalArea = box.width() * box.height()
        if (totalArea <= 0f) return 0f
        val visLeft = max(0f, box.left)
        val visTop = max(0f, box.top)
        val visRight = min(frameW.toFloat(), box.right)
        val visBottom = min(frameH.toFloat(), box.bottom)
        val visW = (visRight - visLeft).coerceAtLeast(0f)
        val visH = (visBottom - visTop).coerceAtLeast(0f)
        return ((visW * visH) / totalArea).coerceIn(0f, 1f)
    }
}
