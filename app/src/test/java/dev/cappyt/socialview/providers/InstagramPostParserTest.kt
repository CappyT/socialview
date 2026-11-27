package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.PostMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramPostParserTest {
    private val videoJson = """
        {
          "gql_data": {
            "shortcode_media": {
              "__typename": "GraphVideo",
              "id": "3935504429539881172",
              "shortcode": "DaduG1us3DU",
              "is_video": true,
              "display_url": "https://cdn.example/cover.jpg?_nc_cat=109&oh=cover&oe=6AE85FDE",
              "video_url": "https://cdn.example/video.mp4?_nc_cat=106&oh=video&oe=6AE86C6F",
              "dimensions": {"width": 1206, "height": 2144},
              "video_duration": 27.7,
              "edge_media_to_caption": {
                "edges": [{"node": {"text": "Telefonino gettato per terra"}}]
              },
              "edge_media_to_comment": {"count": 5829},
              "edge_liked_by": {"count": 43148},
              "owner": {"id": "429150874", "username": "ilsecoloxix"}
            }
          }
        }
    """.trimIndent()

    @Test
    fun `a video post exposes native media and counts`() {
        val post = InstagramPostParser.parse(videoJson)!!

        assertEquals("3935504429539881172", post.mediaId)
        assertEquals("DaduG1us3DU", post.shortcode)
        assertEquals("@ilsecoloxix", post.handle)
        assertNull(post.author)
        assertEquals("Telefonino gettato per terra", post.caption)
        assertEquals(5829, post.commentCount)
        assertEquals(43148, post.likeCount)
        val media = post.media.single()
        assertEquals(PostMediaType.VIDEO, media.type)
        assertEquals(
            "https://cdn.example/video.mp4?_nc_cat=106&oh=video&oe=6AE86C6F",
            media.url,
        )
        assertEquals(
            "https://cdn.example/cover.jpg?_nc_cat=109&oh=cover&oe=6AE85FDE",
            media.thumbnailUrl,
        )
        assertEquals(1206, media.width)
        assertEquals(2144, media.height)
        assertEquals(27.7, media.durationSeconds)
    }

    @Test
    fun `context json is decoded from the embed document`() {
        val document = embedDocument(videoJson)

        val post = InstagramPostParser.parse(document)!!

        assertEquals("@ilsecoloxix", post.handle)
        assertEquals(PostMediaType.VIDEO, post.media.single().type)
        assertEquals(
            "https://cdn.example/video.mp4?_nc_cat=106&oh=video&oe=6AE86C6F",
            post.media.single().url,
        )
    }

    @Test
    fun `a mixed carousel keeps image and video children in order`() {
        val post = InstagramPostParser.parse(carouselJson)!!

        assertEquals("@creators", post.handle)
        assertEquals("Carousel caption", post.caption)
        assertEquals(
            listOf(PostMediaType.IMAGE, PostMediaType.VIDEO, PostMediaType.IMAGE),
            post.media.map { it.type },
        )
        assertEquals("https://cdn.example/a.jpg?oh=11&oe=22", post.media[0].url)
        assertEquals("https://cdn.example/b.mp4?oh=33&oe=44", post.media[1].url)
        assertEquals("https://cdn.example/b.jpg?oh=55&oe=66", post.media[1].thumbnailUrl)
    }

    @Test
    fun `a video without a public stream keeps its cover image`() {
        val post = InstagramPostParser.parse(
            """
            {
              "is_video": true,
              "id": "1",
              "display_url": "https://cdn.example/cover.jpg?oh=1&oe=2",
              "owner": {"username": "nasa"}
            }
            """.trimIndent(),
        )!!

        assertTrue(post.videoUnavailable)
        assertEquals(PostMediaType.IMAGE, post.media.single().type)
        assertEquals("https://cdn.example/cover.jpg?oh=1&oe=2", post.media.single().url)
    }

    @Test
    fun `public comments are parsed when a source returns them`() {
        val comments = InstagramPostParser.parseComments(
            """
            {
              "comments": [
                {"user": {"username": "alice"}, "text": "First", "comment_like_count": 12},
                {"user": {"username": "bob"}, "text": "Second"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(2, comments.size)
        assertEquals("@alice", comments[0].author)
        assertEquals("First", comments[0].body)
        assertEquals(12, comments[0].score)
        assertEquals("@bob", comments[1].author)
        assertNull(comments[1].score)
    }

    @Test
    fun `comment arrays inside the media document are parsed`() {
        val post = InstagramPostParser.parse(
            """
            {
              "id": "1",
              "display_url": "https://cdn.example/a.jpg",
              "owner": {"username": "nasa"},
              "comments": [
                {"user": {"username": "alice"}, "text": "Nice"}
              ]
            }
            """.trimIndent(),
        )!!

        assertEquals(1, post.comments.size)
        assertEquals("@alice", post.comments.single().author)
        assertEquals("Nice", post.comments.single().body)
    }

    @Test
    fun `open graph metadata is a fallback for a blocked document`() {
        val post = InstagramPostParser.parse(
            """
            <html><head>
            <meta property="og:title" content="Il Secolo XIX on Instagram: &quot;Caption text&quot;" />
            <meta property="og:image" content="https://cdn.example/cover.jpg?oh=1&amp;oe=2" />
            <meta property="og:url" content="https://www.instagram.com/ilsecoloxix/reel/DaduG1us3DU/" />
            </head></html>
            """.trimIndent(),
            "DaduG1us3DU",
        )!!

        assertEquals("Il Secolo XIX", post.author)
        assertEquals("@ilsecoloxix", post.handle)
        assertEquals("Caption text", post.caption)
        assertEquals("DaduG1us3DU", post.shortcode)
        assertEquals("https://cdn.example/cover.jpg?oh=1&oe=2", post.media.single().url)
    }

    @Test
    fun `the current polaris item shape is supported`() {
        val post = InstagramPostParser.parse(
            """
            <html><script>
            {"xdt_api__v1__media__shortcode__web_info": {"items": [{
              "code": "ABC123",
              "user": {"username": "nasa"},
              "caption": {"text": "Moon"},
              "image_versions2": {"candidates": [
                {"url": "https://cdn.example/small.jpg?oh=1", "width": 640, "height": 640},
                {"url": "https://cdn.example/large.jpg?oh=2", "width": 1080, "height": 1080}
              ]},
              "comment_count": 4,
              "like_count": 5
            }]}}
            </script></html>
            """.trimIndent(),
        )!!

        assertEquals("@nasa", post.handle)
        assertEquals("Moon", post.caption)
        assertEquals(4, post.commentCount)
        assertEquals(5, post.likeCount)
        assertEquals(
            "https://cdn.example/large.jpg?oh=2",
            post.media.single().url,
        )
    }

    @Test
    fun `an unquoted open graph title keeps the caption`() {
        val post = InstagramPostParser.parse(
            """
            <html><head>
            <meta property="og:title" content="Il Secolo XIX on Instagram: Sunset over the sea" />
            <meta property="og:image" content="https://cdn.example/cover.jpg" />
            <meta property="og:url" content="https://www.instagram.com/ilsecoloxix/p/ABC123/" />
            </head></html>
            """.trimIndent(),
            "ABC123",
        )!!

        assertEquals("Il Secolo XIX", post.author)
        assertEquals("Sunset over the sea", post.caption)
    }

    @Test
    fun `the open graph description is the caption fallback`() {
        val post = InstagramPostParser.parse(
            """
            <html><head>
            <meta property="og:title" content="Il Secolo XIX on Instagram: " />
            <meta property="og:description" content="Description fallback" />
            <meta property="og:image" content="https://cdn.example/cover.jpg" />
            </head></html>
            """.trimIndent(),
            "ABC123",
        )!!

        assertEquals("Description fallback", post.caption)
    }

    @Test
    fun `a candidate with a different shortcode is rejected`() {
        assertNull(InstagramPostParser.parse(videoJson, "AnotherCode"))
        assertNotNull(InstagramPostParser.parse(videoJson, "DaduG1us3DU"))
    }

    @Test
    fun `a canonical page exposes the polaris video behind a copyright blocked embed`() {
        val document = """
            <!DOCTYPE html><html><head>
            <meta property="og:image" content="https://cdn.example/cover.jpg?oh=1&amp;oe=2" />
            </head><body>
            <script type="application/json" nonce="x" data-sjs>{"require":[[["ScheduledServerJS"],{"__bbox":{"require":[[["PolarisMedia"],{"__bbox":{"result":{"data":{"xig_polaris_media":{"__typename":"XIGPolarisVideoMedia","pk":"4015515887196872310","code":"De5-myeTp52","if_not_gated_logged_out":{"__typename":"XIGPolarisVideoMedia","user":{"username":"cuurley","full_name":"CUURLEY (歌厉)(Bo)"},"pk":"4015515887196872310","code":"De5-myeTp52","media_type":2,"product_type":"clips","caption":{"text":"2067 dystopian"},"comment_count":522,"like_count":46591,"original_width":1440,"original_height":2560,"display_uri":"https://cdn.example/cover.jpg?oh=1&oe=2","image_versions2":{"candidates":[{"url":"https://cdn.example/cover-small.jpg?oh=1&oe=2","width":640},{"url":"https://cdn.example/cover.jpg?oh=1&oe=2","width":1440}]},"video_versions":[{"type":101,"url":"https://cdn.example/reel.mp4?oh=3&oe=4"}]}}}}}}]]}}]]}}</script>
            </body></html>
        """.trimIndent()

        val post = InstagramPostParser.parse(document, "De5-myeTp52")!!

        assertEquals("4015515887196872310", post.mediaId)
        assertEquals("De5-myeTp52", post.shortcode)
        assertEquals("@cuurley", post.handle)
        assertEquals("CUURLEY (歌厉)(Bo)", post.author)
        assertEquals("2067 dystopian", post.caption)
        assertEquals(522, post.commentCount)
        assertEquals(46591, post.likeCount)
        assertEquals(PostMediaType.VIDEO, post.media.single().type)
        assertEquals("https://cdn.example/reel.mp4?oh=3&oe=4", post.media.single().url)
        assertEquals("https://cdn.example/cover.jpg?oh=1&oe=2", post.media.single().thumbnailUrl)
        assertFalse(post.videoUnavailable)
    }

    @Test
    fun `an error shell yields no post`() {
        assertNull(
            InstagramPostParser.parse(
                "<html><head><title>Page Not Found</title></head><body></body></html>",
            ),
        )
    }

    private fun embedDocument(contextJson: String): String {
        val escaped = contextJson
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("/", "\\/")
        return """<html><script>require(["PolarisEmbedSimple","init",[],
            [{"contextJSON":"$escaped"}]]);</script></html>"""
    }

    private val carouselJson = """
        {
          "gql_data": {
            "shortcode_media": {
              "__typename": "GraphSidecar",
              "id": "100",
              "shortcode": "DeRxxZzkZu1",
              "is_video": false,
              "edge_media_to_caption": {
                "edges": [{"node": {"text": "Carousel caption"}}]
              },
              "edge_media_to_comment": {"count": 1268},
              "owner": {"username": "creators"},
              "edge_sidecar_to_children": {
                "edges": [
                  {"node": {
                    "display_url": "https://cdn.example/a.jpg?oh=11&oe=22",
                    "dimensions": {"width": 1440, "height": 1800}
                  }},
                  {"node": {
                    "display_url": "https://cdn.example/b.jpg?oh=55&oe=66",
                    "is_video": true,
                    "video_url": "https://cdn.example/b.mp4?oh=33&oe=44",
                    "dimensions": {"width": 1080, "height": 864}
                  }},
                  {"node": {
                    "display_url": "https://cdn.example/c.jpg?oh=77&oe=88",
                    "dimensions": {"width": 1170, "height": 1463}
                  }}
                ]
              }
            }
          }
        }
    """.trimIndent()
}