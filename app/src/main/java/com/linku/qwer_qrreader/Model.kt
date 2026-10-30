package com.linku.qwer_qrreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap.createBitmap
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.nio.ByteOrder
import kotlin.math.exp

class Model(private val context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelFile = copyAsset(context, "model2.onnx")
        copyAsset(context,"model2.onnx.data")
        session = env.createSession(modelFile.absolutePath)
    }

    fun inference(bitmap: Bitmap): Bitmap {
        saveDebugBitmap(bitmap, "before_${System.currentTimeMillis()}.png")
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val buffer = ByteBufferHelper.floatBuffer(w * h)
        for (p in pixels) {
            val gray = (0.299f * Color.red(p) +
                    0.587f * Color.green(p) +
                    0.114f * Color.blue(p)) / 255f
            buffer.put(gray)
        }
        buffer.rewind()

        val inputName = session.inputNames.first()
        val shape = longArrayOf(1, 1, h.toLong(), w.toLong())

        OnnxTensor.createTensor(env, buffer, shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0].value as Array<Array<Array<FloatArray>>>
                val values = output[0][0]
                val oh = values.size
                val ow = values[0].size

                val minValue = values.minOf { row -> row.minOrNull() ?: 0f }
                val maxValue = values.maxOf { row -> row.maxOrNull() ?: 0f }
                val meanValue = values.sumOf { row ->
                    row.sumOf { it.toDouble() }
                } / (oh * ow)

                Timber.d(
                    "ONNX output: shape=${ow}x${oh}, " +
                            "min=$minValue, max=$maxValue, mean=$meanValue"
                )

                val out = createBitmap(
                    ow, oh, Bitmap.Config.ARGB_8888
                )
                val outPixels = IntArray(ow * oh)
                for (y in 0 until oh) {
                    for (x in 0 until ow) {
                        val v = (255f / (1f + exp(-values[y][x])))
                            .toInt().coerceIn(0, 255)
                        outPixels[y * ow + x] = Color.rgb(v, v, v)
                    }
                }
                out.setPixels(outPixels, 0, ow, 0, 0, ow, oh)
                saveDebugBitmap(out, "after_${System.currentTimeMillis()}.png")
                return out
            }
        }
    }

    private fun saveDebugBitmap(bitmap: Bitmap, name: String) {
        val file = File(context.cacheDir, name)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Timber.d("Saved debug image: ${file.absolutePath}")
    }

    override fun close() {
        session.close()
    }
}

private object ByteBufferHelper {
    fun floatBuffer(size: Int): FloatBuffer =
        java.nio.ByteBuffer.allocateDirect(size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
}

private fun copyAsset(context: Context, name: String): File {
    val file = File(context.filesDir, name)

    if (!file.exists()) {
        context.assets.open(name).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    return file
}
