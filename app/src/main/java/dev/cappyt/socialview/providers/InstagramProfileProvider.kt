package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.ProfileDetails
import dev.cappyt.socialview.core.ProfileLink
import dev.cappyt.socialview.core.ProfilePage
import dev.cappyt.socialview.core.ProfilePageRequest
import dev.cappyt.socialview.net.BrowserHeaders
import dev.cappyt.socialview.net.HttpFetcher
import java.net.URLEncoder
import org.json.JSONObject

class InstagramProfileProvider(
    private val fetcher: HttpFetcher,
) : ProfileProvider {
    override val platform = Platform.INSTAGRAM

    // The profile object is only in the page that Instagram serves to a desktop browser.
    override suspend fun load(link: ProfileLink): ProfileDetails {
        val response = fetcher.get(link.browserUrl, BrowserHeaders.navigation())
        val profile = if (response.isSuccess) {
            InstagramProfileParser.parse(response.body, link.username)
        } else {
            null
        }
        if (profile != null) return profile

        // A missing account and a login wall both answer HTTP 200 without the profile object.
        if (response.isSuccess || response.status == 404 || response.status == 410) {
            throw profileNotFound(
                link,
                "Instagram did not return a public profile for @${link.username}. The account can " +
                    "be missing, or Instagram can ask for a login on this network.",
            )
        }
        throw profileHttpError(link, response.status)
    }

    override suspend fun loadMore(request: ProfilePageRequest): ProfilePage {
        val variables = JSONObject()
            .put("after", request.cursor)
            .put("first", PAGE_SIZE)
            .put("id", request.userId)
            .put("__relay_internal__pv__PolarisSEOIsCrawlerrelayprovider", false)
        val body = "doc_id=$TIMELINE_DOC_ID&variables=" +
            URLEncoder.encode(variables.toString(), Charsets.UTF_8.name())
        val response = fetcher.post(GRAPHQL_URL, BrowserHeaders.graphql(), body)
        val page = if (response.isSuccess) {
            InstagramProfileParser.parsePage(response.body, request.userId)
        } else {
            null
        }
        return page ?: throw PostException(
            kind = ErrorKind.UNEXPECTED,
            message = "Instagram did not return more posts.",
            platform = Platform.INSTAGRAM,
        )
    }

    private companion object {
        const val GRAPHQL_URL = "https://www.instagram.com/api/graphql/"
        const val PAGE_SIZE = 12

        // Persisted query PolarisLoggedOutDesktopWWWProfilePostsTabContentQuery_connection. The id
        // is in a script bundle, not in the profile page. When Instagram changes the query, the
        // endpoint answers without `data.node` and the profile keeps the posts it has.
        const val TIMELINE_DOC_ID = "28904005159232689"
    }
}
