package dev.cappyt.socialview.core

import java.net.URI
import java.util.Locale

/** One media file to save: where it comes from and the name it gets on the device. */
data class MediaDownload(
    val url: String,
    val fileName: String,
    val video: Boolean,
    val headers: Map<String, String> = emptyMap(),
)

object MediaDownloads {
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "webm", "m4v")
    private val UNSAFE = Regex("""[^A-Za-z0-9_-]+""")

    /**
     * The download of each media item of the post at [postUrl], in the order of [media]. An entry
     * is null when the platform gives no file for the item, for example a video that is only a
     * stream.
     */
    fun of(postUrl: String, media: List<PostMedia>): List<MediaDownload?> {
        val base = baseName(postUrl)
        return media.mapIndexed { index, item ->
            val url = item.downloadUrl?.takeIf { it.startsWith("https://") } ?: return@mapIndexed null
            val video = item.type == PostMediaType.VIDEO
            val suffix = if (media.size > 1) "-${index + 1}" else ""
            MediaDownload(url, "$base$suffix.${extension(url, video)}", video, item.headers)
        }
    }

    /** A file name stem from the post link, for example `instagram-Df147tCiAyB`. */
    private fun baseName(postUrl: String): String {
        val uri = runCatching { URI(postUrl) }.getOrNull()
        val site = uri?.host.orEmpty().removePrefix("www.").substringBefore('.')
        // The last path segment is the post id, except on the Facebook links that keep it in the query.
        val id = uri?.path.orEmpty().trim('/').substringAfterLast('/')
            .let { if (it.endsWith(".php") || it.isEmpty() || it == "watch") uri?.query.orEmpty() else it }
        return listOf(site, id)
            .map { it.replace(UNSAFE, "-").trim('-') }
            .filter { it.isNotEmpty() }
            .joinToString("-")
            .ifEmpty { "media" }
            .take(80)
    }

    private fun extension(url: String, video: Boolean): String {
        val known = if (video) VIDEO_EXTENSIONS else IMAGE_EXTENSIONS
        val fromPath = runCatching { URI(url).path }.getOrNull().orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
        return fromPath.takeIf { it in known } ?: if (video) "mp4" else "jpg"
    }
}
