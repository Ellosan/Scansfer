package com.scansfer.app

import com.scansfer.app.core.DataFrame
import com.scansfer.app.core.FountainDecoder
import com.scansfer.app.core.FountainEncoder
import com.scansfer.app.core.Manifest
import com.scansfer.app.core.Protocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FountainTest {

    private fun payload(size: Int, seed: Int = 7): ByteArray = Random(seed).nextBytes(size)

    @Test
    fun `clean stream decodes in exactly blockCount frames`() {
        val data = payload(64_000)
        val encoder = FountainEncoder(data, blockSize = 800)
        val decoder = FountainDecoder(encoder.blockCount, 800)

        for (seed in 0 until encoder.blockCount) {
            decoder.offer(seed, encoder.symbol(seed))
        }

        assertTrue(decoder.isComplete)
        assertArrayEquals(data, decoder.assemble(data.size))
    }

    @Test
    fun `recovers through heavy frame loss`() {
        val lossRates = listOf(0.10, 0.30, 0.50, 0.70)
        for (loss in lossRates) {
            val data = payload(120_000, seed = (loss * 100).toInt())
            val encoder = FountainEncoder(data, blockSize = 800)
            val decoder = FountainDecoder(encoder.blockCount, 800)
            val drops = Random(99)

            var seed = 0
            var emitted = 0
            while (!decoder.isComplete && emitted < encoder.blockCount * 40) {
                if (drops.nextDouble() >= loss) {
                    decoder.offer(seed, encoder.symbol(seed))
                }
                seed++
                emitted++
            }

            assertTrue("failed to decode at loss=$loss", decoder.isComplete)
            assertArrayEquals(data, decoder.assemble(data.size))

            // What the user actually waits on is frames shown, not frames caught.
            // A perfect erasure code would need blockCount / (1 - loss); LT coding
            // costs roughly 40% on top of that, so hold the line at 1.8x ideal.
            val ideal = encoder.blockCount / (1 - loss)
            assertTrue(
                "emitted $emitted frames at loss=$loss, ideal is ${ideal.toInt()}",
                emitted < ideal * 1.8,
            )
        }
    }

    @Test
    fun `decodes when frames arrive shuffled and duplicated`() {
        val data = payload(40_000)
        val encoder = FountainEncoder(data, blockSize = 512)
        val decoder = FountainDecoder(encoder.blockCount, 512)

        val seeds = (0 until encoder.blockCount * 2).toMutableList()
        seeds.addAll(seeds.take(40)) // duplicates, as a real camera produces
        seeds.shuffle(Random(3))

        for (seed in seeds) {
            decoder.offer(seed, encoder.symbol(seed))
            if (decoder.isComplete) break
        }

        assertTrue(decoder.isComplete)
        assertArrayEquals(data, decoder.assemble(data.size))
    }

    @Test
    fun `handles files smaller than one block and non-multiple sizes`() {
        for (size in listOf(1, 17, 511, 512, 513, 4097)) {
            val data = payload(size, seed = size)
            val encoder = FountainEncoder(data, blockSize = 512)
            val decoder = FountainDecoder(encoder.blockCount, 512)
            var seed = 0
            while (!decoder.isComplete && seed < 5000) {
                decoder.offer(seed, encoder.symbol(seed))
                seed++
            }
            assertTrue("size=$size did not decode", decoder.isComplete)
            assertArrayEquals("size=$size mismatch", data, decoder.assemble(size))
        }
    }

    @Test
    fun `frames survive a full encode decode round trip`() {
        val original = DataFrame(sessionId = 0x1234ABCD, seed = 42, payload = payload(800))
        val wire = original.encode()
        val frame = Protocol.parse(wire)
        assertNotNull(frame)
        assertEquals(Protocol.TYPE_DATA, frame!!.type)
        val decoded = DataFrame.decode(frame.body)!!
        assertEquals(0x1234ABCD, decoded.sessionId)
        assertEquals(42, decoded.seed)
        assertArrayEquals(original.payload, decoded.payload)
    }

    @Test
    fun `frames are found despite leading and trailing junk`() {
        val wire = DataFrame(1, 2, payload(64)).encode()
        val noisy = byteArrayOf(0x40, 0x1F, 0x00) + wire + byteArrayOf(0x00, 0x00, 0x11)
        val frame = Protocol.parse(noisy)
        assertNotNull(frame)
        assertArrayEquals(wire.copyOfRange(7, wire.size - 4), frame!!.body)
    }

    @Test
    fun `corrupted frames are rejected`() {
        val wire = DataFrame(1, 2, payload(64)).encode()
        for (index in wire.indices) {
            val broken = wire.copyOf()
            broken[index] = (broken[index].toInt() xor 0x5A).toByte()
            val parsed = Protocol.parse(broken)
            if (parsed != null) {
                // Only acceptable if the flipped byte still yields a consistent frame.
                assertArrayEquals(wire.copyOfRange(7, wire.size - 4), parsed.body)
            }
        }
        assertNull(Protocol.parse(ByteArray(40) { 0x33 }))
        assertNull(Protocol.parse("https://example.com/some-other-qr".toByteArray()))
    }

    @Test
    fun `manifest round trips and pads to a fixed size`() {
        val manifest = Manifest(
            sessionId = 77,
            fileSize = 1_234_567,
            blockSize = 800,
            blockCount = 1544,
            fileCrc = -42,
            fileName = "sunset clip.mp4",
            mimeType = "video/mp4",
            durationMs = 12_500,
        )
        val wire = manifest.encode(padTo = 808)
        assertEquals(Protocol.FRAME_OVERHEAD + 808, wire.size)
        val frame = Protocol.parse(wire)!!
        assertEquals(Protocol.TYPE_MANIFEST, frame.type)
        assertEquals(manifest, Manifest.decode(frame.body))
    }

    @Test
    fun `end to end through a lossy simulated camera`() {
        val data = payload(250_000, seed = 5)
        val blockSize = 800
        val encoder = FountainEncoder(data, blockSize)
        val manifest = Manifest(
            sessionId = 1, fileSize = data.size, blockSize = blockSize,
            blockCount = encoder.blockCount, fileCrc = Protocol.crc32(data),
            fileName = "clip.mp4", mimeType = "video/mp4", durationMs = 4000,
        )

        var decoder: FountainDecoder? = null
        var seen: Manifest? = null
        val noise = Random(11)
        var seed = 0
        var frames = 0

        while (frames < encoder.blockCount * 6) {
            val wire = if (frames % 32 == 0) {
                manifest.encode(padTo = Protocol.DATA_HEADER + blockSize)
            } else {
                DataFrame(1, seed, encoder.symbol(seed)).encode().also { seed++ }
            }
            frames++

            // The camera misses a quarter of frames and garbles a few more.
            if (noise.nextDouble() < 0.25) continue
            val received = wire.copyOf()
            if (noise.nextDouble() < 0.05) {
                received[noise.nextInt(received.size)] =
                    (received[noise.nextInt(received.size)].toInt() xor 0xFF).toByte()
            }

            val parsed = Protocol.parse(received) ?: continue
            when (parsed.type) {
                Protocol.TYPE_MANIFEST -> if (seen == null) {
                    seen = Manifest.decode(parsed.body)
                    seen?.let { decoder = FountainDecoder(it.blockCount, it.blockSize) }
                }
                Protocol.TYPE_DATA -> {
                    val df = DataFrame.decode(parsed.body) ?: continue
                    decoder?.offer(df.seed, df.payload)
                }
            }
            if (decoder?.isComplete == true) break
        }

        val m = assertNotNull(seen).let { seen!! }
        val out = decoder!!.assemble(m.fileSize)
        assertNotNull(out)
        assertArrayEquals(data, out)
        assertEquals(m.fileCrc, Protocol.crc32(out!!))
    }
}
