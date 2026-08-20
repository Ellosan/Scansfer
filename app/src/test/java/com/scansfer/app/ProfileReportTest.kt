package com.scansfer.app

import com.scansfer.app.core.DataFrame
import com.scansfer.app.core.Protocol
import com.scansfer.app.core.QrMatrix
import com.scansfer.app.core.TransferProfile
import org.junit.Test
import kotlin.random.Random

/** Prints the shape of each profile so density choices stay visible in CI logs. */
class ProfileReportTest {

    @Test
    fun report() {
        println("profile   block  frame  ecc  modules  version  B/s   5MB estimate")
        for (p in TransferProfile.entries) {
            val wire = DataFrame(1, 1, Random(1).nextBytes(p.blockSize)).encode()
            val width = QrMatrix.encode(wire, p.errorCorrection).width
            val version = (width - 17) / 4
            println(
                "%-9s %5d  %5d  %-3s  %7d  %7d  %5d  %s".format(
                    p.label, p.blockSize, wire.size, p.errorCorrection, width, version,
                    p.bytesPerSecond, com.scansfer.app.util.Format.duration(
                        p.estimateSeconds(5L * 1024 * 1024),
                    ),
                ),
            )
        }
        println("frame overhead: ${Protocol.FRAME_OVERHEAD + Protocol.DATA_HEADER} bytes")
    }
}
