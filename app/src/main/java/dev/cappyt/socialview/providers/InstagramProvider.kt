package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostComment
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

class InstagramProvider(
    private val fetcher: HttpFetcher,
) : PostProvider {
    override val platform = Platform.INSTAGRAM

    override suspend fun load(link: PostLink): PostDetails {
        var post: InstagramPost? = null
        var lastStatus: Int? = null
        var oEmbedStatus: Int? = null
        var anyResponse = false

        val documents = listOf(embedUrl(link), link.browserUrl)
        for (url in documents) {
            val response = request(url, BrowserHeaders.navigation()) ?: continue
            anyResponse = true
            lastStatus = response.status
            if (!response.isSuccess) continue
            val parsed = InstagramPostParser.parse(response.body, link.postId) ?: continue
            post = merge(post, parsed)
            if (post.hasCompleteMedia()) break
        }

        val metadata = if (post?.media.isNullOrEmpty()) {
            fetchOEmbed(link)
        } else {
            null
        }
        if (metadata != null) {
            anyResponse = true
            oEmbedStatus = metadata.status
            metadata.status?.let { lastStatus = it }
            post = merge(post, metadata.post)
        }

        if (!anyResponse) {
            throw PostException(
                kind = ErrorKind.NETWORK,
                message = "Could not reach Instagram. Check the network connection.",
                browserUrl = link.browserUrl,
                platform = platform,
            )
        }

        val resolved = post
        if (resolved == null) {
            if (isNotFound(oEmbedStatus) || isNotFound(lastStatus)) {
                throw PostException(
                    kind = ErrorKind.NOT_FOUND,
                    message = "This Instagram post was not found or is not public.",
                    browserUrl = link.browserUrl,
                    platform = platform,
                )
            }
            return PostDetails(
                platform = platform,
                browserUrl = link.browserUrl,
                note = buildString {
                    append("Instagram did not return public data for this post")
                    if (lastStatus != null && lastStatus !in 200..299) {
                        append(" (HTTP $lastStatus)")
                    }
                    append(". Open it in a browser.")
                },
            )
        }

        val comments = if (resolved.comments.isEmpty() && (resolved.commentCount ?: 0) > 0) {
            val mediaId = resolved.mediaId
            if (mediaId != null) fetchComments(mediaId) else emptyList()
        } else {
            resolved.comments
        }
        return toDetails(link, resolved, comments, lastStatus)
    }

    private fun toDetails(
        link: PostLink,
        post: InstagramPost,
        comments: List<PostComment>,
        lastStatus: Int?,
    ): PostDetails {
        val notes = mutableListOf<String>()
        if (post.videoUnavailable) {
            notes += "Instagram did not return the public video stream for one or more media items. " +
                "Their cover images are shown."
        }
        if (post.media.isEmpty()) {
            notes += "Instagram did not return public media for this post. Open it in a browser."
        }
        if (comments.isEmpty() && (post.commentCount ?: 0) > 0) {
            notes += "Instagram reports ${post.commentCount} comments but does not publish " +
                "comment text to logged-out visitors."
        }
        if (lastStatus != null && lastStatus !in 200..299) {
            notes += "Instagram returned HTTP $lastStatus."
        }
        return PostDetails(
            platform = platform,
            browserUrl = link.browserUrl,
            author = post.author,
            handle = post.handle,
            text = post.caption,
            media = post.media,
            comments = comments,
            note = notes.joinToString(" ").takeIf { it.isNotBlank() },
            initialMediaIndex = link.initialMediaIndex.coerceIn(0, (post.media.size - 1).coerceAtLeast(0)),
        )
    }

    private fun merge(first: InstagramPost?, second: InstagramPost?): InstagramPost? {
        if (first == null) return second
        if (second == null) return first
        val media = preferredMedia(first.media, second.media)
        return first.copy(
            mediaId = first.mediaId ?: second.mediaId,
            shortcode = first.shortcode ?: second.shortcode,
            author = first.author ?: second.author,
            handle = first.handle ?: second.handle,
            caption = first.caption ?: second.caption,
            media = media,
            comments = first.comments.ifEmpty { second.comments },
            commentCount = first.commentCount ?: second.commentCount,
            likeCount = first.likeCount ?: second.likeCount,
            videoUnavailable = if (media.any { it.type == PostMediaType.VIDEO }) {
                false
            } else {
                first.videoUnavailable || second.videoUnavailable
            },
        )
    }

    private fun preferredMedia(first: List<PostMedia>, second: List<PostMedia>): List<PostMedia> =
        when {
            first.any { it.type == PostMediaType.VIDEO } -> first
            second.any { it.type == PostMediaType.VIDEO } -> second
            first.isNotEmpty() -> first
            else -> second
        }

    private fun InstagramPost?.hasCompleteMedia(): Boolean =
        this != null && media.isNotEmpty() && !videoUnavailable

    private fun isNotFound(status: Int?): Boolean = status == 404 || status == 410

    private suspend fun fetchOEmbed(link: PostLink): OEmbedResult? {
        val url = "https://www.instagram.com/api/v1/oembed/?url=${encode(link.browserUrl)}"
        val response = request(url, BrowserHeaders.json()) ?: return null
        if (!response.isSuccess) return OEmbedResult(post = null, status = response.status)
        val metadata = InstagramOEmbedParser.parse(response.body)
            ?: return OEmbedResult(post = null, status = response.status)
        val media = metadata.thumbnailUrl?.let {
            PostMedia(type = PostMediaType.IMAGE, url = it)
        }
        return OEmbedResult(
            post = InstagramPost(
                author = metadata.authorName,
                handle = metadata.handle,
                caption = metadata.caption,
                media = listOfNotNull(media),
                videoUnavailable = link.kind != PostKind.POST,
            ),
            status = response.status,
        )
    }

    private suspend fun fetchComments(mediaId: String): List<PostComment> {
        val url = "https://i.instagram.com/api/v1/media/$mediaId/comments/" +
            "?can_support_threading=true&permalink_enabled=false"
        val response = request(url, APP_HEADERS)
        if (response == null || !response.isSuccess) return emptyList()
        return InstagramPostParser.parseComments(response.body)
    }

    private suspend fun request(url: String, headers: Map<String, String>): HttpResult? = try {
        fetcher.get(url, headers)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun embedUrl(link: PostLink): String {
        val pathSegment = when (link.kind) {
            PostKind.REEL -> "reel"
            PostKind.VIDEO -> "tv"
            PostKind.POST -> "p"
        }
        return "https://www.instagram.com/$pathSegment/${link.postId}/embed/captioned/"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private data class OEmbedResult(
        val post: InstagramPost?,
        val status: Int?,
    )

    private companion object {
        const val APP_USER_AGENT = "Instagram 269.0.0.18.75 Android (26/8.0.0; 480dpi; " +
            "1080x1920; OnePlus; 6T Dev; OnePlus; ONEPLUS A6013; en_US; 314665256)"
        val APP_HEADERS = mapOf(
            "User-Agent" to APP_USER_AGENT,
            "Accept" to "application/json",
            "X-IG-App-ID" to "936619743392459",
            "X-Requested-With" to "XMLHttpRequest",
        )
    }
}