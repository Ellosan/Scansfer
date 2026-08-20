package com.scansfer.app.util

import java.util.Locale
import kotlin.math.roundToInt

object Format {

    fun bytes(value: Long): String = when {
        value < 1024 -> "$value B"
        value < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", value / 1024.0)
        value < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", value / (1024.0 * 1024))
        else -> String.format(Locale.US, "%.2f GB", value / (1024.0 * 1024 * 1024))
    }

    fun rate(bytesPerSecond: Double): String = when {
        bytesPerSecond < 1024 -> "${bytesPerSecond.roundToInt()} B/s"
        else -> String.format(Locale.US, "%.1f KB/s", bytesPerSecond / 1024.0)
    }

    /** Compact and human: "12s", "3m 40s", "1h 12m". */
    fun duration(seconds: Long): String {
        if (seconds < 0) return "--"
        val s = seconds % 60
        val m = (seconds / 60) % 60
        val h = seconds / 3600
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    fun clock(millis: Int): String {
        val total = millis / 1000
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
    }

    fun percent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100).roundToInt()}%"
}
