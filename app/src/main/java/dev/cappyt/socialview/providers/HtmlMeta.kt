package dev.cappyt.socialview.providers

import java.util.Locale

internal object HtmlMeta {
    private val META_TAG = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val LINK_TAG = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTRIBUTE = Regex(
        """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)')""",
    )

    fun meta(html: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (tag in META_TAG.findAll(html)) {
            val attributes = attributes(tag.value)
            val key = attributes["property"] ?: attributes["name"] ?: continue
            val content = attributes["content"] ?: continue
            result.putIfAbsent(key.lowercase(Locale.ROOT), HtmlText.decode(content))
        }
        return result
    }

    fun canonical(html: String): String? = link(html, "canonical")

    fun link(html: String, rel: String): String? {
        for (tag in LINK_TAG.findAll(html)) {
            val attributes = attributes(tag.value)
            if (!attributes["rel"].equals(rel, ignoreCase = true)) continue
            attributes["href"]?.let { return HtmlText.decode(it) }
        }
        return null
    }

    private fun attributes(tag: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (match in ATTRIBUTE.findAll(tag)) {
            val name = match.groupValues[1].lowercase(Locale.ROOT)
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            result.putIfAbsent(name, value)
        }
        return result
    }
}