package com.scansfer.app.receive

import android.content.Context
import android.net.Uri
import com.scansfer.app.core.DataFrame
import com.scansfer.app.core.FountainDecoder
import com.scansfer.app.core.Frame
import com.scansfer.app.core.Manifest
import com.scansfer.app.core.Protocol
import com.scansfer.app.util.MediaStoreSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.zip.CRC32

enum class ReceiveStage { SEARCHING, RECEIVING, SAVING, DONE, FAILED }

data class ReceiverState(
    val stage: ReceiveStage = ReceiveStage.SEARCHING,
    val manifest: Manifest? = null,
    val blocksSolved: Int = 0,
    val blocksTotal: Int = 0,
    val progress: Float = 0f,
    val framesUsed: Int = 0,
    val framesSeen: Int = 0,
    /** Detections the decoder could not use: unreadable, foreign or duplicate. */
    val framesWasted: Int = 0,
    val bytesPerSecond: Double = 0.0,
    val etaSeconds: Long = -1,
    val savedUri: Uri? = null,
    val error: String? = null,
) {
    val isLocked: Boolean get() = manifest != null
}

/**
 * Collects frames from the camera and rebuilds the file.
 *
 * Frames arrive on the analyzer thread, so mutation is guarded; the UI only ever
 * reads the immutable snapshot in [state].
 */
class ReceiverEngine(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(ReceiverState())
    val state: StateFlow<ReceiverState> = _state.asStateFlow()

    private var manifest: Manifest? = null
    private var decoder: FountainDecoder? = null
    private var startedAt = 0L
    private var finishing = false

    /** Sliding window of (timestamp, blocksSolved) used for rate and ETA. */
    private val rateWindow = ArrayDeque<Pair<Long, Int>>()
    private val rateWindowMs = 6_000L

    @Synchronized
    fun onFrame(frame: Frame) {
        if (finishing) return
        when (frame.type) {
            Protocol.TYPE_MANIFEST -> acceptManifest(frame)
            Protocol.TYPE_DATA -> acceptData(frame)
        }
    }

    /**
     * Called for every barcode the camera resolves, whether or not it turned out
     * to be ours. Runs on the analyzer thread while the save coroutine may also
     * be touching state, so it updates through a compare-and-set.
     */
    fun onDetection(parsed: Boolean) {
        _state.update {
            it.copy(
                framesSeen = it.framesSeen + 1,
                framesWasted = if (parsed) it.framesWasted else it.framesWasted + 1,
            )
        }
    }

    private fun acceptManifest(frame: Frame) {
        if (manifest != null) return
        val parsed = Manifest.decode(frame.body) ?: return
        if (parsed.blockCount <= 0 || parsed.blockSize <= 0) return

        manifest = parsed
        decoder = FountainDecoder(parsed.blockCount, parsed.blockSize)
        startedAt = System.currentTimeMillis()
        rateWindow.clear()

        _state.update {
            it.copy(
                stage = ReceiveStage.RECEIVING,
                manifest = parsed,
                blocksTotal = parsed.blockCount,
            )
        }
    }

    private fun acceptData(frame: Frame) {
        val active = manifest ?: return
        val target = decoder ?: return
        val data = DataFrame.decode(frame.body) ?: return
        if (data.sessionId != active.sessionId) return

        val useful = target.offer(data.seed, data.payload)
        val solved = target.solvedCount

        val now = System.currentTimeMillis()
        rateWindow.addLast(now to solved)
        while (rateWindow.size > 1 && now - rateWindow.first().first > rateWindowMs) {
            rateWindow.removeFirst()
        }

        val bytesPerSecond = throughput(active.blockSize)
        val remaining = active.blockCount - solved
        val eta = if (bytesPerSecond > 1.0 && remaining > 0) {
            (remaining.toLong() * active.blockSize / bytesPerSecond).toLong()
        } else {
            -1
        }

        _state.update {
            it.copy(
                blocksSolved = solved,
                progress = solved.toFloat() / active.blockCount,
                framesUsed = if (useful) it.framesUsed + 1 else it.framesUsed,
                framesWasted = if (useful) it.framesWasted else it.framesWasted + 1,
                bytesPerSecond = bytesPerSecond,
                etaSeconds = eta,
            )
        }

        if (target.isComplete) finish(active, target)
    }

    private fun throughput(blockSize: Int): Double {
        if (rateWindow.size < 2) return 0.0
        val (firstAt, firstSolved) = rateWindow.first()
        val (lastAt, lastSolved) = rateWindow.last()
        val seconds = (lastAt - firstAt) / 1000.0
        if (seconds <= 0.25) return 0.0
        return (lastSolved - firstSolved) * blockSize / seconds
    }

    private fun finish(active: Manifest, target: FountainDecoder) {
        if (finishing) return
        finishing = true
        _state.update { it.copy(stage = ReceiveStage.SAVING, progress = 1f) }

        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val crc = CRC32()
                var written = 0
                var index = 0
                while (written < active.fileSize) {
                    val block = requireNotNull(target.block(index)) { "missing block $index" }
                    val take = minOf(block.size, active.fileSize - written)
                    crc.update(block, 0, take)
                    written += take
                    index++
                }
                check(crc.value.toInt() == active.fileCrc) {
                    "The rebuilt file failed its checksum"
                }
                MediaStoreSaver.save(
                    context = context,
                    decoder = target,
                    fileSize = active.fileSize,
                    displayName = active.fileName,
                    mimeType = active.mimeType,
                    kind = active.kind,
                )
            }

            _state.update { current ->
                result.fold(
                    onSuccess = { uri -> current.copy(stage = ReceiveStage.DONE, savedUri = uri) },
                    onFailure = { error ->
                        current.copy(
                            stage = ReceiveStage.FAILED,
                            error = error.message ?: "Could not save the file",
                        )
                    },
                )
            }
        }
    }

    /** Throws away everything and waits for a fresh manifest. */
    @Synchronized
    fun reset() {
        manifest = null
        decoder = null
        finishing = false
        rateWindow.clear()
        _state.value = ReceiverState()
    }
}
