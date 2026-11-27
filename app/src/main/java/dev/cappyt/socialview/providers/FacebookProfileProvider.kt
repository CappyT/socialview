package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.ProfileDetails
import dev.cappyt.socialview.core.ProfileLayout
import dev.cappyt.socialview.core.ProfileLink
import dev.cappyt.socialview.core.ProfilePost
import dev.cappyt.socialview.core.ProfilePostType
import dev.cappyt.socialview.core.ProfileStat
import dev.cappyt.socialview.net.BrowserHeaders
import dev.cappyt.socialview.net.HttpFetcher
import java.net.URLEncoder
import org.json.JSONObject

/**
 * Reads the official Page Plugin document. It carries the page header and its latest posts as
 * JSON. The page itself publishes one post to logged-out visitors.
 */
class FacebookProfileProvider(
    private val fetcher: HttpFetcher,
) : ProfileProvider {
    override val platform = Platform.FACEBOOK

    override suspend fun load(link: ProfileLink): ProfileDetails {
        // The plugin accepts a vanity name or a numeric id, not the other profile URL forms.
        val page = URLEncoder.encode("https://www.facebook.com/${link.username}", Charsets.UTF_8.name())
        val response = fetcher.get(
            "https://www.facebook.com/plugins/page.php?href=$page&tabs=timeline&width=500&height=800",
            BrowserHeaders.navigation(),
        )
        if (!response.isSuccess) throw profileHttpError(link, response.status)
        // A missing page and a restricted page both answer with an empty plugin.
        return FacebookProfileParser.parse(response.body, link) ?: throw profileNotFound(
            link,
            "Facebook did not return a public page for ${link.username}. The page can be missing, " +
                "restricted, or a personal profile that is not public.",
        )
    }
}

object FacebookProfileParser {
    private const val PLUGIN_MARKER = "\"PagePluginV2.react\""
    private const val PROPS_KEY = "\"props\":"
    private val NUMERIC_ID = Regex("""\d+""")

    fun parse(body: String, link: ProfileLink): ProfileDetails? {
        val marker = body.indexOf(PLUGIN_MARKER)
        if (marker < 0) return null
        val key = body.indexOf(PROPS_KEY, marker)
        if (key < 0) return null
        val start = body.indexOf('{', key)
        if (start < 0) return null
        val props = jsonObjectAt(body, start) ?: return null
        val name = props.stringOrNull("pageName") ?: return null
        val posts = postsOf(props)

        return ProfileDetails(
            platform = Platform.FACEBOOK,
            browserUrl = link.browserUrl,
            handle = link.username.takeUnless(NUMERIC_ID::matches) ?: name,
            fullName = name,
            biography = listOfNotNull(
                props.stringOrNull("pageCategory"),
                props.stringOrNull("pageDescription"),
            ).joinToString("\n").takeIf { it.isNotEmpty() },
            avatarUrl = props.stringOrNull("profilePicURL")?.takeIf { it.startsWith("https://") },
            verified = props.optBoolean("isVerified"),
            // The plugin gives the follower count only as a rounded label.
            stats = listOfNotNull(
                props.stringOrNull("followerCountFormatted")?.let { ProfileStat(it, "Followers") },
            ),
            posts = posts,
            layout = ProfileLayout.LIST,
            note = (
                "Facebook publishes only the latest ${posts.size} posts of a page to logged-out " +
                    "visitors, without their links."
                ).takeIf { posts.isNotEmpty() },
        )
    }

    private fun postsOf(props: JSONObject): List<ProfilePost> {
        val items = props.optJSONArray("timelinePosts") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val shared = item.optJSONObject("attachedStory")
            val image = (item.stringOrNull("photoURL") ?: shared?.stringOrNull("photoURL"))
                ?.takeIf { it.startsWith("https://") }
            val text = item.stringOrNull("message") ?: shared?.stringOrNull("message")
            if (text == null && image == null) return@mapNotNull null
            ProfilePost(
                // The plugin has no post id and no permalink.
                browserUrl = null,
                type = when {
                    item.stringOrNull("attachmentType") == "video" -> ProfilePostType.VIDEO
                    image != null -> ProfilePostType.IMAGE
                    else -> ProfilePostType.TEXT
                },
                thumbnailUrl = image,
                text = text,
                label = shared?.stringOrNull("actorName")?.let { "Shared from $it" },
                createdAt = (item.opt("createdTime") as? Number)?.toLong(),
                score = (item.opt("reactionCount") as? Number)?.toLong(),
                commentCount = (item.opt("commentCount") as? Number)?.toLong(),
            )
        }
    }
}
