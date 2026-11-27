package dev.cappyt.socialview.core

import java.net.URI
import java.util.Locale

object PostFormat {
    /**
     * Reddit-style compact count: 999, 1.2k, 12k, 1.2m.
     */
    fun compactCount(value: Int?): String? = compactCount(value?.toLong())

    fun compactCount(value: Long?): String? {
        if (value == null) return null
        val sign = if (value < 0) "-" else ""
        val size = if (value == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(value)
        val text = when {
            size < 1_000 -> size.toString()
            size < 10_000 -> decimal(size, 1_000, "k")
            size < 1_000_000 -> "${size / 1_000}k"
            size < 10_000_000 -> decimal(size, 1_000_000, "m")
            size < 1_000_000_000 -> "${size / 1_000_000}m"
            size < 1_000_000_000_000 -> "${size / 1_000_000_000}b"
            else -> "${size / 1_000_000_000_000}t"
        }
        return sign + text
    }

    /**
     * Relative age in the Reddit style: now, 5m, 3h, 2d, 4mo, 1y.
     */
    fun relativeAge(
        createdAt: Long?,
        nowSeconds: Long = System.currentTimeMillis() / 1_000,
    ): String? {
        if (createdAt == null) return null
        val seconds = (nowSeconds - createdAt).coerceAtLeast(0)
        return when {
            seconds < 60 -> "now"
            seconds < 3_600 -> "${seconds / 60}m"
            seconds < 86_400 -> "${seconds / 3_600}h"
            seconds < 2_592_000 -> "${seconds / 86_400}d"
            seconds < 31_536_000 -> "${seconds / 2_592_000}mo"
            else -> "${seconds / 31_536_000}y"
        }
    }

    /**
     * Host shown on a link post, without the www prefix.
     */
    fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { URI(url).host }.getOrNull()
            ?.removePrefix("www.")
            ?.takeIf { it.isNotEmpty() }
    }

    private fun decimal(size: Long, unit: Long, suffix: String): String {
        val value = String.format(Locale.US, "%.1f", size.toDouble() / unit)
        return value.removeSuffix(".0") + suffix
    }
}