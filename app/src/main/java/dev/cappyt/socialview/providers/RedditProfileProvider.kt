package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.ProfileBioLink
import dev.cappyt.socialview.core.ProfileDetails
import dev.cappyt.socialview.core.ProfileLayout
import dev.cappyt.socialview.core.ProfileLink
import dev.cappyt.socialview.core.ProfilePage
import dev.cappyt.socialview.core.ProfilePageRequest
import dev.cappyt.socialview.core.ProfilePost
import dev.cappyt.socialview.core.ProfilePostType
import dev.cappyt.socialview.core.ProfileStat
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Reads a user profile and the user's posts from the API of the Reddit Android app. */
class RedditProfileProvider(
    private val reddit: RedditGraphql,
) : ProfileProvider {
    override val platform = Platform.REDDIT

    override suspend fun load(link: ProfileLink): ProfileDetails {
        val variables = JSONObject()
            .put("name", link.username)
            .put("includePremiumAvatarTreatment", false)
        val response = reddit.query(PROFILE_OPERATION, PROFILE_QUERY_ID, variables)
        if (!response.isSuccess) throw profileHttpError(link, response.status)
        val profile = when (val result = RedditProfileParser.parseProfile(response.body, link.username)) {
            is RedditProfileResult.Profile -> result.profile
            is RedditProfileResult.Unavailable -> throw profileNotFound(link, result.reason)
            null -> throw profileNotFound(link, "Reddit has no account with the name u/${link.username}.")
        }
        // The header is worth showing when the post feed fails or is hidden.
        val posts = try {
            loadMore(ProfilePageRequest(userId = link.username, cursor = ""))
        } catch (error: CancellationException) {
            throw error
        } catch (_: PostException) {
            return profile.copy(note = FEED_FAILED)
        } catch (_: IOException) {
            return profile.copy(note = FEED_FAILED)
        }
        return profile.copy(posts = posts.posts, nextPage = posts.next)
    }

    override suspend fun loadMore(request: ProfilePageRequest): ProfilePage {
        val variables = JSONObject().put("username", request.userId).put("sort", "NEW")
        if (request.cursor.isNotEmpty()) variables.put("after", request.cursor)
        val response = reddit.query(POSTS_OPERATION, POSTS_QUERY_ID, variables)
        val page = if (response.isSuccess) {
            RedditProfileParser.parsePosts(response.body, request.userId)
        } else {
            null
        }
        return page ?: throw PostException(
            kind = ErrorKind.UNEXPECTED,
            message = "Reddit did not return more posts.",
            platform = platform,
        )
    }

    private companion object {
        const val FEED_FAILED = "Reddit did not return the posts of this profile."

        // Persisted query ids of app build 2026.35.0. A new app version can retire them.
        const val PROFILE_OPERATION = "UserProfile"
        const val PROFILE_QUERY_ID = "a05c628c5469c6b107c85988a8b1d0b79a2e7b6150ebd3a449c67e226e975ddc"
        const val POSTS_OPERATION = "SubmittedPostsFeedSdui"
        const val POSTS_QUERY_ID = "ff280e6784df90e1d5732a0983237b6c20036bd614d3594e734116b334b94de6"
    }
}

sealed interface RedditProfileResult {
    data class Profile(val profile: ProfileDetails) : RedditProfileResult

    data class Unavailable(val reason: String) : RedditProfileResult
}

object RedditProfileParser {
    private val POST_ID = Regex("""t3_([a-z0-9]+)""")
    private val SUBREDDIT = Regex("""r/([A-Za-z0-9_]+)""")

    /** The profile header of [username], or null when Reddit has no account with that name. */
    fun parseProfile(body: String, username: String): RedditProfileResult? {
        val user = runCatching { JSONObject(body) }.getOrNull()
            ?.optJSONObject("data")
            ?.optJSONObject("redditorInfoByName")
            ?: return null
        when (user.stringOrNull("__typename")) {
            "UnavailableRedditor" -> return RedditProfileResult.Unavailable("This Reddit account is suspended.")
            "DeletedRedditor" -> return RedditProfileResult.Unavailable("This Reddit account is deleted.")
        }
        val name = user.stringOrNull("name")?.takeIf { it.equals(username, ignoreCase = true) } ?: return null
        val profile = user.optJSONObject("profile")
        val styles = profile?.optJSONObject("styles")
        val karma = user.optJSONObject("karma")
        val contributions = user.optJSONObject("contributionStats")
        return RedditProfileResult.Profile(
            ProfileDetails(
                platform = Platform.REDDIT,
                browserUrl = "https://www.reddit.com/user/$name/",
                handle = "u/$name",
                fullName = profile?.stringOrNull("title"),
                biography = profile?.stringOrNull("publicDescriptionText"),
                avatarUrl = (styles?.optJSONObject("legacyIcon")?.stringOrNull("url") ?: styles?.stringOrNull("icon"))
                    ?.takeIf { it.startsWith("https://") },
                stats = listOfNotNull(
                    ProfileStat.of(karma?.longOrNull("total"), "Karma"),
                    ProfileStat.of(contributions?.longOrNull("postCount"), "Posts"),
                    ProfileStat.of(contributions?.longOrNull("commentCount"), "Comments"),
                ),
                links = linksOf(profile),
                layout = ProfileLayout.LIST,
            ),
        )
    }

    /** A page of the user's posts, or null when the answer has no feed. */
    fun parsePosts(body: String, username: String): ProfilePage? {
        val elements = runCatching { JSONObject(body) }.getOrNull()
            ?.optJSONObject("data")
            ?.optJSONObject("profileFeed")
            ?.optJSONObject("elements")
            ?: return null
        val edges = elements.optJSONArray("edges")
        val posts = (0 until (edges?.length() ?: 0)).mapNotNull { index ->
            edges?.optJSONObject(index)?.optJSONObject("node")?.let(::postOf)
        }.distinctBy { it.browserUrl }
        val pageInfo = elements.optJSONObject("pageInfo")
        val cursor = pageInfo?.stringOrNull("endCursor")
            ?.takeIf { pageInfo.optBoolean("hasNextPage") }
        return ProfilePage(posts = posts, next = cursor?.let { ProfilePageRequest(username, it) })
    }

    private fun postOf(node: JSONObject): ProfilePost? {
        val id = node.stringOrNull("groupId")?.let(POST_ID::matchEntire)?.groupValues?.get(1) ?: return null
        val cells = node.optJSONArray("cells") ?: return null
        var title: String? = null
        var thumbnail: String? = null
        var type = ProfilePostType.TEXT
        var community: String? = null
        var pinned = false
        var createdAt: Long? = null
        var score: Long? = null
        var comments: Long? = null
        var sensitive = false
        for (index in 0 until cells.length()) {
            val cell = cells.optJSONObject(index) ?: continue
            when (cell.stringOrNull("__typename")) {
                "MetadataCell" -> {
                    community = cell.stringOrNull("detailsString")
                    createdAt = cell.stringOrNull("createdAt")?.let(::redditEpochSeconds)
                    pinned = cell.optJSONArray("statusIndicators")?.let { flags ->
                        (0 until flags.length()).any { flags.optString(it) == "PINNED" }
                    } == true
                }

                "TitleCell" -> title = cell.stringOrNull("title")
                "TitleWithThumbnailCell" -> {
                    title = cell.optJSONObject("titleCell")?.stringOrNull("title")
                    sensitive = sensitive || isNsfw(cell.optJSONObject("indicatorsCell"))
                    val media = cell.optJSONObject("thumbnail")
                    thumbnail = thumbnail
                        ?: mediaPath(media?.optJSONObject("sourceData"))
                        ?: mediaPath(media?.optJSONObject("media"))
                }

                "ImageCell" -> {
                    thumbnail = mediaPath(cell.optJSONObject("media"))
                    type = ProfilePostType.IMAGE
                }

                "LegacyVideoCell" -> {
                    thumbnail = mediaPath(cell.optJSONObject("preview"))
                    type = ProfilePostType.VIDEO
                }

                "GalleryCell" -> {
                    thumbnail = mediaPath(cell.optJSONArray("pages")?.optJSONObject(0)?.optJSONObject("image"))
                    type = ProfilePostType.CAROUSEL
                }

                "IndicatorsCell" -> sensitive = sensitive || isNsfw(cell)

                "ActionCell" -> {
                    score = cell.longOrNull("score")
                    comments = cell.longOrNull("commentCount")
                }
            }
        }
        val subreddit = community?.let(SUBREDDIT::matchEntire)?.groupValues?.get(1)
        return ProfilePost(
            // A post on the user's own profile has no subreddit. The short form resolves it.
            browserUrl = if (subreddit != null) {
                "https://www.reddit.com/r/$subreddit/comments/$id/"
            } else {
                "https://www.reddit.com/comments/$id"
            },
            type = type,
            thumbnailUrl = thumbnail,
            text = title,
            label = listOfNotNull("Pinned".takeIf { pinned }, community).joinToString(" · ")
                .takeIf { it.isNotEmpty() },
            createdAt = createdAt,
            score = score,
            commentCount = comments,
            sensitive = sensitive,
        )
    }

    private fun isNsfw(indicators: JSONObject?): Boolean {
        val flags = indicators?.optJSONArray("indicators") ?: return false
        return (0 until flags.length()).any { flags.optString(it) == "NSFW" }
    }

    private fun mediaPath(source: JSONObject?): String? =
        source?.stringOrNull("path")?.takeIf { it.startsWith("https://") }

    private fun linksOf(profile: JSONObject?): List<ProfileBioLink> {
        val links = profile?.optJSONArray("socialLinks") ?: return emptyList()
        return (0 until links.length()).mapNotNull { index ->
            val link = links.optJSONObject(index) ?: return@mapNotNull null
            val url = link.stringOrNull("outboundUrl")
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?: return@mapNotNull null
            ProfileBioLink(title = link.stringOrNull("title"), url = url)
        }
    }

    // The federation writes the same count as an integer in one field and as a float in another.
    private fun JSONObject.longOrNull(key: String): Long? = (opt(key) as? Number)?.toLong()
}
