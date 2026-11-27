package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.LinkParser
import dev.cappyt.socialview.core.ParsedLink
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.ProfileBioLink
import dev.cappyt.socialview.core.ProfileLayout
import dev.cappyt.socialview.core.ProfileLink
import dev.cappyt.socialview.core.PostMediaType
import dev.cappyt.socialview.core.ProfilePageRequest
import dev.cappyt.socialview.core.ProfilePostType
import dev.cappyt.socialview.core.ProfileStat
import dev.cappyt.socialview.net.HttpFetcher
import dev.cappyt.socialview.net.HttpResult
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileProvidersTest {
    private class Request(val url: String, val headers: Map<String, String>, val body: String?)

    private class FakeFetcher(private val answer: (Request) -> HttpResult) : HttpFetcher {
        val requests = mutableListOf<Request>()

        override suspend fun get(url: String, headers: Map<String, String>): HttpResult =
            answer(Request(url, headers, null).also(requests::add))

        override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResult =
            answer(Request(url, headers, body).also(requests::add))

        override suspend fun resolve(url: String, headers: Map<String, String>): String =
            throw IOException("Unexpected resolve $url")
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/fixtures/$name")) { "Missing fixture $name" }.readText()

    private fun html(body: String, status: Int = 200) = HttpResult(status, body, "text/html")

    private fun json(body: String, status: Int = 200) = HttpResult(status, body, "application/json")

    private fun link(url: String): ProfileLink = (LinkParser.parse(url) as ParsedLink.Profile).link

    private suspend fun failureOf(block: suspend () -> Unit): PostException {
        try {
            block()
        } catch (error: PostException) {
            return error
        }
        throw AssertionError("Expected the call to fail.")
    }

    // TikTok

    @Test
    fun `a tiktok profile comes from the creator embed`() = runBlocking {
        val fetcher = FakeFetcher { html(fixture("tiktok_embed_profile.html")) }

        val profile = TikTokProfileProvider(fetcher).load(link("https://www.tiktok.com/@NASA?lang=it"))

        assertEquals("https://www.tiktok.com/embed/@nasa", fetcher.requests.single().url)
        assertEquals("@nasa", profile.handle)
        assertEquals("NASA", profile.fullName)
        assertEquals("https://www.tiktok.com/@nasa", profile.browserUrl)
        assertTrue(profile.verified)
        assertFalse(profile.private)
        assertEquals(
            listOf(ProfileStat("2.2M", "Followers"), ProfileStat("23", "Following"), ProfileStat("12M", "Likes")),
            profile.stats,
        )
        assertEquals(ProfileLayout.GRID, profile.layout)
        // The fixture repeats its first video.
        assertEquals(3, profile.posts.size)
        val first = profile.posts.first()
        assertEquals("https://www.tiktok.com/@nasa/video/7665075736742530317", first.browserUrl)
        assertEquals(ProfilePostType.VIDEO, first.type)
        assertEquals("Something big just landed on TikTok.", first.text)
        assertTrue(first.thumbnailUrl!!.startsWith("https://"))
        assertNull(profile.note)
        // The next page needs the secUid. A video id of the account leads to it.
        assertEquals(ProfilePageRequest("nasa:item:7665075736742530317", ""), profile.nextPage)
    }

    private val nasaSecUid = "MS4wLjABAAAAU9BRVzC8oCaegVnia8IbqWhPb_-dbU7s00Y3wS1_Nx8g5RUaYvyXrpejgjdxTwd6"

    @Test
    fun `more tiktok posts resolve the secUid once, then page back in time`() = runBlocking {
        val fetcher = FakeFetcher { request ->
            if (request.url.contains("/player/api/v1/items")) {
                json(fixture("tiktok_item_video.json"))
            } else {
                json(fixture("tiktok_creator_item_list.json"))
            }
        }
        val provider = TikTokProfileProvider(fetcher, nowMillis = { 1_795_800_000_000 })

        val first = provider.loadMore(ProfilePageRequest("nasa:item:7665075736742530317", ""))

        assertEquals(2, fetcher.requests.size)
        assertTrue(fetcher.requests[0].url.contains("item_ids=7665075736742530317"))
        val list = fetcher.requests[1].url
        assertTrue(list, list.startsWith("https://www.tiktok.com/api/creator/item_list/?"))
        assertTrue(list, list.contains("cursor=1795800000000") && list.contains("secUid=$nasaSecUid") && list.contains("type=1"))
        assertEquals(
            listOf(
                "https://www.tiktok.com/@nasa/video/7710449624397450509",
                "https://www.tiktok.com/@nasa/video/7710369465740872974",
                // A photo post has the video form of the link, as in the first page.
                "https://www.tiktok.com/@nasa/video/7701866987609197837",
            ),
            first.posts.map { it.browserUrl },
        )
        assertEquals(
            listOf(ProfilePostType.VIDEO, ProfilePostType.VIDEO, ProfilePostType.CAROUSEL),
            first.posts.map { it.type },
        )
        assertTrue(first.posts.all { it.thumbnailUrl!!.startsWith("https://") })
        // The oldest post of the page is from 1793230665.
        assertEquals(ProfilePageRequest("nasa:sec:$nasaSecUid", "1793230665000"), first.next)

        provider.loadMore(first.next!!)

        assertEquals(3, fetcher.requests.size)
        assertTrue(fetcher.requests[2].url.contains("cursor=1793230665000"))
    }

    @Test
    fun `the last tiktok page has no next page and a refused list is an error`() = runBlocking {
        val last = fixture("tiktok_creator_item_list.json").replace("\"hasMorePrevious\": true", "\"hasMorePrevious\": false")
        assertNull(TikTokProfileParser.parsePage(last, "nasa", nasaSecUid)!!.next)

        val refused = FakeFetcher { json("""{"statusCode":10201,"status_code":10201}""", status = 400) }
        val error = failureOf {
            TikTokProfileProvider(refused).loadMore(ProfilePageRequest("nasa:sec:$nasaSecUid", "1"))
        }
        assertEquals(ErrorKind.UNEXPECTED, error.kind)

        val noSecUid = FakeFetcher { json(fixture("tiktok_item_missing.json")) }
        assertEquals(
            ErrorKind.UNEXPECTED,
            failureOf {
                TikTokProfileProvider(noSecUid).loadMore(ProfilePageRequest("nasa:item:7709194461913419039", ""))
            }.kind,
        )
    }

    @Test
    fun `the tiktok post list of another account is never requested or shown`() = runBlocking {
        // The video that should lead to the secUid is of @nasa, not of @esa.
        val fetcher = FakeFetcher { json(fixture("tiktok_item_video.json")) }
        val error = failureOf {
            TikTokProfileProvider(fetcher).loadMore(ProfilePageRequest("esa:item:7665075736742530317", ""))
        }
        assertEquals(ErrorKind.UNEXPECTED, error.kind)
        assertEquals(1, fetcher.requests.size)

        assertTrue(TikTokProfileParser.parsePage(fixture("tiktok_creator_item_list.json"), "esa", "x")!!.posts.isEmpty())
    }

    @Test
    fun `a tiktok create time as text still gives the next cursor and an absurd one does not`() {
        val text = fixture("tiktok_creator_item_list.json").replace("\"createTime\": 1793230665", "\"createTime\": \"1793230665\"")
        assertEquals("1793230665000", TikTokProfileParser.parsePage(text, "nasa", nasaSecUid)!!.next!!.cursor)

        val absurd = fixture("tiktok_creator_item_list.json").replace("\"createTime\": 1793230665", "\"createTime\": 9300000000000000")
        // The oldest sane time of the page is then the second post.
        assertEquals("1795210297000", TikTokProfileParser.parsePage(absurd, "nasa", nasaSecUid)!!.next!!.cursor)
    }

    @Test
    fun `a tiktok item without usable media opens in the official player`() = runBlocking {
        val body = fixture("tiktok_item_video.json").replace("https://v16-webapp-prime.tiktok.com/", "http://v16-webapp-prime.tiktok.com/")
            .replace("https://v19-webapp-prime.tiktok.com/", "http://v19-webapp-prime.tiktok.com/")
        val fetcher = FakeFetcher { request ->
            if (request.url.contains("/player/api/")) json(body) else json("", status = 500)
        }

        val post = TikTokProvider(fetcher).load(postLink("https://www.tiktok.com/@nasa/video/7665075736742530317"))

        assertTrue(post.media.isEmpty())
        assertTrue(post.embed!!.url.startsWith("https://www.tiktok.com/player/v1/7665075736742530317"))
    }

    // TikTok posts

    private fun postLink(url: String) = (LinkParser.parse(url) as ParsedLink.Post).link

    @Test
    fun `a tiktok video comes from the player data endpoint with the headers its host needs`() = runBlocking {
        val fetcher = FakeFetcher { json(fixture("tiktok_item_video.json")) }

        val post = TikTokProvider(fetcher).load(postLink("https://www.tiktok.com/player/v1/7665075736742530317?autoplay=1"))

        assertTrue(fetcher.requests.single().url.startsWith("https://www.tiktok.com/player/api/v1/items?item_ids=7665075736742530317&"))
        assertNull(fetcher.requests.single().headers["Cookie"])
        assertEquals("https://www.tiktok.com/@nasa/video/7665075736742530317", post.browserUrl)
        assertEquals("NASA", post.author)
        assertEquals("@nasa", post.handle)
        assertEquals("Something big just landed on TikTok.", post.text)
        assertEquals(114600, post.score)
        assertEquals(3510, post.commentCount)
        // The high 32 bits of the id.
        assertEquals(1784664517L, post.createdAt)
        assertNull(post.embed)
        val video = post.media.single()
        assertEquals(PostMediaType.VIDEO, video.type)
        assertTrue(video.url, video.url.startsWith("https://v16-webapp-prime.tiktok.com/"))
        assertEquals("https://www.tiktok.com/", video.headers["Referer"])
        assertTrue(video.headers.getValue("User-Agent").contains("Chrome"))
        assertEquals(720, video.width)
        assertEquals(1280, video.height)
        assertEquals(25.0, video.durationSeconds)
        assertTrue(video.thumbnailUrl!!.startsWith("https://"))
    }

    @Test
    fun `a tiktok photo post gives its images in order and no music track`() = runBlocking {
        val fetcher = FakeFetcher { json(fixture("tiktok_item_photo.json")) }

        val post = TikTokProvider(fetcher).load(postLink("https://www.tiktok.com/@burberry/photo/7475761007382629634"))

        assertEquals("https://www.tiktok.com/@burberry/photo/7475761007382629634", post.browserUrl)
        assertEquals(3, post.media.size)
        assertTrue(post.media.all { it.type == PostMediaType.IMAGE && it.headers.isEmpty() })
        assertEquals(1440, post.media.first().width)
        assertEquals(1868, post.media.first().height)
    }

    @Test
    fun `a missing tiktok post is not found and another answer falls back to the official player`() = runBlocking {
        val missing = FakeFetcher { json(fixture("tiktok_item_missing.json")) }
        val error = failureOf {
            TikTokProvider(missing).load(postLink("https://www.tiktok.com/@nasa/video/7709194461913419039"))
        }
        assertEquals(ErrorKind.NOT_FOUND, error.kind)

        // The answer holds another post than the one the link names.
        val other = FakeFetcher { request ->
            if (request.url.contains("/player/api/")) json(fixture("tiktok_item_video.json")) else json("", status = 500)
        }
        val fallback = TikTokProvider(other).load(postLink("https://www.tiktok.com/@nasa/video/1111111111111111111"))
        assertTrue(fallback.embed!!.url.startsWith("https://www.tiktok.com/player/v1/1111111111111111111"))
        assertTrue(fallback.media.isEmpty())
    }

    @Test
    fun `a missing tiktok account is not found and a private one has no posts`() = runBlocking {
        val missing = FakeFetcher { html(fixture("tiktok_embed_missing.html"), status = 400) }
        val error = failureOf { TikTokProfileProvider(missing).load(link("https://www.tiktok.com/@nobody")) }
        assertEquals(ErrorKind.NOT_FOUND, error.kind)
        assertEquals("https://www.tiktok.com/@nobody", error.browserUrl)

        val private = FakeFetcher { html(fixture("tiktok_embed_private.html"), status = 400) }
        val profile = TikTokProfileProvider(private).load(link("https://www.tiktok.com/@hidden"))
        assertTrue(profile.private)
        assertEquals("@hidden", profile.handle)
        assertTrue(profile.posts.isEmpty())
    }

    @Test
    fun `a tiktok answer for another account or without the state is not a profile`() = runBlocking {
        val other = FakeFetcher { html(fixture("tiktok_embed_profile.html")) }
        assertEquals(
            ErrorKind.NOT_FOUND,
            failureOf { TikTokProfileProvider(other).load(link("https://www.tiktok.com/@esa")) }.kind,
        )
        val login = FakeFetcher { html("<html><title>Log in | TikTok</title></html>") }
        assertEquals(
            ErrorKind.NOT_FOUND,
            failureOf { TikTokProfileProvider(login).load(link("https://www.tiktok.com/@nasa")) }.kind,
        )
        val blocked = FakeFetcher { html("", status = 429) }
        assertEquals(
            ErrorKind.UNEXPECTED,
            failureOf { TikTokProfileProvider(blocked).load(link("https://www.tiktok.com/@nasa")) }.kind,
        )
    }

    @Test
    fun `a tiktok like count above the 32-bit range is unwrapped`() {
        // -1 is 4,294,967,295 after the unwrap. A clamp or a sign flip would give another label.
        val body = fixture("tiktok_embed_profile.html").replace("\"heartCount\": 12000000", "\"heartCount\": -1")

        val result = TikTokProfileParser.parse(body, link("https://www.tiktok.com/@nasa")) as TikTokProfileResult.Profile

        assertEquals(ProfileStat("4B", "Likes"), result.profile.stats.last())
    }

    @Test
    fun `tiktok data of another account is not shown`() {
        val nasa = link("https://www.tiktok.com/@nasa")
        val otherKey = fixture("tiktok_embed_private.html")
        // The private entry is under /embed/@hidden, so it says nothing about @nasa.
        assertNull(TikTokProfileParser.parse(otherKey, nasa))

        val otherAuthor = fixture("tiktok_embed_profile.html").replace("\"authorUniqueId\": \"nasa\"", "\"authorUniqueId\": \"esa\"")
        val result = TikTokProfileParser.parse(otherAuthor, nasa) as TikTokProfileResult.Profile
        assertTrue(result.profile.posts.isEmpty())
    }

    // X

    @Test
    fun `an x profile comes from the timeline embed document`() = runBlocking {
        val fetcher = FakeFetcher { html(fixture("x_timeline_profile.html")) }

        val profile = XProfileProvider(fetcher).load(link("https://twitter.com/GitHub/with_replies"))

        assertEquals(
            "https://syndication.twitter.com/srv/timeline-profile/screen-name/GitHub",
            fetcher.requests.single().url,
        )
        assertEquals("@github", profile.handle)
        assertEquals("GitHub", profile.fullName)
        assertEquals("https://x.com/github", profile.browserUrl)
        assertTrue(profile.verified)
        assertTrue(profile.avatarUrl!!.endsWith("_400x400.jpg"))
        assertEquals(
            listOf(ProfileStat("11K", "Posts"), ProfileStat("2.7M", "Followers"), ProfileStat("335", "Following")),
            profile.stats,
        )
        assertEquals(listOf(ProfileBioLink(null, "http://github.com")), profile.links)
        assertEquals(ProfileLayout.LIST, profile.layout)
        assertEquals(6, profile.posts.size)
    }

    @Test
    fun `x posts mark the pinned one, drop the media link and show the original of a repost`() {
        val posts = XProfileParser.parse(fixture("x_timeline_profile.html"), link("https://x.com/github"))!!.posts

        val pinned = posts[0]
        assertEquals("Pinned", pinned.label)
        assertEquals("https://x.com/github/status/2122756587073028306", pinned.browserUrl)
        assertEquals(ProfilePostType.IMAGE, pinned.type)
        assertTrue(pinned.thumbnailUrl!!.startsWith("https://pbs.twimg.com/media/"))
        assertTrue(pinned.text!!.endsWith("https://t.co/DFBXTqn6kF"))
        assertEquals(478L, pinned.score)
        assertEquals(61L, pinned.commentCount)
        // Tue Nov 17 18:19:43 +0000 2026
        assertEquals(1794939583L, pinned.createdAt)

        assertNull(posts[1].label)
        assertEquals(ProfilePostType.TEXT, posts[1].type)
        assertEquals(ProfilePostType.VIDEO, posts[3].type)

        val repost = posts[5]
        assertEquals("Reposted from @astro_anil", repost.label)
        assertEquals("https://x.com/astro_anil/status/2125971502923129200", repost.browserUrl)
        assertTrue(repost.text!!.startsWith("From space to Earth"))
        assertEquals(2345L, repost.score)
    }

    @Test
    fun `an x timeline without posts or of another account is not a profile`() = runBlocking {
        val empty = FakeFetcher { html(fixture("x_timeline_empty.html")) }
        val error = failureOf { XProfileProvider(empty).load(link("https://x.com/ChicagoState")) }
        assertEquals(ErrorKind.NOT_FOUND, error.kind)
        assertTrue(error.message.contains("protected"))

        assertNull(XProfileParser.parse(fixture("x_timeline_profile.html"), link("https://x.com/nasa")))

        val limited = FakeFetcher { html("", status = 429) }
        assertEquals(
            ErrorKind.UNEXPECTED,
            failureOf { XProfileProvider(limited).load(link("https://x.com/github")) }.kind,
        )
    }

    @Test
    fun `an x entry of another account is not a post of the profile`() {
        val body = fixture("x_timeline_profile.html")
            .replaceFirst("\"screen_name\": \"github\"", "\"screen_name\": \"evil\"")

        val posts = XProfileParser.parse(body, link("https://x.com/github"))!!.posts

        // The first entry was the pinned post. Without it nothing is pinned.
        assertEquals(5, posts.size)
        assertTrue(posts.none { it.label == "Pinned" })
    }

    @Test
    fun `sensitive posts of a profile are marked`() {
        val x = fixture("x_timeline_profile.html").replaceFirst("\"id_str\": \"2125754695679344797\"", "\"possibly_sensitive\": true, \"id_str\": \"2125754695679344797\"")
        val xPosts = XProfileParser.parse(x, link("https://x.com/github"))!!.posts
        assertEquals(listOf(false, true), xPosts.take(2).map { it.sensitive })
        // The mark of a repost can be on the repost itself, not on the original.
        val repost = fixture("x_timeline_profile.html").replaceFirst("\"retweeted_status\":", "\"possibly_sensitive\": true, \"retweeted_status\":")
        assertTrue(XProfileParser.parse(repost, link("https://x.com/github"))!!.posts.last().sensitive)

        val reddit = fixture("reddit_user_posts.json")
            .replaceFirst("\"__typename\": \"MarginCell\"", "\"__typename\": \"IndicatorsCell\", \"indicators\": [\"NSFW\"]")
        val redditPosts = RedditProfileParser.parsePosts(reddit, "example_user")!!.posts
        assertTrue(redditPosts.first().sensitive)
        assertFalse(redditPosts[1].sensitive)
    }

    // Facebook

    @Test
    fun `a facebook page comes from the page plugin`() = runBlocking {
        val fetcher = FakeFetcher { html(fixture("facebook_page_plugin.html")) }

        val profile = FacebookProfileProvider(fetcher).load(link("https://m.facebook.com/NASA/?ref=share"))

        val url = fetcher.requests.single().url
        assertTrue(url, url.startsWith("https://www.facebook.com/plugins/page.php?href=https%3A%2F%2Fwww.facebook.com%2FNASA&"))
        assertEquals("NASA", profile.handle)
        assertEquals("NASA - National Aeronautics and Space Administration", profile.fullName)
        assertEquals("https://www.facebook.com/NASA/", profile.browserUrl)
        assertTrue(profile.verified)
        assertEquals(listOf(ProfileStat("28M", "Followers")), profile.stats)
        assertTrue(profile.biography!!.startsWith("Government Organization\nExplore the universe"))
        assertEquals(ProfileLayout.LIST, profile.layout)
        assertEquals(3, profile.posts.size)

        val photo = profile.posts[0]
        assertNull(photo.browserUrl)
        assertEquals(ProfilePostType.IMAGE, photo.type)
        assertTrue(photo.text!!.startsWith("A home away from home"))
        assertEquals(1795539000L, photo.createdAt)
        assertEquals(2527L, photo.score)
        assertEquals(175L, photo.commentCount)
        assertEquals(ProfilePostType.VIDEO, profile.posts[1].type)

        val shared = profile.posts[2]
        assertEquals("Shared from NASA Solar System Exploration", shared.label)
        assertTrue(shared.text!!.startsWith("Send your name to Saturn"))
        assertTrue(profile.note!!.contains("latest 3 posts"))
    }

    @Test
    fun `a facebook numeric id asks the plugin for the id and an empty plugin is not found`() = runBlocking {
        val fetcher = FakeFetcher { html(fixture("facebook_page_plugin_missing.html")) }

        val error = failureOf {
            FacebookProfileProvider(fetcher).load(link("https://www.facebook.com/profile.php?id=100044561550831"))
        }

        assertEquals(ErrorKind.NOT_FOUND, error.kind)
        assertEquals("https://www.facebook.com/profile.php?id=100044561550831", error.browserUrl)
        assertTrue(fetcher.requests.single().url.contains("href=https%3A%2F%2Fwww.facebook.com%2F100044561550831&"))
    }

    // Reddit

    private fun redditFetcher(
        profile: String = fixture("reddit_user_profile.json"),
        posts: (JSONObject) -> HttpResult = { json(fixture("reddit_user_posts.json")) },
        tokenStatus: Int = 200,
    ) = FakeFetcher { request ->
        when {
            request.url.endsWith("/access-token/loid") ->
                json("""{"access_token":"anon-token","expires_in":86399,"token_type":"bearer"}""", tokenStatus)

            JSONObject(request.body!!).getString("operationName") == "UserProfile" -> json(profile)
            else -> posts(JSONObject(request.body).getJSONObject("variables"))
        }
    }

    @Test
    fun `a reddit profile uses one anonymous token for the header and the posts`() = runBlocking {
        val fetcher = redditFetcher()

        val profile = RedditProfileProvider(RedditGraphql(fetcher)).load(link("https://www.reddit.com/u/example_user/submitted/"))

        assertEquals(
            listOf(
                "https://www.reddit.com/auth/v2/oauth/access-token/loid",
                "https://gql-fed.reddit.com/",
                "https://gql-fed.reddit.com/",
            ),
            fetcher.requests.map { it.url },
        )
        assertTrue(fetcher.requests[0].headers.getValue("Authorization").startsWith("Basic "))
        fetcher.requests.drop(1).forEach { request ->
            assertEquals("Bearer anon-token", request.headers["Authorization"])
            assertTrue(request.headers.getValue("User-Agent").startsWith("Reddit/"))
        }
        val profileQuery = JSONObject(fetcher.requests[1].body!!)
        assertEquals("example_user", profileQuery.getJSONObject("variables").getString("name"))
        assertEquals(
            64,
            profileQuery.getJSONObject("extensions").getJSONObject("persistedQuery").getString("sha256Hash").length,
        )
        val postsQuery = JSONObject(fetcher.requests[2].body!!).getJSONObject("variables")
        assertEquals("example_user", postsQuery.getString("username"))
        assertFalse(postsQuery.has("after"))

        assertEquals("u/example_user", profile.handle)
        assertEquals("Example", profile.fullName)
        assertEquals("https://www.reddit.com/user/example_user/", profile.browserUrl)
        assertEquals(
            listOf(ProfileStat("36M", "Karma"), ProfileStat("12K", "Posts"), ProfileStat("14K", "Comments")),
            profile.stats,
        )
        assertEquals("https://twitter.com/example", profile.links.first().url)
        assertEquals(ProfileLayout.LIST, profile.layout)
        assertEquals(ProfilePageRequest("example_user", "dDNfemdrdWJ4"), profile.nextPage)
    }

    @Test
    fun `reddit posts carry their community, media and counts`() {
        // The fixture repeats its first post.
        val posts = RedditProfileParser.parsePosts(fixture("reddit_user_posts.json"), "example_user")!!.posts
        assertEquals(7, posts.size)

        val pinned = posts[0]
        assertEquals("https://www.reddit.com/comments/fgghrs", pinned.browserUrl)
        assertEquals("Pinned · u/example_user", pinned.label)
        assertEquals(ProfilePostType.IMAGE, pinned.type)
        assertTrue(pinned.thumbnailUrl!!.startsWith("https://preview.redd.it/"))
        assertEquals(7956L, pinned.score)
        assertEquals(66L, pinned.commentCount)
        // 2020-03-10T16:27:45.010000+0000
        assertEquals(1583857665L, pinned.createdAt)

        val video = posts[1]
        assertEquals("https://www.reddit.com/r/aww/comments/1uz79k3/", video.browserUrl)
        assertEquals("r/aww", video.label)
        assertEquals(ProfilePostType.VIDEO, video.type)
        assertEquals(ProfilePostType.CAROUSEL, posts[3].type)
        assertEquals(ProfilePostType.TEXT, posts[4].type)
        assertTrue(posts.all { it.text != null })
        posts.forEach { post -> assertTrue(post.browserUrl, LinkParser.parse(post.browserUrl!!) is ParsedLink.Post) }
    }

    @Test
    fun `the next reddit page sends the cursor and a new token replaces a rejected one`() = runBlocking {
        var rejectFirst = true
        val fetcher = redditFetcher(
            posts = { variables ->
                if (rejectFirst) {
                    rejectFirst = false
                    json("""{"errors":[{"extensions":{"code":"NOT_AUTHENTICATED"}}]}""", status = 401)
                } else {
                    assertEquals("dDNfemdrdWJ4", variables.getString("after"))
                    json(fixture("reddit_user_posts.json"))
                }
            },
        )

        val page = RedditProfileProvider(RedditGraphql(fetcher)).loadMore(ProfilePageRequest("example_user", "dDNfemdrdWJ4"))

        assertEquals(7, page.posts.size)
        assertEquals(2, fetcher.requests.count { it.url.endsWith("/access-token/loid") })
    }

    @Test
    fun `a missing, suspended or deleted reddit account is not found`() = runBlocking {
        val cases = mapOf(
            """{"data":{"redditorInfoByName":null}}""" to "no account",
            """{"data":{"redditorInfoByName":{"__typename":"UnavailableRedditor"}}}""" to "suspended",
            """{"data":{"redditorInfoByName":{"__typename":"DeletedRedditor"}}}""" to "deleted",
        )
        cases.forEach { (body, word) ->
            val error = failureOf {
                RedditProfileProvider(RedditGraphql(redditFetcher(profile = body))).load(link("https://www.reddit.com/user/nobody"))
            }
            assertEquals(word, ErrorKind.NOT_FOUND, error.kind)
            assertTrue(error.message, error.message.contains(word))
        }
    }

    @Test
    fun `a reddit profile keeps its header when the feed fails and fails without a token`() = runBlocking {
        val noFeed = redditFetcher(
            posts = { json("""{"errors":[{"extensions":{"code":"NOT_FOUND"}}],"data":{"profileFeed":{"elements":null}}}""") },
        )
        val profile = RedditProfileProvider(RedditGraphql(noFeed)).load(link("https://www.reddit.com/user/example_user"))
        assertEquals("u/example_user", profile.handle)
        assertTrue(profile.posts.isEmpty())
        assertNull(profile.nextPage)

        val offline = redditFetcher(posts = { throw IOException("offline") })
        val withoutFeed = RedditProfileProvider(RedditGraphql(offline)).load(link("https://www.reddit.com/user/example_user"))
        assertEquals("u/example_user", withoutFeed.handle)
        assertTrue(withoutFeed.note!!.contains("did not return the posts"))

        val blocked = redditFetcher(tokenStatus = 403)
        val error = failureOf { RedditProfileProvider(RedditGraphql(blocked)).load(link("https://www.reddit.com/user/example_user")) }
        assertEquals(ErrorKind.UNEXPECTED, error.kind)
        assertTrue(error.message.contains("403"))
    }

    @Test
    fun `the reddit answer for another account is not the profile`() = runBlocking {
        val error = failureOf { RedditProfileProvider(RedditGraphql(redditFetcher())).load(link("https://www.reddit.com/user/spez")) }

        assertEquals(ErrorKind.NOT_FOUND, error.kind)
    }

    @Test
    fun `a reddit token without a lifetime is kept for the next request`() = runBlocking {
        val fetcher = FakeFetcher { request ->
            when {
                request.url.endsWith("/access-token/loid") -> json("""{"access_token":"anon-token"}""")
                JSONObject(request.body!!).getString("operationName") == "UserProfile" ->
                    json(fixture("reddit_user_profile.json"))

                else -> json(fixture("reddit_user_posts.json"))
            }
        }

        RedditProfileProvider(RedditGraphql(fetcher)).load(link("https://www.reddit.com/user/example_user"))

        assertEquals(1, fetcher.requests.count { it.url.endsWith("/access-token/loid") })
    }
}
