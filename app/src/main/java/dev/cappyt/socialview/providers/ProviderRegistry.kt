package dev.cappyt.socialview.providers

import dev.cappyt.socialview.net.HttpFetcher

fun defaultProviders(
    fetcher: HttpFetcher,
    reddit: RedditGraphql = RedditGraphql(fetcher),
): List<PostProvider> = listOf(
    InstagramProvider(fetcher),
    FacebookProvider(fetcher),
    TikTokProvider(fetcher),
    XProvider(fetcher),
    RedditProvider(fetcher, reddit),
)

fun defaultProfileProviders(
    fetcher: HttpFetcher,
    reddit: RedditGraphql = RedditGraphql(fetcher),
): List<ProfileProvider> = listOf(
    InstagramProfileProvider(fetcher),
    FacebookProfileProvider(fetcher),
    TikTokProfileProvider(fetcher),
    XProfileProvider(fetcher),
    RedditProfileProvider(reddit),
)