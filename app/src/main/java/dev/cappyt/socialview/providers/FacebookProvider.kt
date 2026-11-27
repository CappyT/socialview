package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.EmbedSpec
import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.LinkParser
import dev.cappyt.socialview.core.ParsedLink
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.PostKind
import dev.cappyt.socialview.core.PostLink
import dev.cappyt.socialview.core.PostMedia
import dev.cappyt.socialview.core.PostMediaType
import dev.cappyt.socialview.net.BrowserHeaders
import dev.cappyt.socialview.net.HttpFetcher
import dev.cappyt.socialview.net.HttpResult
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException

class FacebookProvider(
    private val fetcher: HttpFetcher,
) : PostProvider {
    override val platform = Platform.FACEBOOK

    override suspend fun load(link: PostLink): PostDetails {
        val response = try {
            fetcher.get(link.browserUrl, BrowserHeaders.navigation())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

        if (response != null && (response.status == 404 || response.status == 410)) {
            throw PostException(
                kind = ErrorKind.NOT_FOUND,
                message = "This Facebook post was not found or is not public.",
                browserUrl = link.browserUrl,
                platform = platform,
            )
        }

        val page = response?.takeIf { it.isSuccess }
            ?.let { FacebookPageParser.parse(it.body, link.postId) }
        if (page == null) {
            val reason = when {
                response == null -> "Facebook could not be reached."
                response.status !in 200..299 -> "Facebook returned HTTP ${response.status}."
                else -> "Facebook did not return public page metadata."
            }
            return pluginFallback(link, reason)
        }

        val video = page.videoUrl
        val media = when {
            video != null -> listOf(
                PostMedia(
                    type = PostMediaType.VIDEO,
                    url = video,
                    thumbnailUrl = page.imageUrl,
                ),
            )

            page.imageUrl != null -> listOf(
                PostMedia(type = PostMediaType.IMAGE, url = page.imageUrl),
            )

            else -> emptyList()
        }
        if (media.isEmpty() && page.caption == null) {
            return pluginFallback(link, "Facebook did not return public page metadata.")
        }

        val isVideoPost = link.kind == PostKind.REEL || link.kind == PostKind.VIDEO
        val note = if (isVideoPost && video == null) {
            if (page.imageUrl != null) {
                "Facebook did not return the public video stream. The cover image is shown."
            } else {
                "Facebook did not return the public video stream."
            }
        } else {
            null
        }
        return PostDetails(
            platform = platform,
            browserUrl = canonicalBrowserUrl(page, link),
            author = page.author,
            handle = page.handle,
            text = page.caption,
            media = media,
            note = note,
        )
    }

    private fun canonicalBrowserUrl(page: FacebookPage, link: PostLink): String {
        val canonical = page.canonicalUrl ?: return link.browserUrl
        val parsed = LinkParser.classify(canonical) as? ParsedLink.Post ?: return link.browserUrl
        return if (parsed.link.postId == link.postId) parsed.link.browserUrl else link.browserUrl
    }

    private fun pluginFallback(link: PostLink, reason: String): PostDetails {
        val isVideo = link.kind == PostKind.REEL || link.kind == PostKind.VIDEO
        val plugin = if (isVideo) "video" else "post"
        val href = URLEncoder.encode(link.browserUrl, Charsets.UTF_8.name())
        return PostDetails(
            platform = platform,
            browserUrl = link.browserUrl,
            embed = EmbedSpec(
                url = "https://www.facebook.com/plugins/$plugin.php?href=$href&show_text=true",
                allowedHosts = setOf("www.facebook.com", "facebook.com", "web.facebook.com"),
                heightDp = if (isVideo) 480 else 640,
            ),
            note = "$reason The official Facebook plugin is shown and can ask for a login.",
        )
    }
}