package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.LinkParser
import dev.cappyt.socialview.core.ParsedLink
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.PostKind
import dev.cappyt.socialview.core.PostLink
import dev.cappyt.socialview.core.PostMediaType
import dev.cappyt.socialview.net.HttpFetcher
import dev.cappyt.socialview.net.HttpResult
import java.io.IOException
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderBehaviourTest {
    private class FakeFetcher(
        private val onGet: (String) -> HttpResult = { throw IOException("Unexpected GET $it") },
        private val onResolve: (String) -> String = { throw IOException("Unexpected resolve $it") },
    ) : HttpFetcher {
        val gets = mutableListOf<String>()
        val resolves = mutableListOf<String>()

        override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
            gets += url
            return onGet(url)
        }

        override suspend fun resolve(url: String, headers: Map<String, String>): String {
            resolves += url
            return onResolve(url)
        }
    }

    private fun link(
        url: String,
        platform: Platform,
        postId: String,
        kind: PostKind = PostKind.POST,
    ): PostLink = PostLink(
        platform = platform,
        postId = postId,
        browserUrl = url,
        originalUrl = url,
        kind = kind,
    )

    private fun commentEmbedScript(url: String): String {
        val prefix = "data:text/html;charset=utf-8,"
        assertTrue("Unexpected fallback URL: $url", url.startsWith(prefix))
        return URLDecoder.decode(url.removePrefix(prefix), "UTF-8")
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (error: Throwable) {
            return error
        }
        throw AssertionError("Expected the call to fail.")
    }

    private val tiktokOEmbed = """
        {
          "title": "Scramble up ur name",
          "author_name": "Tiff",
          "author_url": "https://www.tiktok.com/@scout2015",
          "thumbnail_url": "https://p16-sign-va.tiktokcdn.com/cover.jpg",
          "html": "<iframe src=\"https://www.tiktok.com/player/v1/6718335390845095173?embed_from=oembed&amp;music_info=1\"></iframe>"
        }
    """.trimIndent()

    private val xOEmbed = """
        {
          "author_name": "jack",
          "author_url": "https://x.com/jack",
          "html": "<blockquote><p>just setting up my twttr</p>&mdash; jack (@jack)</blockquote>"
        }
    """.trimIndent()

    private val instagramOEmbed = """
        {
          "title": "Telefonino gettato per terra",
          "author_name": "ilsecoloxix",
          "author_url": "https://www.instagram.com/ilsecoloxix",
          "thumbnail_url": "https://scontent.example/thumb.jpg",
          "media_id": "3935504429539881172_429150874"
        }
    """.trimIndent()

    private val instagramCoverUrl =
        "https://cdn.example/v/cover.jpg?_nc_cat=109&oh=cover&oe=6AE85FDE"
    private val instagramVideoUrl =
        "https://cdn.example/o1/video.mp4?_nc_cat=106&oh=video&oe=6AE86C6F"

    private val instagramVideoDocument = embedDocument(
        """
        {
          "gql_data": {
            "shortcode_media": {
              "__typename": "GraphVideo",
              "id": "3935504429539881172",
              "shortcode": "DaduG1us3DU",
              "is_video": true,
              "display_url": "$instagramCoverUrl",
              "video_url": "$instagramVideoUrl",
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
        """.trimIndent(),
    )

    private val instagramCopyrightCoverUrl =
        "https://cdn.example/v/copyright-cover.jpg?_nc_cat=108&oh=cover&oe=6AE90062"
    private val instagramPolarisVideoUrl =
        "https://cdn.example/o1/polaris.mp4?_nc_cat=110&oh=polaris&oe=6AE90000"

    private val instagramCopyrightBlockedEmbed = embedDocument(
        """
        {
          "context": {
            "type": "GraphVideo",
            "shortcode": "De5-myeTp52",
            "copyright_blocked": true
          },
          "gql_data": {
            "shortcode_media": {
              "__typename": "GraphVideo",
              "id": "4015515887196872310",
              "shortcode": "De5-myeTp52",
              "is_video": true,
              "display_url": "$instagramCopyrightCoverUrl",
              "video_duration": 51.105,
              "edge_media_to_caption": {
                "edges": [{"node": {"text": "2067 dystopian"}}]
              },
              "edge_media_to_comment": {"count": 522},
              "edge_liked_by": {"count": 46591},
              "owner": {"id": "3057368011", "username": "cuurley"}
            }
          }
        }
        """.trimIndent(),
    )

    private val instagramPolarisPage = """
        <!DOCTYPE html><html><head>
        <meta property="og:title" content="CUURLEY (&#x6b4c;&#x5389;)(Bo) on Instagram: &quot;2067 dystopian&quot;" />
        <meta property="og:image" content="$instagramCopyrightCoverUrl" />
        </head><body>
        <script type="application/json" nonce="x" data-content-len="57176" data-sjs>{"require":[[["ScheduledServerJS"],{"__bbox":{"require":[[["PolarisMedia"],{"__bbox":{"result":{"data":{"xig_polaris_media":{"__typename":"XIGPolarisVideoMedia","pk":"4015515887196872310","code":"De5-myeTp52","if_not_gated_logged_out":{"__typename":"XIGPolarisVideoMedia","user":{"username":"cuurley","full_name":"CUURLEY (歌厉)(Bo)"},"pk":"4015515887196872310","code":"De5-myeTp52","media_type":2,"product_type":"clips","caption":{"text":"2067 dystopian"},"comment_count":522,"like_count":46591,"original_width":1440,"original_height":2560,"display_uri":"$instagramCopyrightCoverUrl","image_versions2":{"candidates":[{"url":"$instagramCopyrightCoverUrl","width":1440}]},"video_versions":[{"type":101,"url":"$instagramPolarisVideoUrl"}]}}}}}}]]}}]]}}</script>
        </body></html>
    """.trimIndent()

    private val instagramCarouselDocument = embedDocument(
        """
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
        """.trimIndent(),
    )

    private val facebookVideoUrl =
        "https://video.example/hd.mp4?_nc_cat=100&oh=00_AQ&oe=6AE84E14&efg=eyJ2%3D"

    private val redditFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <category term="Android" label="r/Android"/>
          <entry>
            <author><name>/u/kbDL-</name></author>
            <content type="html">&lt;!-- SC_OFF --&gt;&lt;div class="md"&gt;&lt;p&gt;Pixel update body&lt;/p&gt;&lt;/div&gt;&lt;!-- SC_ON --&gt;</content>
            <id>t3_abc123</id>
            <link href="https://www.reddit.com/r/Android/comments/abc123/google_pixel_update/"/>
            <title>Google Pixel update</title>
          </entry>
          <entry>
            <author><name>/u/bob</name></author>
            <content type="html">&lt;!-- SC_OFF --&gt;&lt;div class="md"&gt;&lt;p&gt;First comment&lt;/p&gt;&lt;/div&gt;</content>
            <id>t1_1</id>
            <link href="https://www.reddit.com/r/Android/comments/abc123/google_pixel_update/def/"/>
            <title>/u/bob on Google Pixel update</title>
          </entry>
        </feed>
    """.trimIndent()

    private val facebookReelPage = """
        <html><head>
        <meta property="og:title" content="22M views &#xb7; 367K reactions | Best print EVER! | 3Demoniak" />
        <meta property="og:description" content="Best print EVER! Just add Remy." />
        <meta property="og:url" content="https://www.facebook.com/3Demoniak/videos/best-print/2000381177287760/" />
        <meta property="og:image" content="https://cdn.example/cover.jpg?oh=1&amp;oe=2" />
        <link rel="canonical" href="https://www.facebook.com/3Demoniak/videos/best-print/2000381177287760/" />
        </head><body><script>
        {"browser_native_hd_url":"https:\/\/video.example\/hd.mp4?_nc_cat=100&oh=00_AQ&oe=6AE84E14&efg=eyJ2\u00253D"}
        </script></body></html>
    """.trimIndent()

    private fun embedDocument(contextJson: String): String {
        val escaped = contextJson
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("/", "\\/")
        return """<html><script>require(["PolarisEmbedSimple","init",[],
            [{"contextJSON":"$escaped"}]]);</script></html>"""
    }

    @Test
    fun `instagram renders a public video natively`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { url ->
            if (url.contains("/embed/captioned/")) {
                HttpResult(200, instagramVideoDocument, "text/html")
            } else {
                HttpResult(200, "{}", "application/json")
            }
        })
        val details = InstagramProvider(fetcher).load(
            link(
                "https://www.instagram.com/reel/DaduG1us3DU/",
                Platform.INSTAGRAM,
                "DaduG1us3DU",
                PostKind.REEL,
            ),
        )

        assertEquals(Platform.INSTAGRAM, details.platform)
        assertNull(details.embed)
        assertEquals("@ilsecoloxix", details.handle)
        assertEquals("Telefonino gettato per terra", details.text)
        val media = details.media.single()
        assertEquals(PostMediaType.VIDEO, media.type)
        assertEquals(instagramVideoUrl, media.url)
        assertEquals(instagramCoverUrl, media.thumbnailUrl)
        assertEquals(1206, media.width)
        assertEquals(2144, media.height)
        assertEquals(27.7, media.durationSeconds)
        assertEquals(
            "https://www.instagram.com/reel/DaduG1us3DU/embed/captioned/",
            fetcher.gets.first(),
        )
    }

    @Test
    fun `instagram renders a mixed carousel natively`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { url ->
            if (url.contains("/embed/captioned/")) {
                HttpResult(200, instagramCarouselDocument, "text/html")
            } else {
                HttpResult(200, "{}", "application/json")
            }
        })
        val details = InstagramProvider(fetcher).load(
            link("https://www.instagram.com/p/DeRxxZzkZu1/", Platform.INSTAGRAM, "DeRxxZzkZu1"),
        )

        assertNull(details.embed)
        assertEquals("@creators", details.handle)
        assertEquals("Carousel caption", details.text)
        assertEquals(
            listOf(PostMediaType.IMAGE, PostMediaType.VIDEO, PostMediaType.IMAGE),
            details.media.map { it.type },
        )
        assertEquals("https://cdn.example/a.jpg?oh=11&oe=22", details.media[0].url)
        assertEquals("https://cdn.example/b.mp4?oh=33&oe=44", details.media[1].url)
        assertEquals("https://cdn.example/b.jpg?oh=55&oe=66", details.media[1].thumbnailUrl)
        assertEquals(0, details.initialMediaIndex)
    }

    @Test
    fun `instagram starts the carousel at the requested image index`() = runBlocking {
        val parsed = LinkParser.parse("https://www.instagram.com/p/DeRxxZzkZu1/?img_index=3")
        val fetcher = FakeFetcher(onGet = { url ->
            if (url.contains("/embed/captioned/")) {
                HttpResult(200, instagramCarouselDocument, "text/html")
            } else {
                HttpResult(200, "{}", "application/json")
            }
        })
        val details = InstagramProvider(fetcher).load((parsed as ParsedLink.Post).link)

        assertEquals(2, details.initialMediaIndex)
        assertEquals(3, details.media.size)
    }

    @Test
    fun `instagram clamps an out of range image index`() = runBlocking {
        val parsed = LinkParser.parse("https://www.instagram.com/p/DeRxxZzkZu1/?img_index=9")
        val fetcher = FakeFetcher(onGet = { url ->
            if (url.contains("/embed/captioned/")) {
                HttpResult(200, instagramCarouselDocument, "text/html")
            } else {
                HttpResult(200, "{}", "application/json")
            }
        })
        val details = InstagramProvider(fetcher).load((parsed as ParsedLink.Post).link)

        assertEquals(2, details.initialMediaIndex)
    }

    @Test
    fun `instagram falls back to oembed and marks the reel cover`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { url ->
            if (url.contains("/oembed/")) {
                HttpResult(200, instagramOEmbed, "application/json")
            } else {
                HttpResult(200, "<html><title>Login</title></html>", "text/html")
            }
        })
        val details = InstagramProvider(fetcher).load(
            link(
                "https://www.instagram.com/reel/DaduG1us3DU/",
                Platform.INSTAGRAM,
                "DaduG1us3DU",
                PostKind.REEL,
            ),
        )

        assertNull(details.embed)
        assertEquals("ilsecoloxix", details.author)
        assertEquals("@ilsecoloxix", details.handle)
        assertEquals("Telefonino gettato per terra", details.text)
        assertEquals(PostMediaType.IMAGE, details.media.single().type)
        assertEquals("https://scontent.example/thumb.jpg", details.media.single().url)
        assertTrue(details.note!!.contains("video stream"))
        assertTrue(details.note!!.contains("cover images"))
    }

    @Test
    fun `instagram continues to the canonical page when the embed has only a cover`() = runBlocking {
        val coverOnly = embedDocument(
            """
            {
              "gql_data": {
                "shortcode_media": {
                  "id": "3935504429539881172",
                  "shortcode": "DaduG1us3DU",
                  "is_video": true,
                  "display_url": "$instagramCoverUrl",
                  "owner": {"username": "ilsecoloxix"}
                }
              }
            }
            """.trimIndent(),
        )
        val fetcher = FakeFetcher(onGet = { url ->
            when {
                url.contains("/embed/captioned/") -> HttpResult(200, coverOnly, "text/html")
                url == "https://www.instagram.com/reel/DaduG1us3DU/" ->
                    HttpResult(200, instagramVideoDocument, "text/html")
                else -> HttpResult(200, "{}", "application/json")
            }
        })
        val details = InstagramProvider(fetcher).load(
            link(
                "https://www.instagram.com/reel/DaduG1us3DU/",
                Platform.INSTAGRAM,
                "DaduG1us3DU",
                PostKind.REEL,
            ),
        )

        val media = details.media.single()
        assertEquals(PostMediaType.VIDEO, media.type)
        assertEquals(instagramVideoUrl, media.url)
        assertEquals(instagramCoverUrl, media.thumbnailUrl)
        assertTrue(fetcher.gets.contains("https://www.instagram.com/reel/DaduG1us3DU/"))
        assertTrue(details.note?.contains("video stream") != true)
    }

    @Test
    fun `instagram reads the video from the polaris page of a copyright blocked reel`() = runBlocking {
        val parsed = LinkParser.parse(
            "https://www.instagram.com/reel/De5-myeTp52/?vrfl=dnF4MWxjeWV5MXc5",
        )
        val postLink = (parsed as ParsedLink.Post).link
        assertEquals("https://www.instagram.com/reel/De5-myeTp52/", postLink.browserUrl)

        val fetcher = FakeFetcher(onGet = { url ->
            when {
                url.contains("/embed/captioned/") ->
                    HttpResult(200, instagramCopyrightBlockedEmbed, "text/html")
                url == "https://www.instagram.com/reel/De5-myeTp52/" ->
                    HttpResult(200, instagramPolarisPage, "text/html")
                else -> HttpResult(200, "{}", "application/json")
            }
        })
        val details = InstagramProvider(fetcher).load(postLink)

        assertEquals("@cuurley", details.handle)
        assertEquals("CUURLEY (歌厉)(Bo)", details.author)
        assertEquals("2067 dystopian", details.text)
        val media = details.media.single()
        assertEquals(PostMediaType.VIDEO, media.type)
        assertEquals(instagramPolarisVideoUrl, media.url)
        assertEquals(instagramCopyrightCoverUrl, media.thumbnailUrl)
        assertTrue(fetcher.gets.contains("https://www.instagram.com/reel/De5-myeTp52/"))
        assertTrue(details.note?.contains("video stream") != true)
    }

    @Test
    fun `instagram reports a missing post when only oembed says not found`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { url ->
            if (url.contains("/oembed/")) {
                HttpResult(404, "Not Found", "text/plain")
            } else {
                HttpResult(200, "<html><head><title>Instagram</title></head></html>", "text/html")
            }
        })
        val error = failureOf {
            InstagramProvider(fetcher).load(
                link(
                    "https://www.instagram.com/reel/ZZZZnotreal999/",
                    Platform.INSTAGRAM,
                    "ZZZZnotreal999",
                    PostKind.REEL,
                ),
            )
        }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.NOT_FOUND, (error as PostException).kind)
        assertTrue(fetcher.gets.any { it.contains("/oembed/") })
    }

    @Test
    fun `instagram reports a missing post`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(404, "No Media Match", "text/plain") })
        val error = failureOf {
            InstagramProvider(fetcher).load(
                link(
                    "https://www.instagram.com/reel/ZZZZnotreal999/",
                    Platform.INSTAGRAM,
                    "ZZZZnotreal999",
                    PostKind.REEL,
                ),
            )
        }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.NOT_FOUND, (error as PostException).kind)
        assertEquals(Platform.INSTAGRAM, error.platform)
    }

    @Test
    fun `instagram keeps a precise limitation when metadata is unavailable`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(500, "", null) })
        val details = InstagramProvider(fetcher).load(
            link("https://www.instagram.com/p/CxS-o4RrdEs/", Platform.INSTAGRAM, "CxS-o4RrdEs"),
        )

        assertNull(details.embed)
        assertTrue(details.media.isEmpty())
        assertTrue(details.note!!.contains("HTTP 500"))
    }

    @Test
    fun `instagram reports a network error when nothing can be reached`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { throw IOException("offline") })
        val error = failureOf {
            InstagramProvider(fetcher).load(
                link("https://www.instagram.com/p/CxS-o4RrdEs/", Platform.INSTAGRAM, "CxS-o4RrdEs"),
            )
        }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.NETWORK, (error as PostException).kind)
    }

    @Test
    fun `instagram explains unavailable comment text`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { url ->
            when {
                url.contains("/embed/captioned/") ->
                    HttpResult(200, instagramVideoDocument, "text/html")
                url.contains("/comments/") ->
                    HttpResult(200, """{"comments":[]}""", "application/json")
                else -> HttpResult(200, "{}", "application/json")
            }
        })
        val details = InstagramProvider(fetcher).load(
            link(
                "https://www.instagram.com/reel/DaduG1us3DU/",
                Platform.INSTAGRAM,
                "DaduG1us3DU",
                PostKind.REEL,
            ),
        )

        assertTrue(details.note!!.contains("reports 5829 comments"))
        assertTrue(details.note!!.contains("logged-out visitors"))
        assertTrue(details.comments.isEmpty())
    }

    @Test
    fun `facebook reel renders its native video and canonical url`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(200, facebookReelPage, "text/html") })
        val details = FacebookProvider(fetcher).load(
            link(
                "https://www.facebook.com/reel/2000381177287760/",
                Platform.FACEBOOK,
                "2000381177287760",
                PostKind.REEL,
            ),
        )

        assertNull(details.embed)
        assertEquals("@3Demoniak", details.handle)
        assertEquals("Best print EVER!", details.text)
        assertEquals(
            "https://www.facebook.com/3Demoniak/videos/2000381177287760/",
            details.browserUrl,
        )
        val media = details.media.single()
        assertEquals(PostMediaType.VIDEO, media.type)
        assertEquals(facebookVideoUrl, media.url)
        assertEquals("https://cdn.example/cover.jpg?oh=1&oe=2", media.thumbnailUrl)
    }

    @Test
    fun `facebook falls back to the official plugin without public metadata`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(200, "<html><title>Error</title></html>", "text/html") })
        val details = FacebookProvider(fetcher).load(
            link(
                "https://www.facebook.com/facebook/posts/10158739848801729/",
                Platform.FACEBOOK,
                "10158739848801729",
            ),
        )

        assertEquals(Platform.FACEBOOK, details.platform)
        assertEquals(
            "https://www.facebook.com/plugins/post.php" +
                "?href=https%3A%2F%2Fwww.facebook.com%2Ffacebook%2Fposts%2F10158739848801729%2F" +
                "&show_text=true",
            details.embed?.url,
        )
        assertEquals(
            setOf("www.facebook.com", "facebook.com", "web.facebook.com"),
            details.embed?.allowedHosts,
        )
        assertEquals(640, details.embed?.heightDp)
        assertTrue(details.note!!.contains("official Facebook plugin"))
    }

    @Test
    fun `facebook video fallback uses the video plugin`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(500, "", null) })
        val details = FacebookProvider(fetcher).load(
            link("https://www.facebook.com/watch/?v=555", Platform.FACEBOOK, "555", PostKind.VIDEO),
        )

        assertTrue(details.embed!!.url.startsWith("https://www.facebook.com/plugins/video.php?href="))
        assertEquals(480, details.embed?.heightDp)
        assertTrue(details.note!!.contains("HTTP 500"))
    }

    @Test
    fun `facebook reports a missing post`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(404, "", null) })
        val error = failureOf {
            FacebookProvider(fetcher).load(
                link("https://www.facebook.com/reel/987654/", Platform.FACEBOOK, "987654", PostKind.REEL),
            )
        }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.NOT_FOUND, (error as PostException).kind)
    }

    @Test
    fun `every platform has a default provider`() {
        val platforms = defaultProviders(FakeFetcher()).map { it.platform }.toSet()
        assertEquals(Platform.entries.toSet(), platforms)
    }

    @Test
    fun `tiktok uses oembed metadata and its player url`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(200, tiktokOEmbed, "application/json") })
        val details = TikTokProvider(fetcher).load(
            link(
                "https://www.tiktok.com/@scout2015/video/6718335390845095173",
                Platform.TIKTOK,
                "6718335390845095173",
            ),
        )

        assertEquals("Tiff", details.author)
        assertEquals("@scout2015", details.handle)
        assertEquals("Scramble up ur name", details.text)
        assertEquals(listOf("https://p16-sign-va.tiktokcdn.com/cover.jpg"), details.images)
        assertEquals(
            "https://www.tiktok.com/player/v1/6718335390845095173?embed_from=oembed&music_info=1",
            details.embed?.url,
        )
        assertNull(details.note)
        assertTrue(
            fetcher.gets.single().startsWith(
                "https://www.tiktok.com/oembed?url=https%3A%2F%2Fwww.tiktok.com%2F%40scout2015%2Fvideo%2F6718335390845095173",
            ),
        )
    }

    @Test
    fun `tiktok falls back to the official player when metadata is rate limited`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(429, "", null) })
        val details = TikTokProvider(fetcher).load(
            link("https://www.tiktok.com/@scout2015/video/6718335390845095173", Platform.TIKTOK, "6718335390845095173"),
        )

        assertEquals(
            "https://www.tiktok.com/player/v1/6718335390845095173" +
                "?embed_from=socialview&music_info=1&description=1&autoplay=1&muted=1",
            details.embed?.url,
        )
        assertEquals("@scout2015", details.handle)
        assertNotNull(details.note)
        assertTrue(details.note!!.contains("rate limiting"))
    }

    @Test
    fun `tiktok falls back when the metadata request fails`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { throw IOException("offline") })
        val details = TikTokProvider(fetcher).load(
            link("https://www.tiktok.com/@scout2015/video/6718335390845095173", Platform.TIKTOK, "6718335390845095173"),
        )

        assertTrue(details.embed!!.url.startsWith("https://www.tiktok.com/player/v1/6718335390845095173"))
        assertTrue(details.note!!.contains("could not be reached"))
    }

    @Test
    fun `tiktok falls back on unexpected metadata`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(200, "not json", "application/json") })
        val details = TikTokProvider(fetcher).load(
            link("https://www.tiktok.com/@scout2015/video/6718335390845095173", Platform.TIKTOK, "6718335390845095173"),
        )

        assertTrue(details.embed!!.url.startsWith("https://www.tiktok.com/player/v1/6718335390845095173"))
        assertTrue(details.note!!.contains("unexpected metadata"))
    }

    @Test
    fun `x uses oembed metadata over the official embed`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(200, xOEmbed, "application/json") })
        val details = XProvider(fetcher).load(
            link("https://x.com/jack/status/20", Platform.X, "20"),
        )

        assertEquals("jack", details.author)
        assertEquals("@jack", details.handle)
        assertEquals("just setting up my twttr", details.text)
        assertEquals(
            "https://platform.twitter.com/embed/Tweet.html?id=20&dnt=true&hideThread=true",
            details.embed?.url,
        )
        assertNull(details.note)
    }

    @Test
    fun `x reports a missing post`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(404, "", null) })
        val error = failureOf {
            XProvider(fetcher).load(link("https://x.com/jack/status/20", Platform.X, "20"))
        }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.NOT_FOUND, (error as PostException).kind)
        assertEquals("https://x.com/jack/status/20", error.browserUrl)
    }

    @Test
    fun `x keeps the embed when metadata is unavailable`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(500, "", null) })
        val details = XProvider(fetcher).load(link("https://x.com/jack/status/20", Platform.X, "20"))

        assertEquals(
            "https://platform.twitter.com/embed/Tweet.html?id=20&dnt=true&hideThread=true",
            details.embed?.url,
        )
        assertTrue(details.note!!.contains("HTTP 500"))
    }

    @Test
    fun `reddit uses public json including comments`() = runBlocking {
        val body = """
            [
              {"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"abc123","subreddit_name_prefixed":"r/Android","author":"alice","title":"Hello","selftext":"Body"}}]}},
              {"kind":"Listing","data":{"children":[{"kind":"t1","data":{"author":"bob","body":"First","score":3}}]}}
            ]
        """.trimIndent()
        val fetcher = FakeFetcher(onGet = { HttpResult(200, body, "application/json") })
        val details = RedditProvider(fetcher).load(
            link("https://www.reddit.com/r/Android/comments/abc123/", Platform.REDDIT, "abc123"),
        )

        assertEquals("u/alice", details.author)
        assertEquals("r/Android", details.handle)
        assertEquals("Hello", details.title)
        assertEquals("Body", details.text)
        assertEquals(1, details.comments.size)
        assertEquals("First", details.comments[0].body)
        assertNull(details.embed)
        assertNull(details.note)
        assertTrue(
            fetcher.gets.contains(
                "https://www.reddit.com/comments/abc123.json?raw_json=1&limit=25&sort=top",
            ),
        )
        assertEquals(
            "https://www.reddit.com/r/Android/comments/abc123/.rss",
            fetcher.gets.first(),
        )
    }

    @Test
    fun `reddit uses the public rss feed when json is blocked`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { url ->
            when {
                url.contains(".json") -> HttpResult(403, "blocked", "text/html")
                url.endsWith(".rss") -> HttpResult(200, redditFeed, "application/atom+xml")
                else -> HttpResult(404, "", null)
            }
        })
        val details = RedditProvider(fetcher).load(
            link(
                "https://www.reddit.com/r/Android/comments/abc123/google_pixel_update/",
                Platform.REDDIT,
                "abc123",
            ),
        )

        assertNull(details.embed)
        assertEquals("Google Pixel update", details.title)
        assertEquals("Pixel update body", details.text)
        assertEquals("u/kbDL-", details.author)
        assertEquals("r/Android", details.handle)
        assertEquals(listOf("First comment"), details.comments.map { it.body })
        assertTrue(details.note!!.contains("RSS feed"))
        assertTrue(
            fetcher.gets.contains("https://www.reddit.com/r/Android/comments/abc123/.rss"),
        )
    }

    @Test
    fun `reddit falls back to the official embed when json is blocked`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(403, "blocked", "text/html") })
        val details = RedditProvider(fetcher).load(
            link(
                "https://www.reddit.com/r/Android/comments/abc123/hello/",
                Platform.REDDIT,
                "abc123",
            ),
        )

        assertEquals(
            "https://embed.reddit.com/r/Android/comments/abc123/hello/?depth=1&showmore=false",
            details.embed?.url,
        )
        assertEquals(setOf("embed.reddit.com"), details.embed?.allowedHosts)
        assertTrue(details.note!!.contains("HTTP 403"))
        assertTrue(fetcher.resolves.isEmpty())
    }

    @Test
    fun `reddit bare comments resolves the subreddit for the fallback`() = runBlocking {
        val fetcher = FakeFetcher(
            onGet = { HttpResult(403, "blocked", "text/html") },
            onResolve = {
                "https://www.reddit.com/r/Android/comments/abc123/hello/?utm_source=share"
            },
        )
        val details = RedditProvider(fetcher).load(
            link("https://www.reddit.com/comments/abc123", Platform.REDDIT, "abc123"),
        )

        assertEquals(
            "https://embed.reddit.com/r/Android/comments/abc123/hello/?depth=1&showmore=false",
            details.embed?.url,
        )
        assertEquals(setOf("embed.reddit.com"), details.embed?.allowedHosts)
        assertEquals(listOf("https://www.reddit.com/comments/abc123"), fetcher.resolves)
        assertTrue(details.note!!.contains("HTTP 403"))
    }

    @Test
    fun `reddit redd it fallback resolves the subreddit`() = runBlocking {
        val parsed = LinkParser.parse("https://redd.it/abc123")
        assertTrue(parsed is ParsedLink.Post)
        val fetcher = FakeFetcher(
            onGet = { HttpResult(429, "", null) },
            onResolve = { "https://www.reddit.com/r/Android/comments/abc123/hello/" },
        )
        val details = RedditProvider(fetcher).load((parsed as ParsedLink.Post).link)

        assertEquals(
            "https://embed.reddit.com/r/Android/comments/abc123/hello/?depth=1&showmore=false",
            details.embed?.url,
        )
        assertTrue(details.note!!.contains("HTTP 429"))
    }

    @Test
    fun `reddit bare comments falls back to the comment embed without a subreddit`() = runBlocking {
        val fetcher = FakeFetcher(
            onGet = { HttpResult(403, "blocked", "text/html") },
            onResolve = { "https://www.reddit.com/" },
        )
        val details = RedditProvider(fetcher).load(
            link("https://www.reddit.com/comments/abc123", Platform.REDDIT, "abc123"),
        )

        assertEquals(
            "<script src=\"https://www.reddit.com/comments/abc123/.embed" +
                "?depth=1&showmore=false\"></script>",
            commentEmbedScript(details.embed!!.url),
        )
        assertEquals(setOf("www.reddit.com"), details.embed?.allowedHosts)
        assertEquals(
            "https://www.reddit.com/comments/abc123/.embed?depth=1&showmore=false",
            details.embed?.requiredScriptUrl,
        )
        assertTrue(details.note!!.contains("subreddit was not resolved"))
    }

    @Test
    fun `reddit reports a removed post`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { HttpResult(404, "", null) })
        val error = failureOf {
            RedditProvider(fetcher).load(
                link("https://www.reddit.com/comments/abc123", Platform.REDDIT, "abc123"),
            )
        }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.NOT_FOUND, (error as PostException).kind)
    }

    @Test
    fun `reddit reports a missing post inside a valid listing`() = runBlocking {
        val fetcher = FakeFetcher(
            onGet = { HttpResult(200, """[{"kind":"Listing","data":{"children":[]}}]""", "application/json") },
        )
        val error = failureOf {
            RedditProvider(fetcher).load(
                link("https://www.reddit.com/comments/abc123", Platform.REDDIT, "abc123"),
            )
        }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.NOT_FOUND, (error as PostException).kind)
    }

    @Test
    fun `reddit falls back when the json request fails`() = runBlocking {
        val fetcher = FakeFetcher(onGet = { throw IOException("offline") })
        val details = RedditProvider(fetcher).load(
            link("https://www.reddit.com/comments/abc123", Platform.REDDIT, "abc123"),
        )

        assertEquals(
            "<script src=\"https://www.reddit.com/comments/abc123/.embed" +
                "?depth=1&showmore=false\"></script>",
            commentEmbedScript(details.embed!!.url),
        )
        assertTrue(details.note!!.contains("could not be reached"))
        assertTrue(details.note!!.contains("subreddit was not resolved"))
    }
}