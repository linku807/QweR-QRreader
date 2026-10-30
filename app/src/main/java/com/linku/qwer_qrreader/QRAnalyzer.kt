package com.linku.qwer_qrreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.common.HybridBinarizer
import timber.log.Timber
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot
import kotlin.math.max

class QRAnalyzer(
    context: Context,
    private val QRcontext: (String?, Int?, Int?) -> Unit,
) : ImageAnalysis.Analyzer {

    private var scanSuccessCount = 0
    private var upscaleSuccessCount = 0
    private var lastCalculationTime = System.currentTimeMillis()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val inferenceExecutor = Executors.newSingleThreadExecutor()
    private val inferenceRunning = AtomicBoolean(false)

    private val qrRestorer = Model(context.applicationContext)

    private val qrScannerOptions =
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAllPotentialBarcodes()
            .build()

    private val scanner = BarcodeScanning.getClient(qrScannerOptions)

    private val zxingHints = EnumMap<DecodeHintType, Any>(
        DecodeHintType::class.java
    ).apply {
        put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
        put(DecodeHintType.TRY_HARDER, true)
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image

        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue

                    // ML Kit에서 이미 디코딩에 성공한 경우
                    if (!rawValue.isNullOrEmpty()) {
                        scanSuccessCount++
                        QRcontext(rawValue, null, null)
                        break
                    }

                    // 후보만 검출된 경우 AI 복원 파이프라인 실행
                    val corners = barcode.cornerPoints ?: continue
                    if (corners.size < 4) continue

                    if (!inferenceRunning.compareAndSet(false, true)) {
                        break
                    }

                    try {
                        // ImageProxy가 유효할 때 독립적인 Bitmap 확보
                        val bitmap = imageProxy.toBitmap()
                        val rotatedBitmap = rotateBitmap(
                            bitmap,
                            imageProxy.imageInfo.rotationDegrees
                        )

                        val qrBitmap = rectifyQr(
                            rotatedBitmap,
                            corners.take(4).toTypedArray()
                        )

                        if (qrBitmap == null) {
                            inferenceRunning.set(false)
                            continue
                        }

                        // 이후 처리는 ImageProxy 수명과 분리
                        inferenceExecutor.execute {
                            try {
                                Timber.d("1. QR 후보 추출 완료: ${qrBitmap.width}x${qrBitmap.height}")

                                val restored = qrRestorer.inference(qrBitmap)

                                Timber.d(
                                    "Restored bitmap: ${restored.width}x${restored.height}, " +
                                            "config=${restored.config}"
                                )

                                val decoded = decodeWithZxing(restored)

                                Timber.d("3. ZXing 결과: $decoded")

                                if (decoded != null) {
                                    mainHandler.post {
                                        upscaleSuccessCount++
                                        QRcontext(decoded, null, null)
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "QR restoration failed")
                            } finally {
                                inferenceRunning.set(false)
                            }
                        }

                        break
                    } catch (e: Exception) {
                        inferenceRunning.set(false)
                        Timber.e(e, "QR candidate extraction failed")
                    }
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Barcode scanning failed")
            }
            .addOnCompleteListener {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastCalculationTime >= 1000L) {
                    QRcontext(null, scanSuccessCount, upscaleSuccessCount)
                    scanSuccessCount = 0
                    upscaleSuccessCount = 0
                    lastCalculationTime = currentTime
                }

                imageProxy.close()
            }
    }

    private fun rotateBitmap(
        source: Bitmap,
        degrees: Int
    ): Bitmap {
        if (degrees == 0) return source

        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
        }

        return Bitmap.createBitmap(
            source, 0, 0,
            source.width, source.height,
            matrix, true
        )
    }

    private fun rectifyQr(
        source: Bitmap,
        corners: Array<Point>
    ): Bitmap? {
        if (corners.size != 4) return null

        fun distance(a: Point, b: Point): Double =
            hypot(
                (a.x - b.x).toDouble(),
                (a.y - b.y).toDouble()
            )

        val side = max(
            max(
                distance(corners[0], corners[1]),
                distance(corners[1], corners[2])
            ),
            max(
                distance(corners[2], corners[3]),
                distance(corners[3], corners[0])
            )
        ).toInt().coerceAtLeast(1)

        val srcPoints = FloatArray(8) { i ->
            val point = corners[i / 2]
            if (i % 2 == 0) point.x.toFloat() else point.y.toFloat()
        }

        val end = (side - 1).toFloat()
        val dstPoints = floatArrayOf(
            0f, 0f,
            end, 0f,
            end, end,
            0f, end
        )

        val matrix = Matrix()
        if (!matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)) {
            return null
        }

        return createBitmap(
            side, side, Bitmap.Config.ARGB_8888
        ).also { output ->
            Canvas(output).drawBitmap(
                source,
                matrix,
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        }
    }

    private fun decodeWithZxing(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)

        bitmap.getPixels(
            pixels, 0, width,
            0, 0, width, height
        )

        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        return try {
            MultiFormatReader().apply {
                setHints(zxingHints)
            }.decode(binaryBitmap).text
        } catch (_: NotFoundException) {
            null
        }
    }

}

