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
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads a post and its comments from the API of the Reddit Android app. When that API does not
 * answer, the official embed is shown.
 */
class RedditProvider(
    private val fetcher: HttpFetcher,
    private val reddit: RedditGraphql = RedditGraphql(fetcher),
) : PostProvider {
    override val platform = Platform.REDDIT

    override suspend fun load(link: PostLink): PostDetails {
        val response = query(POST_OPERATION, POST_QUERY_ID, JSONObject().put("ids", JSONArray().put("t3_${link.postId}")))
        val result = response?.takeIf { it.isSuccess }?.let { RedditPostParser.parsePost(it.body, link.postId) }
        val post = when (result) {
            is RedditPostResult.Post -> result.post
            RedditPostResult.Missing -> throw notFound(link)
            null -> return fallback(
                link = link,
                path = canonicalPath(link),
                reason = when {
                    response == null -> "Reddit's app API could not be reached."
                    response.isSuccess -> "Reddit's app API returned an unexpected answer."
                    else -> "Reddit's app API returned HTTP ${response.status}."
                },
            )
        }

        val variables = JSONObject()
            .put("id", "t3_${link.postId}")
            // The server answers HTTP 400 without these two flags.
            .put("includePremiumAvatarTreatment", false)
            .put("includeConsistencyToken", false)
            .put("includeAwards", false)
            .put("sortType", "CONFIDENCE")
            // Depth N gives comments at depths 0 to N-1.
            .put("maxDepth", 4)
            .put("count", 30)
        val comments = query(COMMENTS_OPERATION, COMMENTS_QUERY_ID, variables)
            ?.takeIf { it.isSuccess }
            ?.let { RedditPostParser.parseComments(it.body) }
        if (comments != null) return post.copy(comments = comments)
        return post.copy(
            note = listOfNotNull(post.note, "Reddit did not return the comments of this post.").joinToString(" "),
        )
    }

    /** The answer of a query, or null when the request or the anonymous session fails. */
    private suspend fun query(operation: String, queryId: String, variables: JSONObject): HttpResult? = try {
        reddit.query(operation, queryId, variables)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
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

    private fun fallback(link: PostLink, path: String?, reason: String): PostDetails {
        val target = embedTarget(link.postId, path)
        val note = buildString {
            append(reason)
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
        val SUBREDDIT_PATH = Regex("""^/r/[A-Za-z0-9_]+/comments/[a-z0-9]+(?:/.*)?$""")

        // Persisted query ids of app build 2026.35.0. A new app version can retire them.
        const val POST_OPERATION = "PostsByIds"
        const val POST_QUERY_ID = "f6b6253d68ecb48132f3b3fff0492a005e3a263c425021c135b21dc4448ea976"
        const val COMMENTS_OPERATION = "PostComments"
        const val COMMENTS_QUERY_ID = "8a25ae56c1d4813102f4eded0c427b99f46f6a63f77630e78767f27ef5186f1f"
    }
}