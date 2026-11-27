package dev.cappyt.socialview.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramOEmbedParserTest {
    private val realBody = """
        {
          "version": "1.0",
          "title": "Telefonino gettato per terra",
          "author_name": "ilsecoloxix",
          "author_url": "https://www.instagram.com/ilsecoloxix",
          "author_id": 429150874,
          "media_id": "3935504429539881172_429150874",
          "provider_name": "Instagram",
          "type": "rich",
          "width": 658,
          "height": null,
          "thumbnail_url": "https://scontent-mxp1-1.cdninstagram.com/thumb.jpg",
          "thumbnail_width": 640,
          "thumbnail_height": 1137
        }
    """.trimIndent()

    @Test
    fun `official oembed response is parsed`() {
        val parsed = InstagramOEmbedParser.parse(realBody)!!
        assertEquals("Telefonino gettato per terra", parsed.caption)
        assertEquals("ilsecoloxix", parsed.authorName)
        assertEquals("@ilsecoloxix", parsed.handle)
        assertEquals("https://www.instagram.com/ilsecoloxix", parsed.authorUrl)
        assertEquals("https://scontent-mxp1-1.cdninstagram.com/thumb.jpg", parsed.thumbnailUrl)
        assertEquals("3935504429539881172_429150874", parsed.mediaId)
    }

    @Test
    fun `handle is derived from the author url when the name is missing`() {
        val body = """{"author_url":"https://www.instagram.com/ilsecoloxix/"}"""
        assertEquals("@ilsecoloxix", InstagramOEmbedParser.parse(body)!!.handle)
    }

    @Test
    fun `handle is derived from the author url before the display name`() {
        val body = """{"author_name":"Il Secolo XIX","author_url":"https://www.instagram.com/ilsecoloxix/"}"""
        assertEquals("@ilsecoloxix", InstagramOEmbedParser.parse(body)!!.handle)
    }

    @Test
    fun `missing fields and malformed json are handled`() {
        assertNull(InstagramOEmbedParser.parse("{}"))
        assertNull(InstagramOEmbedParser.parse("not json"))
        assertNull(InstagramOEmbedParser.parse(""))
    }

    @Test
    fun `private thumbnail schemes are dropped`() {
        val body = """{"title":"Caption","thumbnail_url":"http://example.com/thumb.jpg"}"""
        val parsed = InstagramOEmbedParser.parse(body)!!
        assertEquals("Caption", parsed.caption)
        assertNull(parsed.thumbnailUrl)
    }
}