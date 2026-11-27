package dev.cappyt.socialview.core

enum class ErrorKind(val title: String) {
    NOT_FOUND("Post not found"),
    NETWORK("Network error"),
    UNEXPECTED("Unexpected response"),
    UNSUPPORTED("Unsupported link"),
}

class PostException(
    val kind: ErrorKind,
    override val message: String,
    val browserUrl: String? = null,
    val platform: Platform? = null,
    cause: Throwable? = null,
) : Exception(message, cause)