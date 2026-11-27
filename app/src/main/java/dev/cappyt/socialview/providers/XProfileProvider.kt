package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.ProfileBioLink
import dev.cappyt.socialview.core.ProfileDetails
import dev.cappyt.socialview.core.ProfileLayout
import dev.cappyt.socialview.core.ProfileLink
import dev.cappyt.socialview.core.ProfilePost
import dev.cappyt.socialview.core.ProfilePostType
import dev.cappyt.socialview.core.ProfileStat
import dev.cappyt.socialview.net.BrowserHeaders
import dev.cappyt.socialview.net.HttpFetcher
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Locale
import org.json.JSONObject

/**
 * Reads the document behind the official timeline embed. It carries the account and its latest
 * posts as JSON, and it has no next page.
 */
class XProfileProvider(
    private val fetcher: HttpFetcher,
) : ProfileProvider {
    override val platform = Platform.X

    override suspend fun load(link: ProfileLink): ProfileDetails {
        val response = fetcher.get(
            "https://syndication.twitter.com/srv/timeline-profile/screen-name/${link.username}",
            BrowserHeaders.navigation(),
        )
        if (!response.isSuccess) throw profileHttpError(link, response.status)
        // A missing, a suspended and a protected account all answer with an empty timeline.
        return XProfileParser.parse(response.body, link) ?: throw profileNotFound(
            link,
            "X returned no public posts for @${link.username}. The account can be missing, " +
                "suspended or protected.",
        )
    }
}

object XProfileParser {
    private const val DATA_SCRIPT = "__NEXT_DATA__"
    private val STATUS_PATH = Regex("""/[A-Za-z0-9_]{1,15}/status/\d+""")

    fun parse(body: String, link: ProfileLink): ProfileDetails? {
        val entries = scriptContent(body, DATA_SCRIPT)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("timeline")
            ?.optJSONArray("entries")
            ?: return null
        // The account is the author of every entry, a repost included. Drop any other entry.
        val tweets = (0 until entries.length()).mapNotNull { index ->
            entries.optJSONObject(index)?.optJSONObject("content")?.optJSONObject("tweet")
        }.filter { tweet ->
            tweet.optJSONObject("user")?.stringOrNull("screen_name").equals(link.username, ignoreCase = true)
        }
        val user = tweets.firstOrNull()?.optJSONObject("user") ?: return null
        val name = user.stringOrNull("screen_name") ?: return null
        val pinned = pinnedId(tweets)
        val posts = tweets.mapNotNull { postOf(it, pinned) }.distinctBy { it.browserUrl }

        return ProfileDetails(
            platform = Platform.X,
            browserUrl = "https://x.com/$name",
            handle = "@$name",
            fullName = user.stringOrNull("name"),
            biography = user.stringOrNull("description"),
            // The document has the 48 px picture. The same path serves the 400 px one.
            avatarUrl = user.stringOrNull("profile_image_url_https")
                ?.takeIf { it.startsWith("https://") }
                ?.replace("_normal.", "_400x400."),
            verified = user.optBoolean("verified") || user.optBoolean("is_blue_verified"),
            private = user.optBoolean("protected"),
            stats = listOfNotNull(
                ProfileStat.of(user.longOrNull("statuses_count"), "Posts"),
                ProfileStat.of(user.longOrNull("followers_count"), "Followers"),
                ProfileStat.of(user.longOrNull("friends_count"), "Following"),
            ),
            links = listOfNotNull(linkOf(user)),
            posts = posts,
            layout = ProfileLayout.LIST,
            note = "X publishes only the latest ${posts.size} posts to logged-out visitors."
                .takeIf { posts.isNotEmpty() },
        )
    }

    /** The first entry is the pinned post when it is older than the second one. */
    private fun pinnedId(tweets: List<JSONObject>): String? {
        val first = tweets.getOrNull(0)?.stringOrNull("id_str") ?: return null
        val second = tweets.getOrNull(1)?.stringOrNull("id_str") ?: return null
        val older = runCatching { BigInteger(first) < BigInteger(second) }.getOrDefault(false)
        return first.takeIf { older }
    }

    private fun postOf(tweet: JSONObject, pinnedId: String?): ProfilePost? {
        // A repost holds the original post. Its own text is cut and its counts are zero.
        val original = tweet.optJSONObject("retweeted_status")
        val source = original ?: tweet
        val path = source.stringOrNull("permalink")?.takeIf(STATUS_PATH::matches) ?: return null
        val media = source.optJSONObject("extended_entities")?.optJSONArray("media")?.optJSONObject(0)
        return ProfilePost(
            browserUrl = "https://x.com$path",
            type = when (media?.stringOrNull("type")) {
                "photo" -> ProfilePostType.IMAGE
                "video", "animated_gif" -> ProfilePostType.VIDEO
                else -> ProfilePostType.TEXT
            },
            thumbnailUrl = media?.stringOrNull("media_url_https")?.takeIf { it.startsWith("https://") },
            text = textOf(source),
            label = when {
                original != null ->
                    original.optJSONObject("user")?.stringOrNull("screen_name")?.let { "Reposted from @$it" }

                tweet.stringOrNull("id_str") == pinnedId -> "Pinned"
                else -> null
            },
            createdAt = source.stringOrNull("created_at")?.let(::epochSeconds),
            score = source.longOrNull("favorite_count"),
            commentCount = source.longOrNull("reply_count"),
            sensitive = tweet.optBoolean("possibly_sensitive") || source.optBoolean("possibly_sensitive"),
        )
    }

    /** The text without the trailing media link, which `display_text_range` leaves out. */
    private fun textOf(tweet: JSONObject): String? {
        val text = tweet.stringOrNull("full_text") ?: return null
        val end = tweet.optJSONArray("display_text_range")?.optInt(1, -1) ?: -1
        val codePoints = text.codePointCount(0, text.length)
        val shown = if (end in 1 until codePoints) text.substring(0, text.offsetByCodePoints(0, end)) else text
        return shown.trim().takeIf { it.isNotEmpty() }
    }

    private fun linkOf(user: JSONObject): ProfileBioLink? {
        val url = user.optJSONObject("entities")
            ?.optJSONObject("url")
            ?.optJSONArray("urls")
            ?.optJSONObject(0)
            ?.stringOrNull("expanded_url")
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?: return null
        return ProfileBioLink(title = null, url = url)
    }

    private fun epochSeconds(createdAt: String): Long? = runCatching {
        SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US).parse(createdAt)?.time?.div(1_000)
    }.getOrNull()

    private fun JSONObject.longOrNull(key: String): Long? = (opt(key) as? Number)?.toLong()
}
