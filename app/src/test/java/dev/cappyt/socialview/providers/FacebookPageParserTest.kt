package dev.cappyt.socialview.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FacebookPageParserTest {
    @Test
    fun `a reel page exposes caption author handle image and video`() {
        val page = FacebookPageParser.parse(reelPage)!!

        assertEquals(
            "https://www.facebook.com/3Demoniak/videos/best-print/2000381177287760/",
            page.canonicalUrl,
        )
        assertEquals("3Demoniak", page.author)
        assertEquals("@3Demoniak", page.handle)
        assertEquals("Best print EVER! Just add Remy.", page.caption)
        assertEquals("https://cdn.example/cover.jpg?oh=1&oe=2", page.imageUrl)
        assertEquals(
            "https://video.example/hd.mp4?_nc_cat=100&oh=00_AQ&oe=6AE84E14&efg=eyJ2%3D",
            page.videoUrl,
        )
    }

    @Test
    fun `statistics prefix is removed from the caption`() {
        val page = FacebookPageParser.parse(
            """
            <html><head>
            <meta property="og:title" content="22M views &#xb7; 367K reactions | Best print EVER! | 3Demoniak" />
            <meta property="og:url" content="https://www.facebook.com/3Demoniak/videos/best-print/2000381177287760/" />
            </head></html>
            """.trimIndent(),
        )!!

        assertEquals("3Demoniak", page.author)
        assertEquals("Best print EVER!", page.caption)
    }

    @Test
    fun `a photo page keeps its image and drops the reserved handle`() {
        val page = FacebookPageParser.parse(
            """
            <html><head>
            <meta property="og:title" content="A photo caption | Some Page" />
            <meta property="og:image" content="https://cdn.example/photo.jpg?oh=9&amp;oe=8" />
            <link rel="canonical" href="https://www.facebook.com/photo.php?fbid=111&amp;id=222" />
            </head></html>
            """.trimIndent(),
        )!!

        assertNull(page.author)
        assertNull(page.handle)
        assertEquals("A photo caption | Some Page", page.caption)
        assertEquals("https://cdn.example/photo.jpg?oh=9&oe=8", page.imageUrl)
        assertNull(page.videoUrl)
        assertEquals("https://www.facebook.com/photo.php?fbid=111&id=222", page.canonicalUrl)
    }

    @Test
    fun `the standard definition url is a fallback`() {
        val page = FacebookPageParser.parse(
            """
            <html><head>
            <meta property="og:title" content="Reel caption | Page" />
            <meta property="og:url" content="https://www.facebook.com/Page/videos/123/" />
            </head><body><script>
            {"browser_native_sd_url":"https:\/\/video.example\/sd.mp4?oh=1\u0026oe=2"}
            </script></body></html>
            """.trimIndent(),
        )!!

        assertEquals("Page", page.author)
        assertEquals("Reel caption", page.caption)
        assertEquals("https://video.example/sd.mp4?oh=1&oe=2", page.videoUrl)
    }

    @Test
    fun `a login or error page yields no page`() {
        assertNull(
            FacebookPageParser.parse(
                "<html><head><title>Error Facebook</title></head><body>Log in</body></html>",
            ),
        )
    }

    @Test
    fun `a login shell with generic metadata is not a native post`() {
        assertNull(
            FacebookPageParser.parse(
                """
                <html><head>
                <meta property="og:title" content="Log in to Facebook" />
                <meta property="og:image" content="https://cdn.example/logo.png" />
                <link rel="canonical" href="https://www.facebook.com/login/" />
                </head></html>
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `a canonical post id that differs from the request is rejected`() {
        assertNull(FacebookPageParser.parse(reelPage, "999"))
        assertEquals("3Demoniak", FacebookPageParser.parse(reelPage, "2000381177287760")!!.author)
    }

    @Test
    fun `a page without a handle keeps its media when the post id matches`() {
        val page = FacebookPageParser.parse(
            """
            <html><head>
            <meta property="og:title" content="Caption without a page suffix" />
            <meta property="og:description" content="Description fallback" />
            <meta property="og:image" content="https://cdn.example/image.jpg" />
            <link rel="canonical" href="https://www.facebook.com/watch/?v=555" />
            </head></html>
            """.trimIndent(),
            "555",
        )!!

        assertNull(page.author)
        assertNull(page.handle)
        assertEquals("Caption without a page suffix", page.caption)
        assertEquals("https://cdn.example/image.jpg", page.imageUrl)
    }

    @Test
    fun `a caption suffix that is not the canonical handle stays in the caption`() {
        val page = FacebookPageParser.parse(
            """
            <html><head>
            <meta property="og:title" content="A caption | Not A Page" />
            <meta property="og:image" content="https://cdn.example/image.jpg" />
            <link rel="canonical" href="https://www.facebook.com/3Demoniak/posts/123/" />
            </head></html>
            """.trimIndent(),
        )!!

        assertNull(page.author)
        assertEquals("@3Demoniak", page.handle)
        assertEquals("A caption | Not A Page", page.caption)
    }

    private val reelPage = """
        <html><head>
        <meta property="og:title" content="Best print EVER! Just add Remy. | 3Demoniak" />
        <meta property="og:description" content="Best print EVER! Just add Remy." />
        <meta property="og:url" content="https://www.facebook.com/3Demoniak/videos/best-print/2000381177287760/" />
        <meta property="og:image" content="https://cdn.example/cover.jpg?oh=1&amp;oe=2" />
        <link rel="canonical" href="https://www.facebook.com/3Demoniak/videos/best-print/2000381177287760/" />
        </head><body><script>
        {"browser_native_hd_url":"https:\/\/video.example\/hd.mp4?_nc_cat=100&oh=00_AQ&oe=6AE84E14&efg=eyJ2\u00253D"}
        </script></body></html>
    """.trimIndent()
}