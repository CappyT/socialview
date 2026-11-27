package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.net.HttpFetcher
import dev.cappyt.socialview.net.HttpResult
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Talks to Reddit the way its Android app does when nobody is logged in: an anonymous token, then
 * persisted GraphQL queries. Reddit blocks its public JSON and RSS for logged-out visitors and
 * puts its pages behind a script challenge.
 */
class RedditGraphql(
    private val fetcher: HttpFetcher,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) {
    private val tokenLock = Mutex()
    private var token: String? = null
    private var tokenExpiry = 0L
    private val vendorId = UUID.randomUUID().toString()

    /** Runs a persisted query. The answer is HTTP 200 also for an error: read its `errors`. */
    suspend fun query(operation: String, queryId: String, variables: JSONObject): HttpResult {
        val body = JSONObject()
            .put("operationName", operation)
            .put("variables", variables)
            .put(
                "extensions",
                JSONObject().put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", queryId)),
            )
            .toString()
        val used = anonymousToken()
        val first = fetcher.post(GRAPHQL_URL, graphqlHeaders(used), body)
        if (first.status != 401) return first
        // The token ended before its stated lifetime. One new token is enough, and another
        // request can have replaced it already.
        tokenLock.withLock { if (token == used) token = null }
        return fetcher.post(GRAPHQL_URL, graphqlHeaders(anonymousToken()), body)
    }

    private suspend fun anonymousToken(): String = tokenLock.withLock {
        token?.takeIf { nowSeconds() < tokenExpiry }?.let { return@withLock it }
        val response = fetcher.post(
            TOKEN_URL,
            mapOf(
                "User-Agent" to APP_USER_AGENT,
                "Authorization" to "Basic $APP_CLIENT",
                "Content-Type" to "application/json",
                "Client-Vendor-ID" to vendorId,
            ),
            TOKEN_SCOPES,
        )
        val json = if (response.isSuccess) runCatching { JSONObject(response.body) }.getOrNull() else null
        val value = json?.stringOrNull("access_token") ?: throw PostException(
            kind = ErrorKind.UNEXPECTED,
            message = "Reddit did not give an anonymous session (HTTP ${response.status}).",
            platform = Platform.REDDIT,
        )
        val lifetime = json.optLong("expires_in").takeIf { it > 0 }?.coerceAtMost(TOKEN_MAX_SECONDS)
            ?: TOKEN_DEFAULT_SECONDS
        token = value
        tokenExpiry = nowSeconds() + lifetime - minOf(TOKEN_MARGIN_SECONDS, lifetime / 2)
        value
    }

    private fun graphqlHeaders(token: String) = mapOf(
        "User-Agent" to APP_USER_AGENT,
        "Authorization" to "Bearer $token",
        "Content-Type" to "application/json; charset=utf-8",
    )

    private companion object {
        const val TOKEN_URL = "https://www.reddit.com/auth/v2/oauth/access-token/loid"
        const val GRAPHQL_URL = "https://gql-fed.reddit.com/"
        const val TOKEN_SCOPES = """{"scopes":["*","email","pii","adsread","adsedit"]}"""
        const val TOKEN_MARGIN_SECONDS = 300L
        const val TOKEN_DEFAULT_SECONDS = 3_600L
        const val TOKEN_MAX_SECONDS = 86_400L

        // The edge answers 403 to any other User-Agent. The client id is the one of the app, with
        // an empty secret: base64 of `ohXpoqrZYub1kg:`.
        const val APP_USER_AGENT = "Reddit/Version 2026.35.0/Build 2635091/Android 14"
        const val APP_CLIENT = "b2hYcG9xclpZdWIxa2c6"
    }
}

/** Reddit writes `2014-10-07T09:38:40.859000+0000`: six fraction digits, no colon in the offset. */
internal fun redditEpochSeconds(time: String): Long? = runCatching {
    if (time.length < 24) return@runCatching null
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        .parse(time.substring(0, 19) + time.takeLast(5))?.time?.div(1_000)
}.getOrNull()
