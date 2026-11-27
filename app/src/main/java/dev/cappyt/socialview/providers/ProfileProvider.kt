package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.ProfileDetails
import dev.cappyt.socialview.core.ProfileLink
import dev.cappyt.socialview.core.ProfilePage
import dev.cappyt.socialview.core.ProfilePageRequest

interface ProfileProvider {
    val platform: Platform

    suspend fun load(link: ProfileLink): ProfileDetails

    /** The page of posts after [request]. Only a profile with a next page asks for it. */
    suspend fun loadMore(request: ProfilePageRequest): ProfilePage = throw PostException(
        kind = ErrorKind.UNEXPECTED,
        message = "${platform.label} did not return more posts.",
        platform = platform,
    )
}

/** The error of a profile that the platform does not publish to logged-out visitors. */
internal fun profileNotFound(link: ProfileLink, message: String) = PostException(
    kind = ErrorKind.NOT_FOUND,
    message = message,
    browserUrl = link.browserUrl,
    platform = link.platform,
)

internal fun profileHttpError(link: ProfileLink, status: Int) = PostException(
    kind = ErrorKind.UNEXPECTED,
    message = "${link.platform.label} returned HTTP $status for this profile.",
    browserUrl = link.browserUrl,
    platform = link.platform,
)

/** The content of the first `<script id="[id]" ...>` element, or null. */
internal fun scriptContent(html: String, id: String): String? {
    val marker = html.indexOf("id=\"$id\"")
    if (marker < 0) return null
    val start = html.indexOf('>', marker)
    val end = html.indexOf("</script>", start)
    if (start < 0 || end < 0) return null
    return html.substring(start + 1, end)
}
