package dev.cappyt.socialview.loader

import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.LinkParser
import dev.cappyt.socialview.core.ParsedLink
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.PostLink
import dev.cappyt.socialview.core.ProfileDetails
import dev.cappyt.socialview.core.ProfileLink
import dev.cappyt.socialview.core.ProfilePage
import dev.cappyt.socialview.core.ShortLinkDocument
import dev.cappyt.socialview.net.BrowserHeaders
import dev.cappyt.socialview.net.HttpFetcher
import dev.cappyt.socialview.net.UrlConnectionFetcher
import dev.cappyt.socialview.providers.PostProvider
import dev.cappyt.socialview.providers.ProfileProvider
import dev.cappyt.socialview.providers.RedditGraphql
import dev.cappyt.socialview.providers.defaultProfileProviders
import dev.cappyt.socialview.providers.defaultProviders
import java.io.IOException
import kotlinx.coroutines.CancellationException

sealed interface Loaded {
    data class Post(val post: PostDetails) : Loaded

    data class Profile(val profile: ProfileDetails) : Loaded
}

class PostLoader(
    private val fetcher: HttpFetcher = UrlConnectionFetcher(),
    // Reddit posts and profiles share one anonymous session.
    reddit: RedditGraphql = RedditGraphql(fetcher),
    providers: List<PostProvider> = defaultProviders(fetcher, reddit),
    profileProviders: List<ProfileProvider> = defaultProfileProviders(fetcher, reddit),
) {
    private val providersByPlatform = providers.associateBy { it.platform }
    private val profilesByPlatform = profileProviders.associateBy { it.platform }

    suspend fun load(text: String): Loaded = when (val parsed = LinkParser.parse(text)) {
        is ParsedLink.Post -> loadPost(parsed.link)
        is ParsedLink.Profile -> loadProfile(parsed.link)
        is ParsedLink.Short -> when (val target = resolveShortLink(parsed)) {
            is ParsedLink.Profile -> loadProfile(target.link.copy(originalUrl = parsed.originalUrl))
            is ParsedLink.Post -> loadPost(target.link.copy(originalUrl = parsed.originalUrl))
            else -> throw PostException(
                kind = ErrorKind.UNSUPPORTED,
                message = "The short link does not point to a supported post or profile.",
                browserUrl = parsed.originalUrl,
                platform = parsed.platform,
            )
        }

        is ParsedLink.Unsupported -> throw PostException(
            kind = ErrorKind.UNSUPPORTED,
            message = parsed.reason,
            browserUrl = parsed.url,
            platform = parsed.platform,
        )

        is ParsedLink.Invalid -> throw PostException(
            kind = ErrorKind.UNSUPPORTED,
            message = parsed.reason,
        )
    }

    /** The page of posts after the ones [profile] has. The profile must have a next page. */
    suspend fun loadMorePosts(profile: ProfileDetails): ProfilePage {
        val request = checkNotNull(profile.nextPage) { "The profile has no next page." }
        val provider = profileProvider(profile.platform, profile.browserUrl)
        return guarded(profile.platform, profile.browserUrl, "profile") { provider.loadMore(request) }
    }

    private fun profileProvider(platform: Platform, browserUrl: String): ProfileProvider =
        profilesByPlatform[platform] ?: throw noProvider(platform, browserUrl)

    private fun noProvider(platform: Platform, browserUrl: String) = PostException(
        kind = ErrorKind.UNEXPECTED,
        message = "No provider is available for ${platform.label}.",
        browserUrl = browserUrl,
        platform = platform,
    )

    private suspend fun loadPost(link: PostLink): Loaded {
        val provider = providersByPlatform[link.platform]
            ?: throw noProvider(link.platform, link.browserUrl)
        return guarded(link.platform, link.browserUrl, "post") { Loaded.Post(provider.load(link)) }
    }

    private suspend fun loadProfile(link: ProfileLink): Loaded {
        val provider = profileProvider(link.platform, link.browserUrl)
        return guarded(link.platform, link.browserUrl, "profile") { Loaded.Profile(provider.load(link)) }
    }

    private suspend fun <T> guarded(
        platform: Platform,
        browserUrl: String,
        subject: String,
        block: suspend () -> T,
    ): T = try {
        block()
    } catch (error: PostException) {
        // An error without a link would leave the user without the browser fallback.
        if (error.browserUrl != null) throw error
        throw PostException(error.kind, error.message, browserUrl, error.platform ?: platform, error)
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        throw PostException(
            kind = ErrorKind.NETWORK,
            message = "Could not reach ${platform.label}. Check the network connection.",
            browserUrl = browserUrl,
            platform = platform,
            cause = error,
        )
    } catch (error: Exception) {
        throw PostException(
            kind = ErrorKind.UNEXPECTED,
            message = "Could not load this ${platform.label} $subject.",
            browserUrl = browserUrl,
            platform = platform,
            cause = error,
        )
    }

    /** The post or profile behind a short link, or null when it points to neither. */
    private suspend fun resolveShortLink(short: ParsedLink.Short): ParsedLink? {
        val finalUrl = try {
            fetcher.resolve(short.url, BrowserHeaders.navigation())
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            null
        }

        val direct = finalUrl?.let(LinkParser::classify)
        if (direct is ParsedLink.Post || direct is ParsedLink.Profile) return direct

        // The document lookup finds post links only.
        val documentUrl = fetchShortDocument(short)
        val document = documentUrl?.let(LinkParser::classify)
        if (document is ParsedLink.Post) return document

        if (finalUrl == null && documentUrl == null) {
            throw PostException(
                kind = ErrorKind.NETWORK,
                message = "Could not resolve the ${short.platform.label} short link.",
                browserUrl = short.originalUrl,
                platform = short.platform,
            )
        }
        return null
    }

    private suspend fun fetchShortDocument(short: ParsedLink.Short): String? {
        val response = try {
            fetcher.get(short.url, BrowserHeaders.navigation())
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return null
        }
        if (!response.isSuccess) return null
        return ShortLinkDocument.findPostUrl(response.body)
    }
}
