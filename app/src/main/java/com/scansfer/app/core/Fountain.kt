package com.scansfer.app.core

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Luby-transform fountain coding.
 *
 * A camera pointed at a flickering screen misses frames constantly, and there is
 * no back channel to ask for a retransmission. So instead of numbering blocks
 * 1..N and hoping, the sender emits an endless stream of symbols; the receiver
 * collects *any* slightly-larger-than-N subset and reconstructs the file.
 *
 * The first [blockCount] seeds are systematic (one symbol per raw block), so a
 * clean read finishes in exactly N frames. Everything after that is a random XOR
 * mixture that plugs whatever holes are left.
 */
object Fountain {

    /** Beyond this degree, rejection sampling stops being the cheap option. */
    private const val REJECTION_SAMPLING_LIMIT = 32

    /**
     * Which source blocks are mixed into the symbol identified by [seed].
     * Encoder and decoder both call this; it must stay bit-for-bit deterministic.
     */
    fun indicesFor(seed: Int, blockCount: Int, degrees: DegreeDistribution): IntArray {
        if (blockCount <= 1) return intArrayOf(0)
        if (seed < blockCount) return intArrayOf(seed)

        val random = Random(seed.toLong() * 6364136223846793005L + 1442695040888963407L)
        val degree = degrees.sample(random.nextDouble()).coerceIn(1, blockCount)

        if (degree <= REJECTION_SAMPLING_LIMIT) {
            val picked = IntArray(degree)
            var count = 0
            while (count < degree) {
                val candidate = random.nextInt(blockCount)
                var duplicate = false
                for (i in 0 until count) {
                    if (picked[i] == candidate) {
                        duplicate = true
                        break
                    }
                }
                if (!duplicate) picked[count++] = candidate
            }
            return picked
        }

        // Partial Fisher-Yates for the rare high-degree symbol.
        val pool = IntArray(blockCount) { it }
        for (i in 0 until degree) {
            val j = i + random.nextInt(blockCount - i)
            val tmp = pool[i]
            pool[i] = pool[j]
            pool[j] = tmp
        }
        return pool.copyOf(degree)
    }
}

/**
 * Robust soliton distribution over degrees 1..k, precomputed as a cumulative
 * table so sampling is a binary search.
 */
class DegreeDistribution(private val k: Int, c: Double = 0.05, delta: Double = 0.05) {

    private val cumulative: DoubleArray = DoubleArray(k + 1)

    init {
        if (k <= 2) {
            for (d in 1..k) cumulative[d] = d.toDouble() / k
        } else {
            val r = max(1.0, c * ln(k / delta) * sqrt(k.toDouble()))
            val pivot = min(k, max(1, floor(k / r).toInt()))

            val weights = DoubleArray(k + 1)
            weights[1] = 1.0 / k
            for (d in 2..k) weights[d] = 1.0 / (d.toDouble() * (d - 1).toDouble())

            for (d in 1 until pivot) weights[d] += r / (d.toDouble() * k)
            weights[pivot] += r * ln(r / delta) / k

            var total = 0.0
            for (d in 1..k) total += weights[d]

            var acc = 0.0
            for (d in 1..k) {
                acc += weights[d] / total
                cumulative[d] = acc
            }
        }
        cumulative[k] = 1.0
    }

    fun sample(u: Double): Int {
        var low = 1
        var high = k
        while (low < high) {
            val mid = (low + high) ushr 1
            if (u <= cumulative[mid]) high = mid else low = mid + 1
        }
        return low
    }
}

/**
 * Random-access view over the bytes being sent. Backed by a memory mapping in
 * the app so a large video never has to sit on the heap.
 */
interface ByteSource {
    val size: Int

    fun copyInto(offset: Int, length: Int, dst: ByteArray)

    fun close() {}
}

class ArrayByteSource(private val bytes: ByteArray) : ByteSource {
    override val size: Int get() = bytes.size

    override fun copyInto(offset: Int, length: Int, dst: ByteArray) {
        System.arraycopy(bytes, offset, dst, 0, length)
    }
}

/** Slices a file into fixed-size blocks and mixes them on demand. */
class FountainEncoder(private val source: ByteSource, val blockSize: Int) {

    constructor(bytes: ByteArray, blockSize: Int) : this(ArrayByteSource(bytes), blockSize)

    val blockCount: Int = maxOf(1, (source.size + blockSize - 1) / blockSize)
    private val degrees = DegreeDistribution(blockCount)
    private val scratch = ByteArray(blockSize)

    fun symbol(seed: Int): ByteArray {
        val out = ByteArray(blockSize)
        for (index in Fountain.indicesFor(seed, blockCount, degrees)) {
            val start = index * blockSize
            val length = min(blockSize, source.size - start)
            if (length <= 0) continue
            source.copyInto(start, length, scratch)
            for (i in 0 until length) {
                out[i] = (out[i].toInt() xor scratch[i].toInt()).toByte()
            }
        }
        return out
    }
}

/**
 * Peeling decoder. Feed it symbols in any order, with any amount of loss; once
 * [isComplete] flips, [assemble] returns the original bytes.
 */
class FountainDecoder(val blockCount: Int, private val blockSize: Int) {

    private class Symbol(val data: ByteArray, val remaining: MutableSet<Int>) {
        var retired = false
    }

    private val degrees = DegreeDistribution(blockCount)
    private val solved = arrayOfNulls<ByteArray>(blockCount)
    private val pending = HashMap<Int, MutableList<Symbol>>()
    private val seenSeeds = HashSet<Int>()

    var solvedCount: Int = 0
        private set

    /** Symbols accepted, including ones that only helped indirectly. */
    var symbolsAccepted: Int = 0
        private set

    val isComplete: Boolean get() = solvedCount == blockCount

    val progress: Float get() = if (blockCount == 0) 0f else solvedCount.toFloat() / blockCount

    /**
     * @return true when this symbol was new information (i.e. worth counting
     *   towards progress), false when it was a duplicate or fully redundant.
     */
    fun offer(seed: Int, payload: ByteArray): Boolean {
        if (isComplete) return false
        if (payload.size != blockSize) return false
        if (!seenSeeds.add(seed)) return false

        symbolsAccepted++

        val data = payload.copyOf()
        val remaining = HashSet<Int>()
        for (index in Fountain.indicesFor(seed, blockCount, degrees)) {
            val known = solved[index]
            if (known != null) {
                xorInto(data, known)
            } else if (!remaining.add(index)) {
                // The same block twice cancels itself out.
                remaining.remove(index)
            }
        }

        if (remaining.isEmpty()) return false

        val symbol = Symbol(data, remaining)
        if (remaining.size == 1) {
            resolve(remaining.first(), symbol)
        } else {
            for (index in remaining) {
                pending.getOrPut(index) { ArrayList(2) }.add(symbol)
            }
        }
        return true
    }

    private fun resolve(startIndex: Int, startSymbol: Symbol) {
        val ripple = ArrayDeque<Int>()
        solved[startIndex] = startSymbol.data
        solvedCount++
        startSymbol.retired = true
        ripple.addLast(startIndex)

        while (ripple.isNotEmpty()) {
            val index = ripple.removeFirst()
            val block = solved[index] ?: continue
            val dependents = pending.remove(index) ?: continue
            for (symbol in dependents) {
                if (symbol.retired) continue
                if (!symbol.remaining.remove(index)) continue
                xorInto(symbol.data, block)
                if (symbol.remaining.size == 1) {
                    val last = symbol.remaining.first()
                    symbol.retired = true
                    if (solved[last] == null) {
                        solved[last] = symbol.data
                        solvedCount++
                        ripple.addLast(last)
                    }
                } else if (symbol.remaining.isEmpty()) {
                    symbol.retired = true
                }
            }
        }
    }

    /** The recovered block at [index], or null while it is still unknown. */
    fun block(index: Int): ByteArray? = solved.getOrNull(index)

    fun assemble(fileSize: Int): ByteArray? {
        if (!isComplete) return null
        val out = ByteArray(fileSize)
        for (index in 0 until blockCount) {
            val block = solved[index] ?: return null
            val start = index * blockSize
            val length = min(blockSize, fileSize - start)
            if (length <= 0) break
            System.arraycopy(block, 0, out, start, length)
        }
        return out
    }

    private fun xorInto(target: ByteArray, other: ByteArray) {
        for (i in target.indices) {
            target[i] = (target[i].toInt() xor other[i].toInt()).toByte()
        }
    }
}
