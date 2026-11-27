package dev.cappyt.socialview.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextTest {
    @Test
    fun `named and numeric entities are decoded`() {
        assertEquals(
            "A & B 'C' \uD83D\uDE00 \u2014",
            HtmlText.decode("A &amp; B &#39;C&#39; &#x1F600; &mdash;"),
        )
    }

    @Test
    fun `unknown entity is preserved`() {
        assertEquals("&unknown;", HtmlText.decode("&unknown;"))
    }

    @Test
    fun `tags and breaks become plain text`() {
        val text = HtmlText.toPlainText("<p>Hello<br>world</p><p>Two &amp; three</p>")
        assertTrue(text, text.contains("Hello\nworld"))
        assertTrue(text, text.contains("Two & three"))
    }

    @Test
    fun `first paragraph ignores the attribution`() {
        val html = """<blockquote class="twitter-tweet"><p lang="en">Tweet body &amp; link</p>&mdash; jack (@jack) <a href="https://x.com/jack/status/20">March 21, 2006</a></blockquote>"""
        assertEquals("Tweet body & link", HtmlText.firstParagraph(html))
    }

    @Test
    fun `missing paragraph returns null`() {
        assertNull(HtmlText.firstParagraph("<blockquote>no paragraph</blockquote>"))
    }
}