package dev.cappyt.socialview.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParserTest {
    private fun post(input: String): PostLink {
        val parsed = LinkParser.parse(input)
        assertTrue("Expected a post, got $parsed", parsed is ParsedLink.Post)
        return (parsed as ParsedLink.Post).link
    }

    @Test
    fun `instagram post is classified and normalized`() {
        val link = post("https://www.instagram.com/p/CxS-o4RrdEs/?igsh=tracking")
        assertEquals(Platform.INSTAGRAM, link.platform)
        assertEquals("CxS-o4RrdEs", link.postId)
        assertEquals("https://www.instagram.com/p/CxS-o4RrdEs/", link.browserUrl)
    }

    @Test
    fun `instagram reel and tv posts are accepted`() {
        val reel = post("https://instagram.com/reel/ABC_123/")
        assertEquals("ABC_123", reel.postId)
        assertEquals(PostKind.REEL, reel.kind)
        assertEquals("https://www.instagram.com/reel/ABC_123/", reel.browserUrl)

        val reels = post("https://www.instagram.com/reels/ABC_123/")
        assertEquals("https://www.instagram.com/reel/ABC_123/", reels.browserUrl)

        val tv = post("https://m.instagram.com/tv/ABC-123/")
        assertEquals("ABC-123", tv.postId)
        assertEquals(PostKind.VIDEO, tv.kind)
        assertEquals("https://www.instagram.com/tv/ABC-123/", tv.browserUrl)
    }

    @Test
    fun `instagram reel acceptance url keeps the reel kind and drops tracking`() {
        val link = post("https://www.instagram.com/reel/DaduG1us3DU/?exln=dWlicmQ2d2tvM3B1")
        assertEquals(Platform.INSTAGRAM, link.platform)
        assertEquals("DaduG1us3DU", link.postId)
        assertEquals(PostKind.REEL, link.kind)
        assertEquals("https://www.instagram.com/reel/DaduG1us3DU/", link.browserUrl)
        assertEquals(
            "https://www.instagram.com/reel/DaduG1us3DU/?exln=dWlicmQ2d2tvM3B1",
            link.originalUrl,
        )
    }

    @Test
    fun `instagram vrfl tracking is dropped from a reel acceptance url`() {
        val link = post("https://www.instagram.com/reel/De5-myeTp52/?vrfl=dnF4MWxjeWV5MXc5")

        assertEquals(Platform.INSTAGRAM, link.platform)
        assertEquals("De5-myeTp52", link.postId)
        assertEquals(PostKind.REEL, link.kind)
        assertEquals("https://www.instagram.com/reel/De5-myeTp52/", link.browserUrl)
        assertEquals(
            "https://www.instagram.com/reel/De5-myeTp52/?vrfl=dnF4MWxjeWV5MXc5",
            link.originalUrl,
        )
    }

    @Test
    fun `instagram post kind path is case insensitive`() {
        val reel = post("https://www.instagram.com/REEL/ABC_123/")
        assertEquals("ABC_123", reel.postId)
        assertEquals(PostKind.REEL, reel.kind)
        assertEquals("https://www.instagram.com/reel/ABC_123/", reel.browserUrl)

        val tv = post("https://www.instagram.com/Tv/ABC-123/")
        assertEquals(PostKind.VIDEO, tv.kind)
        assertEquals("https://www.instagram.com/tv/ABC-123/", tv.browserUrl)
    }

    @Test
    fun `instagram image index is preserved in the browser url`() {
        val link = post("https://www.instagram.com/p/ABC123/?img_index=2&igsh=tracking")
        assertEquals("https://www.instagram.com/p/ABC123/?img_index=2", link.browserUrl)
        assertEquals(PostKind.POST, link.kind)
    }

    @Test
    fun `instagram share link is a short link`() {
        val parsed = LinkParser.parse("https://www.instagram.com/share/reel/BAabc123?igsh=x")
        assertEquals(
            ParsedLink.Short(
                Platform.INSTAGRAM,
                "https://www.instagram.com/share/reel/BAabc123",
                "https://www.instagram.com/share/reel/BAabc123?igsh=x",
            ),
            parsed,
        )
    }

    @Test
    fun `instagram profile is unsupported`() {
        val parsed = LinkParser.parse("https://www.instagram.com/nasa/")
        assertTrue(parsed is ParsedLink.Unsupported)
        assertEquals(Platform.INSTAGRAM, (parsed as ParsedLink.Unsupported).platform)
        assertTrue(parsed.reason.contains("profile"))
    }

    @Test
    fun `instagram story needs login`() {
        val parsed = LinkParser.parse("https://www.instagram.com/stories/nasa/123456/")
        assertTrue(parsed is ParsedLink.Unsupported)
        assertTrue((parsed as ParsedLink.Unsupported).reason.contains("login"))
    }

    @Test
    fun `tiktok video and photo posts are accepted`() {
        val video = post("https://www.tiktok.com/@scout2015/video/6718335390845095173?is_from_webapp=1")
        assertEquals("6718335390845095173", video.postId)
        assertEquals(Platform.TIKTOK, video.platform)
        assertEquals(
            "https://www.tiktok.com/@scout2015/video/6718335390845095173",
            video.browserUrl,
        )

        val photo = post("https://www.tiktok.com/@user/photo/123456")
        assertEquals("123456", photo.postId)
    }

    @Test
    fun `tiktok short hosts and t path are short links`() {
        listOf(
            "https://vm.tiktok.com/ZMabc/",
            "https://vt.tiktok.com/ZSabc/",
            "https://www.tiktok.com/t/ZTabc/",
        ).forEach { url ->
            assertTrue(url, LinkParser.parse(url) is ParsedLink.Short)
        }
    }

    @Test
    fun `tiktok profile is unsupported`() {
        val parsed = LinkParser.parse("https://www.tiktok.com/@scout2015")
        assertTrue(parsed is ParsedLink.Unsupported)
        assertEquals(Platform.TIKTOK, (parsed as ParsedLink.Unsupported).platform)
    }

    @Test
    fun `x status on x and twitter is canonicalized to x`() {
        val x = post("https://x.com/jack/status/20?s=20")
        assertEquals("20", x.postId)
        assertEquals("https://x.com/jack/status/20", x.browserUrl)

        val twitter = post("https://twitter.com/jack/status/20")
        assertEquals("https://x.com/jack/status/20", twitter.browserUrl)

        val i = post("https://x.com/i/web/status/20")
        assertEquals("https://x.com/i/status/20", i.browserUrl)
    }

    @Test
    fun `t co is a short link`() {
        val parsed = LinkParser.parse("https://t.co/AbCdEf")
        assertEquals(
            ParsedLink.Short(Platform.X, "https://t.co/AbCdEf", "https://t.co/AbCdEf"),
            parsed,
        )
    }

    @Test
    fun `x profile is unsupported`() {
        val parsed = LinkParser.parse("https://x.com/jack")
        assertTrue(parsed is ParsedLink.Unsupported)
        assertEquals(Platform.X, (parsed as ParsedLink.Unsupported).platform)
    }

    @Test
    fun `reddit post paths are accepted`() {
        val permalink = post(
            "https://www.reddit.com/r/Android/comments/1qi0flx/instabridges_first_public_post/?utm_source=share",
        )
        assertEquals("1qi0flx", permalink.postId)
        assertEquals(
            "https://www.reddit.com/r/Android/comments/1qi0flx/",
            permalink.browserUrl,
        )

        assertEquals(
            "1qi0flx",
            post("https://reddit.com/comments/1qi0flx.json").postId,
        )
    }

    @Test
    fun `reddit short links are accepted`() {
        val reddIt = post("https://redd.it/1qi0flx")
        assertEquals("https://www.reddit.com/comments/1qi0flx", reddIt.browserUrl)

        assertTrue(
            LinkParser.parse("https://www.reddit.com/r/Android/s/AbCd12") is ParsedLink.Short,
        )
        assertTrue(
            LinkParser.parse("https://reddit.app.link/AbCd12") is ParsedLink.Short,
        )
    }

    @Test
    fun `reddit subreddit is unsupported`() {
        val parsed = LinkParser.parse("https://www.reddit.com/r/Android/")
        assertTrue(parsed is ParsedLink.Unsupported)
        assertEquals(Platform.REDDIT, (parsed as ParsedLink.Unsupported).platform)
    }

    @Test
    fun `facebook post and group post are accepted`() {
        val postLink = post("https://www.facebook.com/facebook/posts/10158739848801729/?mibextid=tracking")
        assertEquals(Platform.FACEBOOK, postLink.platform)
        assertEquals("10158739848801729", postLink.postId)
        assertEquals(PostKind.POST, postLink.kind)
        assertEquals(
            "https://www.facebook.com/facebook/posts/10158739848801729/",
            postLink.browserUrl,
        )

        val group = post("https://m.facebook.com/groups/123456/posts/789012/")
        assertEquals("789012", group.postId)
        assertEquals("https://www.facebook.com/groups/123456/posts/789012/", group.browserUrl)

        val permalink = post("https://www.facebook.com/groups/123456/permalink/789012/")
        assertEquals("789012", permalink.postId)
        assertEquals("https://www.facebook.com/groups/123456/permalink/789012/", permalink.browserUrl)
    }

    @Test
    fun `facebook story and photo links keep only required parameters`() {
        val story = post("https://www.facebook.com/story.php?story_fbid=123456&id=789012&mibextid=tracking")
        assertEquals("123456", story.postId)
        assertEquals(
            "https://www.facebook.com/story.php?story_fbid=123456&id=789012",
            story.browserUrl,
        )

        val permalink = post("https://www.facebook.com/permalink.php?story_fbid=pfbid0AbC&id=42")
        assertEquals("pfbid0AbC", permalink.postId)
        assertEquals(
            "https://www.facebook.com/permalink.php?story_fbid=pfbid0AbC&id=42",
            permalink.browserUrl,
        )

        val photo = post("https://www.facebook.com/photo.php?fbid=111&set=a.222&id=333&theater")
        assertEquals("111", photo.postId)
        assertEquals(
            "https://www.facebook.com/photo.php?fbid=111&id=333&set=a.222",
            photo.browserUrl,
        )
    }

    @Test
    fun `facebook video reel and watch links are accepted`() {
        val video = post("https://www.facebook.com/page/videos/123456/?ref=sharing")
        assertEquals("123456", video.postId)
        assertEquals(PostKind.VIDEO, video.kind)
        assertEquals("https://www.facebook.com/page/videos/123456/", video.browserUrl)

        val reel = post("https://web.facebook.com/reel/987654/")
        assertEquals("987654", reel.postId)
        assertEquals(PostKind.REEL, reel.kind)
        assertEquals("https://www.facebook.com/reel/987654/", reel.browserUrl)

        val watch = post("https://www.facebook.com/watch/?v=555&ref=sharing")
        assertEquals("555", watch.postId)
        assertEquals(PostKind.VIDEO, watch.kind)
        assertEquals("https://www.facebook.com/watch/?v=555", watch.browserUrl)

        val videoPhp = post("https://www.facebook.com/video.php?v=777")
        assertEquals("https://www.facebook.com/video.php?v=777", videoPhp.browserUrl)
    }

    @Test
    fun `facebook video path with a title slug keeps the numeric id`() {
        val link = post(
            "https://www.facebook.com/3Demoniak/videos/best-print-ever-just-add-remy-and-" +
                "turn-your-girlfriend-into-a-french-chef-bon-ap/2000381177287760/?rdid=abc",
        )

        assertEquals("2000381177287760", link.postId)
        assertEquals(PostKind.VIDEO, link.kind)
        assertEquals(
            "https://www.facebook.com/3Demoniak/videos/2000381177287760/",
            link.browserUrl,
        )
    }

    @Test
    fun `tracking parameters are dropped on every platform`() {
        assertEquals(
            "https://www.instagram.com/p/ABC123/",
            post(
                "https://www.instagram.com/p/ABC123/?utm_source=ig_web_copy_link" +
                    "&igsh=tracking&fbclid=abc&gclid=def&exln=ghi",
            ).browserUrl,
        )
        assertEquals(
            "https://www.facebook.com/page/posts/123/",
            post(
                "https://www.facebook.com/page/posts/123/?mibextid=abc&utm_source=fb" +
                    "&fbclid=def&exln=ghi",
            ).browserUrl,
        )
        assertEquals(
            "https://www.tiktok.com/@user/video/123456",
            post(
                "https://www.tiktok.com/@user/video/123456?is_from_webapp=1&sender_device=pc" +
                    "&utm_source=copy",
            ).browserUrl,
        )
        assertEquals(
            "https://x.com/jack/status/20",
            post("https://x.com/jack/status/20?s=20&t=abc&utm_source=x").browserUrl,
        )
        assertEquals(
            "https://www.reddit.com/r/Android/comments/1qi0flx/",
            post(
                "https://www.reddit.com/r/Android/comments/1qi0flx/?utm_source=share" +
                    "&share_id=abc&fbclid=def",
            ).browserUrl,
        )
    }

    @Test
    fun `required facebook parameters survive the allowlist`() {
        val story = post(
            "https://www.facebook.com/story.php?story_fbid=111&id=222&mibextid=abc&fbclid=def",
        )
        assertEquals(
            "https://www.facebook.com/story.php?story_fbid=111&id=222",
            story.browserUrl,
        )
    }

    @Test
    fun `facebook short links are short and profiles are unsupported`() {
        listOf(
            "https://fb.watch/abcDEF/",
            "https://www.facebook.com/share/p/AbCdEf/",
            "https://www.facebook.com/share/v/AbCdEf/",
        ).forEach { url ->
            assertTrue(url, LinkParser.parse(url) is ParsedLink.Short)
        }

        val profile = LinkParser.parse("https://www.facebook.com/zuck")
        assertTrue(profile is ParsedLink.Unsupported)
        assertEquals(Platform.FACEBOOK, (profile as ParsedLink.Unsupported).platform)
        assertTrue(profile.reason.contains("profile"))

        val story = LinkParser.parse("https://www.facebook.com/stories/123/")
        assertTrue(story is ParsedLink.Unsupported)
        assertTrue((story as ParsedLink.Unsupported).reason.contains("login"))
    }

    @Test
    fun `facebook mobile and fb hosts are canonicalized`() {
        assertEquals(
            "https://www.facebook.com/page/posts/123/",
            post("https://m.facebook.com/page/posts/123/").browserUrl,
        )
        assertEquals(
            "https://www.facebook.com/page/posts/123/",
            post("https://fb.com/page/posts/123/").browserUrl,
        )
        assertEquals(
            "https://www.facebook.com/page/posts/123/",
            post("facebook.com/page/posts/123").browserUrl,
        )
    }

    @Test
    fun `facebook links without a post id are unsupported`() {
        listOf(
            "https://www.facebook.com/watch/",
            "https://www.facebook.com/story.php?id=123",
            "https://www.facebook.com/photo.php",
        ).forEach { url ->
            assertTrue(url, LinkParser.parse(url) is ParsedLink.Unsupported)
        }
    }

    @Test
    fun `unknown host is unsupported with the original url`() {
        val parsed = LinkParser.parse("https://example.com/post/1")
        assertTrue(parsed is ParsedLink.Unsupported)
        val unsupported = parsed as ParsedLink.Unsupported
        assertNull(unsupported.platform)
        assertEquals("https://example.com/post/1", unsupported.url)
    }

    @Test
    fun `lookalike host is not accepted`() {
        val parsed = LinkParser.parse("https://notinstagram.com/p/abc")
        assertTrue(parsed is ParsedLink.Unsupported)
        assertNull((parsed as ParsedLink.Unsupported).platform)
    }

    @Test
    fun `bare known host is completed with https`() {
        val link = post("instagram.com/p/ABC123")
        assertEquals("https://www.instagram.com/p/ABC123/", link.browserUrl)
    }

    @Test
    fun `url is extracted from share text and trailing punctuation is trimmed`() {
        val link = post("Look at this: https://x.com/jack/status/20, it is old.")
        assertEquals("20", link.postId)
    }

    @Test
    fun `http is upgraded to https`() {
        val link = post("http://www.reddit.com/r/Android/comments/1qi0flx/")
        assertEquals("https://www.reddit.com/r/Android/comments/1qi0flx/", link.browserUrl)
    }

    @Test
    fun `invalid and non web schemes are rejected`() {
        assertTrue(LinkParser.parse("hello") is ParsedLink.Invalid)
        assertTrue(LinkParser.parse("ftp://example.com/file") is ParsedLink.Invalid)
        assertTrue(LinkParser.parse("javascript:alert(1)") is ParsedLink.Invalid)
    }

    @Test
    fun `platform hint resolves supported and unsupported platform links`() {
        assertEquals(Platform.INSTAGRAM, LinkParser.platformHint("https://www.instagram.com/p/ABC123/"))
        assertEquals(Platform.FACEBOOK, LinkParser.platformHint("https://www.facebook.com/page/posts/123/"))
        assertEquals(Platform.X, LinkParser.platformHint("https://t.co/abc"))
        assertEquals(Platform.REDDIT, LinkParser.platformHint("https://www.reddit.com/r/Android/"))
        assertNull(LinkParser.platformHint("https://example.com/"))
    }
}