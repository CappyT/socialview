package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.LinkParser
import dev.cappyt.socialview.core.ParsedLink
import java.net.URI
import java.util.Locale
import org.json.JSONTokener

data class FacebookPage(
    val canonicalUrl: String? = null,
    val author: String? = null,
    val handle: String? = null,
    val caption: String? = null,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
)

object FacebookPageParser {
    private val HD_VIDEO = Regex(
        """"browser_native_hd_url"\s*:\s*"((?:\\.|[^"\\])*)"""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val SD_VIDEO = Regex(
        """"browser_native_sd_url"\s*:\s*"((?:\\.|[^"\\])*)"""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val PLAYABLE_VIDEO = Regex(
        """"playable_url_quality_hd"\s*:\s*"((?:\\.|[^"\\])*)"""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val STATS = Regex(
        """^\d[\d.,]*\s*[KMB]?\s+(?:views|reactions|likes|comments|shares)""" +
            """(?:\s*[·•]\s*\d[\d.,]*\s*[KMB]?\s+(?:views|reactions|likes|comments|shares))*""" +
            """\s*\|\s*""",
        RegexOption.IGNORE_CASE,
    )

    private val RESERVED_SEGMENTS = setOf(
        "reel", "reels", "watch", "videos", "video.php", "photo.php", "photo",
        "story.php", "permalink.php", "share", "groups", "profile.php", "people",
        "pages", "media", "p", "r", "login", "checkpoint", "home",
    )

    fun parse(body: String, requestedId: String? = null): FacebookPage? {
        val meta = HtmlMeta.meta(body)
        val canonical = (HtmlMeta.canonical(body) ?: meta["og:url"])
            ?.takeIf { it.startsWith("https://") }
            ?: return null
        val postId = postIdOf(canonical) ?: return null
        if (requestedId != null && postId != requestedId) return null
        val handle = handleOf(canonical)
        val title = meta["og:title"]
        val author = title?.let { authorOf(it, handle) }
        val caption = title?.let { captionOf(it, author) }
            ?.takeIf { it.isNotBlank() }
            ?: meta["og:description"]?.trim()?.takeIf { it.isNotEmpty() }
        val image = (meta["og:image"] ?: meta["og:image:secure_url"])
            ?.takeIf { it.startsWith("https://") }
        val video = videoUrlOf(body) ?: meta["og:video:secure_url"]
            ?.takeIf { it.startsWith("https://") }
            ?: meta["og:video"]?.takeIf { it.startsWith("https://") }

        if (caption == null && image == null && video == null) return null
        return FacebookPage(
            canonicalUrl = canonical,
            author = author,
            handle = handle,
            caption = caption,
            imageUrl = image,
            videoUrl = video,
        )
    }

    private fun postIdOf(canonical: String): String? =
        (LinkParser.classify(canonical) as? ParsedLink.Post)?.link?.postId

    private fun videoUrlOf(body: String): String? {
        for (pattern in listOf(HD_VIDEO, SD_VIDEO, PLAYABLE_VIDEO)) {
            val value = pattern.find(body)?.groupValues?.get(1) ?: continue
            val decoded = decodeJsonString(value) ?: continue
            if (decoded.startsWith("https://")) return decoded
        }
        return null
    }

    private fun authorOf(title: String, handle: String?): String? {
        val name = handle?.removePrefix("@") ?: return null
        val index = title.lastIndexOf(" | ")
        if (index < 0) return null
        val suffix = title.substring(index + 3).trim()
        return suffix.takeIf { it.removePrefix("@").equals(name, ignoreCase = true) }
    }

    private fun captionOf(title: String, author: String?): String {
        var value = title
        if (author != null) {
            val suffix = " | $author"
            if (value.endsWith(suffix)) value = value.removeSuffix(suffix)
        }
        val stats = STATS.find(value)
        if (stats != null && stats.range.first == 0) {
            value = value.substring(stats.range.last + 1)
        }
        return value.trim()
    }

    private fun handleOf(raw: String): String? {
        val path = runCatching { URI(raw).path }.getOrNull()?.trim('/') ?: return null
        val first = path.substringBefore('/')
        if (first.isEmpty()) return null
        if (first.lowercase(Locale.ROOT) in RESERVED_SEGMENTS) return null
        if (first.all { it.isDigit() }) return null
        return "@$first"
    }

    private fun decodeJsonString(raw: String): String? =
        runCatching { JSONTokener("\"$raw\"").nextValue() as? String }.getOrNull()
}