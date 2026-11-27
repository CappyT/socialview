package dev.cappyt.socialview.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbedSubresourceTest {
    private val script = "https://www.reddit.com/comments/abc123/.embed?depth=1&showmore=false"

    @Test
    fun `the required script matches while the query changes`() {
        assertTrue(
            EmbedSubresource.matches(
                "https://www.reddit.com/comments/abc123/.embed?depth=1&showmore=false",
                script,
            ),
        )
        assertTrue(
            EmbedSubresource.matches(
                "https://WWW.REDDIT.COM/comments/abc123/.embed",
                script,
            ),
        )
    }

    @Test
    fun `another script does not match`() {
        assertFalse(
            EmbedSubresource.matches(
                "https://www.reddit.com/comments/other/.embed",
                script,
            ),
        )
        assertFalse(
            EmbedSubresource.matches(
                "https://embed.reddit.com/comments/abc123/.embed",
                script,
            ),
        )
        assertFalse(EmbedSubresource.matches("data:text/javascript,", script))
    }

    @Test
    fun `the failure message names a transport or HTTP failure`() {
        assertEquals(
            "The required embed script returned HTTP 503.",
            EmbedSubresource.failureMessage(503),
        )
        assertEquals(
            "The required embed script did not load.",
            EmbedSubresource.failureMessage(),
        )
    }
}