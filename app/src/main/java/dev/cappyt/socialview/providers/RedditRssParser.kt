package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostComment
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostFormat
import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

object RedditRssParser {
    private const val MAX_COMMENTS = 5
    private const val MAX_IMAGES = 10

    private val MD_BLOCK = Regex(
        """<div class="md">(.*?)</div>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val IMG_TAG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val SRC_ATTRIBUTE = Regex(
        """\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)')""",
        RegexOption.IGNORE_CASE,
    )
    private val FULL_IMAGE_LINK = Regex(
        """<a\b[^>]*\bhref\s*=\s*["'](https://i\.redd\.it/[^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val SUBREDDIT_IN_URL = Regex("""/r/([A-Za-z0-9_]+)/comments/""")
    private val ANCHOR_HREF = Regex(
        """<a\b[^>]*\bhref\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(body: String, browserUrl: String, postId: String): PostDetails? {
        val feed = document(body)?.documentElement ?: return null
        if (nameOf(feed) != "feed") return null
        val entries = children(feed, "entry")
        if (entries.isEmpty()) return null

        val post = entries.firstOrNull { textOf(it, "id")?.startsWith("t3_") == true }
            ?: entries.firstOrNull { linkOf(it)?.contains("/comments/$postId/") == true }
            ?: entries.first()
        val title = textOf(post, "title")?.takeIf { it.isNotBlank() } ?: return null
        val content = textOf(post, "content").orEmpty()
        val text = mdHtml(content)
            ?.let(HtmlText::toPlainText)
            ?.takeIf { it.isNotBlank() }
        val images = images(content)

        return PostDetails(
            platform = Platform.REDDIT,
            browserUrl = browserUrl,
            author = authorOf(post),
            handle = subredditOf(browserUrl, feed),
            title = title,
            text = text,
            images = images,
            comments = comments(entries, post),
            createdAt = timestampOf(post),
            linkUrl = if (images.isEmpty() && text == null) externalLink(content) else null,
        )
    }

    private fun timestampOf(entry: Element): Long? {
        val value = textOf(entry, "published") ?: textOf(entry, "updated") ?: return null
        return runCatching { OffsetDateTime.parse(value).toEpochSecond() }.getOrNull()
    }

    private fun externalLink(content: String): String? =
        ANCHOR_HREF.findAll(content)
            .map { HtmlText.decode(it.groupValues[1]) }
            .firstOrNull { url ->
                if (!url.startsWith("https://") && !url.startsWith("http://")) return@firstOrNull false
                val host = PostFormat.hostOf(url) ?: return@firstOrNull false
                host != "reddit.com" && !host.endsWith(".reddit.com") &&
                    host != "redd.it" && !host.endsWith(".redd.it")
            }

    private fun document(body: String): Document? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
        }
        factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)))
    }.getOrNull()

    private fun children(parent: Element, name: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = parent.childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node is Element && nameOf(node) == name) result += node
        }
        return result
    }

    private fun child(parent: Element, name: String): Element? =
        children(parent, name).firstOrNull()

    private fun textOf(parent: Element, name: String): String? =
        child(parent, name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun nameOf(element: Element): String = element.localName ?: element.nodeName

    private fun linkOf(entry: Element): String? =
        child(entry, "link")?.getAttribute("href")?.takeIf { it.isNotBlank() }

    private fun subredditOf(browserUrl: String, feed: Element): String? {
        SUBREDDIT_IN_URL.find(browserUrl)?.groupValues?.get(1)?.let { return "r/$it" }
        val category = child(feed, "category") ?: return null
        val label = category.getAttribute("label").trim()
        if (label.startsWith("r/")) return label
        val term = category.getAttribute("term").trim()
        return term.takeIf { it.isNotEmpty() }?.let { "r/$it" }
    }

    private fun authorOf(entry: Element): String? {
        val author = child(entry, "author") ?: return null
        val name = child(author, "name")?.textContent?.trim().orEmpty()
        val handle = name
            .removePrefix("/u/")
            .removePrefix("u/")
            .trim()
        if (handle.isEmpty() || handle.equals("[deleted]", ignoreCase = true)) return null
        return "u/$handle"
    }

    private fun mdHtml(content: String): String? =
        MD_BLOCK.find(content)?.groupValues?.get(1)

    private fun images(content: String): List<String> {
        val urls = linkedSetOf<String>()
        FULL_IMAGE_LINK.findAll(content).forEach { addImage(urls, it.groupValues[1]) }
        if (urls.isEmpty()) {
            IMG_TAG.findAll(content).forEach { tag ->
                val src = srcOf(tag.value) ?: return@forEach
                addImage(urls, src)
            }
        }
        return urls.take(MAX_IMAGES)
    }

    private fun srcOf(tag: String): String? {
        val match = SRC_ATTRIBUTE.find(tag) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
    }

    private fun addImage(urls: MutableSet<String>, raw: String) {
        if (raw.isBlank()) return
        val decoded = HtmlText.decode(raw)
        val https = when {
            decoded.startsWith("https://") -> decoded
            decoded.startsWith("http://") -> decoded.replaceFirst("http://", "https://")
            else -> return
        }
        urls.add(https)
    }

    private fun comments(entries: List<Element>, post: Element): List<PostComment> {
        val comments = mutableListOf<PostComment>()
        for (entry in entries) {
            if (entry === post) continue
            if (comments.size >= MAX_COMMENTS) break
            val content = textOf(entry, "content").orEmpty()
            val body = mdHtml(content)?.let(HtmlText::toPlainText)
                ?: HtmlText.toPlainText(content)
            if (body.isBlank() || body == "[deleted]" || body == "[removed]") continue
            comments += PostComment(
                author = authorOf(entry) ?: "u/[deleted]",
                body = body,
                score = null,
                createdAt = timestampOf(entry),
            )
        }
        return comments
    }
}