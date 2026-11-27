package dev.cappyt.socialview.ui

import java.net.URI
import java.util.Locale

internal object EmbedSubresource {
    fun matches(requestUrl: String, requiredUrl: String): Boolean {
        val request = parse(requestUrl) ?: return false
        val required = parse(requiredUrl) ?: return false
        return request.scheme.equals(required.scheme, ignoreCase = true) &&
            request.host.equals(required.host, ignoreCase = true) &&
            normalizePath(request.path) == normalizePath(required.path)
    }

    fun failureMessage(httpStatus: Int? = null): String = if (httpStatus != null) {
        "The required embed script returned HTTP $httpStatus."
    } else {
        "The required embed script did not load."
    }

    private fun parse(value: String): URI? =
        runCatching { URI(value) }.getOrNull()?.takeIf { it.isAbsolute }

    private fun normalizePath(path: String?): String =
        path?.trimEnd('/')?.ifEmpty { "/" }.orEmpty().lowercase(Locale.ROOT)
}