package dev.cappyt.socialview.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostFormatTest {
    @Test
    fun `counts below one thousand are plain`() {
        assertEquals("0", PostFormat.compactCount(0))
        assertEquals("999", PostFormat.compactCount(999))
        assertEquals("-42", PostFormat.compactCount(-42))
        assertNull(PostFormat.compactCount(null))
    }

    @Test
    fun `thousands use a compact suffix`() {
        assertEquals("1k", PostFormat.compactCount(1_000))
        assertEquals("1.2k", PostFormat.compactCount(1_234))
        assertEquals("12k", PostFormat.compactCount(12_345))
        assertEquals("-1.2k", PostFormat.compactCount(-1_234))
    }

    @Test
    fun `millions and billions use a compact suffix`() {
        assertEquals("1m", PostFormat.compactCount(1_000_000))
        assertEquals("1.5m", PostFormat.compactCount(1_500_000))
        assertEquals("12m", PostFormat.compactCount(12_345_678))
        assertEquals("1b", PostFormat.compactCount(1_000_000_000))
    }

    @Test
    fun `relative age covers each boundary`() {
        val now = 1_800_000_000L
        assertEquals("now", PostFormat.relativeAge(now, now))
        assertEquals("now", PostFormat.relativeAge(now - 59, now))
        assertEquals("now", PostFormat.relativeAge(now + 30, now))
        assertEquals("1m", PostFormat.relativeAge(now - 60, now))
        assertEquals("59m", PostFormat.relativeAge(now - 3_599, now))
        assertEquals("1h", PostFormat.relativeAge(now - 3_600, now))
        assertEquals("23h", PostFormat.relativeAge(now - 86_399, now))
        assertEquals("1d", PostFormat.relativeAge(now - 86_400, now))
        assertEquals("29d", PostFormat.relativeAge(now - 2_591_999, now))
        assertEquals("1mo", PostFormat.relativeAge(now - 2_592_000, now))
        assertEquals("12mo", PostFormat.relativeAge(now - 31_535_999, now))
        assertEquals("1y", PostFormat.relativeAge(now - 31_536_000, now))
        assertNull(PostFormat.relativeAge(null, now))
    }

    @Test
    fun `link host drops the www prefix`() {
        assertEquals(
            "support.google.com",
            PostFormat.hostOf("https://support.google.com/pixelphone/thread/471669543"),
        )
        assertEquals("example.com", PostFormat.hostOf("https://www.example.com/a"))
        assertNull(PostFormat.hostOf(null))
        assertNull(PostFormat.hostOf("not a url"))
    }
}