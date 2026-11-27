package dev.cappyt.socialview.ui

import java.net.URI
import java.util.Locale

internal sealed interface EmbedNavigation {
    data object Load : EmbedNavigation

    data class External(val url: String) : EmbedNavigation

    data class Unsupported(val url: String) : EmbedNavigation

    companion object {
        fun decide(
            target: String,
            embedUrl: String,
            allowedHosts: Set<String>,
        ): EmbedNavigation {
            val uri = parse(target) ?: return Unsupported(target)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") {
                return Unsupported(target)
            }

            val host = uri.host?.lowercase(Locale.ROOT)
            val hostAllowed = host != null && allowedHosts.any { it.equals(host, ignoreCase = true) }
            val embed = parse(embedUrl)
            if (scheme != "https" || !hostAllowed || embed == null) {
                return External(target)
            }

            // A data: wrapper has no path. It must not match the target root path.
            val embedPath = normalizePath(embed.path) ?: return External(target)
            return if (normalizePath(uri.path) == embedPath) {
                Load
            } else {
                External(target)
            }
        }

        private fun parse(value: String): URI? =
            runCatching { URI(value) }.getOrNull()?.takeIf { it.isAbsolute }

        private fun normalizePath(path: String?): String? =
            path?.trimEnd('/')?.ifEmpty { "/" }
    }
}