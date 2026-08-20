package com.scansfer.app.receive

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.scansfer.app.core.Frame
import com.scansfer.app.core.Protocol

/**
 * Pulls Scansfer frames out of the camera stream.
 *
 * ML Kit does the detecting because it is markedly better than anything else at
 * locking onto a dense, moving code. Its `rawBytes` is the byte-exact payload we
 * want, but a handful of devices only surface a decoded string. If that happens
 * we notice (detections that never parse) and fall back to ZXing, which always
 * hands back the raw byte segments.
 */
class QrAnalyzer(
    private val onFrame: (Frame) -> Unit,
    private val onDetection: (parsed: Boolean) -> Unit,
) : ImageAnalysis.Analyzer {

    /** Consecutive detections that failed to parse before we distrust ML Kit. */
    private val fallbackThreshold = 12

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    private val zxing = QRCodeReader()
    private val zxingHints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "ISO-8859-1",
    )

    private var unparsedStreak = 0
    private var useZxing = false

    fun close() = scanner.close()

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (useZxing) {
            try {
                analyzeWithZxing(imageProxy)
            } finally {
                imageProxy.close()
            }
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val bytes = barcode.rawBytes
                        ?: barcode.rawValue?.toByteArray(Charsets.ISO_8859_1)
                        ?: continue
                    handle(Protocol.parse(bytes))
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun handle(frame: Frame?) {
        if (frame != null) {
            unparsedStreak = 0
            onDetection(true)
            onFrame(frame)
        } else {
            unparsedStreak++
            onDetection(false)
            if (!useZxing && unparsedStreak >= fallbackThreshold) {
                useZxing = true
                scanner.close()
            }
        }
    }

    private fun analyzeWithZxing(imageProxy: ImageProxy) {
        val width = imageProxy.width
        val height = imageProxy.height
        val plane = imageProxy.planes.getOrNull(0) ?: return
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val luminance = ByteArray(width * height)

        if (rowStride == width) {
            buffer.get(luminance, 0, minOf(buffer.remaining(), luminance.size))
        } else {
            val row = ByteArray(rowStride)
            for (y in 0 until height) {
                if (buffer.remaining() < rowStride) break
                buffer.get(row, 0, rowStride)
                System.arraycopy(row, 0, luminance, y * width, width)
            }
        }

        val source = PlanarYUVLuminanceSource(
            luminance, width, height, 0, 0, width, height, false,
        )
        val result = runCatching {
            zxing.decode(BinaryBitmap(HybridBinarizer(source)), zxingHints)
        }.getOrNull()
        zxing.reset()
        if (result == null) return

        @Suppress("UNCHECKED_CAST")
        val segments = result.resultMetadata
            ?.get(com.google.zxing.ResultMetadataType.BYTE_SEGMENTS) as? List<ByteArray>
        val bytes = segments?.takeIf { it.isNotEmpty() }
            ?.reduce { acc, next -> acc + next }
            ?: result.text.toByteArray(Charsets.ISO_8859_1)

        val frame = Protocol.parse(bytes)
        onDetection(frame != null)
        if (frame != null) onFrame(frame)
    }
}
