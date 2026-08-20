package com.scansfer.app.core

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder

/**
 * Frame bytes to QR modules. Deliberately free of Android types so the byte
 * round trip can be tested on the JVM.
 */
object QrMatrix {

    /** Modules of white space around the code. Below 2 many scanners struggle. */
    const val QUIET_ZONE = 2

    private val hints = mapOf(
        // ISO-8859-1 is a 1:1 byte<->char mapping, and it is ZXing's default for
        // byte mode, so the encoder emits no ECI header and the bytes come back
        // out of a decoder exactly as they went in.
        EncodeHintType.CHARACTER_SET to "ISO-8859-1",
        EncodeHintType.MARGIN to 0,
    )

    fun encode(frame: ByteArray, ecLevel: ErrorCorrectionLevel): ByteMatrix {
        val code = Encoder.encode(String(frame, Charsets.ISO_8859_1), ecLevel, hints)
        return requireNotNull(code.matrix) { "ZXing produced no matrix" }
    }
}
