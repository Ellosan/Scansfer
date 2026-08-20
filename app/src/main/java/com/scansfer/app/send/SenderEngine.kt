package com.scansfer.app.send

import android.content.Context
import android.graphics.Bitmap
import com.scansfer.app.core.ByteSource
import com.scansfer.app.core.DataFrame
import com.scansfer.app.core.FountainEncoder
import com.scansfer.app.core.Manifest
import com.scansfer.app.core.Protocol
import com.scansfer.app.core.QrCodec
import com.scansfer.app.core.TransferProfile
import com.scansfer.app.util.VideoInfo
import com.scansfer.app.util.VideoSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

data class SenderState(
    val frame: Bitmap? = null,
    /** Bumped on every frame so Compose redraws even if a bitmap is reused. */
    val tick: Long = 0,
    val framesSent: Long = 0,
    val blockCount: Int = 0,
    /** How far through the current sweep of the file we are, 0..1. */
    val passProgress: Float = 0f,
    val passNumber: Int = 1,
    val elapsedMs: Long = 0,
    val paused: Boolean = false,
    val error: String? = null,
    val ready: Boolean = false,
)

/**
 * Drives the display side of a transfer: turns the picked video into an endless
 * stream of fountain-coded QR frames.
 *
 * There is no back channel, so the sender simply keeps looping. The receiver
 * stops when it has enough; the user then stops the sender.
 */
class SenderEngine(
    private val context: Context,
    private val video: VideoInfo,
    private val profile: TransferProfile,
) {

    private val _state = MutableStateFlow(SenderState())
    val state: StateFlow<SenderState> = _state.asStateFlow()

    /** Re-broadcast the manifest this often so a late receiver can still join. */
    private val manifestInterval = 24

    /**
     * Pause is written from the UI thread and read from the render loop, so it
     * lives outside the state snapshot rather than racing writes to it.
     */
    private val paused = AtomicBoolean(false)

    /**
     * Renders frames until cancelled. Runs on [Dispatchers.Default]; QR encoding
     * of a dense symbol costs a few milliseconds, which must stay off the UI
     * thread at 15 fps.
     */
    suspend fun run() = withContext(Dispatchers.Default) {
        var source: ByteSource? = null
        try {
            val opened = VideoSource.open(context, video.uri)
            source = opened

            val encoder = FountainEncoder(opened, profile.blockSize)
            val sessionId = Random.nextInt()
            val manifest = Manifest(
                sessionId = sessionId,
                fileSize = opened.size,
                blockSize = profile.blockSize,
                blockCount = encoder.blockCount,
                fileCrc = crcOf(opened),
                fileName = video.displayName,
                mimeType = video.mimeType,
                durationMs = video.durationMs,
            )
            // Every frame in a session is the same byte length, so the QR keeps a
            // constant version and never resizes mid-stream.
            val bodySize = Protocol.DATA_HEADER + profile.blockSize
            val manifestFrame = manifest.encode(padTo = bodySize)

            _state.update { it.copy(blockCount = encoder.blockCount, ready = true) }

            val frameNanos = 1_000_000_000L / profile.fps
            val startedAt = System.nanoTime()
            var nextDeadline = startedAt
            var seed = 0
            var framesSent = 0L
            var counter = 0

            while (coroutineContext.isActive) {
                if (paused.get()) {
                    delay(80)
                    nextDeadline = System.nanoTime()
                    continue
                }

                val wire = if (counter % manifestInterval == 0) {
                    manifestFrame
                } else {
                    DataFrame(sessionId, seed, encoder.symbol(seed)).encode().also { seed++ }
                }
                counter++

                val bitmap = QrCodec.render(wire, profile.errorCorrection)
                framesSent++

                _state.update {
                    it.copy(
                        frame = bitmap,
                        tick = framesSent,
                        framesSent = framesSent,
                        passProgress = (seed % encoder.blockCount).toFloat() / encoder.blockCount,
                        passNumber = seed / encoder.blockCount + 1,
                        elapsedMs = (System.nanoTime() - startedAt) / 1_000_000,
                    )
                }

                nextDeadline += frameNanos
                val sleepMs = (nextDeadline - System.nanoTime()) / 1_000_000
                if (sleepMs > 0) {
                    delay(sleepMs)
                } else {
                    // Rendering fell behind; resync rather than sprint to catch up.
                    nextDeadline = System.nanoTime()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            _state.update { it.copy(error = t.message ?: "Could not read that video") }
        } finally {
            source?.close()
        }
    }

    fun setPaused(value: Boolean) {
        paused.set(value)
        _state.update { it.copy(paused = value) }
    }

    fun togglePaused() = setPaused(!paused.get())

    private fun crcOf(source: ByteSource): Int {
        val crc = java.util.zip.CRC32()
        val buffer = ByteArray(64 * 1024)
        var offset = 0
        while (offset < source.size) {
            val take = minOf(buffer.size, source.size - offset)
            source.copyInto(offset, take, buffer)
            crc.update(buffer, 0, take)
            offset += take
        }
        return crc.value.toInt()
    }
}
