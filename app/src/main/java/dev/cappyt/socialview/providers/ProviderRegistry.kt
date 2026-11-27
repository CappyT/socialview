package dev.cappyt.socialview.providers

import dev.cappyt.socialview.net.HttpFetcher

fun defaultProviders(fetcher: HttpFetcher): List<PostProvider> = listOf(
    InstagramProvider(fetcher),
    FacebookProvider(fetcher),
    TikTokProvider(fetcher),
    XProvider(fetcher),
    RedditProvider(fetcher),
)