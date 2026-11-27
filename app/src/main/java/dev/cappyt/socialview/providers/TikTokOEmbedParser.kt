package dev.cappyt.socialview.providers

import java.net.URI
import java.util.Locale
import org.json.JSONObject

data class TikTokOEmbed(
    val playerUrl: String?,
    val title: String?,
    val authorName: String?,
    val handle: String?,
    val thumbnailUrl: String?,
)

object TikTokOEmbedParser {
    private val SRC_PATTERN = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    fun parse(body: String): TikTokOEmbed? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val html = root.stringOrNull("html").orEmpty()
        val playerUrl = SRC_PATTERN.find(html)
            ?.groupValues
            ?.get(1)
            ?.let(HtmlText::decode)
            ?.let(::validPlayerUrl)

        return TikTokOEmbed(
            playerUrl = playerUrl,
            title = root.stringOrNull("title"),
            authorName = root.stringOrNull("author_name"),
            handle = handleFrom(root.stringOrNull("author_url")),
            thumbnailUrl = root.stringOrNull("thumbnail_url")?.takeIf { it.startsWith("https://") },
        )
    }

    private fun validPlayerUrl(raw: String): String? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) != "https") return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host != "www.tiktok.com" && host != "tiktok.com") return null
        val path = uri.path.orEmpty()
        if (!path.startsWith("/player/") && !path.startsWith("/embed/")) return null
        return raw
    }

    private fun handleFrom(authorUrl: String?): String? {
        if (authorUrl.isNullOrBlank()) return null
        val path = runCatching { URI(authorUrl).path }.getOrNull().orEmpty()
        val handle = path.trim('/').split('/').firstOrNull().orEmpty()
        return handle.takeIf { it.startsWith("@") }
    }
}