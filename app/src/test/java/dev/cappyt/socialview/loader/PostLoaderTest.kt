package dev.cappyt.socialview.loader

import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.PostLink
import dev.cappyt.socialview.net.HttpFetcher
import dev.cappyt.socialview.net.HttpResult
import dev.cappyt.socialview.providers.PostProvider
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PostLoaderTest {
    private class FakeFetcher(
        private val onGet: (String) -> HttpResult = { throw IOException("Unexpected GET $it") },
        private val onResolve: (String) -> String = { throw IOException("Unexpected resolve $it") },
    ) : HttpFetcher {
        val gets = mutableListOf<String>()
        val resolves = mutableListOf<String>()

        override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
            gets += url
            return onGet(url)
        }

        override suspend fun resolve(url: String, headers: Map<String, String>): String {
            resolves += url
            return onResolve(url)
        }
    }

    private class RecordingProvider(
        override val platform: Platform,
        private val onLoad: suspend (PostLink) -> PostDetails,
    ) : PostProvider {
        val links = mutableListOf<PostLink>()

        override suspend fun load(link: PostLink): PostDetails {
            links += link
            return onLoad(link)
        }
    }

    private fun provider(platform: Platform) = RecordingProvider(platform) { link ->
        PostDetails(platform = link.platform, browserUrl = link.browserUrl)
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (error: Throwable) {
            return error
        }
        throw AssertionError("Expected the call to fail.")
    }

    @Test
    fun `the provider for the link platform is selected`() = runBlocking {
        val instagram = provider(Platform.INSTAGRAM)
        val reddit = provider(Platform.REDDIT)
        val loader = PostLoader(
            fetcher = FakeFetcher(),
            providers = listOf(instagram, reddit),
        )

        val details = loader.load("https://www.reddit.com/r/Android/comments/abc123/")

        assertEquals(Platform.REDDIT, details.platform)
        assertEquals(1, reddit.links.size)
        assertEquals("abc123", reddit.links.single().postId)
        assertTrue(instagram.links.isEmpty())
    }

    @Test
    fun `an unsupported link keeps the original url for the browser fallback`() = runBlocking {
        val loader = PostLoader(fetcher = FakeFetcher(), providers = listOf(provider(Platform.INSTAGRAM)))
        val url = "https://www.instagram.com/nasa/"

        val error = failureOf { loader.load(url) }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.UNSUPPORTED, (error as PostException).kind)
        assertEquals(Platform.INSTAGRAM, error.platform)
        assertEquals(url, error.browserUrl)
    }

    @Test
    fun `text without a link is unsupported and has no browser url`() = runBlocking {
        val loader = PostLoader(fetcher = FakeFetcher(), providers = emptyList())

        val error = failureOf { loader.load("hello") }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.UNSUPPORTED, (error as PostException).kind)
        assertNull(error.browserUrl)
    }

    @Test
    fun `a short link is resolved before the provider is selected`() = runBlocking {
        val fetcher = FakeFetcher(
            onResolve = { "https://www.tiktok.com/@scout2015/video/6718335390845095173?is_from_webapp=1" },
        )
        val tiktok = provider(Platform.TIKTOK)
        val loader = PostLoader(fetcher = fetcher, providers = listOf(tiktok))
        val shortUrl = "https://vm.tiktok.com/ZMabc/"

        loader.load(shortUrl)

        val link = tiktok.links.single()
        assertEquals("6718335390845095173", link.postId)
        assertEquals("https://www.tiktok.com/@scout2015/video/6718335390845095173", link.browserUrl)
        assertEquals(shortUrl, link.originalUrl)
        assertEquals(listOf(shortUrl), fetcher.resolves)
    }

    @Test
    fun `a facebook short link is resolved before the provider is selected`() = runBlocking {
        val fetcher = FakeFetcher(
            onResolve = { "https://www.facebook.com/page/posts/123456/?mibextid=tracking" },
        )
        val facebook = provider(Platform.FACEBOOK)
        val loader = PostLoader(fetcher = fetcher, providers = listOf(facebook))
        val shortUrl = "https://fb.watch/abcDEF/"

        loader.load(shortUrl)

        val link = facebook.links.single()
        assertEquals("123456", link.postId)
        assertEquals("https://www.facebook.com/page/posts/123456/", link.browserUrl)
        assertEquals(shortUrl, link.originalUrl)
        assertEquals(listOf(shortUrl), fetcher.resolves)
    }

    @Test
    fun `a short link that resolves to a profile is unsupported`() = runBlocking {
        val fetcher = FakeFetcher(onResolve = { "https://www.tiktok.com/@scout2015" })
        val loader = PostLoader(fetcher = fetcher, providers = listOf(provider(Platform.TIKTOK)))
        val shortUrl = "https://vm.tiktok.com/ZMabc/"

        val error = failureOf { loader.load(shortUrl) }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.UNSUPPORTED, (error as PostException).kind)
        assertEquals(shortUrl, error.browserUrl)
    }

    @Test
    fun `a short link resolve failure is a network error`() = runBlocking {
        val fetcher = FakeFetcher(onResolve = { throw IOException("offline") })
        val loader = PostLoader(fetcher = fetcher, providers = listOf(provider(Platform.X)))
        val shortUrl = "https://t.co/AbCdEf"

        val error = failureOf { loader.load(shortUrl) }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.NETWORK, (error as PostException).kind)
        assertEquals(shortUrl, error.browserUrl)
    }

    @Test
    fun `a provider without a registered implementation is unexpected`() = runBlocking {
        val loader = PostLoader(fetcher = FakeFetcher(), providers = emptyList())

        val error = failureOf { loader.load("https://www.reddit.com/comments/abc123") }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.UNEXPECTED, (error as PostException).kind)
        assertTrue(error.message!!.contains("Reddit"))
    }

    @Test
    fun `provider IOException becomes a network error with the canonical url`() = runBlocking {
        val reddit = RecordingProvider(Platform.REDDIT) { throw IOException("offline") }
        val loader = PostLoader(fetcher = FakeFetcher(), providers = listOf(reddit))

        val error = failureOf { loader.load("https://www.reddit.com/comments/abc123") }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.NETWORK, (error as PostException).kind)
        assertEquals("https://www.reddit.com/comments/abc123", error.browserUrl)
        assertEquals(Platform.REDDIT, error.platform)
    }

    @Test
    fun `a provider PostException is passed through unchanged`() = runBlocking {
        val expected = PostException(
            kind = ErrorKind.NOT_FOUND,
            message = "Missing.",
            browserUrl = "https://x.com/jack/status/20",
            platform = Platform.X,
        )
        val x = RecordingProvider(Platform.X) { throw expected }
        val loader = PostLoader(fetcher = FakeFetcher(), providers = listOf(x))

        val error = failureOf { loader.load("https://x.com/jack/status/20") }

        assertSame(expected, error)
    }

    @Test
    fun `an unexpected provider error becomes an unexpected error`() = runBlocking {
        val reddit = RecordingProvider(Platform.REDDIT) { throw IllegalStateException("boom") }
        val loader = PostLoader(fetcher = FakeFetcher(), providers = listOf(reddit))

        val error = failureOf { loader.load("https://www.reddit.com/comments/abc123") }

        assertTrue(error is PostException)
        assertEquals(ErrorKind.UNEXPECTED, (error as PostException).kind)
    }

    @Test
    fun `cancellation is not converted into a user error`() = runBlocking {
        val reddit = RecordingProvider(Platform.REDDIT) { throw CancellationException("cancelled") }
        val loader = PostLoader(fetcher = FakeFetcher(), providers = listOf(reddit))

        val error = failureOf { loader.load("https://www.reddit.com/comments/abc123") }

        assertTrue(error is CancellationException)
    }
}