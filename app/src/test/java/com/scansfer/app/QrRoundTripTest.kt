package com.scansfer.app

import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.scansfer.app.core.DataFrame
import com.scansfer.app.core.FountainEncoder
import com.scansfer.app.core.Manifest
import com.scansfer.app.core.Protocol
import com.scansfer.app.core.QrMatrix
import com.scansfer.app.core.TransferProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The whole protocol rests on QR byte mode carrying arbitrary bytes untouched.
 * These tests push real frames through ZXing's encoder and decoder to prove the
 * bytes survive, and that every profile's frame actually fits in a symbol.
 */
class QrRoundTripTest {

    private fun bitMatrix(matrix: ByteMatrix): BitMatrix {
        val bits = BitMatrix(matrix.width, matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y).toInt() == 1) bits.set(x, y)
            }
        }
        return bits
    }

    private fun decodeBytes(frame: ByteArray, ec: ErrorCorrectionLevel): ByteArray {
        val result = Decoder().decode(bitMatrix(QrMatrix.encode(frame, ec)))
        val segments = assertNotNull(result.byteSegments)
        return result.byteSegments.reduce { a, b -> a + b }
    }

    @Test
    fun `every profile round trips a full data frame byte for byte`() {
        for (profile in TransferProfile.entries) {
            val payload = Random(profile.ordinal).nextBytes(profile.blockSize)
            val wire = DataFrame(sessionId = -12345, seed = 9001, payload = payload).encode()

            val decoded = decodeBytes(wire, profile.errorCorrection)
            assertArrayEquals("${profile.label} data frame mangled", wire, decoded)

            val parsed = assertNotNull(Protocol.parse(decoded))
            val frame = Protocol.parse(decoded)!!
            assertEquals(Protocol.TYPE_DATA, frame.type)
            assertArrayEquals(payload, DataFrame.decode(frame.body)!!.payload)
        }
    }

    @Test
    fun `manifest and data frames encode to the same QR version`() {
        for (profile in TransferProfile.entries) {
            val bodySize = Protocol.DATA_HEADER + profile.blockSize
            val manifest = Manifest(
                sessionId = 1, fileSize = 900_000, blockSize = profile.blockSize,
                blockCount = 1125, fileCrc = 7, fileName = "holiday-clip.mp4",
                mimeType = "video/mp4", durationMs = 8_000,
            ).encode(padTo = bodySize)
            val data = DataFrame(1, 0, ByteArray(profile.blockSize)).encode()

            assertEquals(data.size, manifest.size)
            assertEquals(
                "${profile.label} would resize the code mid-stream",
                QrMatrix.encode(data, profile.errorCorrection).width,
                QrMatrix.encode(manifest, profile.errorCorrection).width,
            )
        }
    }

    @Test
    fun `frames stay within the largest QR symbol`() {
        for (profile in TransferProfile.entries) {
            val width = QrMatrix.encode(
                DataFrame(1, 1, Random(1).nextBytes(profile.blockSize)).encode(),
                profile.errorCorrection,
            ).width
            // Version 40 is 177 modules; anything at or under that is encodable.
            assertTrue("${profile.label} needs $width modules", width <= 177)
        }
    }

    @Test
    fun `bytes with every possible value survive the round trip`() {
        // Byte 0x00 and the 0x80..0xFF range are where charset bugs show up.
        val payload = ByteArray(768) { (it % 256).toByte() }
        val wire = DataFrame(0, 0, payload).encode()
        assertArrayEquals(wire, decodeBytes(wire, ErrorCorrectionLevel.M))
    }

    @Test
    fun `real fountain symbols survive the round trip`() {
        val profile = TransferProfile.BALANCED
        val file = Random(4).nextBytes(40_000)
        val encoder = FountainEncoder(file, profile.blockSize)

        for (seed in listOf(0, 1, 7, encoder.blockCount, encoder.blockCount + 13, 99_999)) {
            val wire = DataFrame(5, seed, encoder.symbol(seed)).encode()
            val decoded = decodeBytes(wire, profile.errorCorrection)
            val frame = assertNotNull(Protocol.parse(decoded)).let { Protocol.parse(decoded)!! }
            val data = DataFrame.decode(frame.body)!!
            assertEquals(seed, data.seed)
            assertArrayEquals(encoder.symbol(seed), data.payload)
        }
    }
}
