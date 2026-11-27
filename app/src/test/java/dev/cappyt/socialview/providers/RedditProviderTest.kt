package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.LinkParser
import dev.cappyt.socialview.core.ParsedLink
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.PostLink
import dev.cappyt.socialview.core.PostMediaType
import dev.cappyt.socialview.net.HttpFetcher
import dev.cappyt.socialview.net.HttpResult
import java.io.IOException
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditProviderTest {
    private class FakeFetcher(
        private val onResolve: (String) -> String = { throw IOException("Unexpected resolve $it") },
        private val onQuery: (operation: String, variables: JSONObject) -> HttpResult,
    ) : HttpFetcher {
        val operations = mutableListOf<Pair<String, JSONObject>>()
        var tokens = 0

        override suspend fun get(url: String, headers: Map<String, String>): HttpResult =
            throw IOException("Unexpected GET $url")

        override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResult {
            if (url.endsWith("/access-token/loid")) {
                tokens++
                return HttpResult(200, """{"access_token":"anon","expires_in":86399}""", "application/json")
            }
            val query = JSONObject(body)
            val operation = query.getString("operationName")
            val variables = query.getJSONObject("variables")
            operations += operation to variables
            return onQuery(operation, variables)
        }

        override suspend fun resolve(url: String, headers: Map<String, String>): String = onResolve(url)
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/fixtures/$name")) { "Missing fixture $name" }.readText()

    /** One `PostsByIds` answer of `reddit_posts.json`: text, image, gallery, video, gif, link, crosspost, profile. */
    private fun postAnswer(name: String): String = JSONObject(fixture("reddit_posts.json")).getJSONObject(name).toString()

    private fun json(body: String, status: Int = 200) = HttpResult(status, body, "application/json")

    private fun link(url: String): PostLink = (LinkParser.parse(url) as ParsedLink.Post).link

    private fun fetcherFor(post: String, comments: HttpResult = json(fixture("reddit_post_comments.json"))) =
        FakeFetcher { operation, _ -> if (operation == "PostsByIds") json(postAnswer(post)) else comments }

    private suspend fun failureOf(block: suspend () -> Unit): PostException {
        try {
            block()
        } catch (error: PostException) {
            return error
        }
        throw AssertionError("Expected the call to fail.")
    }

    @Test
    fun `a text post comes from the app api with one token and two queries`() = runBlocking {
        val fetcher = fetcherFor("text")

        val post = RedditProvider(fetcher).load(link("https://www.reddit.com/r/tifu/comments/1aaaaa1/slug/?share_id=x"))

        assertEquals(1, fetcher.tokens)
        assertEquals(listOf("PostsByIds", "PostComments"), fetcher.operations.map { it.first })
        assertEquals("t3_1aaaaa1", fetcher.operations[0].second.getJSONArray("ids").getString(0))
        val commentQuery = fetcher.operations[1].second
        assertEquals("t3_1aaaaa1", commentQuery.getString("id"))
        assertFalse(commentQuery.getBoolean("includePremiumAvatarTreatment"))
        assertFalse(commentQuery.getBoolean("includeConsistencyToken"))

        assertEquals("https://www.reddit.com/r/tifu/comments/1aaaaa1/", post.browserUrl)
        assertEquals("r/tifu", post.handle)
        assertTrue(post.author!!.startsWith("u/"))
        assertTrue(post.title!!.startsWith("TIFU by writing"))
        assertEquals("Today, this is the body of a text post.\n\nIt has a second paragraph.", post.text)
        assertEquals("M", post.flair)
        assertEquals(105, post.score)
        assertEquals(14, post.commentCount)
        // 2026-11-26T21:52:32.212000+0000
        assertEquals(1795729952L, post.createdAt)
        assertTrue(post.media.isEmpty())
        assertNull(post.linkUrl)
        assertNull(post.embed)
        assertNull(post.note)
    }

    @Test
    fun `comments keep their order and depth, without placeholders and deeper replies`() = runBlocking {
        val post = RedditProvider(fetcherFor("text")).load(link("https://www.reddit.com/r/tifu/comments/1aaaaa1/"))

        // The fixture has 30 comments and 29 "more replies" placeholders.
        assertEquals(30, post.comments.size)
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 0), post.comments.take(7).map { it.depth })
        val first = post.comments.first()
        assertTrue(first.author.startsWith("u/"))
        assertEquals(11123, first.score)
        // 2026-11-27T05:09:12.181000+0000
        assertEquals(1795756152L, first.createdAt)
        assertTrue(post.comments.all { it.body.isNotBlank() && it.depth <= 3 })
    }

    @Test
    fun `comments drop deep, removed and orphan replies and stop at thirty`() {
        fun entry(id: String, depth: Int, parent: String?, body: String? = "text $id", removed: Boolean = false) =
            """{"depth":$depth,"parentId":${parent?.let { "\"$it\"" }},"more":null,"node":{"id":"$id",""" +
                """"authorInfo":{"name":"user"},"content":${body?.let { """{"markdown":"$it"}""" }},""" +
                """"score":5,"isScoreHidden":${id == "hidden"},"isRemoved":$removed,"isStickied":false}}"""
        val entries = listOf(
            entry("a", 0, null),
            entry("a1", 1, "a"),
            entry("a2", 2, "a1"),
            entry("a3", 3, "a2"),
            entry("a4", 4, "a3"),
            entry("b", 0, null, body = null),
            entry("b1", 1, "b"),
            entry("b2", 2, "b1"),
            entry("c", 0, null, body = "[deleted]"),
            entry("d", 0, null, removed = true),
            entry("hidden", 0, null),
        ) + (1..40).map { entry("x$it", 0, null) }
        val body = """{"data":{"postInfoById":{"commentForest":{"trees":[${entries.joinToString(",")},""" +
            """{"depth":0,"parentId":null,"more":{"count":9,"cursor":"c"},"node":null}]}}}}"""

        val comments = RedditPostParser.parseComments(body)!!

        assertEquals(30, comments.size)
        assertEquals(
            listOf("text a", "text a1", "text a2", "text a3", "text hidden", "text x1"),
            comments.take(6).map { it.body },
        )
        assertEquals(listOf(0, 1, 2, 3, 0, 0), comments.take(6).map { it.depth })
        assertNull(comments[4].score)
        assertEquals(5, comments[0].score)
    }

    private fun postWith(name: String, edit: (JSONObject) -> Unit): String {
        val answer = JSONObject(postAnswer(name))
        edit(answer.getJSONObject("data").getJSONArray("postsInfoByIds").getJSONObject(0))
        return answer.toString()
    }

    private fun parsed(body: String, id: String) = (RedditPostParser.parsePost(body, id) as RedditPostResult.Post).post

    @Test
    fun `nsfw, removed and hidden-score posts are marked`() {
        val nsfw = parsed(postWith("image") { it.put("isNsfw", true) }, "1x1pjzw")
        assertTrue(nsfw.sensitive)
        assertTrue(nsfw.note!!.contains("NSFW"))
        assertFalse(parsed(postAnswer("image"), "1x1pjzw").sensitive)

        val nsfwOriginal = parsed(
            postWith("crosspost") { it.getJSONObject("crosspostRoot").getJSONObject("post").put("isNsfw", true) },
            "1rzd8im",
        )
        assertTrue(nsfwOriginal.sensitive)

        val removed = parsed(postWith("text") { it.put("removedByCategory", "MODERATOR") }, "1aaaaa1")
        assertTrue(removed.note!!.contains("removed"))

        val hidden = parsed(postWith("text") { it.put("isScoreHidden", true) }, "1aaaaa1")
        assertNull(hidden.score)
    }

    @Test
    fun `a video keeps a file to save, and its cover when the stream is missing`() {
        val video = parsed(postAnswer("video"), "1uz79k3").media.single()
        assertTrue(video.url.contains("HLSPlaylist.m3u8"))
        assertTrue(video.downloadUrl!!.startsWith("https://packaged-media.redd.it/"))

        val coverOnly = parsed(
            postWith("video") { post ->
                post.getJSONObject("media").put("streaming", JSONObject.NULL).put("packagedMedia", JSONObject.NULL)
            },
            "1uz79k3",
        )
        assertEquals(PostMediaType.IMAGE, coverOnly.media.single().type)
        assertTrue(coverOnly.note!!.contains("did not return the video stream"))
        assertNull(coverOnly.linkUrl)
    }

    @Test
    fun `a crosspost of a deleted post, a deleted post and a refused post`() {
        val orphan = parsed(
            postWith("crosspost") {
                it.getJSONObject("crosspostRoot").put("post", JSONObject().put("__typename", "DeletedSubredditPost"))
            },
            "1rzd8im",
        )
        assertEquals("r/BorderCollie", orphan.handle)
        assertTrue(orphan.media.isEmpty())
        assertEquals("https://v.redd.it/gpc0e1y3baqg1", orphan.linkUrl)

        val deleted = """{"data":{"postsInfoByIds":[{"__typename":"DeletedSubredditPost","id":"t3_abc123"}]}}"""
        assertEquals(RedditPostResult.Missing, RedditPostParser.parsePost(deleted, "abc123"))

        // A refusal is not proof that the post is missing: the embed can still show it.
        val refused = """{"errors":[{"message":"forbidden"}],"data":{"postsInfoByIds":[null]}}"""
        assertNull(RedditPostParser.parsePost(refused, "abc123"))
    }

    @Test
    fun `media posts give native media`() = runBlocking {
        suspend fun mediaOf(name: String, url: String) = RedditProvider(fetcherFor(name)).load(link(url))

        val image = mediaOf("image", "https://www.reddit.com/r/aww/comments/1x1pjzw/").media.single()
        assertEquals(PostMediaType.IMAGE, image.type)
        assertTrue(image.url.startsWith("https://preview.redd.it/"))
        assertEquals(1206, image.width)
        assertEquals(1641, image.height)

        val gallery = mediaOf("gallery", "https://www.reddit.com/r/aww/comments/1eigbzg/").media
        assertEquals(2, gallery.size)
        assertTrue(gallery.all { it.type == PostMediaType.IMAGE && it.width != null })

        val video = mediaOf("video", "https://www.reddit.com/r/aww/comments/1uz79k3/").media.single()
        assertEquals(PostMediaType.VIDEO, video.type)
        assertTrue(video.url, video.url.contains("HLSPlaylist.m3u8"))
        assertTrue(video.thumbnailUrl!!.startsWith("https://external-preview.redd.it/"))
        assertEquals(1080, video.width)
        assertEquals(1920, video.height)
        assertEquals(40.0, video.durationSeconds)

        val gif = mediaOf("gif", "https://www.reddit.com/r/gifs/comments/1y68k6u/").media.single()
        assertEquals(PostMediaType.VIDEO, gif.type)
        assertTrue(gif.url, gif.url.contains(".mp4") || gif.url.contains("format=mp4"))
    }

    @Test
    fun `a link post keeps its target and a crosspost shows the media of the original`() = runBlocking {
        val linkPost = RedditProvider(fetcherFor("link")).load(link("https://www.reddit.com/r/nextfuckinglevel/comments/qknx7b/"))
        assertEquals("https://teamseas.org/", linkPost.linkUrl)

        val crosspost = RedditProvider(fetcherFor("crosspost")).load(link("https://www.reddit.com/r/BorderCollie/comments/1rzd8im/"))
        assertEquals("r/BorderCollie", crosspost.handle)
        assertEquals(PostMediaType.VIDEO, crosspost.media.single().type)
        assertNull(crosspost.linkUrl)
    }

    @Test
    fun `a link without a subreddit gets its canonical link from the answer`() = runBlocking {
        val subredditPost = RedditProvider(fetcherFor("video")).load(link("https://redd.it/1uz79k3"))
        assertEquals("https://www.reddit.com/r/aww/comments/1uz79k3/", subredditPost.browserUrl)

        val profilePost = RedditProvider(fetcherFor("profile")).load(link("https://www.reddit.com/comments/1xiupm0"))
        assertEquals("u/spez", profilePost.handle)
        assertEquals("https://www.reddit.com/user/spez/comments/1xiupm0/", profilePost.browserUrl)
    }

    @Test
    fun `a missing post is not found and the post of another id is not shown`() = runBlocking {
        val missing = FakeFetcher { _, _ -> json("""{"data":{"postsInfoByIds":[null]}}""") }
        val error = failureOf { RedditProvider(missing).load(link("https://www.reddit.com/r/aww/comments/zzzzzz/")) }
        assertEquals(ErrorKind.NOT_FOUND, error.kind)
        assertEquals("https://www.reddit.com/r/aww/comments/zzzzzz/", error.browserUrl)

        // The answer holds t3_1x1pjzw, not the post that the link names.
        val other = RedditProvider(fetcherFor("image")).load(link("https://www.reddit.com/r/aww/comments/abc123/"))
        assertTrue(other.embed!!.url.startsWith("https://embed.reddit.com/r/aww/comments/abc123/"))
    }

    @Test
    fun `the post stays when the comments fail`() = runBlocking {
        val fetcher = fetcherFor("image", comments = json("", status = 400))

        val post = RedditProvider(fetcher).load(link("https://www.reddit.com/r/aww/comments/1x1pjzw/"))

        assertEquals(1, post.media.size)
        assertTrue(post.comments.isEmpty())
        assertTrue(post.note!!.contains("did not return the comments"))
    }

    @Test
    fun `the official embed is the fallback when the app api does not answer`() = runBlocking {
        val blocked = FakeFetcher { _, _ -> json("<html>blocked</html>", status = 403) }
        val withSubreddit = RedditProvider(blocked).load(link("https://www.reddit.com/r/Android/comments/abc123/"))
        assertEquals(
            "https://embed.reddit.com/r/Android/comments/abc123/?depth=1&showmore=false",
            withSubreddit.embed!!.url,
        )
        assertTrue(withSubreddit.note!!.contains("HTTP 403"))

        val resolved = FakeFetcher(
            onResolve = { "https://www.reddit.com/r/Android/comments/abc123/google_pixel_update/" },
            onQuery = { _, _ -> throw IOException("offline") },
        )
        val bare = RedditProvider(resolved).load(link("https://redd.it/abc123"))
        assertTrue(bare.embed!!.url.startsWith("https://embed.reddit.com/r/Android/comments/abc123/google_pixel_update/"))
        assertTrue(bare.note!!.contains("could not be reached"))

        val unresolved = FakeFetcher { _, _ -> throw IOException("offline") }
        val commentOnly = RedditProvider(unresolved).load(link("https://www.reddit.com/comments/abc123"))
        val prefix = "data:text/html;charset=utf-8,"
        assertEquals(
            "<script src=\"https://www.reddit.com/comments/abc123/.embed?depth=1&showmore=false\"></script>",
            URLDecoder.decode(commentOnly.embed!!.url.removePrefix(prefix), "UTF-8"),
        )
        assertTrue(commentOnly.note!!.contains("subreddit was not resolved"))
    }
}
