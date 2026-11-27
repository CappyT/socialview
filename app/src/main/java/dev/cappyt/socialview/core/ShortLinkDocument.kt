package dev.cappyt.socialview.core

import java.net.URI

object ShortLinkDocument {
    private val META_PROPERTY_FIRST = Regex(
        """<meta[^>]+(?:property|name)=["']og:url["'][^>]+content=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val META_CONTENT_FIRST = Regex(
        """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']og:url["']""",
        RegexOption.IGNORE_CASE,
    )
    private val CANONICAL_LINK = Regex(
        """<link[^>]+rel=["']canonical["'][^>]+href=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val CANONICAL_JSON = Regex(
        """"canonical_url"\s*:\s*"([^"]+)"""",
        RegexOption.IGNORE_CASE,
    )
    private val FACEBOOK_REEL = Regex(
        """https:(?:\\?/){2}www\.facebook\.com(?:\\?/)+reel(?:\\?/)+(\d+)""",
    )
    private val INSTAGRAM_POST = Regex(
        """https:(?:\\?/){2}www\.instagram\.com(?:\\?/)+(p|reel|tv)(?:\\?/)+([A-Za-z0-9_-]+)""",
    )

    fun findPostUrl(html: String): String? {
        val candidates = buildList {
            META_PROPERTY_FIRST.find(html)?.let { add(it.groupValues[1]) }
            META_CONTENT_FIRST.find(html)?.let { add(it.groupValues[1]) }
            CANONICAL_LINK.find(html)?.let { add(it.groupValues[1]) }
            CANONICAL_JSON.find(html)?.let { add(it.groupValues[1]) }
        }
        candidates.forEach { candidate ->
            normalize(candidate)?.let { url ->
                if (LinkParser.classify(url) is ParsedLink.Post) return url
            }
        }

        FACEBOOK_REEL.find(html)?.let { match ->
            return "https://www.facebook.com/reel/${match.groupValues[1]}/"
        }
        INSTAGRAM_POST.find(html)?.let { match ->
            return "https://www.instagram.com/${match.groupValues[1]}/${match.groupValues[2]}/"
        }
        return null
    }

    private fun normalize(raw: String): String? {
        val decoded = HtmlEscape.unescape(raw).replace("\\/", "/").replace("http://", "https://")
        val uri = runCatching { URI(decoded) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in SUPPORTED_HOSTS) return null
        return decoded
    }

    private val SUPPORTED_HOSTS = setOf(
        "www.facebook.com",
        "facebook.com",
        "m.facebook.com",
        "www.instagram.com",
        "instagram.com",
    )
}

internal object HtmlEscape {
    private val UNICODE = Regex("""\\u([0-9A-Fa-f]{4})""")

    fun unescape(value: String): String {
        val unicode = UNICODE.replace(value) { match ->
            val code = match.groupValues[1].toInt(16)
            String(Character.toChars(code))
        }
        return unicode
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#x2F;", "/")
            .replace("&#47;", "/")
    }
}