package dev.cappyt.socialview.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XOEmbedParserTest {
    private val realBody = """
        {"url":"https:\/\/x.com\/jack\/status\/20","author_name":"jack","author_url":"https:\/\/x.com\/jack","html":"\u003Cblockquote class=\"twitter-tweet\"\u003E\u003Cp lang=\"en\" dir=\"ltr\"\u003Ejust setting up my twttr\u003C\/p\u003E&mdash; jack (@jack) \u003Ca href=\"https:\/\/x.com\/jack\/status\/20?ref_src=twsrc%5Etfw\"\u003EMarch 21, 2006\u003C\/a\u003E\u003C\/blockquote\u003E\n\u003Cscript async src=\"https:\/\/platform.x.com\/widgets.js\" charset=\"utf-8\"\u003E\u003C\/script\u003E\n\n","width":550,"height":null,"type":"rich","cache_age":"3153600000","provider_name":"X","provider_url":"https:\/\/x.com","version":"1.0"}
    """.trimIndent()

    @Test
    fun `official oembed response is parsed`() {
        val parsed = XOEmbedParser.parse(realBody)!!
        assertEquals("jack", parsed.authorName)
        assertEquals("@jack", parsed.handle)
        assertEquals("just setting up my twttr", parsed.text)
    }

    @Test
    fun `entities links and line breaks are converted`() {
        val body = """
            {
              "author_name": "NASA",
              "author_url": "https://twitter.com/NASA",
              "html": "<blockquote><p lang=\"en\">Hello &amp; <a href=\"https://example.com\">world</a><br>second line</p>&mdash; NASA (@NASA)</blockquote>"
            }
        """.trimIndent()
        val parsed = XOEmbedParser.parse(body)!!
        assertEquals("NASA", parsed.authorName)
        assertEquals("@NASA", parsed.handle)
        assertEquals("Hello & world\nsecond line", parsed.text)
    }

    @Test
    fun `missing fields and malformed json are handled`() {
        val parsed = XOEmbedParser.parse("""{"html":""}""")!!
        assertNull(parsed.authorName)
        assertNull(parsed.handle)
        assertNull(parsed.text)
        assertNull(XOEmbedParser.parse("not json"))
    }
}