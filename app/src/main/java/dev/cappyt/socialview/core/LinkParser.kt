package dev.cappyt.socialview.core

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.util.Locale

object LinkParser {
    private val URL_PATTERN = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val BARE_HOST_PATTERN = Regex(
        """^(?:[a-z0-9-]+\.)+[a-z]{2,}(?::\d+)?/\S*$""",
        RegexOption.IGNORE_CASE,
    )
    private val BARE_KNOWN_HOST_PATTERN = Regex(
        """^(?:[a-z0-9-]+\.)*(?:instagram\.com|instagr\.am|facebook\.com|fb\.com|fb\.watch|tiktok\.com|twitter\.com|x\.com|t\.co|reddit\.com|redd\.it|reddit\.app\.link)(?::\d+)?(?:/.*)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_PUNCTUATION = ".,;:!?)]}'\""

    private val INSTAGRAM_POST_PATH = Regex(
        """^/(?:[A-Za-z0-9._]+/)?(p|reel|reels|tv)/([A-Za-z0-9_-]+)(?:/.*)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val FACEBOOK_ID = Regex("""[A-Za-z0-9._-]+""")
    private val FACEBOOK_POST_PATH = Regex("""^/([A-Za-z0-9._-]+)/posts/([A-Za-z0-9._-]+)(?:/.*)?$""")
    private val FACEBOOK_GROUP_POST_PATH =
        Regex("""^/groups/([A-Za-z0-9._-]+)/posts/([A-Za-z0-9._-]+)(?:/.*)?$""")
    private val FACEBOOK_GROUP_PERMALINK_PATH =
        Regex("""^/groups/([A-Za-z0-9._-]+)/permalink/([A-Za-z0-9._-]+)(?:/.*)?$""")
    private val FACEBOOK_VIDEO_PATH = Regex(
        """^/([A-Za-z0-9._-]+)/videos/(?:[A-Za-z0-9._-]+/)?([0-9]+)/?(?:.*)?$""",
    )
    private val FACEBOOK_REEL_PATH = Regex("""^/reel/([A-Za-z0-9._-]+)(?:/.*)?$""")
    private val TIKTOK_POST_PATH = Regex("""^/@([A-Za-z0-9._-]+)/(video|photo)/(\d+)(?:/.*)?$""")
    private val TIKTOK_EMBED_PATH = Regex("""^/(?:embed/(?:v2/)?|player/v1/)(\d+)(?:/.*)?$""")
    private val X_STATUS_PATH = Regex("""^/([A-Za-z0-9_]{1,15})/status(?:es)?/(\d+)(?:/.*)?$""")
    private val X_I_STATUS_PATH = Regex("""^/i/(?:web/)?status/(\d+)(?:/.*)?$""")
    private val X_STATUSES_PATH = Regex("""^/statuses/(\d+)(?:/.*)?$""")
    private val REDDIT_POST_PATH = Regex("""^/r/([A-Za-z0-9_]+)/comments/([a-z0-9]+)(?:\.json)?(?:/.*)?$""")
    private val REDDIT_COMMENTS_PATH = Regex("""^/comments/([a-z0-9]+)(?:\.json)?(?:/.*)?$""")
    private val INSTAGRAM_QUERY_ALLOWLIST = setOf("img_index")
    private val FACEBOOK_QUERY_ALLOWLIST = setOf("story_fbid", "id", "fbid", "set", "v")

    fun findUrl(text: String): String? {
        val direct = URL_PATTERN.find(text)?.value?.trimEnd { it in TRAILING_PUNCTUATION }
        if (!direct.isNullOrBlank()) return direct

        val candidate = text.trim().trimEnd { it in TRAILING_PUNCTUATION }
        val bare = BARE_KNOWN_HOST_PATTERN.matches(candidate) || BARE_HOST_PATTERN.matches(candidate)
        return if (bare) "https://$candidate" else null
    }

    fun parse(text: String): ParsedLink {
        val url = findUrl(text) ?: return ParsedLink.Invalid("No web link found. Paste a post URL.")
        return classify(url)
    }

    fun classify(raw: String): ParsedLink {
        val value = decodeSharedEntities(raw.trim())
        val uri = try {
            URI(value)
        } catch (_: URISyntaxException) {
            return ParsedLink.Invalid("This is not a valid web link.")
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            return ParsedLink.Invalid("Only http and https links are supported.")
        }

        val host = uri.host?.lowercase(Locale.ROOT)
            ?: return ParsedLink.Invalid("This link has no host name.")
        val platform = platformForHost(host)
            ?: return ParsedLink.Unsupported(
                platform = null,
                url = value,
                reason = "SocialView supports single posts from Instagram, Facebook, TikTok, X and Reddit.",
            )
        val path = uri.path.orEmpty().ifBlank { "/" }
        val query = allowedQuery(platform, queryParameters(uri.rawQuery))
        val requestUrl = normalizedUrl(host, path)

        return when (platform) {
            Platform.INSTAGRAM -> classifyInstagram(path, requestUrl, query, value)
            Platform.FACEBOOK -> classifyFacebook(host, path, requestUrl, query, value)
            Platform.TIKTOK -> classifyTikTok(host, path, requestUrl, value)
            Platform.X -> classifyX(host, path, requestUrl, value)
            Platform.REDDIT -> classifyReddit(host, path, requestUrl, value)
        }
    }

    fun platformHint(text: String): Platform? = when (val parsed = parse(text)) {
        is ParsedLink.Post -> parsed.link.platform
        is ParsedLink.Short -> parsed.platform
        is ParsedLink.Unsupported -> parsed.platform
        is ParsedLink.Invalid -> null
    }

    private fun platformForHost(host: String): Platform? = when (host) {
        "instagram.com", "www.instagram.com", "m.instagram.com",
        "instagr.am", "www.instagr.am",
        -> Platform.INSTAGRAM

        "facebook.com", "www.facebook.com", "m.facebook.com", "mobile.facebook.com",
        "web.facebook.com", "mbasic.facebook.com",
        "fb.com", "www.fb.com", "fb.watch", "m.fb.watch",
        -> Platform.FACEBOOK

        "tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com",
        -> Platform.TIKTOK

        "x.com", "www.x.com", "mobile.x.com",
        "twitter.com", "www.twitter.com", "m.twitter.com", "mobile.twitter.com",
        "t.co",
        -> Platform.X

        "reddit.com", "www.reddit.com", "old.reddit.com", "new.reddit.com",
        "np.reddit.com", "i.reddit.com", "sh.reddit.com",
        "redd.it", "www.redd.it", "reddit.app.link",
        -> Platform.REDDIT

        else -> null
    }

    private fun classifyInstagram(
        path: String,
        requestUrl: String,
        query: Map<String, String>,
        originalUrl: String,
    ): ParsedLink {
        if (path.startsWith("/share/")) {
            return ParsedLink.Short(Platform.INSTAGRAM, requestUrl, originalUrl)
        }
        val match = INSTAGRAM_POST_PATH.matchEntire(path)
        if (match != null) {
            val shortcode = match.groupValues[2]
            val kind = when (match.groupValues[1].lowercase(Locale.ROOT)) {
                "reel", "reels" -> PostKind.REEL
                "tv" -> PostKind.VIDEO
                else -> PostKind.POST
            }
            val pathSegment = when (kind) {
                PostKind.REEL -> "reel"
                PostKind.VIDEO -> "tv"
                PostKind.POST -> "p"
            }
            val imageIndex = query["img_index"]?.takeIf { it.matches(Regex("""\d+""")) }
            val suffix = imageIndex?.let { "?img_index=$it" }.orEmpty()
            return ParsedLink.Post(
                PostLink(
                    platform = Platform.INSTAGRAM,
                    postId = shortcode,
                    browserUrl = "https://www.instagram.com/$pathSegment/$shortcode/$suffix",
                    originalUrl = originalUrl,
                    kind = kind,
                    initialMediaIndex = imageIndex?.toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0,
                ),
            )
        }
        val reason = when {
            path.startsWith("/stories/") -> "Instagram stories need a login. Open this link in a browser."
            path == "/" || path.count { it == '/' } == 1 ->
                "This Instagram link is a profile, not a single post."
            else -> "This Instagram link is not a single post. Feeds, profiles and stories are not supported."
        }
        return ParsedLink.Unsupported(Platform.INSTAGRAM, originalUrl, reason)
    }

    private fun classifyFacebook(
        host: String,
        path: String,
        requestUrl: String,
        query: Map<String, String>,
        originalUrl: String,
    ): ParsedLink {
        if (host == "fb.watch" || host == "m.fb.watch") {
            if (path.trim('/').isEmpty()) {
                return ParsedLink.Unsupported(Platform.FACEBOOK, originalUrl, "This fb.watch link has no target.")
            }
            return ParsedLink.Short(Platform.FACEBOOK, requestUrl, originalUrl)
        }
        if (path.startsWith("/share/")) {
            return ParsedLink.Short(Platform.FACEBOOK, requestUrl, originalUrl)
        }
        if (path.startsWith("/stories/")) {
            return ParsedLink.Unsupported(
                Platform.FACEBOOK,
                originalUrl,
                "Facebook stories need a login. Open this link in a browser.",
            )
        }

        if (path == "/permalink.php" || path == "/story.php") {
            val storyId = query["story_fbid"]
            if (storyId != null && FACEBOOK_ID.matches(storyId)) {
                val parameters = mutableListOf("story_fbid" to storyId)
                query["id"]?.takeIf { FACEBOOK_ID.matches(it) }?.let { parameters += "id" to it }
                return facebookPost(
                    postId = storyId,
                    browserUrl = facebookQueryUrl(path, parameters),
                    originalUrl = originalUrl,
                    kind = PostKind.POST,
                )
            }
            return ParsedLink.Unsupported(
                Platform.FACEBOOK,
                originalUrl,
                "This Facebook story link has no post id.",
            )
        }

        if (path == "/photo.php" || path == "/photo" || path == "/photo/") {
            val photoId = query["fbid"]
            if (photoId != null && FACEBOOK_ID.matches(photoId)) {
                val parameters = mutableListOf("fbid" to photoId)
                query["id"]?.takeIf { FACEBOOK_ID.matches(it) }?.let { parameters += "id" to it }
                query["set"]?.takeIf { FACEBOOK_ID.matches(it) }?.let { parameters += "set" to it }
                return facebookPost(
                    postId = photoId,
                    browserUrl = facebookQueryUrl("/photo.php", parameters),
                    originalUrl = originalUrl,
                    kind = PostKind.POST,
                )
            }
            return ParsedLink.Unsupported(
                Platform.FACEBOOK,
                originalUrl,
                "This Facebook photo link has no photo id.",
            )
        }

        if (path == "/watch" || path == "/watch/") {
            val videoId = query["v"]
            if (videoId != null && videoId.matches(Regex("""\d+"""))) {
                return facebookPost(
                    postId = videoId,
                    browserUrl = "https://www.facebook.com/watch/?v=$videoId",
                    originalUrl = originalUrl,
                    kind = PostKind.VIDEO,
                )
            }
            return ParsedLink.Unsupported(
                Platform.FACEBOOK,
                originalUrl,
                "This Facebook watch link has no video id.",
            )
        }

        if (path == "/video.php") {
            val videoId = query["v"]
            if (videoId != null && FACEBOOK_ID.matches(videoId)) {
                return facebookPost(
                    postId = videoId,
                    browserUrl = "https://www.facebook.com/video.php?v=$videoId",
                    originalUrl = originalUrl,
                    kind = PostKind.VIDEO,
                )
            }
            return ParsedLink.Unsupported(
                Platform.FACEBOOK,
                originalUrl,
                "This Facebook video link has no video id.",
            )
        }

        FACEBOOK_GROUP_PERMALINK_PATH.matchEntire(path)?.let { match ->
            val group = match.groupValues[1]
            val id = match.groupValues[2]
            return facebookPost(
                postId = id,
                browserUrl = "https://www.facebook.com/groups/$group/permalink/$id/",
                originalUrl = originalUrl,
                kind = PostKind.POST,
            )
        }
        FACEBOOK_GROUP_POST_PATH.matchEntire(path)?.let { match ->
            val group = match.groupValues[1]
            val id = match.groupValues[2]
            return facebookPost(
                postId = id,
                browserUrl = "https://www.facebook.com/groups/$group/posts/$id/",
                originalUrl = originalUrl,
                kind = PostKind.POST,
            )
        }
        FACEBOOK_POST_PATH.matchEntire(path)?.let { match ->
            val user = match.groupValues[1]
            val id = match.groupValues[2]
            return facebookPost(
                postId = id,
                browserUrl = "https://www.facebook.com/$user/posts/$id/",
                originalUrl = originalUrl,
                kind = PostKind.POST,
            )
        }
        FACEBOOK_VIDEO_PATH.matchEntire(path)?.let { match ->
            val user = match.groupValues[1]
            val id = match.groupValues[2]
            return facebookPost(
                postId = id,
                browserUrl = "https://www.facebook.com/$user/videos/$id/",
                originalUrl = originalUrl,
                kind = PostKind.VIDEO,
            )
        }
        FACEBOOK_REEL_PATH.matchEntire(path)?.let { match ->
            val id = match.groupValues[1]
            return facebookPost(
                postId = id,
                browserUrl = "https://www.facebook.com/reel/$id/",
                originalUrl = originalUrl,
                kind = PostKind.REEL,
            )
        }

        val reason = when {
            path == "/" || path.count { it == '/' } == 1 ->
                "This Facebook link is a profile or page, not a single post."
            else -> "This Facebook link is not a single post. Profiles, feeds and albums are not supported."
        }
        return ParsedLink.Unsupported(Platform.FACEBOOK, originalUrl, reason)
    }

    private fun facebookPost(
        postId: String,
        browserUrl: String,
        originalUrl: String,
        kind: PostKind,
    ): ParsedLink = ParsedLink.Post(
        PostLink(
            platform = Platform.FACEBOOK,
            postId = postId,
            browserUrl = browserUrl,
            originalUrl = originalUrl,
            kind = kind,
        ),
    )

    private fun facebookQueryUrl(path: String, parameters: List<Pair<String, String>>): String =
        "https://www.facebook.com$path?" + parameters.joinToString("&") { (name, value) -> "$name=$value" }

    private fun classifyTikTok(host: String, path: String, requestUrl: String, originalUrl: String): ParsedLink {
        TIKTOK_POST_PATH.matchEntire(path)?.let { match ->
            val user = match.groupValues[1]
            val kind = match.groupValues[2]
            val id = match.groupValues[3]
            return ParsedLink.Post(
                PostLink(
                    platform = Platform.TIKTOK,
                    postId = id,
                    browserUrl = "https://www.tiktok.com/@$user/$kind/$id",
                    originalUrl = originalUrl,
                ),
            )
        }
        TIKTOK_EMBED_PATH.matchEntire(path)?.let { match ->
            return ParsedLink.Post(
                PostLink(
                    platform = Platform.TIKTOK,
                    postId = match.groupValues[1],
                    browserUrl = "https://www.tiktok.com/@i/video/${match.groupValues[1]}",
                    originalUrl = originalUrl,
                ),
            )
        }

        val shortHost = host == "vm.tiktok.com" || host == "vt.tiktok.com"
        if (shortHost || path.startsWith("/t/")) {
            return ParsedLink.Short(Platform.TIKTOK, requestUrl, originalUrl)
        }

        val reason = when {
            path.endsWith("/live") -> "TikTok Live is not a single post."
            path.startsWith("/@") -> "This TikTok link is a profile, not a single post."
            else -> "This TikTok link is not a single post. Profiles and feeds are not supported."
        }
        return ParsedLink.Unsupported(Platform.TIKTOK, originalUrl, reason)
    }

    private fun classifyX(host: String, path: String, requestUrl: String, originalUrl: String): ParsedLink {
        if (host == "t.co") {
            return if (path == "/") {
                ParsedLink.Unsupported(Platform.X, originalUrl, "This t.co link has no target.")
            } else {
                ParsedLink.Short(Platform.X, requestUrl, originalUrl)
            }
        }

        X_STATUS_PATH.matchEntire(path)?.let { match ->
            val user = match.groupValues[1]
            val id = match.groupValues[2]
            return ParsedLink.Post(
                PostLink(
                    platform = Platform.X,
                    postId = id,
                    browserUrl = "https://x.com/$user/status/$id",
                    originalUrl = originalUrl,
                ),
            )
        }
        X_I_STATUS_PATH.matchEntire(path)?.let { match ->
            val id = match.groupValues[1]
            return ParsedLink.Post(
                PostLink(
                    platform = Platform.X,
                    postId = id,
                    browserUrl = "https://x.com/i/status/$id",
                    originalUrl = originalUrl,
                ),
            )
        }
        X_STATUSES_PATH.matchEntire(path)?.let { match ->
            val id = match.groupValues[1]
            return ParsedLink.Post(
                PostLink(
                    platform = Platform.X,
                    postId = id,
                    browserUrl = "https://x.com/i/status/$id",
                    originalUrl = originalUrl,
                ),
            )
        }

        return ParsedLink.Unsupported(
            Platform.X,
            originalUrl,
            "This X link is not a single post. Profiles, searches and threads are not supported.",
        )
    }

    private fun classifyReddit(host: String, path: String, requestUrl: String, originalUrl: String): ParsedLink {
        if (host == "reddit.app.link") {
            return ParsedLink.Short(Platform.REDDIT, requestUrl, originalUrl)
        }
        if (host == "redd.it" || host == "www.redd.it") {
            val id = path.trim('/').lowercase(Locale.ROOT)
            if (id.matches(Regex("[a-z0-9]+"))) {
                return ParsedLink.Post(
                    PostLink(
                        platform = Platform.REDDIT,
                        postId = id,
                        browserUrl = "https://www.reddit.com/comments/$id",
                        originalUrl = originalUrl,
                    ),
                )
            }
            return ParsedLink.Unsupported(Platform.REDDIT, originalUrl, "This redd.it link has no post id.")
        }
        if (Regex("""^/r/[A-Za-z0-9_]+/s/[A-Za-z0-9]+/?$""").matches(path)) {
            return ParsedLink.Short(Platform.REDDIT, requestUrl, originalUrl)
        }

        REDDIT_POST_PATH.matchEntire(path)?.let { match ->
            val subreddit = match.groupValues[1]
            val id = match.groupValues[2]
            return ParsedLink.Post(
                PostLink(
                    platform = Platform.REDDIT,
                    postId = id,
                    browserUrl = "https://www.reddit.com/r/$subreddit/comments/$id/",
                    originalUrl = originalUrl,
                ),
            )
        }
        REDDIT_COMMENTS_PATH.matchEntire(path)?.let { match ->
            val id = match.groupValues[1]
            return ParsedLink.Post(
                PostLink(
                    platform = Platform.REDDIT,
                    postId = id,
                    browserUrl = "https://www.reddit.com/comments/$id",
                    originalUrl = originalUrl,
                ),
            )
        }

        return ParsedLink.Unsupported(
            Platform.REDDIT,
            originalUrl,
            "This Reddit link is not a single post. Subreddit feeds and user pages are not supported.",
        )
    }

    private fun decodeSharedEntities(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&#38;", "&")
        .replace("&#x26;", "&", ignoreCase = true)

    private fun normalizedUrl(host: String, path: String): String = "https://$host$path"

    private fun allowedQuery(platform: Platform, query: Map<String, String>): Map<String, String> {
        val allowlist = when (platform) {
            Platform.INSTAGRAM -> INSTAGRAM_QUERY_ALLOWLIST
            Platform.FACEBOOK -> FACEBOOK_QUERY_ALLOWLIST
            else -> emptySet()
        }
        return query.filterKeys { it in allowlist }
    }

    private fun queryParameters(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val values = LinkedHashMap<String, String>()
        for (part in rawQuery.split('&')) {
            if (part.isEmpty()) continue
            val separator = part.indexOf('=')
            val name = if (separator >= 0) part.substring(0, separator) else part
            val value = if (separator >= 0) part.substring(separator + 1) else ""
            if (name.isEmpty()) continue
            values[decodeQueryValue(name)] = decodeQueryValue(value)
        }
        return values
    }

    private fun decodeQueryValue(value: String): String = try {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        value
    }
}