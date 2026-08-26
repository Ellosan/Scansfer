package com.scansfer.app

import com.scansfer.app.core.Unlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The shipped table holds only hashes, so these build their own table from
 * codes minted here — no real unlock code appears in the repository.
 */
class UnlockTest {

    private val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    private fun mint(random: Random): String =
        (0 until 12).map { alphabet[random.nextInt(alphabet.length)] }
            .joinToString("").chunked(4).joinToString("-")

    private fun tableOf(codes: List<String>): ByteArray =
        codes.map { Unlock.digest(it) }
            .sortedWith { a, b ->
                a.indices.firstNotNullOfOrNull { i ->
                    ((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)).takeIf { it != 0 }
                } ?: 0
            }
            .fold(ByteArray(0)) { acc, d -> acc + d }

    @Test
    fun `every minted code is accepted and nothing else is`() {
        val random = Random(1)
        val issued = List(500) { mint(random) }
        val table = tableOf(issued)

        for (code in issued) {
            assertTrue("issued code $code was rejected", Unlock.isValid(code, table))
        }
        // Codes from the same alphabet that were never issued must not work.
        val forged = List(2_000) { mint(Random(9_000 + it)) }.filter { it !in issued }
        for (code in forged) {
            assertFalse("unissued code $code was accepted", Unlock.isValid(code, table))
        }
    }

    @Test
    fun `formatting differences never decide the outcome`() {
        val code = "TEST-TEST-TEST"
        val table = tableOf(listOf(code))
        val accepted = listOf(
            code,
            code.lowercase(),
            code.replace("-", ""),
            code.replace("-", " "),
            "  $code  ",
            code.replace("-", " – "),
            code.lowercase().replace("-", ""),
        )
        for (variant in accepted) {
            assertTrue("'$variant' should be accepted", Unlock.isValid(variant, table))
        }
    }

    @Test
    fun `a near miss is rejected`() {
        val code = "TEST-TEST-TEST"
        val table = tableOf(listOf(code))
        // One character different, in each position.
        for (i in 0 until 12) {
            val bare = Unlock.normalize(code).toCharArray()
            bare[i] = if (bare[i] == '7') '8' else '7'
            val near = String(bare)
            if (near == Unlock.normalize(code)) continue
            assertFalse("'$near' should be rejected", Unlock.isValid(near, table))
        }
    }

    @Test
    fun `empty and malformed input is rejected rather than crashing`() {
        val table = tableOf(listOf("TEST-TEST-TEST"))
        for (junk in listOf("", "   ", "----", "!!!", "\n\t")) {
            assertFalse("'$junk' should be rejected", Unlock.isValid(junk, table))
        }
        // A truncated or absent table must fail closed, not throw.
        assertFalse(Unlock.isValid("TEST-TEST-TEST", ByteArray(0)))
        assertFalse(Unlock.isValid("TEST-TEST-TEST", ByteArray(7)))
        assertFalse(Unlock.isValid("TEST-TEST-TEST", table.copyOf(table.size - 1)))
    }

    @Test
    fun `digests are truncated and codes reformat predictably`() {
        assertEquals(Unlock.DIGEST_BYTES, Unlock.digest("TEST-TEST-TEST").size)
        assertEquals("TEST-TEST-TEST", Unlock.format("testtesttest"))
        assertEquals("TEST-TEST-TEST", Unlock.format("  TEST TEST TEST "))
    }

    @Test
    fun `the shipped table is well formed`() {
        val blob = java.io.File("src/main/res/raw/unlock_codes.bin").readBytes()
        assertTrue("table is missing", blob.isNotEmpty())
        assertEquals("table is not a whole number of digests", 0, blob.size % Unlock.DIGEST_BYTES)

        // Ascending order is what makes the binary search correct.
        val entries = blob.toList().chunked(Unlock.DIGEST_BYTES)
        for (i in 1 until entries.size) {
            val prev = entries[i - 1]
            val curr = entries[i]
            val cmp = prev.indices.firstNotNullOfOrNull { j ->
                ((prev[j].toInt() and 0xFF) - (curr[j].toInt() and 0xFF)).takeIf { it != 0 }
            } ?: 0
            assertTrue("table is not sorted at entry $i", cmp < 0)
        }
    }
}
