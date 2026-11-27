package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.PostComment
import dev.cappyt.socialview.core.PostMedia
import dev.cappyt.socialview.core.PostMediaType
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class InstagramPost(
    val mediaId: String? = null,
    val shortcode: String? = null,
    val author: String? = null,
    val handle: String? = null,
    val caption: String? = null,
    val media: List<PostMedia> = emptyList(),
    val comments: List<PostComment> = emptyList(),
    val commentCount: Int? = null,
    val likeCount: Int? = null,
    val videoUnavailable: Boolean = false,
)

object InstagramPostParser {
    private const val MAX_COMMENTS = 10
    private const val CONTEXT_JSON_KEY = "\"contextJSON\""

    private val EXTERNAL_API_MARKERS = listOf(
        "\"xdt_api__v1__media__shortcode__web_info\"",
        // Canonical pages carry the logged-out media object in the Polaris script.
        "\"xig_polaris_media\"",
    )

    fun parse(body: String, shortcode: String? = null): InstagramPost? {
        val candidates = buildList {
            contextJsonValues(body).forEach { value ->
                runCatching { JSONObject(value) }.getOrNull()?.let(::add)
            }
            EXTERNAL_API_MARKERS.forEach { marker ->
                extractObject(body, marker)?.let(::add)
            }
            body.trim().takeIf { it.startsWith("{") }
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?.let(::add)
        }

        val parsed = candidates.asSequence()
            .flatMap { mediaObjects(it).asSequence() }
            .mapNotNull { parseNode(it, shortcode) }
            .firstOrNull { it.hasData() }
        return parsed ?: parseOpenGraph(body, shortcode)
    }

    fun parseComments(body: String): List<PostComment> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        return commentsOf(root)
    }

    private fun mediaObjects(root: JSONObject): List<JSONObject> = buildList {
        root.optJSONObject("gql_data")?.optJSONObject("shortcode_media")?.let(::add)
        root.optJSONObject("data")?.optJSONObject("shortcode_media")?.let(::add)
        root.optJSONObject("graphql")?.optJSONObject("shortcode_media")?.let(::add)
        root.optJSONObject("shortcode_media")?.let(::add)
        root.optJSONArray("items")?.optJSONObject(0)?.let(::add)
        root.optJSONObject("xdt_api__v1__media__shortcode__web_info")
            ?.optJSONArray("items")
            ?.optJSONObject(0)
            ?.let(::add)
        root.optJSONObject("if_not_gated_logged_out")?.let(::add)
        if (root.has("edge_media_to_caption") || root.has("display_url") || root.has("video_url")) {
            add(root)
        }
    }

    private fun parseNode(node: JSONObject, fallbackShortcode: String?): InstagramPost? {
        val owner = node.optJSONObject("owner") ?: node.optJSONObject("user")
        val handleName = owner?.stringOrNull("username")?.removePrefix("@")
        val authorName = owner?.stringOrNull("full_name")
        val caption = captionOf(node)
        val shortcode = node.stringOrNull("shortcode") ?: node.stringOrNull("code")
        if (shortcode != null && fallbackShortcode != null && shortcode != fallbackShortcode) {
            return null
        }
        val children = childrenOf(node)
        val videoUnavailable = children.any { isVideo(it) && videoUrlOf(it) == null } ||
            (children.isEmpty() && isVideo(node) && videoUrlOf(node) == null)
        val media = if (children.isNotEmpty()) {
            children.mapNotNull(::mediaOf)
        } else {
            listOfNotNull(mediaOf(node))
        }
        val parsed = InstagramPost(
            mediaId = node.stringOrNull("pk") ?: node.stringOrNull("id"),
            shortcode = shortcode ?: fallbackShortcode,
            author = authorName,
            handle = handleName?.let { "@$it" },
            caption = caption,
            media = media,
            comments = commentsOf(node),
            commentCount = node.optJSONObject("edge_media_to_comment")?.intOrNull("count")
                ?: node.intOrNull("comment_count"),
            likeCount = node.optJSONObject("edge_liked_by")?.intOrNull("count")
                ?: node.optJSONObject("edge_media_preview_like")?.intOrNull("count")
                ?: node.intOrNull("like_count"),
            videoUnavailable = videoUnavailable,
        )
        return parsed.takeIf { it.hasData() }
    }

    private fun captionOf(node: JSONObject): String? {
        val edges = node.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")
        if (edges != null && edges.length() > 0) {
            edges.optJSONObject(0)
                ?.optJSONObject("node")
                ?.stringOrNull("text")
                ?.let { return it }
        }
        val caption = node.optJSONObject("caption")
        caption?.stringOrNull("text")?.let { return it }
        return node.stringOrNull("caption")
    }

    private fun childrenOf(node: JSONObject): List<JSONObject> {
        val fromEdges = node.optJSONObject("edge_sidecar_to_children")
            ?.optJSONArray("edges")
            ?.objects()
            .orEmpty()
            .mapNotNull { it.optJSONObject("node") }
        if (fromEdges.isNotEmpty()) return fromEdges
        return node.optJSONArray("carousel_media")?.objects().orEmpty()
    }

    private fun mediaOf(node: JSONObject): PostMedia? {
        val image = imageUrlOf(node)
        val video = videoUrlOf(node)
        val dimensions = node.optJSONObject("dimensions")
        val width = dimensions?.intOrNull("width") ?: node.intOrNull("original_width")
        val height = dimensions?.intOrNull("height") ?: node.intOrNull("original_height")
        val duration = node.doubleOrNull("video_duration")
        return when {
            video != null -> PostMedia(
                type = PostMediaType.VIDEO,
                url = video,
                thumbnailUrl = image,
                width = width,
                height = height,
                durationSeconds = duration,
            )

            image != null -> PostMedia(
                type = PostMediaType.IMAGE,
                url = image,
                width = width,
                height = height,
            )

            else -> null
        }
    }

    private fun isVideo(node: JSONObject): Boolean =
        node.optBoolean("is_video") || node.optInt("media_type") == 2 || videoUrlOf(node) != null

    private fun imageUrlOf(node: JSONObject): String? {
        node.stringOrNull("display_url")?.let { return it }
        val candidates = node.optJSONArray("display_resources")
            ?.objects()
            .orEmpty()
            .sortedByDescending { it.optInt("config_width") }
            .mapNotNull { it.stringOrNull("src") }
            .firstOrNull()
        if (candidates != null) return candidates
        val versions = node.optJSONObject("image_versions2")
            ?.optJSONArray("candidates")
            ?.objects()
            .orEmpty()
            .sortedByDescending { it.optInt("width") }
            .mapNotNull { it.stringOrNull("url") }
            .firstOrNull()
        return versions ?: node.stringOrNull("thumbnail_src")
    }

    private fun videoUrlOf(node: JSONObject): String? {
        node.stringOrNull("video_url")?.let { return it }
        return node.optJSONArray("video_versions")
            ?.objects()
            .orEmpty()
            .sortedByDescending { it.optInt("width") }
            .mapNotNull { it.stringOrNull("url") }
            .firstOrNull()
    }

    private fun commentsOf(node: JSONObject): List<PostComment> {
        val array = node.optJSONArray("comments")
            ?: node.optJSONObject("data")?.optJSONArray("comments")
            ?: node.optJSONObject("edge_media_to_parent_comment")?.optJSONArray("edges")
            ?: return emptyList()
        val comments = mutableListOf<PostComment>()
        for (index in 0 until array.length()) {
            if (comments.size >= MAX_COMMENTS) break
            val item = array.optJSONObject(index) ?: continue
            val entry = item.optJSONObject("node") ?: item
            val text = entry.stringOrNull("text")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val user = entry.optJSONObject("user")
                ?: entry.optJSONObject("owner")
                ?: entry.optJSONObject("node")?.optJSONObject("owner")
            val username = user?.stringOrNull("username")?.removePrefix("@") ?: "instagram"
            val score = entry.intOrNull("comment_like_count")
                ?: entry.optJSONObject("edge_liked_by")?.intOrNull("count")
            comments += PostComment(author = "@$username", body = text, score = score)
        }
        return comments
    }

    private fun parseOpenGraph(body: String, fallbackShortcode: String?): InstagramPost? {
        val meta = HtmlMeta.meta(body)
        val image = meta["og:image"]?.takeIf(::isHttps)
        val video = (meta["og:video:secure_url"] ?: meta["og:video"])?.takeIf(::isHttps)
        val title = meta["og:title"]
        val author = title?.substringBefore(" on Instagram:", "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val caption = captionFromTitle(title)
            ?: meta["og:description"]?.trim()?.takeIf { it.isNotEmpty() }
        if (image == null && video == null && author == null && caption == null) return null

        val handle = meta["og:url"]?.let(::handleFromUrl)
        val media = when {
            video != null -> PostMedia(
                type = PostMediaType.VIDEO,
                url = video,
                thumbnailUrl = image,
            )

            image != null -> PostMedia(type = PostMediaType.IMAGE, url = image)
            else -> null
        }
        return InstagramPost(
            shortcode = fallbackShortcode,
            author = author,
            handle = handle,
            caption = caption,
            media = listOfNotNull(media),
        )
    }

    private fun captionFromTitle(title: String?): String? {
        if (title == null) return null
        val marker = " on Instagram:"
        val index = title.indexOf(marker)
        if (index < 0) return null
        return title.substring(index + marker.length)
            .trim()
            .removeSurrounding("\"")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun handleFromUrl(raw: String): String? {
        val path = runCatching { URI(raw).path }.getOrNull()?.trim('/') ?: return null
        val segments = path.split('/')
        val index = segments.indexOfFirst { it == "p" || it == "reel" || it == "reels" || it == "tv" }
        if (index <= 0) return null
        val name = segments[index - 1]
        return name.takeIf { it.isNotEmpty() }?.let { "@$it" }
    }

    private fun InstagramPost.hasData(): Boolean =
        media.isNotEmpty() || caption != null || handle != null || author != null

    private fun decodeJsonString(raw: String): String? =
        runCatching { JSONTokener("\"$raw\"").nextValue() as? String }.getOrNull()

    // A regex over the multi-hundred-kilobyte contextJSON value overflows the JVM stack.
    private fun contextJsonValues(body: String): List<String> {
        val values = mutableListOf<String>()
        var index = 0
        while (index < body.length) {
            val key = body.indexOf(CONTEXT_JSON_KEY, index)
            if (key < 0) break
            var cursor = skipWhitespace(body, key + CONTEXT_JSON_KEY.length)
            if (cursor >= body.length || body[cursor] != ':') {
                index = cursor
                continue
            }
            cursor = skipWhitespace(body, cursor + 1)
            if (cursor >= body.length || body[cursor] != '"') {
                index = cursor
                continue
            }
            cursor++
            val value = StringBuilder()
            var escaped = false
            while (cursor < body.length) {
                val char = body[cursor]
                when {
                    escaped -> {
                        value.append('\\').append(char)
                        escaped = false
                    }

                    char == '\\' -> escaped = true
                    char == '"' -> break
                    else -> value.append(char)
                }
                cursor++
            }
            decodeJsonString(value.toString())?.let(values::add)
            index = cursor + 1
        }
        return values
    }

    private fun skipWhitespace(body: String, start: Int): Int {
        var index = start
        while (index < body.length && body[index].isWhitespace()) index++
        return index
    }

    private fun extractObject(body: String, marker: String): JSONObject? {
        val markerIndex = body.indexOf(marker)
        if (markerIndex < 0) return null
        val start = body.indexOf('{', markerIndex + marker.length)
        if (start < 0) return null
        return jsonObjectAt(body, start)
    }

    private fun isHttps(url: String): Boolean = url.startsWith("https://")

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null
}