package dev.cappyt.socialview.loader

import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.LinkParser
import dev.cappyt.socialview.core.ParsedLink
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.PostLink
import dev.cappyt.socialview.core.ShortLinkDocument
import dev.cappyt.socialview.net.BrowserHeaders
import dev.cappyt.socialview.net.HttpFetcher
import dev.cappyt.socialview.net.UrlConnectionFetcher
import dev.cappyt.socialview.providers.PostProvider
import dev.cappyt.socialview.providers.defaultProviders
import java.io.IOException
import kotlinx.coroutines.CancellationException

class PostLoader(
    private val fetcher: HttpFetcher = UrlConnectionFetcher(),
    providers: List<PostProvider> = defaultProviders(fetcher),
) {
    private val providersByPlatform = providers.associateBy { it.platform }

    suspend fun load(text: String): PostDetails {
        val parsed = LinkParser.parse(text)
        val link = when (parsed) {
            is ParsedLink.Post -> parsed.link
            is ParsedLink.Short -> resolveShortLink(parsed)
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

        val provider = providersByPlatform[link.platform]
            ?: throw PostException(
                kind = ErrorKind.UNEXPECTED,
                message = "No provider is available for ${link.platform.label}.",
                browserUrl = link.browserUrl,
                platform = link.platform,
            )

        return try {
            provider.load(link)
        } catch (error: PostException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            throw PostException(
                kind = ErrorKind.NETWORK,
                message = "Could not reach ${link.platform.label}. Check the network connection.",
                browserUrl = link.browserUrl,
                platform = link.platform,
                cause = error,
            )
        } catch (error: Exception) {
            throw PostException(
                kind = ErrorKind.UNEXPECTED,
                message = "Could not load this ${link.platform.label} post.",
                browserUrl = link.browserUrl,
                platform = link.platform,
                cause = error,
            )
        }
    }

    private suspend fun resolveShortLink(short: ParsedLink.Short): PostLink {
        val finalUrl = try {
            fetcher.resolve(short.url, BrowserHeaders.navigation())
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            null
        }

        val direct = finalUrl?.let { LinkParser.classify(it) as? ParsedLink.Post }
        if (direct != null) return direct.link.copy(originalUrl = short.originalUrl)

        val documentUrl = fetchShortDocument(short)
        val documentLink = documentUrl?.let { LinkParser.classify(it) as? ParsedLink.Post }
        if (documentLink != null) return documentLink.link.copy(originalUrl = short.originalUrl)

        if (finalUrl == null && documentUrl == null) {
            throw PostException(
                kind = ErrorKind.NETWORK,
                message = "Could not resolve the ${short.platform.label} short link.",
                browserUrl = short.originalUrl,
                platform = short.platform,
            )
        }

        throw PostException(
            kind = ErrorKind.UNSUPPORTED,
            message = "The short link does not point to a single supported post.",
            browserUrl = short.originalUrl,
            platform = short.platform,
        )
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