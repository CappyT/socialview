package dev.cappyt.socialview.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortLinkDocumentTest {
    @Test
    fun `og url is preferred for a facebook share page`() {
        val url = ShortLinkDocument.findPostUrl(
            """
            <html><head>
            <meta property="og:url" content="https://www.facebook.com/reel/2000381177287760/" />
            </head></html>
            """.trimIndent(),
        )

        assertEquals("https://www.facebook.com/reel/2000381177287760/", url)
    }

    @Test
    fun `content first og url is accepted`() {
        val url = ShortLinkDocument.findPostUrl(
            """
            <html><head>
            <meta content="https://www.instagram.com/p/ABC123/" property="og:url" />
            </head></html>
            """.trimIndent(),
        )

        assertEquals("https://www.instagram.com/p/ABC123/", url)
    }

    @Test
    fun `canonical link and canonical json are accepted`() {
        val link = ShortLinkDocument.findPostUrl(
            """
            <html><head>
            <link rel="canonical" href="https://www.facebook.com/3Demoniak/videos/best-print/2000381177287760/" />
            </head></html>
            """.trimIndent(),
        )
        assertEquals(
            "https://www.facebook.com/3Demoniak/videos/best-print/2000381177287760/",
            link,
        )

        val json = ShortLinkDocument.findPostUrl(
            """<script>{"canonical_url":"https:\/\/www.facebook.com\/reel\/123456\/"}</script>""",
        )
        assertEquals("https://www.facebook.com/reel/123456/", json)
    }

    @Test
    fun `html entities and escaped slashes are decoded`() {
        val url = ShortLinkDocument.findPostUrl(
            """
            <meta property="og:url"
                  content="https://www.facebook.com/permalink.php?story_fbid=1&amp;id=2" />
            """.trimIndent(),
        )

        assertEquals("https://www.facebook.com/permalink.php?story_fbid=1&id=2", url)
    }

    @Test
    fun `a reel id is found without metadata`() {
        val url = ShortLinkDocument.findPostUrl(
            """<script>{"target":"https:\/\/www.facebook.com\/reel\/2000381177287760\/"}</script>""",
        )

        assertEquals("https://www.facebook.com/reel/2000381177287760/", url)
    }

    @Test
    fun `unsupported hosts and login pages yield no post url`() {
        assertNull(
            ShortLinkDocument.findPostUrl(
                """<meta property="og:url" content="https://example.com/post/1" />""",
            ),
        )
        assertNull(
            ShortLinkDocument.findPostUrl(
                "<html><head><title>Log into Facebook</title></head></html>",
            ),
        )
    }
}