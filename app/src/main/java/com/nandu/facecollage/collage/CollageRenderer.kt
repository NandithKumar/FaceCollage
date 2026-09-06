package com.nandu.facecollage.collage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.nandu.facecollage.pipeline.models.PersonCluster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Builds a single shareable collage bitmap for one video: a soft gradient
 * backdrop, a title header, and one generously-cropped, rounded-corner tile
 * per distinct person with their appearance count as a caption chip -
 * loosely modelled on an Instagram Story photo-grid layout.
 */
object CollageRenderer {

    suspend fun render(
        people: List<PersonCluster>,
        videoLabel: String
    ): Bitmap = withContext(Dispatchers.Default) {
        val canvasW = 1080
        val headerH = 220
        val footerH = 110
        val gridPadding = 40
        val tileSpacing = 24

        val n = people.size.coerceAtLeast(1)
        val cols = ceil(sqrt(n.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(n.toDouble() / cols).toInt().coerceAtLeast(1)

        val tileSize = (canvasW - gridPadding * 2 - tileSpacing * (cols - 1)) / cols
        val gridH = rows * tileSize + (rows - 1) * tileSpacing
        val canvasH = headerH + gridH + gridPadding * 2 + footerH

        val output = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        drawBackground(canvas, canvasW, canvasH)
        drawHeader(canvas, canvasW, headerH, videoLabel, people.size)

        val startY = headerH + gridPadding
        for ((idx, person) in people.withIndex()) {
            val row = idx / cols
            val col = idx % cols
            val left = gridPadding + col * (tileSize + tileSpacing)
            val top = startY + row * (tileSize + tileSpacing)
            drawPersonTile(
                canvas = canvas,
                rect = RectF(
                    left.toFloat(),
                    top.toFloat(),
                    (left + tileSize).toFloat(),
                    (top + tileSize).toFloat()
                ),
                person = person,
                accentSeed = idx
            )
        }

        drawFooter(canvas, canvasW, canvasH, footerH, people)

        output
    }

    private fun drawBackground(canvas: Canvas, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(
                Color.parseColor("#1F1147"),
                Color.parseColor("#3B1E6D"),
                Color.parseColor("#5B2A86")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // Soft decorative glow circles for a little visual life.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.color = Color.WHITE
        glow.alpha = 18
        canvas.drawCircle(w * 0.85f, h * 0.08f, w * 0.35f, glow)
        glow.alpha = 12
        canvas.drawCircle(w * 0.1f, h * 0.95f, w * 0.3f, glow)
    }

    private fun drawHeader(canvas: Canvas, w: Int, headerH: Int, videoLabel: String, peopleCount: Int) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 255, 255)
            textSize = 34f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Who's in the clip", w / 2f, headerH * 0.42f, titlePaint)
        val sub = "$videoLabel  ·  $peopleCount ${if (peopleCount == 1) "person" else "people"} detected"
        canvas.drawText(sub, w / 2f, headerH * 0.42f + 56f, subPaint)
    }

    private fun drawFooter(canvas: Canvas, w: Int, h: Int, footerH: Int, people: List<PersonCluster>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 255, 255, 255)
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        val totalAppearances = people.sumOf { it.appearanceCount }
        canvas.drawText(
            "$totalAppearances total appearances  ·  built on-device with Face Collage",
            w / 2f,
            (h - footerH * 0.35f),
            paint
        )
    }

    private fun drawPersonTile(canvas: Canvas, rect: RectF, person: PersonCluster, accentSeed: Int) {
        val cornerRadius = 36f

        // Drop shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
            maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        val shadowRect = RectF(rect.left, rect.top + 10f, rect.right, rect.bottom + 10f)
        canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)

        // Clip to rounded rect and draw the photo, center-cropped to fill.
        val clipPath = Path().apply { addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW) }
        val saveCount = canvas.save()
        canvas.clipPath(clipPath)

        val photo = person.representativeShot
        val matrix = centerCropMatrix(photo, rect)
        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(photo, matrix, photoPaint)

        // Bottom gradient scrim so the caption stays legible over any photo.
        val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, rect.bottom - rect.height() * 0.42f, 0f, rect.bottom,
                Color.TRANSPARENT, Color.argb(200, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(rect.left, rect.bottom - rect.height() * 0.42f, rect.right, rect.bottom, scrimPaint)

        canvas.restoreToCount(saveCount)

        // Accent border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = ColorUtils.HSLToColor(
                floatArrayOf((accentSeed * 47f) % 360f, 0.55f, 0.6f)
            )
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        // Caption: "Person N" + appearance count chip
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = rect.width() * 0.10f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = rect.width() * 0.075f
        }
        val padding = rect.width() * 0.07f
        canvas.drawText(
            "Person ${person.personIndex}",
            rect.left + padding,
            rect.bottom - padding - countPaint.textSize - 6f,
            namePaint
        )
        val timesWord = if (person.appearanceCount == 1) "appearance" else "appearances"
        canvas.drawText(
            "${person.appearanceCount} $timesWord",
            rect.left + padding,
            rect.bottom - padding,
            countPaint
        )
    }

    /** Scales+translates [bitmap] so it fills [dest] fully (center-crop), like ImageView's centerCrop. */
    private fun centerCropMatrix(bitmap: Bitmap, dest: RectF): Matrix {
        val scaleX = dest.width() / bitmap.width.toFloat()
        val scaleY = dest.height() / bitmap.height.toFloat()
        val finalScale = maxOf(scaleX, scaleY)
        val dx = dest.left + (dest.width() - bitmap.width * finalScale) / 2f
        val dy = dest.top + (dest.height() - bitmap.height * finalScale) / 2f
        return Matrix().apply {
            setScale(finalScale, finalScale)
            postTranslate(dx, dy)
        }
    }
}
