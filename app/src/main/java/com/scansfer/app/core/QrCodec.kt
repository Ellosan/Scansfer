package com.scansfer.app.core

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Turns frame bytes into a bitmap, one pixel per QR module.
 *
 * The bitmap is deliberately tiny; the UI scales it up with nearest-neighbour
 * filtering, which keeps the module edges razor sharp and costs nothing per
 * frame.
 */
object QrCodec {

    fun render(frame: ByteArray, ecLevel: ErrorCorrectionLevel): Bitmap {
        val matrix = QrMatrix.encode(frame, ecLevel)
        val quiet = QrMatrix.QUIET_ZONE
        val size = matrix.width + quiet * 2

        val pixels = IntArray(size * size) { Color.WHITE }
        for (y in 0 until matrix.height) {
            val row = (y + quiet) * size
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y).toInt() == 1) {
                    pixels[row + x + quiet] = Color.BLACK
                }
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
