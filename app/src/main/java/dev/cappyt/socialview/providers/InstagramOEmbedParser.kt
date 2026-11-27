package dev.cappyt.socialview.providers

import java.net.URI
import org.json.JSONObject

data class InstagramOEmbed(
    val caption: String?,
    val authorName: String?,
    val authorUrl: String?,
    val thumbnailUrl: String?,
    val mediaId: String?,
) {
    val handle: String?
        get() = authorUrl?.let(::userHandleFromUrl) ?: authorName?.let(::userHandle)

    private fun userHandle(name: String): String =
        if (name.startsWith("@")) name else "@$name"

    private fun userHandleFromUrl(url: String): String? {
        val path = runCatching { URI(url).path }.getOrNull().orEmpty()
        val name = path.trim('/').substringBefore('/')
        return name.takeIf { it.isNotEmpty() }?.let(::userHandle)
    }
}

object InstagramOEmbedParser {
    fun parse(body: String): InstagramOEmbed? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val metadata = InstagramOEmbed(
            caption = root.stringOrNull("title"),
            authorName = root.stringOrNull("author_name"),
            authorUrl = root.stringOrNull("author_url"),
            thumbnailUrl = root.stringOrNull("thumbnail_url")
                ?.takeIf { it.startsWith("https://") },
            mediaId = root.stringOrNull("media_id"),
        )
        val hasMetadata = metadata.caption != null ||
            metadata.authorName != null ||
            metadata.authorUrl != null ||
            metadata.thumbnailUrl != null ||
            metadata.mediaId != null
        return metadata.takeIf { hasMetadata }
    }
}