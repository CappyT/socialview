package dev.cappyt.socialview.core

data class PostLink(
    val platform: Platform,
    val postId: String,
    val browserUrl: String,
    val originalUrl: String,
    val kind: PostKind = PostKind.POST,
    val initialMediaIndex: Int = 0,
)

enum class PostKind {
    POST,
    REEL,
    VIDEO,
}

data class ProfileLink(
    val platform: Platform,
    val username: String,
    val browserUrl: String,
    val originalUrl: String,
)

sealed interface ParsedLink {
    data class Post(val link: PostLink) : ParsedLink

    data class Profile(val link: ProfileLink) : ParsedLink

    data class Short(
        val platform: Platform,
        val url: String,
        val originalUrl: String,
    ) : ParsedLink

    data class Unsupported(
        val platform: Platform?,
        val url: String,
        val reason: String,
    ) : ParsedLink

    data class Invalid(val reason: String) : ParsedLink
}