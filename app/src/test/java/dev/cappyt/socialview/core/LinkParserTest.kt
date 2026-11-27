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
    fun `instagram profile links are profiles without tracking`() {
        val expected = ProfileLink(
            platform = Platform.INSTAGRAM,
            username = "nasa",
            browserUrl = "https://www.instagram.com/nasa/",
            originalUrl = "https://www.instagram.com/NASA?igsh=abc123&utm_source=qr",
        )

        assertEquals(
            ParsedLink.Profile(expected),
            LinkParser.parse("https://www.instagram.com/NASA?igsh=abc123&utm_source=qr"),
        )
        listOf(
            "https://instagram.com/nasa/",
            "https://www.instagram.com/_u/nasa",
            "https://www.instagram.com/nasa/reels/",
            "https://www.instagram.com/nasa/tagged",
        ).forEach { url ->
            val parsed = LinkParser.parse(url)
            assertTrue(url, parsed is ParsedLink.Profile)
            assertEquals(url, expected.browserUrl, (parsed as ParsedLink.Profile).link.browserUrl)
        }
    }

    @Test
    fun `instagram pages that are not a profile are unsupported`() {
        listOf(
            "https://www.instagram.com/",
            "https://www.instagram.com/explore/",
            "https://www.instagram.com/accounts/login/",
            "https://www.instagram.com/reels/",
            "https://www.instagram.com/nasa/followers/",
            "https://www.instagram.com/a-b/",
            "https://www.instagram.com/../",
            "https://www.instagram.com/.../",
            "https://www.instagram.com/na..sa/",
            "https://www.instagram.com/nasa./",
            "https://www.instagram.com/consent/",
            "https://www.instagram.com/your_activity/",
        ).forEach { url ->
            val parsed = LinkParser.parse(url)
            assertTrue(url, parsed is ParsedLink.Unsupported)
            assertEquals(Platform.INSTAGRAM, (parsed as ParsedLink.Unsupported).platform)
        }
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

    private fun profile(url: String): ProfileLink {
        val parsed = LinkParser.parse(url)
        assertTrue("$url -> $parsed", parsed is ParsedLink.Profile)
        return (parsed as ParsedLink.Profile).link
    }

    @Test
    fun `tiktok profile links use the lowercase name`() {
        val link = profile("https://www.tiktok.com/@Scout2015?lang=it&_t=abc")
        assertEquals(Platform.TIKTOK, link.platform)
        assertEquals("scout2015", link.username)
        assertEquals("https://www.tiktok.com/@scout2015", link.browserUrl)

        listOf(
            "https://www.tiktok.com/@scout2015/live",
            "https://www.tiktok.com/tag/cats",
            "https://www.tiktok.com/discover",
        ).forEach { url ->
            assertTrue(url, LinkParser.parse(url) is ParsedLink.Unsupported)
        }
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
    fun `x profile links and their tabs are profiles`() {
        listOf(
            "https://x.com/jack",
            "https://twitter.com/jack/?ref_src=twsrc%5Etfw",
            "https://mobile.twitter.com/jack/with_replies",
            "https://x.com/jack/media",
        ).forEach { url ->
            val link = profile(url)
            assertEquals(url, Platform.X, link.platform)
            assertEquals(url, "https://x.com/jack", link.browserUrl)
        }
    }

    @Test
    fun `x pages that are not an account are unsupported`() {
        listOf(
            "https://x.com/home",
            "https://x.com/explore",
            "https://x.com/i/lists/123",
            "https://x.com/search?q=cats",
            "https://x.com/a_name_longer_than_15",
            "https://x.com/_",
            "https://x.com/jack/followers",
        ).forEach { url ->
            val parsed = LinkParser.parse(url)
            assertTrue(url, parsed is ParsedLink.Unsupported)
            assertEquals(Platform.X, (parsed as ParsedLink.Unsupported).platform)
        }
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
    fun `reddit user links are profiles and user posts are not`() {
        listOf(
            "https://www.reddit.com/user/spez",
            "https://old.reddit.com/u/spez/",
            "https://www.reddit.com/user/spez/submitted/?sort=top",
        ).forEach { url ->
            val link = profile(url)
            assertEquals(url, Platform.REDDIT, link.platform)
            assertEquals(url, "spez", link.username)
            assertEquals(url, "https://www.reddit.com/user/spez/", link.browserUrl)
        }
        listOf(
            "https://www.reddit.com/user/spez/comments/",
            "https://www.reddit.com/user/",
        ).forEach { url ->
            assertTrue(url, LinkParser.parse(url) is ParsedLink.Unsupported)
        }
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
    fun `facebook short links are short and stories are unsupported`() {
        listOf(
            "https://fb.watch/abcDEF/",
            "https://www.facebook.com/share/p/AbCdEf/",
            "https://www.facebook.com/share/v/AbCdEf/",
        ).forEach { url ->
            assertTrue(url, LinkParser.parse(url) is ParsedLink.Short)
        }

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
    fun `facebook page links are profiles`() {
        val vanity = profile("https://m.facebook.com/NASA/?ref=share&mibextid=abc")
        assertEquals(Platform.FACEBOOK, vanity.platform)
        assertEquals("NASA", vanity.username)
        assertEquals("https://www.facebook.com/NASA/", vanity.browserUrl)

        val numeric = profile("https://www.facebook.com/profile.php?id=100044561550831&sk=about")
        assertEquals("100044561550831", numeric.username)
        assertEquals("https://www.facebook.com/profile.php?id=100044561550831", numeric.browserUrl)

        val people = profile("https://www.facebook.com/people/The-Human-Timeline/61590395142848")
        assertEquals("61590395142848", people.username)
        assertEquals("https://www.facebook.com/profile.php?id=61590395142848", people.browserUrl)
        assertEquals(
            people.browserUrl,
            profile("https://www.facebook.com/people/John%20Doe%3Fa%23b/61590395142848/").browserUrl,
        )
    }

    @Test
    fun `facebook pages that are not a page are unsupported`() {
        listOf(
            "https://www.facebook.com/",
            "https://www.facebook.com/marketplace/",
            "https://www.facebook.com/groups/123/",
            "https://www.facebook.com/login.php",
            "https://www.facebook.com/profile.php",
            "https://www.facebook.com/NASA/photos/",
            "https://www.facebook.com/home",
            "https://www.facebook.com/feed/",
            "https://www.facebook.com/photos/",
            "https://www.facebook.com/r.php",
            "https://www.facebook.com/policy.php",
        ).forEach { url ->
            val parsed = LinkParser.parse(url)
            assertTrue("$url -> $parsed", parsed is ParsedLink.Unsupported)
        }
    }

    @Test
    fun `an author handle gives a profile link only when the app can open it`() {
        assertEquals("https://www.instagram.com/natgeo/", LinkParser.profileUrl(Platform.INSTAGRAM, "@natgeo"))
        assertEquals("https://x.com/NASA", LinkParser.profileUrl(Platform.X, "@NASA"))
        assertEquals("https://www.tiktok.com/@nasa", LinkParser.profileUrl(Platform.TIKTOK, "@NASA"))
        assertEquals("https://www.facebook.com/NASA/", LinkParser.profileUrl(Platform.FACEBOOK, "@NASA"))
        assertNull(LinkParser.profileUrl(Platform.INSTAGRAM, "National Geographic"))
        assertNull(LinkParser.profileUrl(Platform.X, "@explore"))
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