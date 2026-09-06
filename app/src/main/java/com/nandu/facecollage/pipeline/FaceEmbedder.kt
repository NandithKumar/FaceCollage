package com.nandu.facecollage.pipeline

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Stage 2 of the pipeline: turns a cropped face into a fixed-length embedding
 * vector so Stage 3 (clustering) can compare "is this the same person".
 *
 * Model: FaceNet (Inception-ResNet-v1 trained with triplet loss on VGGFace2 /
 * MS1M, converted to TFLite, int8-quantized weights, float32 I/O), 160x160x3
 * input, 128-dim embedding output. Bundled at assets/facenet.tflite.
 * See README for full provenance and license.
 */
class FaceEmbedder(context: Context) {

    private val interpreter: Interpreter
    private val inputSize: Int
    val embeddingDim: Int

    init {
        val model = loadModelFile(context)
        interpreter = Interpreter(model, Interpreter.Options().apply { numThreads = 4 })
        // Read actual tensor shapes instead of hardcoding, so a swapped-in
        // model (e.g. facenet_512.tflite) keeps working without code changes.
        val inputShape = interpreter.getInputTensor(0).shape() // [1, H, W, 3]
        inputSize = inputShape[1]
        val outputShape = interpreter.getOutputTensor(0).shape() // [1, D]
        embeddingDim = outputShape[1]
    }

    /** Returns an L2-normalized embedding so cosine similarity == dot product. */
    suspend fun embed(faceBitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        val resized = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
        val inputBuffer = bitmapToStandardizedBuffer(resized)
        val output = Array(1) { FloatArray(embeddingDim) }
        interpreter.run(inputBuffer, output)
        l2Normalize(output[0])
    }

    /** Mean of several embeddings (e.g. one appearance's sampled frames), re-normalized. */
    fun average(embeddings: List<FloatArray>): FloatArray {
        val dim = embeddings.first().size
        val out = FloatArray(dim)
        for (e in embeddings) for (i in 0 until dim) out[i] += e[i]
        for (i in 0 until dim) out[i] = out[i] / embeddings.size
        return l2Normalize(out)
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot // both are already L2-normalized
    }

    fun close() = interpreter.close()

    /**
     * Per-image standardization: x' = (x - mean) / max(std, 1/sqrt(N)).
     * This is FaceNet's standard "prewhiten" preprocessing (matches the
     * reference Android implementation this model was sourced from).
     */
    private fun bitmapToStandardizedBuffer(bmp: Bitmap): ByteBuffer {
        val n = inputSize * inputSize
        val pixels = IntArray(n)
        bmp.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val r = FloatArray(n); val g = FloatArray(n); val b = FloatArray(n)
        var sum = 0.0
        for (i in 0 until n) {
            val p = pixels[i]
            r[i] = ((p shr 16) and 0xFF).toFloat()
            g[i] = ((p shr 8) and 0xFF).toFloat()
            b[i] = (p and 0xFF).toFloat()
            sum += r[i] + g[i] + b[i]
        }
        val mean = sum / (n * 3)
        var sumSq = 0.0
        for (i in 0 until n) {
            sumSq += (r[i] - mean) * (r[i] - mean)
            sumSq += (g[i] - mean) * (g[i] - mean)
            sumSq += (b[i] - mean) * (b[i] - mean)
        }
        val variance = sumSq / (n * 3)
        val std = maxOf(sqrt(variance), 1.0 / sqrt((n * 3).toDouble()))

        val buffer = ByteBuffer.allocateDirect(4 * n * 3).apply { order(ByteOrder.nativeOrder()) }
        for (i in 0 until n) {
            buffer.putFloat(((r[i] - mean) / std).toFloat())
            buffer.putFloat(((g[i] - mean) / std).toFloat())
            buffer.putFloat(((b[i] - mean) / std).toFloat())
        }
        buffer.rewind()
        return buffer
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var normSq = 0f
        for (x in v) normSq += x * x
        val norm = sqrt(normSq.toDouble()).toFloat().coerceAtLeast(1e-8f)
        return FloatArray(v.size) { v[it] / norm }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val afd = context.assets.openFd("facenet.tflite")
        FileInputStream(afd.fileDescriptor).use { input ->
            return input.channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
        }
    }
}
