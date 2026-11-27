package dev.cappyt.socialview.providers

import java.net.URI
import org.json.JSONObject

data class XOEmbed(
    val authorName: String?,
    val handle: String?,
    val text: String?,
)

object XOEmbedParser {
    fun parse(body: String): XOEmbed? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        return XOEmbed(
            authorName = root.stringOrNull("author_name"),
            handle = handleFrom(root.stringOrNull("author_url")),
            text = HtmlText.firstParagraph(root.stringOrNull("html").orEmpty()),
        )
    }

    private fun handleFrom(authorUrl: String?): String? {
        if (authorUrl.isNullOrBlank()) return null
        val path = runCatching { URI(authorUrl).path }.getOrNull().orEmpty()
        val name = path.trim('/').split('/').firstOrNull().orEmpty()
        return name.takeIf { it.isNotBlank() }?.let { "@$it" }
    }
}