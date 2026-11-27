package dev.cappyt.socialview.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditRssParserTest {
    private val browserUrl = "https://www.reddit.com/r/Android/comments/abc123/google_pixel_update/"

    @Test
    fun `a post and its comments are parsed from the feed`() {
        val details = RedditRssParser.parse(feed(), browserUrl, "abc123")!!

        assertEquals("Google Pixel update", details.title)
        assertEquals("Pixel update body", details.text)
        assertEquals("u/kbDL-", details.author)
        assertEquals("r/Android", details.handle)
        assertEquals(1, details.comments.size)
        assertEquals("u/bob", details.comments.single().author)
        assertEquals("First comment", details.comments.single().body)
        assertEquals(1_791_311_037L, details.createdAt)
        assertEquals(1_791_311_332L, details.comments.single().createdAt)
        assertTrue(details.images.isEmpty())
        assertNull(details.linkUrl)
    }

    @Test
    fun `a link post exposes the external destination`() {
        val body = feed(
            content = """
                &#32; submitted by &#32; &lt;a href="https://www.reddit.com/user/kbDL-"&gt; /u/kbDL- &lt;/a&gt; &lt;br/&gt; &lt;span&gt;&lt;a href="https://support.google.com/pixelphone/thread/471669543?hl=en&amp;amp;sjid=1"&gt;[link]&lt;/a&gt;&lt;/span&gt; &#32; &lt;span&gt;&lt;a href="https://www.reddit.com/r/Android/comments/abc123/"&gt;[comments]&lt;/a&gt;&lt;/span&gt;
            """.trimIndent(),
        )

        val details = RedditRssParser.parse(body, browserUrl, "abc123")!!

        assertEquals(
            "https://support.google.com/pixelphone/thread/471669543?hl=en&sjid=1",
            details.linkUrl,
        )
        assertNull(details.text)
    }

    @Test
    fun `the linked full image is preferred over the preview`() {
        val body = feed(
            """
            &lt;!-- SC_OFF --&gt;&lt;div class="md"&gt;&lt;p&gt;&lt;a href="https://i.redd.it/abc.jpg"&gt;&lt;img src="https://external-preview.redd.it/abc.jpg?width=320&amp;amp;format=pjpg&amp;amp;s=xyz" /&gt;&lt;/a&gt;&lt;/p&gt;&lt;/div&gt;
            """.trimIndent(),
        )

        val details = RedditRssParser.parse(body, browserUrl, "abc123")!!

        assertEquals(listOf("https://i.redd.it/abc.jpg"), details.images)
    }

    @Test
    fun `the preview image is used when there is no full image link`() {
        val body = feed(
            """
            &lt;!-- SC_OFF --&gt;&lt;div class="md"&gt;&lt;p&gt;&lt;img src="https://external-preview.redd.it/abc.jpg?width=320&amp;amp;format=pjpg&amp;amp;s=xyz" /&gt;&lt;/p&gt;&lt;/div&gt;
            """.trimIndent(),
        )

        val details = RedditRssParser.parse(body, browserUrl, "abc123")!!

        assertEquals(
            listOf("https://external-preview.redd.it/abc.jpg?width=320&format=pjpg&s=xyz"),
            details.images,
        )
    }

    @Test
    fun `the post entry is chosen by its t3 id`() {
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <category term="Android" label="r/Android"/>
              <entry>
                <author><name>/u/bob</name></author>
                <content type="html">&lt;div class="md"&gt;&lt;p&gt;Early comment&lt;/p&gt;&lt;/div&gt;</content>
                <id>t1_1</id>
                <link href="$browserUrl"/>
                <title>/u/bob on the post</title>
              </entry>
              <entry>
                <author><name>/u/alice</name></author>
                <content type="html">&lt;div class="md"&gt;&lt;p&gt;Post body&lt;/p&gt;&lt;/div&gt;</content>
                <id>t3_abc123</id>
                <link href="$browserUrl"/>
                <title>Real post</title>
              </entry>
            </feed>
        """.trimIndent()

        val details = RedditRssParser.parse(body, browserUrl, "abc123")!!

        assertEquals("Real post", details.title)
        assertEquals("u/alice", details.author)
        assertEquals(listOf("Early comment"), details.comments.map { it.body })
    }

    @Test
    fun `a feed without entries is not a native post`() {
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"></feed>
        """.trimIndent()

        assertNull(RedditRssParser.parse(body, browserUrl, "abc123"))
    }

    @Test
    fun `malformed xml is not a native post`() {
        assertNull(RedditRssParser.parse("<feed><entry>", browserUrl, "abc123"))
        assertNull(RedditRssParser.parse("not xml", browserUrl, "abc123"))
    }

    private fun feed(content: String = DEFAULT_CONTENT): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <category term="Android" label="r/Android"/>
          <entry>
            <author><name>/u/kbDL-</name></author>
            <content type="html">$content</content>
            <id>t3_abc123</id>
            <link href="$browserUrl"/>
            <published>2026-10-06T18:23:57+00:00</published>
            <title>Google Pixel update</title>
          </entry>
          <entry>
            <author><name>/u/bob</name></author>
            <content type="html">&lt;!-- SC_OFF --&gt;&lt;div class="md"&gt;&lt;p&gt;First comment&lt;/p&gt;&lt;/div&gt;</content>
            <id>t1_1</id>
            <link href="${browserUrl}def/"/>
            <published>2026-10-06T18:28:52+00:00</published>
            <title>/u/bob on Google Pixel update</title>
          </entry>
        </feed>
    """.trimIndent()

    private companion object {
        const val DEFAULT_CONTENT =
            "&lt;!-- SC_OFF --&gt;&lt;div class=\"md\"&gt;&lt;p&gt;Pixel update body&lt;/p&gt;" +
                "&lt;/div&gt;&lt;!-- SC_ON --&gt;"
    }
}