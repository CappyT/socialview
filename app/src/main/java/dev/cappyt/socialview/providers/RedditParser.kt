package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.EmbedSpec
import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostComment
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.PostFormat
import dev.cappyt.socialview.core.PostMedia
import dev.cappyt.socialview.core.PostMediaType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object RedditParser {
    private const val MAX_IMAGES = 10
    private const val MAX_COMMENTS = 30
    private const val MAX_COMMENT_DEPTH = 3

    fun parse(body: String, browserUrl: String): PostDetails {
        val listing = try {
            JSONArray(body)
        } catch (error: JSONException) {
            throw unexpected(browserUrl, error)
        }

        val post = findPost(listing)
            ?: throw PostException(
                kind = ErrorKind.NOT_FOUND,
                message = "The Reddit post was not found or was removed.",
                browserUrl = browserUrl,
                platform = Platform.REDDIT,
            )

        val title = post.stringOrNull("title")
        val selfText = post.stringOrNull("selftext")
            ?.takeUnless { it == "[removed]" || it == "[deleted]" }
        val text = selfText?.takeIf { it.isNotBlank() }

        val images = images(post)
        val media = videoMedia(post)
        val permalink = post.stringOrNull("permalink").orEmpty()
        val embed = if (media.isEmpty() && permalink.isNotBlank() && isVideo(post)) {
            EmbedSpec(
                url = "https://embed.reddit.com${permalink.ensureLeadingSlash()}?depth=1&showmore=false",
                allowedHosts = setOf("embed.reddit.com"),
                heightDp = 520,
            )
        } else {
            null
        }

        return PostDetails(
            platform = Platform.REDDIT,
            browserUrl = browserUrl,
            author = post.stringOrNull("author")
                ?.takeIf { it != "[deleted]" }
                ?.let { "u/$it" },
            handle = post.stringOrNull("subreddit_name_prefixed")
                ?: post.stringOrNull("subreddit")?.let { "r/$it" },
            title = title,
            text = text,
            images = images,
            media = media,
            embed = embed,
            comments = comments(listing),
            note = if (post.optBoolean("over_18")) {
                "This post is marked NSFW. Reddit can ask you to confirm before it renders."
            } else {
                null
            },
            createdAt = epochSeconds(post),
            score = intOrNull(post, "score"),
            commentCount = intOrNull(post, "num_comments"),
            flair = post.stringOrNull("link_flair_text")?.takeIf { it.isNotBlank() },
            linkUrl = if (images.isEmpty() && media.isEmpty()) linkUrl(post) else null,
        )
    }

    private fun findPost(listing: JSONArray): JSONObject? {
        val children = listing.optJSONObject(0)
            ?.optJSONObject("data")
            ?.optJSONArray("children")
            ?: return null
        for (index in 0 until children.length()) {
            val child = children.optJSONObject(index) ?: continue
            if (child.stringOrNull("kind") == "t3") {
                return child.optJSONObject("data")
            }
        }
        return null
    }

    private fun images(post: JSONObject): List<String> {
        val urls = linkedSetOf<String>()
        if (post.optBoolean("is_gallery")) {
            val items = post.optJSONObject("gallery_data")?.optJSONArray("items")
            val metadata = post.optJSONObject("media_metadata")
            if (items != null && metadata != null) {
                for (index in 0 until minOf(items.length(), MAX_IMAGES)) {
                    val mediaId = items.optJSONObject(index)?.stringOrNull("media_id") ?: continue
                    val media = metadata.optJSONObject(mediaId) ?: continue
                    if (media.stringOrNull("status") != "valid") continue
                    val source = media.optJSONObject("s")
                    addImage(urls, source?.stringOrNull("u") ?: source?.stringOrNull("gif"))
                }
            }
        }

        if (urls.isEmpty()) {
            val hint = post.stringOrNull("post_hint")
            if (hint == "image" || isVideo(post) || post.optBoolean("is_gallery")) {
                if (hint == "image") {
                    addImage(
                        urls,
                        post.stringOrNull("url_overridden_by_dest") ?: post.stringOrNull("url"),
                    )
                }
                if (urls.isEmpty()) previewImages(post, urls)
            }
        }
        return urls.toList()
    }

    private fun previewImages(post: JSONObject, urls: MutableSet<String>) {
        val preview = post.optJSONObject("preview")?.optJSONArray("images") ?: return
        for (index in 0 until minOf(preview.length(), MAX_IMAGES)) {
            val image = preview.optJSONObject(index) ?: continue
            addImage(urls, image.optJSONObject("source")?.stringOrNull("url"))
        }
    }

    private fun addImage(urls: MutableSet<String>, raw: String?) {
        if (raw.isNullOrBlank()) return
        val decoded = HtmlText.decode(raw)
        val https = when {
            decoded.startsWith("https://") -> decoded
            decoded.startsWith("http://") -> decoded.replaceFirst("http://", "https://")
            else -> return
        }
        urls.add(https)
    }

    private fun isVideo(post: JSONObject): Boolean =
        post.optBoolean("is_video") || redditVideo(post) != null

    private fun redditVideo(post: JSONObject): JSONObject? =
        post.optJSONObject("secure_media")?.optJSONObject("reddit_video")
            ?: post.optJSONObject("media")?.optJSONObject("reddit_video")

    private fun videoMedia(post: JSONObject): List<PostMedia> {
        val video = redditVideo(post) ?: return emptyList()
        // HLS carries audio; the DASH mp4 fallback is often muted.
        val url = (video.stringOrNull("hls_url") ?: video.stringOrNull("fallback_url"))
            ?.let(HtmlText::decode)
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.replaceFirst("http://", "https://")
            ?: return emptyList()
        return listOf(
            PostMedia(
                type = PostMediaType.VIDEO,
                url = url,
                width = video.optInt("width").takeIf { it > 0 },
                height = video.optInt("height").takeIf { it > 0 },
                durationSeconds = video.optDouble("duration")
                    .takeIf { it.isFinite() && it > 0 },
            ),
        )
    }

    private fun linkUrl(post: JSONObject): String? {
        if (post.optBoolean("is_self") || post.optBoolean("is_gallery") || isVideo(post)) {
            return null
        }
        if (post.stringOrNull("post_hint") == "image") return null
        val raw = post.stringOrNull("url_overridden_by_dest")
            ?: post.stringOrNull("url")
            ?: return null
        val url = HtmlText.decode(raw)
        if (!url.startsWith("https://") && !url.startsWith("http://")) return null
        val host = PostFormat.hostOf(url) ?: return null
        if (host == "reddit.com" || host.endsWith(".reddit.com")) return null
        if (host == "redd.it" || host.endsWith(".redd.it")) return null
        return url
    }

    private fun epochSeconds(item: JSONObject): Long? =
        item.optDouble("created_utc").takeIf { it.isFinite() && it > 0 }?.toLong()

    private fun intOrNull(item: JSONObject, name: String): Int? =
        if (item.has(name) && !item.isNull(name)) item.optInt(name) else null

    private fun comments(listing: JSONArray): List<PostComment> {
        val children = listing.optJSONObject(1)
            ?.optJSONObject("data")
            ?.optJSONArray("children")
            ?: return emptyList()
        val comments = mutableListOf<PostComment>()
        collectComments(children, 0, comments)
        return comments
    }

    private fun collectComments(
        children: JSONArray,
        depth: Int,
        comments: MutableList<PostComment>,
    ) {
        for (index in 0 until children.length()) {
            if (comments.size >= MAX_COMMENTS) return
            val child = children.optJSONObject(index) ?: continue
            if (child.stringOrNull("kind") != "t1") continue
            val data = child.optJSONObject("data") ?: continue
            val body = data.stringOrNull("body")
                ?.takeUnless { it == "[removed]" || it == "[deleted]" }
            if (body != null) {
                val author = data.stringOrNull("author")
                    ?.takeIf { it != "[deleted]" }
                    ?.let { "u/$it" }
                    ?: "u/[deleted]"
                comments += PostComment(
                    author = author,
                    body = body.trim(),
                    score = intOrNull(data, "score"),
                    depth = depth,
                    createdAt = epochSeconds(data),
                    stickied = data.optBoolean("stickied"),
                )
            }
            if (depth < MAX_COMMENT_DEPTH) {
                val replies = data.optJSONObject("replies")
                    ?.optJSONObject("data")
                    ?.optJSONArray("children")
                if (replies != null) collectComments(replies, depth + 1, comments)
            }
        }
    }

    private fun unexpected(browserUrl: String, error: Exception) = PostException(
        kind = ErrorKind.UNEXPECTED,
        message = "Reddit returned a response that is not a post listing.",
        browserUrl = browserUrl,
        platform = Platform.REDDIT,
        cause = error,
    )

    private fun String.ensureLeadingSlash(): String = if (startsWith("/")) this else "/$this"
}