package dev.cappyt.socialview.core

data class EmbedSpec(
    val url: String,
    val allowedHosts: Set<String>,
    val heightDp: Int,
    val requiredScriptUrl: String? = null,
)

data class PostComment(
    val author: String,
    val body: String,
    val score: Int?,
    val depth: Int = 0,
    val createdAt: Long? = null,
    val stickied: Boolean = false,
)

enum class PostMediaType {
    IMAGE,
    VIDEO,
}

data class PostMedia(
    val type: PostMediaType,
    val url: String,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Double? = null,
    /** The file to save. It differs from [url] for a stream playlist, and is null without a file. */
    val downloadUrl: String? = url,
    /** Request headers that the media host needs, for the player and for a download. */
    val headers: Map<String, String> = emptyMap(),
)

data class PostDetails(
    val platform: Platform,
    val browserUrl: String,
    val author: String? = null,
    val handle: String? = null,
    val title: String? = null,
    val text: String? = null,
    val images: List<String> = emptyList(),
    val media: List<PostMedia> = emptyList(),
    val embed: EmbedSpec? = null,
    val comments: List<PostComment> = emptyList(),
    val note: String? = null,
    val initialMediaIndex: Int = 0,
    /** The platform marks the post as NSFW or sensitive. Its media stays covered until the user asks. */
    val sensitive: Boolean = false,
    /** Reddit metadata, in epoch seconds. */
    val createdAt: Long? = null,
    val score: Int? = null,
    val commentCount: Int? = null,
    val flair: String? = null,
    val linkUrl: String? = null,
)