package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.EmbedSpec
import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.PostLink
import dev.cappyt.socialview.net.HttpFetcher
import dev.cappyt.socialview.net.HttpResult
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException

class RedditProvider(
    private val fetcher: HttpFetcher,
) : PostProvider {
    override val platform = Platform.REDDIT

    override suspend fun load(link: PostLink): PostDetails {
        val path = pathOf(link.browserUrl)?.takeIf { SUBREDDIT_PATH.matches(it) }

        // The RSS feed is the cheaper native source and the last one Reddit blocks.
        // Fetch it before the JSON request so a shared rate-limit window is not spent on JSON.
        val rss = requestRss(path, link.postId)
        val rssPost = rss?.takeIf { it.isSuccess }
            ?.let { RedditRssParser.parse(it.body, link.browserUrl, link.postId) }

        val json = requestJson(link)
        if (json.isNotFound() && rssPost == null) throw notFound(link)

        var reason: String? = null
        if (json != null && json.isSuccess && json.body.trimStart().startsWith("[")) {
            try {
                return RedditParser.parse(json.body, link.browserUrl)
            } catch (error: PostException) {
                if (error.kind == ErrorKind.NOT_FOUND && rssPost == null) throw error
                reason = "Reddit returned unexpected JSON."
            }
        } else {
            reason = when {
                json == null -> "Reddit's public JSON could not be reached."
                json.status == 401 || json.status == 403 ->
                    "Reddit blocked its public JSON endpoint (HTTP ${json.status})."
                json.status == 429 -> "Reddit is rate limiting public JSON (HTTP 429)."
                json.isSuccess -> "Reddit returned unexpected JSON."
                else -> "Reddit returned HTTP ${json.status}."
            }
        }

        if (rssPost != null) {
            return rssPost.copy(note = "$reason The post is shown from Reddit's public RSS feed.")
        }
        if (rss.isNotFound()) throw notFound(link)

        return fallback(
            link,
            path ?: canonicalPath(link),
            reason ?: "Reddit did not return public data.",
            rss?.status,
        )
    }

    private fun HttpResult?.isNotFound(): Boolean = this != null && (status == 404 || status == 410)

    private suspend fun requestJson(link: PostLink): HttpResult? = try {
        fetcher.get(
            "https://www.reddit.com/comments/${link.postId}.json?raw_json=1&limit=25&sort=top",
            mapOf(
                "Accept" to "application/json",
                "User-Agent" to USER_AGENT,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun requestRss(path: String?, postId: String): HttpResult? = try {
        fetcher.get(
            rssUrl(path, postId),
            mapOf(
                "Accept" to "application/atom+xml, application/xml;q=0.9, text/xml;q=0.8",
                "User-Agent" to USER_AGENT,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun rssUrl(path: String?, postId: String): String {
        val subreddit = path?.let { SUBREDDIT_IN_PATH.find(it)?.groupValues?.get(1) }
        return if (subreddit != null) {
            "https://www.reddit.com/r/$subreddit/comments/$postId/.rss"
        } else {
            "https://www.reddit.com/comments/$postId/.rss"
        }
    }

    private suspend fun canonicalPath(link: PostLink): String? {
        val direct = pathOf(link.browserUrl)
        if (direct != null && SUBREDDIT_PATH.matches(direct)) return direct
        return try {
            pathOf(fetcher.resolve("https://www.reddit.com/comments/${link.postId}"))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun fallback(
        link: PostLink,
        path: String?,
        reason: String,
        rssStatus: Int?,
    ): PostDetails {
        val target = embedTarget(link.postId, path)
        val note = buildString {
            append(reason)
            when {
                rssStatus == null -> append(" Reddit's public RSS feed could not be reached.")
                rssStatus !in 200..299 ->
                    append(" Reddit's public RSS feed returned HTTP $rssStatus.")
            }
            if (target.commentOnly) {
                append(" The subreddit was not resolved, so the official comment embed is shown. ")
                append("If its script fails, use the browser for the full post.")
            } else {
                append(" The official embed is shown.")
            }
        }
        return PostDetails(
            platform = platform,
            browserUrl = link.browserUrl,
            embed = EmbedSpec(
                url = target.url,
                allowedHosts = setOf(target.host),
                heightDp = 420,
                requiredScriptUrl = target.scriptUrl,
            ),
            note = note,
        )
    }

    private fun embedTarget(postId: String, path: String?): EmbedTarget {
        if (path != null && SUBREDDIT_PATH.matches(path)) {
            return EmbedTarget(
                url = "https://embed.reddit.com${path.withTrailingSlash()}?depth=1&showmore=false",
                host = "embed.reddit.com",
                commentOnly = false,
                scriptUrl = null,
            )
        }

        return EmbedTarget(
            url = commentEmbedPage(postId),
            host = "www.reddit.com",
            commentOnly = true,
            scriptUrl = commentScriptUrl(postId),
        )
    }

    private fun commentEmbedPage(postId: String): String {
        // The .embed endpoint returns JavaScript with a nosniff content type, so a top-level
        // navigation shows its source. A wrapper document runs it as a script instead.
        val script = "<script src=\"${commentScriptUrl(postId)}\"></script>"
        return "data:text/html;charset=utf-8," +
            URLEncoder.encode(script, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun commentScriptUrl(postId: String): String =
        "https://www.reddit.com/comments/$postId/.embed?depth=1&showmore=false"

    private fun pathOf(url: String): String? =
        runCatching { URI(url).path }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun String.withTrailingSlash(): String =
        if (startsWith("/") && endsWith("/")) this
        else "/${trimStart('/')}/"

    private fun notFound(link: PostLink) = PostException(
        kind = ErrorKind.NOT_FOUND,
        message = "The Reddit post was not found or was removed.",
        browserUrl = link.browserUrl,
        platform = platform,
    )

    private data class EmbedTarget(
        val url: String,
        val host: String,
        val commentOnly: Boolean,
        val scriptUrl: String?,
    )

    private companion object {
        const val USER_AGENT = "android:dev.cappyt.socialview:v0.1 (by /u/socialview)"
        val SUBREDDIT_PATH = Regex("""^/r/[A-Za-z0-9_]+/comments/[a-z0-9]+(?:/.*)?$""")
        val SUBREDDIT_IN_PATH = Regex("""^/r/([A-Za-z0-9_]+)/comments/""")
    }
}