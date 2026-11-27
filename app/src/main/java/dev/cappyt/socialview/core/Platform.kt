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
            "visitors, so the app can show a comment count without the comments. A profile shows " +
            "its public header and its posts. Stories are not supported.",
    ),
    FACEBOOK(
        label = "Facebook",
        limitation = "Public posts and reels are read from Facebook's public page and rendered " +
            "natively. Private, age-gated and region-locked posts can hide the media. When " +
            "Facebook returns no public metadata, the official plugin is shown and can ask for " +
            "a login. A page shows its header and its latest posts, without their links.",
    ),
    TIKTOK(
        label = "TikTok",
        limitation = "Public posts are read from the data endpoint of TikTok's web player and " +
            "rendered natively. When it does not answer, TikTok's official player is shown. " +
            "Private accounts and region blocks can hide a post. A profile shows its header and " +
            "its posts.",
    ),
    X(
        label = "X",
        limitation = "Public posts are rendered by X's official embed. " +
            "Protected or age-restricted posts can ask for a login, and X can rate-limit embeds. " +
            "A profile shows its header and its latest posts.",
    ),
    REDDIT(
        label = "Reddit",
        limitation = "Posts are rendered natively, with up to 30 comments. They come from the " +
            "API of the Reddit app, with an anonymous session. When it does not answer, " +
            "SocialView shows the official embed, or the official comment embed when the " +
            "subreddit cannot be resolved. Private or quarantined posts may need a browser. A " +
            "user profile shows its header and the posts of the user.",
    ),
}