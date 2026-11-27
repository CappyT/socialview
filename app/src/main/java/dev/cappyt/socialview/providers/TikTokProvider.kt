package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.EmbedSpec
import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.PostLink
import dev.cappyt.socialview.net.BrowserHeaders
import dev.cappyt.socialview.net.HttpFetcher
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException

class TikTokProvider(
    private val fetcher: HttpFetcher,
) : PostProvider {
    override val platform = Platform.TIKTOK

    override suspend fun load(link: PostLink): PostDetails {
        val response = try {
            fetcher.get(itemUrl(link.postId), BrowserHeaders.json())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val result = response?.takeIf { it.isSuccess }?.let { TikTokItemParser.parse(it.body, link.postId) }
        return when (result) {
            // The official player can still show a post whose media the endpoint left out.
            is TikTokItemResult.Post -> if (result.post.media.isEmpty()) officialPlayer(link) else result.post
            TikTokItemResult.Missing -> throw PostException(
                kind = ErrorKind.NOT_FOUND,
                message = "This TikTok post was not found or is not public.",
                browserUrl = link.browserUrl,
                platform = platform,
            )

            null -> officialPlayer(link)
        }
    }

    /** The post in TikTok's official web player, for when the data endpoint does not answer. */
    private suspend fun officialPlayer(link: PostLink): PostDetails {
        var playerUrl = fallbackPlayerUrl(link.postId)
        var title: String? = null
        var author: String? = null
        var handle: String? = null
        var thumbnail: String? = null
        var note: String? = null

        val metadataUrl = "https://www.tiktok.com/oembed?url=${encode(link.browserUrl)}"
        val response = try {
            fetcher.get(metadataUrl, mapOf("Accept" to "application/json"))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

        when {
            response == null -> {
                note = "TikTok metadata could not be reached. The official player is shown."
            }

            response.isSuccess -> {
                val metadata = TikTokOEmbedParser.parse(response.body)
                if (metadata == null) {
                    note = "TikTok returned unexpected metadata. The official player is shown."
                } else {
                    playerUrl = metadata.playerUrl ?: playerUrl
                    title = metadata.title
                    author = metadata.authorName
                    handle = metadata.handle
                    thumbnail = metadata.thumbnailUrl
                }
            }

            response.status == 429 -> {
                note = "TikTok is rate limiting metadata requests. The official player is shown."
            }

            response.status in 400..499 -> {
                note = "TikTok did not return metadata (HTTP ${response.status}). The official player is shown."
            }

            else -> {
                note = "TikTok metadata is unavailable (HTTP ${response.status}). The official player is shown."
            }
        }

        return PostDetails(
            platform = platform,
            browserUrl = link.browserUrl,
            author = author,
            handle = handle ?: pathHandle(link.browserUrl),
            text = title,
            images = listOfNotNull(thumbnail),
            embed = EmbedSpec(
                url = playerUrl,
                allowedHosts = setOf("www.tiktok.com", "tiktok.com"),
                heightDp = 620,
            ),
            note = note,
        )
    }

    private fun fallbackPlayerUrl(postId: String): String =
        "https://www.tiktok.com/player/v1/$postId" +
            "?embed_from=socialview&music_info=1&description=1&autoplay=1&muted=1"

    private fun pathHandle(browserUrl: String): String? {
        val path = runCatching { URI(browserUrl).path }.getOrNull().orEmpty()
        return path.split('/').firstOrNull { it.startsWith("@") }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        /** The data endpoint of the official web player. It needs no cookie and no signature. */
        internal fun itemUrl(postId: String): String =
            "https://www.tiktok.com/player/api/v1/items?item_ids=$postId&language=en&aid=1459&data_source=web_core"
    }
}
