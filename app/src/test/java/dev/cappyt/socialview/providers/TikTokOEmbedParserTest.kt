package dev.cappyt.socialview.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TikTokOEmbedParserTest {
    private val minimalBody = """
        {
          "version": "1.0",
          "type": "video",
          "width": "325",
          "height": "578",
          "html": "<iframe height=\"578\" width=\"325\" src=\"https://www.tiktok.com/player/v1/6718335390845095173?embed_from=oembed&amp;music_info=1&amp;description=1\" frameborder=\"0\"></iframe>",
          "provider_url": "https://www.tiktok.com",
          "provider_name": "TikTok",
          "embed_product_id": "6718335390845095173",
          "embed_type": "video"
        }
    """.trimIndent()

    @Test
    fun `current minimal response yields a player url`() {
        val parsed = TikTokOEmbedParser.parse(minimalBody)!!
        assertEquals(
            "https://www.tiktok.com/player/v1/6718335390845095173?embed_from=oembed&music_info=1&description=1",
            parsed.playerUrl,
        )
        assertNull(parsed.title)
        assertNull(parsed.authorName)
    }

    @Test
    fun `optional metadata is parsed when present`() {
        val body = """
            {
              "title": "Scramble up ur name",
              "author_name": "Tiff",
              "author_url": "https://www.tiktok.com/@scout2015",
              "thumbnail_url": "https://p16-sign-va.tiktokcdn.com/cover.jpg",
              "html": "<iframe src='https://www.tiktok.com/player/v1/123'></iframe>"
            }
        """.trimIndent()
        val parsed = TikTokOEmbedParser.parse(body)!!
        assertEquals("Scramble up ur name", parsed.title)
        assertEquals("Tiff", parsed.authorName)
        assertEquals("@scout2015", parsed.handle)
        assertEquals("https://p16-sign-va.tiktokcdn.com/cover.jpg", parsed.thumbnailUrl)
        assertEquals("https://www.tiktok.com/player/v1/123", parsed.playerUrl)
    }

    @Test
    fun `unexpected iframe host is rejected`() {
        val body = """{"html":"<iframe src=\"https://evil.example/player/v1/123\"></iframe>"}"""
        assertNull(TikTokOEmbedParser.parse(body)!!.playerUrl)
    }

    @Test
    fun `http iframe and malformed json are rejected`() {
        assertNull(
            TikTokOEmbedParser.parse("""{"html":"<iframe src=\"http://www.tiktok.com/player/v1/123\"></iframe>"}""")!!.playerUrl,
        )
        assertNull(TikTokOEmbedParser.parse("not json"))
    }

    @Test
    fun `non player tiktok path is rejected`() {
        val body = """{"html":"<iframe src=\"https://www.tiktok.com/login\"></iframe>"}"""
        assertNull(TikTokOEmbedParser.parse(body)!!.playerUrl)
    }
}