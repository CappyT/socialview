package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostComment
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostMedia
import dev.cappyt.socialview.core.PostMediaType
import org.json.JSONObject

sealed interface RedditPostResult {
    data class Post(val post: PostDetails) : RedditPostResult

    /** Reddit has no post with the id. */
    data object Missing : RedditPostResult
}

/** Reads the `PostsByIds` and `PostComments` answers of the Reddit app API. */
object RedditPostParser {
    private const val MAX_IMAGES = 20
    private const val MAX_COMMENTS = 30
    private const val MAX_COMMENT_DEPTH = 3
    private val NAME = Regex("""[A-Za-z0-9_-]+""")
    private val POST_TYPES = setOf("SubredditPost", "ProfilePost")
    private val VIDEO_HINTS = setOf("VIDEO", "GIFVIDEO")
    private val GONE = setOf("[removed]", "[deleted]")

    /** The post [postId] without its comments, or null when the answer is not a post list. */
    fun parsePost(body: String, postId: String): RedditPostResult? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val posts = root.optJSONObject("data")?.optJSONArray("postsInfoByIds") ?: return null
        if (posts.length() == 0 || posts.isNull(0)) {
            // An error next to the empty slot is a refusal, for example a quarantined community.
            return if (root.has("errors")) null else RedditPostResult.Missing
        }
        val post = posts.optJSONObject(0) ?: return null
        if (post.stringOrNull("__typename") == "DeletedSubredditPost") return RedditPostResult.Missing
        if (post.stringOrNull("id") != "t3_$postId") return null

        // A post on a user profile has a profile in place of the subreddit.
        val subreddit = post.optJSONObject("subreddit")?.stringOrNull("name")?.takeIf(NAME::matches)
        val profile = post.optJSONObject("profile")?.stringOrNull("name")
            ?.removePrefix("u_")
            ?.takeIf(NAME::matches)
        val path = when {
            subreddit != null -> "/r/$subreddit/comments/$postId/"
            profile != null -> "/user/$profile/comments/$postId/"
            else -> return null
        }
        // A crosspost carries its text and media in the original post, unless that post is gone.
        val source = post.optJSONObject("crosspostRoot")?.optJSONObject("post")
            ?.takeIf { it.stringOrNull("__typename") in POST_TYPES }
            ?: post
        val media = mediaOf(source)
        val sensitive = post.optBoolean("isNsfw") || source.optBoolean("isNsfw")
        val notes = listOfNotNull(
            "This post is marked NSFW.".takeIf { sensitive },
            "Reddit marks this post as removed.".takeIf { post.stringOrNull("removedByCategory") != null },
            "Reddit did not return the video stream. Its cover image is shown."
                .takeIf { isVideo(source) && media.none { it.type == PostMediaType.VIDEO } },
        )
        return RedditPostResult.Post(
            PostDetails(
                platform = Platform.REDDIT,
                browserUrl = "https://www.reddit.com$path",
                author = authorOf(post),
                handle = if (subreddit != null) "r/$subreddit" else "u/$profile",
                title = post.stringOrNull("postTitle"),
                text = textOf(source),
                media = media,
                note = notes.joinToString(" ").takeIf { it.isNotEmpty() },
                sensitive = sensitive,
                createdAt = post.stringOrNull("createdAt")?.let(::redditEpochSeconds),
                score = post.intOrNull("score").takeUnless { post.optBoolean("isScoreHidden") },
                commentCount = post.intOrNull("commentCount"),
                flair = post.optJSONObject("flair")?.stringOrNull("text"),
                linkUrl = source.stringOrNull("url")
                    ?.takeIf { media.isEmpty() && !source.optBoolean("isSelfPost") }
                    ?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
            ),
        )
    }

    /** The comments in reading order, each with its depth, or null when the answer has none. */
    fun parseComments(body: String): List<PostComment>? {
        val trees = runCatching { JSONObject(body) }.getOrNull()
            ?.optJSONObject("data")
            ?.optJSONObject("postInfoById")
            ?.optJSONObject("commentForest")
            ?.optJSONArray("trees")
            ?: return null
        val comments = mutableListOf<PostComment>()
        // A reply to a comment that is not shown would look like a reply to the comment above it.
        val hidden = HashSet<String>()
        for (index in 0 until trees.length()) {
            if (comments.size >= MAX_COMMENTS) break
            val entry = trees.optJSONObject(index) ?: continue
            // An entry without a node is a "more replies" placeholder.
            val node = entry.optJSONObject("node") ?: continue
            val depth = entry.optInt("depth")
            val text = node.optJSONObject("content")?.stringOrNull("markdown")?.takeUnless { it in GONE }
            val skip = depth > MAX_COMMENT_DEPTH || node.optBoolean("isRemoved") ||
                entry.stringOrNull("parentId") in hidden
            if (text == null || skip) {
                node.stringOrNull("id")?.let(hidden::add)
                continue
            }
            comments += PostComment(
                author = authorOf(node) ?: "[deleted]",
                body = text,
                score = node.intOrNull("score").takeUnless { node.optBoolean("isScoreHidden") },
                depth = depth,
                createdAt = node.stringOrNull("createdAt")?.let(::redditEpochSeconds),
                stickied = node.optBoolean("isStickied"),
            )
        }
        return comments
    }

    private fun authorOf(node: JSONObject): String? =
        node.optJSONObject("authorInfo")?.stringOrNull("name")
            ?.takeIf { it != "[deleted]" }
            ?.let { "u/$it" }

    // `content.html` is the body as plain text. `markdown` keeps the markup characters.
    private fun textOf(post: JSONObject): String? {
        val content = post.optJSONObject("content") ?: return null
        return (content.stringOrNull("html") ?: content.stringOrNull("markdown"))?.takeUnless { it in GONE }
    }

    private fun isVideo(post: JSONObject): Boolean {
        val media = post.optJSONObject("media") ?: return false
        return media.stringOrNull("typeHint") in VIDEO_HINTS || media.optJSONObject("streaming") != null
    }

    private fun mediaOf(post: JSONObject): List<PostMedia> {
        val gallery = post.optJSONObject("gallery")?.optJSONArray("items")
        if (gallery != null && gallery.length() > 0) {
            return (0 until minOf(gallery.length(), MAX_IMAGES)).mapNotNull { index ->
                val image = gallery.optJSONObject(index)?.optJSONObject("media") ?: return@mapNotNull null
                val url = image.httpsOrNull("url") ?: return@mapNotNull null
                PostMedia(PostMediaType.IMAGE, url, width = image.intOrNull("width"), height = image.intOrNull("height"))
            }
        }

        val media = post.optJSONObject("media") ?: return emptyList()
        val still = media.optJSONObject("still")?.optJSONObject("source")
        val poster = still?.httpsOrNull("url")
        val streaming = media.optJSONObject("streaming")
        val file = media.optJSONObject("packagedMedia")
            ?.optJSONObject("muxedMp4s")
            ?.optJSONObject("recommended")
            ?.httpsOrNull("url")
            // A GIF on i.redd.it has no stream. Reddit serves it as a short MP4.
            ?: media.optJSONObject("animated")?.optJSONObject("mp4_source")?.httpsOrNull("url")
        val video = streaming?.httpsOrNull("hlsUrl") ?: file
        val size = (streaming ?: still)?.optJSONObject("dimensions")
        return when {
            video != null -> listOf(
                PostMedia(
                    type = PostMediaType.VIDEO,
                    url = video,
                    thumbnailUrl = poster,
                    width = size?.intOrNull("width"),
                    height = size?.intOrNull("height"),
                    durationSeconds = (streaming?.opt("duration") as? Number)?.toDouble(),
                    // The stream is a playlist. Only the muxed file can be saved.
                    downloadUrl = file,
                ),
            )

            // A video without a stream or a file still has its cover image.
            poster != null && (media.stringOrNull("typeHint") == "IMAGE" || isVideo(post)) -> listOf(
                PostMedia(PostMediaType.IMAGE, poster, width = size?.intOrNull("width"), height = size?.intOrNull("height")),
            )

            else -> emptyList()
        }
    }

    private fun JSONObject.httpsOrNull(key: String): String? =
        stringOrNull(key)?.takeIf { it.startsWith("https://") }

    // The federation writes the same count as an integer in one field and as a float in another.
    private fun JSONObject.intOrNull(key: String): Int? =
        (opt(key) as? Number)?.toLong()?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt()
}
