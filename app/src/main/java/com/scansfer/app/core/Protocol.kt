package com.scansfer.app.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Wire format for a single QR frame.
 *
 * ```
 * offset  size  field
 * 0       3     magic "SXF"
 * 3       1     protocol version
 * 4       1     frame type (0 = manifest, 1 = data)
 * 5       2     body length (unsigned, big endian)
 * 7       n     body
 * 7+n     4     CRC32 over bytes [0, 7+n)
 * ```
 *
 * The frame is self delimiting and self verifying, which matters because some
 * barcode decoders hand back the payload with extra bytes glued to the front or
 * the back. [Frame.parse] scans a small window for the magic and then trusts the
 * CRC to reject anything that does not line up.
 */
object Protocol {
    val MAGIC = byteArrayOf(0x53, 0x58, 0x46) // "SXF"
    const val VERSION = 1

    const val TYPE_MANIFEST: Byte = 0
    const val TYPE_DATA: Byte = 1

    /** Bytes of framing overhead added to every payload. */
    const val FRAME_OVERHEAD = 11

    /** Bytes a data frame adds on top of the raw block: session id + seed. */
    const val DATA_HEADER = 8

    /** How many leading bytes we are willing to skip while hunting for the magic. */
    private const val MAGIC_SEARCH_WINDOW = 16

    fun build(type: Byte, body: ByteArray): ByteArray {
        require(body.size <= 0xFFFF) { "frame body too large: ${body.size}" }
        val out = ByteArray(FRAME_OVERHEAD + body.size)
        System.arraycopy(MAGIC, 0, out, 0, 3)
        out[3] = VERSION.toByte()
        out[4] = type
        out[5] = ((body.size ushr 8) and 0xFF).toByte()
        out[6] = (body.size and 0xFF).toByte()
        System.arraycopy(body, 0, out, 7, body.size)
        val crc = CRC32().apply { update(out, 0, 7 + body.size) }.value.toInt()
        writeInt(out, 7 + body.size, crc)
        return out
    }

    /**
     * Extracts a valid frame from [raw], or null when the bytes are not one of
     * ours (foreign QR code, corrupted read, torn frame).
     */
    fun parse(raw: ByteArray): Frame? {
        val limit = minOf(MAGIC_SEARCH_WINDOW, raw.size)
        for (offset in 0 until limit) {
            if (raw.size - offset < FRAME_OVERHEAD) break
            if (raw[offset] != MAGIC[0] || raw[offset + 1] != MAGIC[1] || raw[offset + 2] != MAGIC[2]) continue
            if (raw[offset + 3] != VERSION.toByte()) continue
            val bodyLen = ((raw[offset + 5].toInt() and 0xFF) shl 8) or (raw[offset + 6].toInt() and 0xFF)
            val end = offset + FRAME_OVERHEAD + bodyLen
            if (end > raw.size) continue
            val expected = readInt(raw, offset + 7 + bodyLen)
            val actual = CRC32().apply { update(raw, offset, 7 + bodyLen) }.value.toInt()
            if (expected != actual) continue
            val body = raw.copyOfRange(offset + 7, offset + 7 + bodyLen)
            return Frame(raw[offset + 4], body)
        }
        return null
    }

    fun writeInt(dst: ByteArray, at: Int, value: Int) {
        dst[at] = (value ushr 24).toByte()
        dst[at + 1] = (value ushr 16).toByte()
        dst[at + 2] = (value ushr 8).toByte()
        dst[at + 3] = value.toByte()
    }

    fun readInt(src: ByteArray, at: Int): Int =
        ((src[at].toInt() and 0xFF) shl 24) or
            ((src[at + 1].toInt() and 0xFF) shl 16) or
            ((src[at + 2].toInt() and 0xFF) shl 8) or
            (src[at + 3].toInt() and 0xFF)

    fun crc32(bytes: ByteArray): Int = CRC32().apply { update(bytes) }.value.toInt()
}

class Frame(val type: Byte, val body: ByteArray)

/**
 * Everything the receiver needs to know before it can make sense of data
 * frames. Re-broadcast regularly so a receiver can join a transfer already in
 * progress.
 */
data class Manifest(
    val sessionId: Int,
    val fileSize: Int,
    val blockSize: Int,
    val blockCount: Int,
    val fileCrc: Int,
    val fileName: String,
    val mimeType: String,
    val durationMs: Int,
) {
    /** Photo or video, derived from the MIME type rather than sent explicitly. */
    val kind: MediaKind get() = MediaKind.of(mimeType, fileName)

    /**
     * @param padTo pads the body so every frame in a session encodes to the same
     *   QR version. A manifest that shrank the symbol would make the code resize
     *   mid-stream, forcing the receiver's camera to refocus.
     */
    fun encode(padTo: Int = 0): ByteArray {
        val name = fileName.take(120).toByteArray(Charsets.UTF_8)
        val mime = mimeType.take(60).toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(22 + 1 + name.size + 1 + mime.size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(sessionId)
        buf.putInt(fileSize)
        buf.putShort(blockSize.toShort())
        buf.putInt(blockCount)
        buf.putInt(fileCrc)
        buf.putInt(durationMs)
        buf.put(name.size.toByte())
        buf.put(name)
        buf.put(mime.size.toByte())
        buf.put(mime)
        val body = buf.array().copyOf(maxOf(buf.position(), padTo))
        return Protocol.build(Protocol.TYPE_MANIFEST, body)
    }

    companion object {
        fun decode(body: ByteArray): Manifest? = runCatching {
            val buf = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN)
            val sessionId = buf.int
            val fileSize = buf.int
            val blockSize = buf.short.toInt() and 0xFFFF
            val blockCount = buf.int
            val fileCrc = buf.int
            val durationMs = buf.int
            val name = ByteArray(buf.get().toInt() and 0xFF).also { buf.get(it) }
            val mime = ByteArray(buf.get().toInt() and 0xFF).also { buf.get(it) }
            if (fileSize <= 0 || blockSize <= 0 || blockCount <= 0) return null
            Manifest(
                sessionId = sessionId,
                fileSize = fileSize,
                blockSize = blockSize,
                blockCount = blockCount,
                fileCrc = fileCrc,
                fileName = String(name, Charsets.UTF_8),
                mimeType = String(mime, Charsets.UTF_8),
                durationMs = durationMs,
            )
        }.getOrNull()
    }
}

/** A single fountain-coded symbol: [seed] identifies which blocks were mixed. */
class DataFrame(val sessionId: Int, val seed: Int, val payload: ByteArray) {
    fun encode(): ByteArray {
        val body = ByteArray(Protocol.DATA_HEADER + payload.size)
        Protocol.writeInt(body, 0, sessionId)
        Protocol.writeInt(body, 4, seed)
        System.arraycopy(payload, 0, body, Protocol.DATA_HEADER, payload.size)
        return Protocol.build(Protocol.TYPE_DATA, body)
    }

    companion object {
        fun decode(body: ByteArray): DataFrame? {
            if (body.size <= Protocol.DATA_HEADER) return null
            return DataFrame(
                sessionId = Protocol.readInt(body, 0),
                seed = Protocol.readInt(body, 4),
                payload = body.copyOfRange(Protocol.DATA_HEADER, body.size),
            )
        }
    }
}
