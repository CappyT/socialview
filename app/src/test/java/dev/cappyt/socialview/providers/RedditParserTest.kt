package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.PostMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditParserTest {
    private val browserUrl = "https://www.reddit.com/r/Android/comments/abc123/"

    private fun listing(post: String, comments: List<String> = emptyList()): String = """
        [
          {"kind":"Listing","data":{"children":[{"kind":"t3","data":$post}]}},
          {"kind":"Listing","data":{"children":[${comments.joinToString(",")}]}}
        ]
    """.trimIndent()

    private fun comment(author: String, body: String, score: Int = 1): String =
        """{"kind":"t1","data":{"author":"$author","body":"$body","score":$score}}"""

    @Test
    fun `text post and comments are parsed`() {
        val body = listing(
            """{"id":"abc123","subreddit":"Android","subreddit_name_prefixed":"r/Android","author":"alice","title":"Hello Reddit","selftext":"Body text","is_self":true,"permalink":"/r/Android/comments/abc123/hello_reddit/"}""",
            listOf(comment("bob", "Nice post", 7)),
        )
        val post = RedditParser.parse(body, browserUrl)
        assertEquals(Platform.REDDIT, post.platform)
        assertEquals(browserUrl, post.browserUrl)
        assertEquals("u/alice", post.author)
        assertEquals("r/Android", post.handle)
        assertEquals("Hello Reddit", post.title)
        assertEquals("Body text", post.text)
        assertEquals(1, post.comments.size)
        assertEquals("u/bob", post.comments[0].author)
        assertEquals("Nice post", post.comments[0].body)
        assertEquals(7, post.comments[0].score)
        assertNull(post.embed)
    }

    @Test
    fun `image post returns only the direct image`() {
        val body = listing(
            """{"id":"abc123","author":"alice","title":"Photo","is_self":false,"post_hint":"image","url_overridden_by_dest":"https://i.redd.it/abc.jpg","preview":{"images":[{"source":{"url":"https://preview.redd.it/abc.jpg?width=640&amp;format=pjpg"}}]}}""",
        )
        val post = RedditParser.parse(body, browserUrl)
        assertEquals(listOf("https://i.redd.it/abc.jpg"), post.images)
    }

    @Test
    fun `image post without a direct url falls back to the preview image`() {
        val body = listing(
            """{"id":"abc123","author":"alice","title":"Photo","is_self":false,"post_hint":"image","preview":{"images":[{"source":{"url":"https://preview.redd.it/abc.jpg?width=640&amp;format=pjpg"}}]}}""",
        )
        val post = RedditParser.parse(body, browserUrl)
        assertEquals(
            listOf("https://preview.redd.it/abc.jpg?width=640&format=pjpg"),
            post.images,
        )
    }

    @Test
    fun `gallery returns each valid media item`() {
        val body = listing(
            """{"id":"abc123","author":"alice","title":"Gallery","is_self":false,"is_gallery":true,"gallery_data":{"items":[{"media_id":"m1"},{"media_id":"m2"},{"media_id":"missing"}]},"media_metadata":{"m1":{"status":"valid","s":{"u":"https://preview.redd.it/one.jpg"}},"m2":{"status":"valid","s":{"u":"https://preview.redd.it/two.jpg"}}}}""",
        )
        val post = RedditParser.parse(body, browserUrl)
        assertEquals(
            listOf("https://preview.redd.it/one.jpg", "https://preview.redd.it/two.jpg"),
            post.images,
        )
    }

    @Test
    fun `video post returns a preview image and native hls video`() {
        val body = listing(
            """{"id":"abc123","author":"alice","title":"Cat","is_self":false,"is_video":true,"permalink":"/r/aww/comments/abc123/cat/","preview":{"images":[{"source":{"url":"https://preview.redd.it/thumb.jpg"}}]},"secure_media":{"reddit_video":{"hls_url":"https://v.redd.it/abc/HLSPlaylist.m3u8?x=1\u0026y=2","fallback_url":"https://v.redd.it/abc/DASH_720.mp4","width":1920,"height":1080,"duration":42}}}""",
        )
        val post = RedditParser.parse(body, browserUrl)
        assertEquals(listOf("https://preview.redd.it/thumb.jpg"), post.images)
        assertEquals(1, post.media.size)
        assertEquals(PostMediaType.VIDEO, post.media.single().type)
        assertEquals("https://v.redd.it/abc/HLSPlaylist.m3u8?x=1&y=2", post.media.single().url)
        assertEquals(1920, post.media.single().width)
        assertEquals(1080, post.media.single().height)
        assertEquals(42.0, post.media.single().durationSeconds!!, 0.0)
        assertNull(post.embed)
    }

    @Test
    fun `video without hls uses the mp4 fallback and no embed`() {
        val body = listing(
            """{"id":"abc123","author":"alice","title":"Cat","is_video":true,"permalink":"/r/aww/comments/abc123/cat/","media":{"reddit_video":{"fallback_url":"https://v.redd.it/abc/DASH_720.mp4"}}}""",
        )
        val post = RedditParser.parse(body, browserUrl)
        assertEquals("https://v.redd.it/abc/DASH_720.mp4", post.media.single().url)
        assertNull(post.embed)
    }

    @Test
    fun `post metadata is parsed`() {
        val body = listing(
            """{"id":"abc123","author":"alice","title":"Title","created_utc":1760000000.5,"score":1234,"num_comments":42,"link_flair_text":"Discussion"}""",
        )
        val post = RedditParser.parse(body, browserUrl)
        assertEquals(1_760_000_000L, post.createdAt)
        assertEquals(1234, post.score)
        assertEquals(42, post.commentCount)
        assertEquals("Discussion", post.flair)
    }

    @Test
    fun `link post exposes the external url`() {
        val body = listing(
            """{"id":"abc123","author":"alice","title":"News","is_self":false,"post_hint":"link","url_overridden_by_dest":"https://www.example.com/story?utm=1"}""",
        )
        val post = RedditParser.parse(body, browserUrl)
        assertEquals("https://www.example.com/story?utm=1", post.linkUrl)
        assertTrue(post.images.isEmpty())
    }

    @Test
    fun `replies keep their depth, time and pinned flag`() {
        val comments = """
            {"kind":"t1","data":{"author":"a","body":"top","score":3,"created_utc":1760000000.0,"stickied":true,"replies":{"kind":"Listing","data":{"children":[{"kind":"t1","data":{"author":"b","body":"reply","replies":{"kind":"Listing","data":{"children":[{"kind":"t1","data":{"author":"c","body":"deep"}}]}}}}]}}}}
        """.trimIndent()
        val post = RedditParser.parse(
            listing("""{"id":"abc123","author":"alice","title":"Title"}""", listOf(comments)),
            browserUrl,
        )
        assertEquals(listOf("u/a", "u/b", "u/c"), post.comments.map { it.author })
        assertEquals(listOf(0, 1, 2), post.comments.map { it.depth })
        assertEquals(1_760_000_000L, post.comments.first().createdAt)
        assertTrue(post.comments.first().stickied)
    }

    @Test
    fun `nsfw post has a note`() {
        val body = listing("""{"id":"abc123","author":"alice","title":"Adult","over_18":true}""")
        assertNotNull(RedditParser.parse(body, browserUrl).note)
    }

    @Test
    fun `removed selftext keeps only the title`() {
        val body = listing("""{"id":"abc123","author":"alice","title":"Only title","selftext":"[removed]"}""")
        val post = RedditParser.parse(body, browserUrl)
        assertEquals("Only title", post.title)
        assertNull(post.text)
    }

    @Test
    fun `only thirty comments are kept and removed bodies are skipped`() {
        val comments = (1..31).map { comment("user$it", "comment $it") } + comment("gone", "[removed]")
        val body = listing("""{"id":"abc123","author":"alice","title":"Title"}""", comments)
        val post = RedditParser.parse(body, browserUrl)
        assertEquals(30, post.comments.size)
        assertEquals("comment 1", post.comments.first().body)
        assertEquals("comment 30", post.comments.last().body)
    }

    @Test
    fun `empty listing is not found`() {
        val error = assertThrows(PostException::class.java) {
            RedditParser.parse(
                """[{"kind":"Listing","data":{"children":[]}},{"kind":"Listing","data":{"children":[]}}]""",
                browserUrl,
            )
        }
        assertEquals(ErrorKind.NOT_FOUND, error.kind)
    }

    @Test
    fun `malformed response is unexpected`() {
        val error = assertThrows(PostException::class.java) {
            RedditParser.parse("<!doctype html><html></html>", browserUrl)
        }
        assertEquals(ErrorKind.UNEXPECTED, error.kind)
        assertEquals(Platform.REDDIT, error.platform)
    }

    @Test
    fun `deleted author is hidden`() {
        val body = listing("""{"id":"abc123","author":"[deleted]","title":"Title"}""")
        assertNull(RedditParser.parse(body, browserUrl).author)
    }
}