package dev.cappyt.socialview.providers

private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
    "mdash" to "\u2014",
    "ndash" to "\u2013",
    "hellip" to "\u2026",
    "lsquo" to "\u2018",
    "rsquo" to "\u2019",
    "ldquo" to "\u201C",
    "rdquo" to "\u201D",
    "bull" to "\u2022",
    "middot" to "\u00B7",
    "copy" to "\u00A9",
    "reg" to "\u00AE",
    "trade" to "\u2122",
    "deg" to "\u00B0",
)

private val ENTITY_PATTERN = Regex("""&(#x?[0-9A-Fa-f]+|[A-Za-z]+);""")
private val BREAK_PATTERN = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val BLOCK_END_PATTERN = Regex("""</(p|div|blockquote)>""", RegexOption.IGNORE_CASE)
private val TAG_PATTERN = Regex("""<[^>]*>""")
private val PARAGRAPH_PATTERN = Regex("""<p\b[^>]*>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

object HtmlText {
    fun decode(input: String): String = ENTITY_PATTERN.replace(input) { match ->
        val body = match.groupValues[1]
        val entity = when {
            body.startsWith("#x", ignoreCase = true) -> codePoint(body.substring(2), 16)
            body.startsWith("#") -> codePoint(body.substring(1), 10)
            else -> NAMED_ENTITIES[body.lowercase()]
        }
        entity ?: match.value
    }

    fun toPlainText(html: String): String {
        val stripped = html
            .replace(BREAK_PATTERN, "\n")
            .replace(BLOCK_END_PATTERN, "\n\n")
            .replace(TAG_PATTERN, "")
        return decode(stripped)
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    fun firstParagraph(html: String): String? = PARAGRAPH_PATTERN.find(html)
        ?.groupValues
        ?.get(1)
        ?.let(::toPlainText)
        ?.takeIf { it.isNotBlank() }

    private fun codePoint(raw: String, radix: Int): String? {
        val value = raw.toIntOrNull(radix) ?: return null
        if (value < 0 || value > Character.MAX_CODE_POINT) return null
        return runCatching { String(Character.toChars(value)) }.getOrNull()
    }
}