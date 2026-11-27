package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.ProfileDetails
import dev.cappyt.socialview.core.ProfileLink
import dev.cappyt.socialview.core.ProfilePage
import dev.cappyt.socialview.core.ProfilePageRequest
import dev.cappyt.socialview.core.ProfilePost
import dev.cappyt.socialview.core.ProfilePostType
import dev.cappyt.socialview.core.ProfileStat
import dev.cappyt.socialview.net.BrowserHeaders
import dev.cappyt.socialview.net.HttpFetcher
import java.net.URLEncoder
import org.json.JSONObject

/**
 * Reads the creator embed document for the header and the latest videos: the profile page itself
 * redirects logged-out visitors to the login page. The next pages come from the creator post
 * list, which wants the `secUid` of the account. The data endpoint of the web player gives it
 * for any video of the account.
 */
class TikTokProfileProvider(
    private val fetcher: HttpFetcher,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ProfileProvider {
    override val platform = Platform.TIKTOK

    override suspend fun loadMore(request: ProfilePageRequest): ProfilePage {
        // The request names the account, then a video of it or its secUid.
        val parts = request.userId.split(':', limit = 3)
        if (parts.size != 3) throw noMorePosts()
        val (owner, kind, value) = parts
        val secUid = if (kind == TikTokProfileParser.ITEM) secUidOf(owner, value) else value
        // The list goes back in time from the cursor, in milliseconds.
        val cursor = request.cursor.ifEmpty { nowMillis().toString() }
        val response = fetcher.get(
            "https://www.tiktok.com/api/creator/item_list/?aid=1988&count=15&cursor=$cursor" +
                "&secUid=${URLEncoder.encode(secUid, Charsets.UTF_8.name())}&type=1",
            BrowserHeaders.json(),
        )
        val page = if (response.isSuccess) {
            TikTokProfileParser.parsePage(response.body, owner, secUid)
        } else {
            null
        }
        return page ?: throw noMorePosts()
    }

    private suspend fun secUidOf(owner: String, videoId: String): String {
        val response = fetcher.get(TikTokProvider.itemUrl(videoId), BrowserHeaders.json())
        val item = if (response.isSuccess) TikTokItemParser.parse(response.body, videoId) else null
        // The video must be of the account, or the list would be of another one.
        return (item as? TikTokItemResult.Post)
            ?.takeIf { it.post.handle.equals("@$owner", ignoreCase = true) }
            ?.secUid
            ?: throw noMorePosts()
    }

    private fun noMorePosts() = PostException(
        kind = ErrorKind.UNEXPECTED,
        message = "TikTok did not return more posts.",
        platform = platform,
    )

    override suspend fun load(link: ProfileLink): ProfileDetails {
        // An error answers HTTP 400 with the same document, so the state decides, not the status.
        val response = fetcher.get(
            "https://www.tiktok.com/embed/@${link.username}",
            BrowserHeaders.navigation(),
        )
        return when (val result = TikTokProfileParser.parse(response.body, link)) {
            is TikTokProfileResult.Profile -> result.profile
            TikTokProfileResult.Missing -> throw profileNotFound(
                link,
                "TikTok has no account with the name @${link.username}.",
            )

            null -> if (response.isSuccess || response.status == 400) {
                throw profileNotFound(
                    link,
                    "TikTok did not return a public profile for @${link.username}.",
                )
            } else {
                throw profileHttpError(link, response.status)
            }
        }
    }
}

sealed interface TikTokProfileResult {
    data class Profile(val profile: ProfileDetails) : TikTokProfileResult

    data object Missing : TikTokProfileResult
}

object TikTokProfileParser {
    /** The two kinds of page request: `<account>:item:<video id>` and `<account>:sec:<secUid>`. */
    const val ITEM = "item"
    const val SEC_UID = "sec"

    /** A create time in seconds that fits a cursor in milliseconds. */
    private val CREATE_TIME = 1L..99_999_999_999L
    private const val STATE_SCRIPT = "__FRONTITY_CONNECT_STATE__"
    private const val ERROR_NOT_FOUND = 10221
    private const val ERROR_PRIVATE = 10222
    private val VIDEO_ID = Regex("""\d+""")
    private val USER_NAME = Regex("""[A-Za-z0-9._]+""")

    fun parse(body: String, link: ProfileLink): TikTokProfileResult? {
        val data = scriptContent(body, STATE_SCRIPT)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.optJSONObject("source")
            ?.optJSONObject("data")
            ?: return null
        // The key is the request path, in the letter case that the server chooses.
        val embed = data.keys().asSequence()
            .firstOrNull { it.equals("/embed/@${link.username}", ignoreCase = true) }
            ?.let(data::optJSONObject)
            ?: return null

        if (embed.optBoolean("isError")) {
            return when (embed.optInt("errorCode")) {
                ERROR_NOT_FOUND -> TikTokProfileResult.Missing
                ERROR_PRIVATE -> TikTokProfileResult.Profile(
                    ProfileDetails(
                        platform = Platform.TIKTOK,
                        browserUrl = link.browserUrl,
                        handle = "@${link.username}",
                        private = true,
                    ),
                )

                else -> null
            }
        }

        val user = embed.optJSONObject("userInfo") ?: return null
        val name = user.stringOrNull("uniqueId")
            ?.takeIf { it.equals(link.username, ignoreCase = true) }
            ?: return null
        val posts = postsOf(embed, name)
        return TikTokProfileResult.Profile(
            ProfileDetails(
                platform = Platform.TIKTOK,
                browserUrl = link.browserUrl,
                handle = "@$name",
                fullName = user.stringOrNull("nickname"),
                biography = user.stringOrNull("signature"),
                avatarUrl = user.stringOrNull("avatarThumbUrl")?.takeIf { it.startsWith("https://") },
                verified = user.optBoolean("verified"),
                private = user.optBoolean("privateAccount"),
                stats = listOfNotNull(
                    ProfileStat.of(user.countOrNull("followerCount"), "Followers"),
                    ProfileStat.of(user.countOrNull("followingCount"), "Following"),
                    ProfileStat.of(user.countOrNull("heartCount"), "Likes"),
                ),
                posts = posts,
                // Any video of the account leads to its secUid, which the post list wants.
                nextPage = posts.firstOrNull()?.browserUrl?.substringAfterLast('/')
                    ?.let { ProfilePageRequest(userId = "$name:$ITEM:$it", cursor = "") },
            ),
        )
    }

    /** A page of the creator post list of [owner], or null when the answer is not a list. */
    fun parsePage(body: String, owner: String, secUid: String): ProfilePage? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val items = root.optJSONArray("itemList") ?: return null
        var oldest: Long? = null
        val posts = (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val id = item.stringOrNull("id")?.takeIf(VIDEO_ID::matches) ?: return@mapNotNull null
            val author = item.optJSONObject("author")?.stringOrNull("uniqueId")
                ?.takeIf { USER_NAME.matches(it) && it.equals(owner, ignoreCase = true) }
                ?: return@mapNotNull null
            val time = when (val value = item.opt("createTime")) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }?.takeIf { it in CREATE_TIME }
            if (time != null) oldest = minOf(oldest ?: time, time)
            val images = item.optJSONObject("imagePost")?.optJSONArray("images")
            ProfilePost(
                // The embed list does not tell a photo post from a video, so both sources use the
                // video form of the link. The post itself gives the canonical form when it opens.
                browserUrl = "https://www.tiktok.com/@$author/video/$id",
                type = if (images != null) ProfilePostType.CAROUSEL else ProfilePostType.VIDEO,
                thumbnailUrl = (
                    item.optJSONObject("video")?.stringOrNull("cover")
                        ?: images?.optJSONObject(0)?.optJSONObject("imageURL")?.optJSONArray("urlList")?.optString(0)
                    )?.takeIf { it.startsWith("https://") },
                text = item.stringOrNull("desc"),
            )
        }.distinctBy { it.browserUrl }
        val last = oldest
        val next = if (root.optBoolean("hasMorePrevious") && last != null) {
            ProfilePageRequest(userId = "$owner:$SEC_UID:$secUid", cursor = (last * 1_000).toString())
        } else {
            null
        }
        return ProfilePage(posts = posts, next = next)
    }

    private fun postsOf(embed: JSONObject, owner: String): List<ProfilePost> {
        val videos = embed.optJSONArray("videoList") ?: return emptyList()
        return (0 until videos.length()).mapNotNull { index ->
            val video = videos.optJSONObject(index) ?: return@mapNotNull null
            val id = video.stringOrNull("id")?.takeIf(VIDEO_ID::matches) ?: return@mapNotNull null
            val author = video.stringOrNull("authorUniqueId")
                ?.takeIf { USER_NAME.matches(it) && it.equals(owner, ignoreCase = true) }
                ?: return@mapNotNull null
            ProfilePost(
                browserUrl = "https://www.tiktok.com/@$author/video/$id",
                type = ProfilePostType.VIDEO,
                thumbnailUrl = video.stringOrNull("coverUrl")?.takeIf { it.startsWith("https://") },
                text = video.stringOrNull("desc"),
            )
        }.distinctBy { it.browserUrl }
    }

    // The embed writes some counts as numbers and others as strings. A count above 2^31 comes
    // as a wrapped 32-bit number: 2.7 billion likes arrive as -1597542564.
    private fun JSONObject.countOrNull(key: String): Long? {
        val count = when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        } ?: return null
        return if (count < 0 && count >= Int.MIN_VALUE) count + (1L shl 32) else count
    }
}
