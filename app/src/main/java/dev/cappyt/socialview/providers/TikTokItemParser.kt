package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostMedia
import dev.cappyt.socialview.core.PostMediaType
import dev.cappyt.socialview.net.BrowserHeaders
import org.json.JSONObject

sealed interface TikTokItemResult {
    /** A post, with the `secUid` of its author for the profile post list. */
    data class Post(val post: PostDetails, val secUid: String?) : TikTokItemResult

    /** TikTok has no public post with the id. */
    data object Missing : TikTokItemResult
}

/** Reads the answer of the data endpoint of TikTok's official web player. */
object TikTokItemParser {
    private const val PHOTO_POST = 150
    private val USER_NAME = Regex("""[A-Za-z0-9._]+""")

    // The video host answers 403 without this referer, and 403 with any TikTok cookie.
    val MEDIA_HEADERS = mapOf(
        "User-Agent" to BrowserHeaders.USER_AGENT,
        "Referer" to "https://www.tiktok.com/",
    )

    /** The post [postId], or null when the answer is not an item list. */
    fun parse(body: String, postId: String): TikTokItemResult? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val items = root.optJSONArray("items")
        val item = (0 until (items?.length() ?: 0))
            .mapNotNull { items?.optJSONObject(it) }
            .firstOrNull { it.stringOrNull("id_str") == postId }
        if (item == null) {
            val code = root.optJSONArray("results")?.optJSONObject(0)?.stringOrNull("code")
            return TikTokItemResult.Missing.takeIf { code == "nil_core_data" }
        }
        val author = item.optJSONObject("author_info")
        val user = author?.stringOrNull("unique_id")?.takeIf(USER_NAME::matches) ?: return null
        val images = imagesOf(item)
        val photoPost = item.optInt("aweme_type") == PHOTO_POST || images.isNotEmpty()
        // The video of a photo post is its music track.
        val media = if (photoPost) images else listOfNotNull(videoOf(item))
        val counts = item.optJSONObject("statistics_info")
        return TikTokItemResult.Post(
            post = PostDetails(
                platform = Platform.TIKTOK,
                browserUrl = "https://www.tiktok.com/@$user/${if (photoPost) "photo" else "video"}/$postId",
                author = author.stringOrNull("nickname"),
                handle = "@$user",
                text = item.stringOrNull("desc"),
                media = media,
                // The endpoint has no create time. The high bits of the id are its second.
                createdAt = postId.toLongOrNull()?.shr(32)?.takeIf { it > 0 },
                score = counts?.intOrNull("digg_count"),
                commentCount = counts?.intOrNull("comment_count"),
            ),
            secUid = author.stringOrNull("secret_id"),
        )
    }

    private fun videoOf(item: JSONObject): PostMedia? {
        val video = item.optJSONObject("video_info") ?: return null
        val urls = video.optJSONArray("url_list") ?: return null
        // The list ends with a www.tiktok.com link that redirects to an error page.
        val url = (0 until urls.length())
            .map { urls.optString(it) }
            .firstOrNull { it.startsWith("https://") && !it.startsWith("https://www.tiktok.com/") }
            ?: return null
        val meta = video.optJSONObject("meta")
        return PostMedia(
            type = PostMediaType.VIDEO,
            url = url,
            thumbnailUrl = firstUrl(video.optJSONObject("cover")),
            width = meta?.intOrNull("width"),
            height = meta?.intOrNull("height"),
            durationSeconds = (meta?.opt("duration") as? Number)?.toDouble(),
            headers = MEDIA_HEADERS,
        )
    }

    private fun imagesOf(item: JSONObject): List<PostMedia> {
        val images = item.optJSONObject("image_post_info")?.optJSONArray("images") ?: return emptyList()
        return (0 until images.length()).mapNotNull { index ->
            val image = images.optJSONObject(index)?.optJSONObject("display_image") ?: return@mapNotNull null
            val url = firstUrl(image) ?: return@mapNotNull null
            PostMedia(PostMediaType.IMAGE, url, width = image.intOrNull("width"), height = image.intOrNull("height"))
        }
    }

    private fun firstUrl(holder: JSONObject?): String? =
        holder?.optJSONArray("url_list")?.optString(0)?.takeIf { it.startsWith("https://") }

    private fun JSONObject.intOrNull(key: String): Int? =
        (opt(key) as? Number)?.toLong()?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt()
}
