package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.ProfileBioLink
import dev.cappyt.socialview.core.ProfilePageRequest
import dev.cappyt.socialview.core.ProfilePostType
import dev.cappyt.socialview.core.ProfileStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramProfileParserTest {
    // The shape of the logged-out profile page: two Polaris scripts, linked by `pk`.
    private val userScript = """
        <script type="application/json" data-content-len="1" data-sjs>{"require":[["ScheduledServerJS","handle",null,[{"__bbox":{"result":{"data":{"xig_user_by_username":{"pk":"787132","username":"natgeo","profile_pic_url":"https:\/\/cdn.example\/pic.jpg?stp=s150x150&oh=a&oe=b","is_private":false,"biography":"Step into wonder 🌎","full_name":"National Geographic","is_verified":true,"bio_links":[{"title":"","url":"http:\/\/visitstore.bio\/natgeo","lynx_url":"https:\/\/l.instagram.com\/?u=tracked"},{"title":"Subscribe Here!","url":"ngm.example\/subscribe"},{"title":"Mail","url":"mailto:a@example.com"}],"follower_count":267184557,"following_count":195,"all_media_count":null,"id":"17841400573960012"}}}}}]]]}</script>
    """.trimIndent()

    private val timelineScript = """
        <script type="application/json" data-sjs>{"require":[[{"__bbox":{"result":{"data":{"xig_user_by_username":{"pk":"787132","polaris_ordered_timeline_connection":{"edges":[{"node":{"__typename":"XIGPolarisCarouselMedia","pk":"1","code":"Df147tCiAyB","accessibility_caption":"Photo by National Geographic.","caption":{"text":"Pictures of the Year"},"display_uri":"https:\/\/cdn.example\/1.jpg?oh=1&oe=2","media_type":8,"product_type":"carousel_container","carousel_media_count":3},"cursor":"c1"},{"node":{"__typename":"XIGPolarisVideoMedia","pk":"2","code":"DfAWxdIFmVB","accessibility_caption":null,"caption":{"text":"Everest"},"display_uri":"https:\/\/cdn.example\/2.jpg","media_type":2,"product_type":"clips"},"cursor":"c2"},{"node":{"__typename":"XIGPolarisImageMedia","pk":"3","code":"DeuOR-CjKE9","caption":null,"display_uri":"https:\/\/cdn.example\/3.jpg","media_type":1,"product_type":"feed"},"cursor":"c3"}],"page_info":{"end_cursor":"CURSOR1","has_next_page":true}},"id":"17841400573960012"}}}}}]]}</script>
    """.trimIndent()

    private val description = "<meta property=\"og:description\" content=\"267M Followers, 196 " +
        "Following, 32K Posts - See Instagram photos and videos from National Geographic " +
        "(&#064;natgeo)\" />"

    private val page = "<html><head>$description</head><body>$userScript$timelineScript</body></html>"

    @Test
    fun `the profile header comes from the user object and the open graph description`() {
        val profile = InstagramProfileParser.parse(page, "natgeo")!!

        assertEquals("@natgeo", profile.handle)
        assertEquals("https://www.instagram.com/natgeo/", profile.browserUrl)
        assertEquals("National Geographic", profile.fullName)
        assertEquals("Step into wonder 🌎", profile.biography)
        assertEquals("https://cdn.example/pic.jpg?stp=s150x150&oh=a&oe=b", profile.avatarUrl)
        assertTrue(profile.verified)
        assertFalse(profile.private)
        assertEquals(
            listOf(
                ProfileStat("32K", "Posts"),
                ProfileStat("267M", "Followers"),
                ProfileStat("195", "Following"),
            ),
            profile.stats,
        )
    }

    @Test
    fun `bio links keep the direct web url and drop other schemes`() {
        val profile = InstagramProfileParser.parse(page, "natgeo")!!

        assertEquals(
            listOf(
                ProfileBioLink(title = null, url = "http://visitstore.bio/natgeo"),
                ProfileBioLink(title = "Subscribe Here!", url = "https://ngm.example/subscribe"),
            ),
            profile.links,
        )
    }

    @Test
    fun `timeline posts keep their order, type and canonical link`() {
        val posts = InstagramProfileParser.parse(page, "natgeo")!!.posts

        assertEquals(
            listOf(
                "https://www.instagram.com/p/Df147tCiAyB/",
                "https://www.instagram.com/reel/DfAWxdIFmVB/",
                "https://www.instagram.com/p/DeuOR-CjKE9/",
            ),
            posts.map { it.browserUrl },
        )
        assertEquals(
            listOf(ProfilePostType.CAROUSEL, ProfilePostType.VIDEO, ProfilePostType.IMAGE),
            posts.map { it.type },
        )
        assertEquals("https://cdn.example/1.jpg?oh=1&oe=2", posts[0].thumbnailUrl)
        assertEquals("Photo by National Geographic.", posts[0].text)
        assertEquals("Everest", posts[1].text)
        assertNull(posts[2].text)
    }

    @Test
    fun `a repeated or malformed shortcode gives no second tile`() {
        val body = userScript + timelineScript
            .replace("\"code\":\"DfAWxdIFmVB\"", "\"code\":\"Df147tCiAyB\"")
            .replace("\"code\":\"DeuOR-CjKE9\"", "\"code\":\"x/../../accounts/login\"")

        val posts = InstagramProfileParser.parse(body, "natgeo")!!.posts

        assertEquals(listOf("https://www.instagram.com/p/Df147tCiAyB/"), posts.map { it.browserUrl })
    }

    @Test
    fun `a count that is not a number is left out`() {
        val body = userScript.replace("\"follower_count\":267184557", "\"follower_count\":\"1,234\"")

        val profile = InstagramProfileParser.parse(body, "natgeo")!!

        assertEquals(listOf(ProfileStat("195", "Following")), profile.stats)
    }

    @Test
    fun `bio links are trimmed and need a host`() {
        fun linksOf(url: String) = InstagramProfileParser.parse(
            userScript.replace("http:\\/\\/visitstore.bio\\/natgeo", url),
            "natgeo",
        )!!.links.map { it.url }

        assertEquals("https://sp.example", linksOf(" https://sp.example").first())
        assertEquals("https://UP.example/x", linksOf("HTTPS://UP.example/x").first())
        assertEquals("https://example.com:8080/x", linksOf("example.com:8080/x").first())
        assertEquals(listOf("https://ngm.example/subscribe"), linksOf("//evil.example"))
    }

    @Test
    fun `the next page uses the user id and the end cursor`() {
        val profile = InstagramProfileParser.parse(page, "NatGeo")!!

        assertEquals(ProfilePageRequest("17841400573960012", "CURSOR1"), profile.nextPage)
    }

    @Test
    fun `a private profile has a header and no posts`() {
        val body = userScript.replace("\"is_private\":false", "\"is_private\":true")

        val profile = InstagramProfileParser.parse(body, "natgeo")!!

        assertTrue(profile.private)
        assertTrue(profile.posts.isEmpty())
        assertNull(profile.nextPage)
    }

    @Test
    fun `the timeline of another account is ignored`() {
        val body = userScript + timelineScript.replace("\"pk\":\"787132\"", "\"pk\":\"42\"")

        val profile = InstagramProfileParser.parse(body, "natgeo")!!

        assertTrue(profile.posts.isEmpty())
        assertNull(profile.nextPage)
    }

    @Test
    fun `a page without the requested account has no profile`() {
        val missing = """<script>{"data":{"xig_user_by_username":null},"extensions":{"is_final":true}}</script>"""

        assertNull(InstagramProfileParser.parse(missing, "natgeo"))
        assertNull(InstagramProfileParser.parse(page, "nasa"))
        assertNull(InstagramProfileParser.parse("<html><title>Instagram</title></html>", "natgeo"))
    }

    @Test
    fun `a timeline page has posts and the next cursor`() {
        val body = """
            {"data":{"node":{"__typename":"User","polaris_ordered_timeline_connection":{"edges":[
              {"node":{"code":"DgJGfHpAS-1","display_uri":"https:\/\/cdn.example\/4.jpg","media_type":2,"product_type":"clips"},"cursor":"c4"}
            ],"page_info":{"end_cursor":"CURSOR2","has_next_page":true}},"id":"17841400573960012"}}}
        """.trimIndent()

        val result = InstagramProfileParser.parsePage(body, "17841400573960012")!!

        assertEquals("https://www.instagram.com/reel/DgJGfHpAS-1/", result.posts.single().browserUrl)
        assertEquals(ProfilePageRequest("17841400573960012", "CURSOR2"), result.next)
    }

    @Test
    fun `the last timeline page has no next cursor`() {
        val body = """
            {"data":{"node":{"id":"1","polaris_ordered_timeline_connection":{"edges":[],
            "page_info":{"end_cursor":null,"has_next_page":false}}}}}
        """.trimIndent()

        val result = InstagramProfileParser.parsePage(body, "1")!!

        assertTrue(result.posts.isEmpty())
        assertNull(result.next)
    }

    @Test
    fun `an answer without the timeline of the account is not a page`() {
        val other = """{"data":{"node":{"id":"OTHER","polaris_ordered_timeline_connection":{"edges":[]}}}}"""
        assertNull(InstagramProfileParser.parsePage(other, "1"))
        assertNull(InstagramProfileParser.parsePage("""{"data":{"node":null}}""", "1"))
        assertNull(InstagramProfileParser.parsePage("<!DOCTYPE html><html></html>", "1"))
    }
}
