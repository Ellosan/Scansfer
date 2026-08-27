package com.scansfer.app.core

import java.security.MessageDigest

/**
 * Offline verification of premium unlock codes.
 *
 * The app ships only a table of truncated hashes, never the codes themselves,
 * so a valid code cannot be derived from the source. Checking one is a hash and
 * a binary search — no network, no server, nothing to be down.
 *
 * This is deliberately not tamper-proof. The source is public and MIT licensed,
 * so anyone willing to edit and rebuild can remove the check entirely. The point
 * is to make paying the easy path for ordinary users, not to make bypassing it
 * impossible, which is not achievable for open-source software.
 */
object Unlock {

    /** Bytes of SHA-256 kept per code. 128 bits, far beyond preimage reach. */
    const val DIGEST_BYTES = 16

    /**
     * Strips anything that is not a code character and upper-cases the rest, so
     * spacing, dashes and case never decide whether a valid code is accepted.
     */
    fun normalize(raw: String): String =
        raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    fun digest(code: String): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(normalize(code).toByteArray(Charsets.US_ASCII))
            .copyOf(DIGEST_BYTES)

    /**
     * @param table concatenated [DIGEST_BYTES]-byte digests in ascending order.
     */
    fun isValid(code: String, table: ByteArray): Boolean {
        if (normalize(code).isEmpty()) return false
        if (table.isEmpty() || table.size % DIGEST_BYTES != 0) return false

        val needle = digest(code)
        var low = 0
        var high = table.size / DIGEST_BYTES - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val cmp = compareAt(table, mid, needle)
            when {
                cmp == 0 -> return true
                cmp < 0 -> low = mid + 1
                else -> high = mid - 1
            }
        }
        return false
    }

    /** Unsigned comparison of the entry at [index] against [needle]. */
    private fun compareAt(table: ByteArray, index: Int, needle: ByteArray): Int {
        val offset = index * DIGEST_BYTES
        for (i in 0 until DIGEST_BYTES) {
            val a = table[offset + i].toInt() and 0xFF
            val b = needle[i].toInt() and 0xFF
            if (a != b) return a - b
        }
        return 0
    }

    /** Groups a normalized code back into the form shown to the user. */
    fun format(raw: String): String =
        normalize(raw).chunked(4).joinToString("-")
}
