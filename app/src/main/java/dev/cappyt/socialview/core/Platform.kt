package dev.cappyt.socialview.core

enum class Platform(
    val label: String,
    val limitation: String,
) {
    INSTAGRAM(
        label = "Instagram",
        limitation = "Public posts are read from Instagram's public post document and rendered " +
            "natively. Private or inactive accounts, age-restricted posts and region blocks can " +
            "hide the media or the caption. Instagram publishes comment text only to logged-in " +
            "visitors, so the app can show a comment count without the comments. Stories are not " +
            "supported.",
    ),
    FACEBOOK(
        label = "Facebook",
        limitation = "Public posts and reels are read from Facebook's public page and rendered " +
            "natively. Private, age-gated and region-locked posts can hide the media. When " +
            "Facebook returns no public metadata, the official plugin is shown and can ask for " +
            "a login.",
    ),
    TIKTOK(
        label = "TikTok",
        limitation = "Public posts are rendered by TikTok's official player. " +
            "Region blocks and rate limits can hide a post, and the public oEmbed response " +
            "does not always include the caption.",
    ),
    X(
        label = "X",
        limitation = "Public posts are rendered by X's official embed. " +
            "Protected or age-restricted posts can ask for a login, and X can rate-limit embeds.",
    ),
    REDDIT(
        label = "Reddit",
        limitation = "Posts are rendered natively. Text and comments come from Reddit's public " +
            "RSS feed, then its public JSON. The RSS feed returns up to five comments and no " +
            "vote or comment totals. When both are blocked, SocialView shows the official " +
            "embed, or the official comment embed when the subreddit cannot be resolved. " +
            "Private, quarantined or NSFW-gated posts may need a browser.",
    ),
}