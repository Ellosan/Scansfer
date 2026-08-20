package com.scansfer.app.core

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Speed / reliability trade-off. Denser codes carry more bytes per frame but
 * need a steadier hand, better light and a closer camera.
 */
/**
 * Frame rates are divisors of 60 so each code lands on a whole number of display
 * refreshes; a code that changes mid-refresh tears and fails its checksum.
 */
enum class TransferProfile(
    val label: String,
    val tagline: String,
    val blockSize: Int,
    val fps: Int,
    val errorCorrection: ErrorCorrectionLevel,
) {
    STEADY(
        label = "Steady",
        tagline = "Chunky codes. Best for older phones or dim rooms.",
        blockSize = 400,
        fps = 10,
        errorCorrection = ErrorCorrectionLevel.Q,
    ),
    BALANCED(
        label = "Balanced",
        tagline = "The sweet spot for most phones.",
        blockSize = 1000,
        fps = 12,
        errorCorrection = ErrorCorrectionLevel.M,
    ),
    TURBO(
        label = "Turbo",
        tagline = "Dense and fast. Hold steady, keep the light good.",
        blockSize = 1800,
        fps = 15,
        errorCorrection = ErrorCorrectionLevel.L,
    ),
    ;

    /** Rough ceiling, before scan misses. Real throughput lands lower. */
    val bytesPerSecond: Int get() = blockSize * fps

    fun estimateSeconds(fileSize: Long): Long {
        // Measured end to end: fountain overhead plus a realistic share of frames
        // the camera never catches lands around 1.9x the raw byte count.
        return ((fileSize * 1.9) / bytesPerSecond).toLong().coerceAtLeast(1)
    }

    companion object {
        val DEFAULT = BALANCED
    }
}
