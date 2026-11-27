package dev.cappyt.socialview.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbedNavigationTest {
    private val embedUrl = "https://www.instagram.com/reel/DaduG1us3DU/embed/captioned/"
    private val allowedHosts = setOf("www.instagram.com", "instagram.com")

    private fun decide(target: String) = EmbedNavigation.decide(
        target = target,
        embedUrl = embedUrl,
        allowedHosts = allowedHosts,
    )

    @Test
    fun `the embed document loads in the WebView`() {
        assertEquals(EmbedNavigation.Load, decide(embedUrl))
        assertEquals(
            EmbedNavigation.Load,
            decide("https://www.instagram.com/reel/DaduG1us3DU/embed/captioned?utm_source=ig_embed"),
        )
        assertEquals(
            EmbedNavigation.Load,
            decide("https://instagram.com/reel/DaduG1us3DU/embed/captioned/"),
        )
    }

    @Test
    fun `another path on an allowed host goes to the browser`() {
        assertEquals(
            EmbedNavigation.External("https://www.instagram.com/p/CxS-o4RrdEs/"),
            decide("https://www.instagram.com/p/CxS-o4RrdEs/"),
        )
        assertEquals(
            EmbedNavigation.External("https://www.instagram.com/accounts/login/"),
            decide("https://www.instagram.com/accounts/login/"),
        )
    }

    @Test
    fun `another host goes to the browser`() {
        assertEquals(
            EmbedNavigation.External("https://example.com/reel/DaduG1us3DU/embed/captioned/"),
            decide("https://example.com/reel/DaduG1us3DU/embed/captioned/"),
        )
    }

    @Test
    fun `http and non web links are classified`() {
        assertEquals(
            EmbedNavigation.External("http://www.instagram.com/reel/DaduG1us3DU/embed/captioned/"),
            decide("http://www.instagram.com/reel/DaduG1us3DU/embed/captioned/"),
        )
        assertEquals(
            EmbedNavigation.Unsupported("mailto:someone@example.com"),
            decide("mailto:someone@example.com"),
        )
        assertEquals(EmbedNavigation.Unsupported("not a url"), decide("not a url"))
    }

    @Test
    fun `a script wrapper embed sends post links to the browser`() {
        val wrapper = "data:text/html;charset=utf-8,%3Cscript%20src%3D%22https%3A%2F%2Fwww" +
            ".reddit.com%2Fcomments%2Fabc123%2F.embed%22%3E%3C%2Fscript%3E"

        assertEquals(
            EmbedNavigation.External("https://www.reddit.com/r/Android/comments/abc123/"),
            EmbedNavigation.decide(
                target = "https://www.reddit.com/r/Android/comments/abc123/",
                embedUrl = wrapper,
                allowedHosts = setOf("www.reddit.com"),
            ),
        )
    }

    @Test
    fun `a script wrapper embed sends the root link to the browser`() {
        val wrapper = "data:text/html;charset=utf-8,%3Cscript%20src%3D%22https%3A%2F%2Fwww" +
            ".reddit.com%2Fcomments%2Fabc123%2F.embed%22%3E%3C%2Fscript%3E"

        assertEquals(
            EmbedNavigation.External("https://www.reddit.com/"),
            EmbedNavigation.decide(
                target = "https://www.reddit.com/",
                embedUrl = wrapper,
                allowedHosts = setOf("www.reddit.com"),
            ),
        )
    }

    @Test
    fun `host matching ignores case`() {
        assertEquals(
            EmbedNavigation.Load,
            decide("https://WWW.INSTAGRAM.COM/reel/DaduG1us3DU/embed/captioned/"),
        )
    }
}