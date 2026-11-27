package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.EmbedSpec
import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.PostLink
import dev.cappyt.socialview.net.HttpFetcher
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException

class XProvider(
    private val fetcher: HttpFetcher,
) : PostProvider {
    override val platform = Platform.X

    override suspend fun load(link: PostLink): PostDetails {
        val embed = EmbedSpec(
            url = "https://platform.twitter.com/embed/Tweet.html?id=${link.postId}&dnt=true&hideThread=true",
            allowedHosts = setOf("platform.twitter.com", "platform.x.com"),
            heightDp = 480,
        )

        val metadataUrl = "https://publish.twitter.com/oembed?omit_script=1&dnt=true" +
            "&url=${encode(link.browserUrl)}"
        val response = try {
            fetcher.get(metadataUrl, mapOf("Accept" to "application/json"))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

        if (response == null) {
            return embedOnly(link, embed, "X metadata could not be reached. The official embed is shown.")
        }
        if (response.status == 404 || response.status == 410) {
            throw PostException(
                kind = ErrorKind.NOT_FOUND,
                message = "This X post was not found or is not public.",
                browserUrl = link.browserUrl,
                platform = platform,
            )
        }
        if (!response.isSuccess) {
            return embedOnly(
                link,
                embed,
                "X did not return metadata (HTTP ${response.status}). The official embed is shown.",
            )
        }

        val metadata = XOEmbedParser.parse(response.body)
            ?: return embedOnly(link, embed, "X returned unexpected metadata. The official embed is shown.")

        return PostDetails(
            platform = platform,
            browserUrl = link.browserUrl,
            author = metadata.authorName,
            handle = metadata.handle,
            text = metadata.text,
            embed = embed,
        )
    }

    private fun embedOnly(link: PostLink, embed: EmbedSpec, note: String) = PostDetails(
        platform = platform,
        browserUrl = link.browserUrl,
        embed = embed,
        note = note,
    )

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}