package com.scansfer.app.receive

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.scansfer.app.core.Frame
import com.scansfer.app.core.Protocol
import zxingcpp.BarcodeReader

/**
 * Pulls Scansfer frames out of the camera stream.
 *
 * zxing-cpp reads CameraX frames directly and hands back the raw byte payload,
 * which is exactly what the protocol needs — no charset round trip to get wrong.
 * Decoding is synchronous and fast enough to run inline on the analyzer thread;
 * CameraX drops any frames that arrive while we are busy, which the fountain code
 * absorbs as a slightly longer transfer rather than a failure.
 */
class QrAnalyzer(
    private val onFrame: (Frame) -> Unit,
    private val onDetection: (parsed: Boolean) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = BarcodeReader().apply {
        options.formats = setOf(BarcodeReader.Format.QR_CODE)
        // Only one code is ever on screen; stopping after the first saves a
        // full second pass over every frame.
        options.maxNumberOfSymbols = 1
        options.tryHarder = true
        // The code is upright, black on white, and dense enough that working at
        // full resolution matters — a downscaled pass loses module detail at the
        // version 31 symbols the Turbo profile emits.
        options.tryRotate = false
        options.tryInvert = false
        options.tryDownscale = false
        // Screens glare unevenly, which a local-average threshold handles far
        // better than a single global one.
        options.binarizer = BarcodeReader.Binarizer.LOCAL_AVERAGE
    }

    /** Decode time in milliseconds for the most recent frame, for diagnostics. */
    @Volatile
    var lastDecodeMs: Int = 0
        private set

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val results = reader.read(imageProxy)
            lastDecodeMs = reader.lastReadTime
            for (result in results) {
                val bytes = result.bytes
                if (bytes == null || bytes.isEmpty()) continue
                val frame = Protocol.parse(bytes)
                onDetection(frame != null)
                if (frame != null) onFrame(frame)
            }
        } catch (t: Throwable) {
            // A single unreadable frame must never take the analyzer down; the
            // next one is 60-odd milliseconds away.
        } finally {
            imageProxy.close()
        }
    }
}
