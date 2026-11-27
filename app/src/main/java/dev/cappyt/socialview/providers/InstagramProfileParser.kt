package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.ProfileBioLink
import dev.cappyt.socialview.core.ProfileDetails
import dev.cappyt.socialview.core.ProfilePage
import dev.cappyt.socialview.core.ProfilePageRequest
import dev.cappyt.socialview.core.ProfilePost
import dev.cappyt.socialview.core.ProfilePostType
import dev.cappyt.socialview.core.ProfileStat
import java.net.URI
import java.util.Locale
import org.json.JSONObject

/**
 * Reads the logged-out profile page. Its Polaris scripts carry `xig_user_by_username` twice: one
 * object with the profile fields and one, linked by `pk`, with the first page of the timeline.
 * The next pages come from the GraphQL endpoint as `data.node`.
 */
object InstagramProfileParser {
    private const val USER_KEY = "\"xig_user_by_username\""
    private const val MEDIA_TYPE_VIDEO = 2
    private const val MEDIA_TYPE_CAROUSEL = 8
    private val POST_COUNT = Regex("""([\d.,]+[KMB]?)\s+Posts?\b""")
    private val SHORTCODE = Regex("""[A-Za-z0-9_-]+""")

    fun parse(body: String, username: String): ProfileDetails? {
        val objects = userObjects(body)
        val user = objects.firstOrNull { it.stringOrNull("username").equals(username, ignoreCase = true) }
            ?: return null
        val name = user.stringOrNull("username")?.lowercase(Locale.ROOT) ?: return null
        val pk = user.stringOrNull("pk")
        val timeline = objects
            .filter { pk != null && it.stringOrNull("pk") == pk }
            .firstNotNullOfOrNull { it.optJSONObject("polaris_ordered_timeline_connection") }

        return ProfileDetails(
            platform = Platform.INSTAGRAM,
            browserUrl = "https://www.instagram.com/$name/",
            handle = "@$name",
            fullName = user.stringOrNull("full_name"),
            biography = user.stringOrNull("biography"),
            avatarUrl = user.stringOrNull("profile_pic_url")?.takeIf { it.startsWith("https://") },
            verified = user.optBoolean("is_verified"),
            private = user.optBoolean("is_private"),
            stats = listOfNotNull(
                // The page has the post count only as the rounded label of its description.
                HtmlMeta.meta(body)["og:description"]
                    ?.let { POST_COUNT.find(it) }
                    ?.let { ProfileStat(it.groupValues[1], "Posts") },
                ProfileStat.of(user.longOrNull("follower_count"), "Followers"),
                ProfileStat.of(user.longOrNull("following_count"), "Following"),
            ),
            links = linksOf(user),
            posts = postsOf(timeline),
            nextPage = nextPageOf(timeline, user.stringOrNull("id")),
        )
    }

    /** A page of the GraphQL timeline query for the user [userId], or null for any other body. */
    fun parsePage(body: String, userId: String): ProfilePage? {
        val node = runCatching { JSONObject(body) }.getOrNull()
            ?.optJSONObject("data")
            ?.optJSONObject("node")
            ?.takeIf { it.stringOrNull("id") == userId }
        val timeline = node?.optJSONObject("polaris_ordered_timeline_connection") ?: return null
        return ProfilePage(posts = postsOf(timeline), next = nextPageOf(timeline, userId))
    }

    private fun nextPageOf(timeline: JSONObject?, userId: String?): ProfilePageRequest? {
        val pageInfo = timeline?.optJSONObject("page_info") ?: return null
        if (userId == null || !pageInfo.optBoolean("has_next_page")) return null
        val cursor = pageInfo.stringOrNull("end_cursor") ?: return null
        return ProfilePageRequest(userId = userId, cursor = cursor)
    }

    private fun userObjects(body: String): List<JSONObject> {
        val objects = mutableListOf<JSONObject>()
        var index = body.indexOf(USER_KEY)
        while (index >= 0) {
            var cursor = skipWhitespace(body, index + USER_KEY.length)
            if (body.getOrNull(cursor) == ':') {
                cursor = skipWhitespace(body, cursor + 1)
                // A missing account carries `null` here, so the next brace is not this value.
                if (body.getOrNull(cursor) == '{') jsonObjectAt(body, cursor)?.let(objects::add)
            }
            index = body.indexOf(USER_KEY, index + USER_KEY.length)
        }
        return objects
    }

    private fun linksOf(user: JSONObject): List<ProfileBioLink> {
        val array = user.optJSONArray("bio_links") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.stringOrNull("url")?.let(::webUrl) ?: return@mapNotNull null
            ProfileBioLink(title = item.stringOrNull("title"), url = url)
        }
    }

    /** An http or https link with a host, or null. A link without a scheme gets https. */
    private fun webUrl(raw: String): String? {
        val value = raw.trim()
        val hasScheme = value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
        val uri = runCatching { URI(if (hasScheme) value else "https://$value") }.getOrNull()
        // Another scheme, such as `mailto:`, parses as user information or gives no host.
        if (uri?.host.isNullOrEmpty() || uri?.userInfo != null) return null
        // The browser helper compares the scheme in lowercase.
        val scheme = value.substringBefore("://").lowercase(Locale.ROOT)
        return if (hasScheme) "$scheme://${value.substringAfter("://")}" else "https://$value"
    }

    private fun postsOf(timeline: JSONObject?): List<ProfilePost> {
        val edges = timeline?.optJSONArray("edges") ?: return emptyList()
        val codes = HashSet<String>()
        return (0 until edges.length()).mapNotNull { index ->
            val node = edges.optJSONObject(index)?.optJSONObject("node") ?: return@mapNotNull null
            val code = node.stringOrNull("code")?.takeIf(SHORTCODE::matches) ?: return@mapNotNull null
            // The post link is the key of a grid tile, so a shortcode must not come twice.
            if (!codes.add(code)) return@mapNotNull null
            val segment = if (node.stringOrNull("product_type") == "clips") "reel" else "p"
            ProfilePost(
                browserUrl = "https://www.instagram.com/$segment/$code/",
                thumbnailUrl = node.stringOrNull("display_uri")?.takeIf { it.startsWith("https://") },
                type = when (node.optInt("media_type")) {
                    MEDIA_TYPE_CAROUSEL -> ProfilePostType.CAROUSEL
                    MEDIA_TYPE_VIDEO -> ProfilePostType.VIDEO
                    else -> ProfilePostType.IMAGE
                },
                text = node.stringOrNull("accessibility_caption")
                    ?: node.optJSONObject("caption")?.stringOrNull("text"),
            )
        }
    }

    private fun skipWhitespace(body: String, start: Int): Int {
        var index = start
        while (index < body.length && body[index].isWhitespace()) index++
        return index
    }

    private fun JSONObject.longOrNull(key: String): Long? = (opt(key) as? Number)?.toLong()
}
