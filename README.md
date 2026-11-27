# SocialView

SocialView is a small Android app that shows one public post or one public profile from
Instagram, Facebook, TikTok, X or Reddit. Paste a link, share a link from another app, or open a
supported link with SocialView.

The app has no backend, no login, no API keys, no feeds, no analytics and no ads. Instagram and
Facebook posts are read from their public post documents and rendered with native Compose views
and Media3. TikTok posts are read from the data endpoint of TikTok's web player and rendered
natively. X posts use the official embed. Profiles are rendered natively from
the public source that each platform leaves open to logged-out visitors: see "Profiles".
Reddit posts and profiles come from the API of the Reddit Android app, with an anonymous
session: Reddit serves neither its RSS feed nor its public JSON to logged-out visitors. Reddit
posts get a native post card, with the official embed as the fallback.

## Supported links

| Platform  | Accepted links | Metadata source | Renderer |
|-----------|----------------|-----------------|----------|
| Instagram | `/p/<id>/`, `/reel/<id>/`, `/reels/<id>/`, `/tv/<id>/`, `/share/...` short links | Public embed document (`instagram.com/<kind>/<id>/embed/captioned/`), then the canonical post page, including its Polaris media object, then public oEmbed JSON | Native text, author and media: single photo, video, reel and mixed photo/video carousel |
| Facebook  | `/<user>/posts/<id>`, `/groups/<group>/posts/<id>`, `/groups/<group>/permalink/<id>`, `/<user>/videos/<slug>/<id>`, `/<user>/videos/<id>`, `/reel/<id>`, `/watch/?v=<id>`, `/video.php?v=<id>`, `/photo.php?fbid=<id>`, `story.php` and `permalink.php` with `story_fbid`, `/share/...` and `fb.watch` short links | Public page HTML: Open Graph tags, canonical link and `browser_native_*` video URLs | Native text, author and video or image. The official plugin is a fallback when public metadata is missing. |
| TikTok    | `/@user/video/<id>`, `/@user/photo/<id>`, `/embed/v2/<id>`, `/player/v1/<id>`, `vm.tiktok.com`, `vt.tiktok.com`, `/t/...` short links | The data endpoint of the official web player (`tiktok.com/player/api/v1/items?item_ids=<id>`), without cookie or signature. Public oEmbed JSON when it does not answer. | Native author, text, age, like and comment counts, and a video in the native player or a photo post as a pager. Fallback: WebView with the player URL from oEmbed, or the official `player/v1` URL |
| X         | `/<user>/status/<id>`, `/i/status/<id>`, `/statuses/<id>`, `t.co` short links | Public oEmbed JSON | WebView, `platform.twitter.com/embed/Tweet.html` |
| Reddit    | `/r/<sub>/comments/<id>`, `/comments/<id>`, `redd.it`, `/r/<sub>/s/...`, `reddit.app.link` | The API of the Reddit Android app with an anonymous token: the `PostsByIds` and `PostComments` persisted queries on `gql-fed.reddit.com`. The post id is enough: the answer names the subreddit. | Native Compose post card: subreddit avatar, `r/<sub>`, author, relative time, title, body, flair, link card, image, gallery or HLS video, vote and comment counts, and up to 30 threaded comments to a depth of three, with OP and pinned labels. Official `embed.reddit.com/r/<sub>/comments/<id>` fallback, or the official comment embed script on `www.reddit.com/comments/<id>/.embed` when the subreddit is unknown |

### Profiles

| Platform  | Accepted links | Source | Shown |
|-----------|----------------|--------|-------|
| Instagram | `/<username>/`, `/<username>/reels/`, `/<username>/tagged/`, `/_u/<username>`, `/share/...` short links that resolve to a profile | Public profile page (`instagram.com/<username>/`): the Polaris user object and the first 12 posts, then the public GraphQL timeline query for each next page of 12 | Picture, name, verified badge, post, follower and following counts, bio, links. A post grid that loads the next page at its end. |
| Facebook  | `/<name>/`, `/profile.php?id=<id>`, `/people/<slug>/<id>/` | Official Page Plugin document (`facebook.com/plugins/page.php`): its `PagePluginV2` properties | Picture, name, verified badge, rounded follower count, category, description. A list of the latest 5 posts, which have no link. |
| TikTok    | `/@<user>` | Creator embed document (`tiktok.com/embed/@<user>`): its state script, for the header and about 10 posts: the pinned ones, then the latest. Then the creator post list (`tiktok.com/api/creator/item_list/`), 15 per page, with the `secUid` that the player data endpoint gives for a video of the account | Picture, name, verified badge, follower, following and like counts, bio. A grid of videos and photo posts that loads the next page at its end. |
| X         | `/<user>`, with the `with_replies`, `media`, `likes` and `highlights` tabs | Timeline embed document (`syndication.twitter.com/srv/timeline-profile/screen-name/<user>`): its `__NEXT_DATA__` script | Picture, name, verified badge, post, follower and following counts, bio, link. A list of the latest 20 posts, with the pinned post and reposts marked. |
| Reddit    | `/user/<name>/`, `/u/<name>/`, with the `submitted`, `overview` and `posts` tabs | The API of the Reddit Android app with an anonymous token: `UserProfile` and `SubmittedPostsFeedSdui` persisted queries on `gql-fed.reddit.com` | Picture, display name, karma, post and comment counts, bio, social links. A list of the user's posts, 25 per page, that loads the next page at its end. |

A post of a profile opens in the app when the platform publishes its link. The author row of a
post opens the profile of the author on Instagram, Facebook, TikTok and X.

The link parser accepts `http` and `https`, upgrades `http` to `https`, and rejects hosts that
only look similar (for example `notinstagram.com`). A link must point to a single post or to a
profile. Feeds, stories, searches, subreddits and the pages that a platform reserves for itself
(for example `/explore/`) show an explicit "unsupported link" message.

Short links are resolved before the first provider request. The resolved URL is parsed again, so
the provider receives the canonical post or profile URL. If a short link resolves to a login page, the app
reads the public document and looks for `og:url`, `canonical_url` or a post link.

### Tracking parameters

Each provider keeps only the query parameters its post URL needs:

| Platform | Kept parameters |
|----------|-----------------|
| Instagram | `img_index` |
| Facebook | `story_fbid`, `id`, `fbid`, `set`, `v` |
| TikTok, X, Reddit | none |

The allowlist removes `utm_*`, `igsh`, `exln`, `fbclid`, `gclid`, `mibextid`, `s`, `t` and the
other tracking or share baggage from post links. A profile link keeps no query parameters. The
canonical URL is the one the app requests, shows, shares and opens in a browser.

The share action in the top bar sends the canonical URL as plain text through the system share
sheet, without SocialView as a target. Links in a profile bio use the direct URL, not
Instagram's `l.instagram.com` redirect.

Direct media URLs from Instagram and Facebook CDNs are not rewritten. Their query parameters
(for example `_nc_cat`, `_nc_ohc`, `oh`, `oe` and `efg`) are required and are preserved
byte-for-byte.

## Build

Requirements:

- JDK 21. This project was built and tested with JDK 21. The Java bytecode target is 17.
  A host JDK 27 failed, so use the tested JDK instead of assuming every newer JDK works.
- Android SDK with platform 36 and build-tools 36. A `local.properties` file with `sdk.dir`
  must point to the SDK.

Commands:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

The debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device:

```bash
./gradlew :app:installDebug
# or
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Releases

A release is a signed APK on the releases page of the repository, with a `SHA256SUMS` file.
Android installs an update only over a build with the same signature, so a release does not
install over a debug build: uninstall the debug build first.

The certificate of the release key has this SHA-256 digest:

```
6789831fa9246d6106c719261f8f519e200b2ad8b53563d17230f34b6572a217
```

Compare it with the output of `apksigner verify --print-certs socialview-<version>.apk`.

Two workflows are in `.github/workflows/`:

- `ci.yaml` runs the unit tests, lint and the debug build on each pull request and on each
  push to `main`, with JDK 21.
- `release.yaml` runs on a tag `v<versionName>`, for example `v0.1.0`. It stops when the tag is
  not the `versionName` of `app/build.gradle.kts`. It runs the unit tests, builds the release
  APK and publishes the release.

The release key is not in the repository. `./gradlew :app:assembleRelease` signs the APK when a
`key.properties` file in the repository root gives `storeFile`, `storePassword`, `keyAlias` and
`keyPassword`. Without that file the release build is not signed. The release workflow writes
the file from the repository secrets `RELEASE_KEYSTORE` (the key store in base64),
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`.

## Open links with SocialView

The manifest registers `VIEW` filters for `http` and `https` on the supported hosts only. It
does not register a wildcard, so the app is not a general browser.

To open links with the app:

1. Tap a supported link in another app. Select SocialView in the "Open with" chooser.
2. Choose "Always" if the system offers it.
3. On Android 12 and newer, open Settings > Apps > SocialView > Open by default and enable the
   supported links. The app does not own the platform domains, so the filters set
   `android:autoVerify="false"` and Android cannot verify them as App Links. The system may
   continue to send links to the browser. Sharing a link to SocialView and pasting a link
   always work.

A profile link opens the profile. A supported link to a feed, a story or a search opens
SocialView and shows the unsupported message plus an "Open in browser" button. This is
intentional: the filters cannot tell a post path from the other paths before the app reads the
URL.

## Screens

- Home: the link field with its paste and clear actions, the "Open" button and the supported
  platforms. The paste action opens the link when the clipboard holds one.
- Viewer: a top bar with back, the share action and "Open in browser", then the post, the
  profile, the loading state or the error.
- A post opens the profile of its author. A profile opens its posts. Back returns to the
  previous screen and keeps its scroll position. Back on the first screen returns to Home,
  or leaves the app when the link came from another app.

## Theme

The UI uses Material 3. On Android 12 and newer it follows Material You and uses the system
wallpaper color scheme. On older versions it uses the bundled teal light and dark schemes. The
app follows the system dark mode setting.

The top app bar, loading indicator, errors, buttons, text fields, cards and text all use the
Material 3 color roles. A post and each block of a profile sit in a tonal `surfaceContainerLow`
card with the extra-large shape. Status and navigation bars are transparent, and the system bar icons
switch with the theme. Native posts follow the theme. The TikTok, X and fallback Reddit embeds
keep their own appearance.

## Behavior and errors

The app shows one of these errors with a browser fallback when the URL is known:

- `Not found`: the platform says the post or the profile is missing, removed or not public.
- `Network error`: the request or the short link failed.
- `Unexpected response`: the response did not match the expected format.
- `Unsupported link`: the URL is not a supported post or profile.

The provider code also shows a short note when it falls back to an official embed, or when
public data is incomplete. Examples:

- Instagram returns no caption or media: the post is shown with a note and a browser fallback.
  A copyright-blocked reel omits the stream from its embed document; the canonical page
  Polaris object supplies it. A video whose embed and canonical page both omit the stream
  keeps its cover image and adds a note. A video that fails to load in the native player can
  be opened in the browser.
- Instagram publishes comment counts, not comment text, to logged-out visitors. The app tries
  the public comments endpoint and shows the count with a note when the text is not available.
- Instagram answers a missing account and a login wall with the same page, without the profile
  object: the app shows `Not found` and names both causes.
- A private Instagram or TikTok profile shows its header and a note. Its posts are not public.
- The request for more profile posts fails: the profile keeps the posts it has and shows "Try
  again" and "Open in browser".
- TikTok answers a missing account with error code 10221 and a private one with 10222, both as
  HTTP 400. The app reads the code, not the status.
- X answers a missing, a suspended and a protected account with the same empty timeline: the
  app shows `Not found` and names the three causes.
- Facebook answers a missing page and a restricted page with the same empty plugin: the app
  shows `Not found` and names both causes.
- Reddit answers a suspended and a deleted account with a typed object: the app shows `Not
  found` with the cause. A profile whose post feed fails keeps its header.
- Facebook returns no public metadata: the official plugin is shown and can ask for a login.
  A reel or video without a public stream keeps its cover image and adds a note.
- The TikTok data endpoint does not answer, or answers for another post: the official player is
  shown, with the oEmbed metadata when TikTok returns it.
- The Reddit app API does not answer, or refuses the anonymous session: the official Reddit
  embed is shown with a note. When the link has no subreddit and Reddit does not redirect to
  the canonical path, the comment embed is shown without the post header.
- The Reddit comments request fails: the post is shown with a note and without comments.
- X oEmbed is unavailable: the official Tweet embed is shown.

The input and the screen state live in a ViewModel. Rotation keeps the loaded post or profile
and the screens below it. When the system recreates the process, the app restores the input and
the open screens, and reloads the one on top.

An official embed can ask for a login, show an age gate, or refuse to render because of a
region block. The WebView usually receives HTTP 200 in these cases, so the app cannot detect
them. The platform limitation text under each post explains this. The app never claims that
an embed loaded when the WebView reports an error.

## Media

Instagram, Facebook and Reddit posts produce a list of media items when public data exists,
rendered without a WebView:

- A single image is shown at its aspect ratio when the platform reports the dimensions.
- A video or reel uses Media3 with a native player, controls, audio focus, pause on lifecycle
  stop, resume on lifecycle start, pause when headphones are unplugged, release when the view
  leaves the screen, and playback position kept across rotation.
- A carousel or Reddit gallery uses a native horizontal pager with a page counter and starts at
  the `img_index` page of the link, clamped to the media count. Only the visible page plays its
  video. Photo and video children keep their order.
- A Reddit video uses the HLS stream, with the muxed MP4 as the fallback. A GIF plays as the
  MP4 that Reddit makes from it. An external Reddit link post shows a tappable link card with
  its host. A crosspost shows the text and the media of the original post.

- A TikTok video is requested with the `Referer` of the site and without cookies. Its host
  answers 403 to a request without that header, and to a request with a TikTok cookie. The
  player and the download send the same headers.

When the platform does not report the dimensions, the image fills the width and a video uses a
9:16 frame.

### Saving media

Native media has a save button. A post with more than one item asks whether to save the item
on screen or all of them. The system download manager writes pictures to `Pictures/SocialView`
and videos to `Movies/SocialView`, with a name from the site and the post id, for example
`instagram-Df147tCiAyB-2.jpg`. A Reddit video saves its muxed MP4, because its stream is a
playlist. An item without a file, and the media inside an official embed, have no save button.

### Sensitive content

When the platform marks a post as NSFW or sensitive, its media stays covered: a blurred preview
from Android 12, a plain cover on older versions. Nothing plays and the save button is hidden
until the user taps "Show". The thumbnail of such a post in a profile is blurred too, or left
out before Android 12. The mark
comes from Reddit (`isNsfw` of a post, the `NSFW` indicator of a profile post) and from X
(`possibly_sensitive` of a profile post). The Instagram, Facebook and TikTok sources carry no
such mark, and an official embed applies the rules of its platform.

## Security

- The app declares the `INTERNET` permission and, up to Android 9 only, `WRITE_EXTERNAL_STORAGE`,
  which it requests when the user saves media. From Android 10 the download manager needs no
  permission for the public media folders. The merged manifest also declares AndroidX's
  signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; the system does not prompt the
  user for it.
- Cleartext traffic is disabled (`android:usesCleartextTraffic="false"`).
- The app sends `POST` requests to two platforms. Instagram: the public GraphQL timeline query
  for more profile posts, with the query id, the account id and the page cursor, without
  cookie or token. Reddit: the anonymous token request and the persisted GraphQL queries of the
  Reddit Android app, with the app's public client id, its User-Agent and the anonymous token.
  The token stays in memory and is not tied to an account.
- Network requests use HTTPS and the system TLS stack. The app does not install a custom
  trust manager and does not disable certificate validation.
- Native media uses the HTTPS CDN URLs returned by the platform. The app does not modify their
  required query parameters.
- The embed WebView enables JavaScript and DOM storage only because the official TikTok, X and
  Reddit embeds need them. File and content access are off, mixed content is never allowed, and
  multiple windows are off.
- There is no JavaScript interface (`addJavascriptInterface` is not used), so page scripts
  cannot call app code.
- The WebView loads only the official embed document path. A main-frame link with the same
  path on an allowed host stays in the WebView. Any other link opens in the user's browser.
  A link that is not `http` or `https` shows a visible note. An embed with a `data:` URL has no
  path, so its root link always opens in the browser.
- When an embed requires a script from a specific URL, a failed script load shows a message and
  a browser fallback instead of an empty frame.
- The share action uses an `ACTION_SEND` intent with the canonical URL as plain text.
- External links, including "Open in browser", use an `ACTION_VIEW` intent. The app accepts
  `http` and `https` links, upgrades `http` to `https`, and opens the canonical `https` URL.
  The browser helper probes a neutral `https` URL for generic browsers, opens the post
  directly when one browser is installed, and excludes SocialView and host-specific apps from
  the chooser.

## Project layout

```
app/src/main/java/dev/cappyt/socialview/
  core/       URL parsing, models, errors, short-link documents, Reddit count and age formatting
  net/        HTTP GET, redirect resolution, browser headers
  providers/  one post strategy and one profile strategy per platform, response parsers
  loader/     link parsing, short-link resolution, provider selection
  ui/         Compose screens and navigation, post card, profile grid, native media pager,
              WebView embed, browser and share helpers
app/src/test/java/dev/cappyt/socialview/
  core/       link classification, normalization, short-link documents, count and age formatting
  providers/  post and profile parsing per platform, oEmbed, the Reddit app API, HTML text
  loader/     provider selection, profiles, short links, error mapping
app/src/test/resources/fixtures/
              trimmed real documents: TikTok posts and profiles, X and Facebook profiles,
              Reddit posts, comments and profiles
  ui/         browser target selection, embed navigation, required subresources
```

Provider endpoints are isolated in `providers/`. The rest of the app depends on `PostProvider`,
`ProfileProvider`, `PostDetails`, `PostMedia`, `ProfileDetails` and `PostException`, not on
platform response formats.

## Tests

`./gradlew :app:testDebugUnitTest` runs 211 JVM unit tests:

| Test class | Tests | Coverage |
|------------|-------|----------|
| `LinkParserTest` | 40 | Hosts, paths, short links, canonical URLs, profile paths and reserved names of every platform, author handles, tracking removal including `vrfl`, required query parameters, invalid input |
| `ProviderBehaviourTest` | 28 | Native Instagram and Facebook posts, oEmbed metadata, `img_index`, copyright-blocked reel fallback, Instagram profile errors and the timeline request, TikTok and X metadata and fallbacks |
| `RedditProviderTest` | 12 | Reddit posts from the app API: text, image, gallery, video, GIF, link, crosspost and profile posts, NSFW, removed and hidden-score posts, the file to save, comments with depth, deep, removed and orphan replies, the canonical link of a short id, missing and refused posts, failed comments, embed fallbacks |
| `InstagramPostParserTest` | 13 | Video, mixed carousel, legacy and Polaris shapes, copyright-blocked canonical page, Open Graph captions, shortcode match, comments, error shell |
| `PostLoaderTest` | 16 | Provider selection, Instagram profiles, short-link resolution, error mapping, cancellation |
| `InstagramProfileParserTest` | 13 | Profile header, bio links, timeline posts, repeated and malformed shortcodes, next page cursor, private and missing accounts, GraphQL timeline pages |
| `ProfileProvidersTest` | 27 | Native TikTok posts (video with its request headers, photo post, missing post, player fallback) and the TikTok post list pages. TikTok, X, Facebook and Reddit profiles from trimmed real documents: header, posts, pinned posts and reposts, data of another account, missing, private, suspended and deleted accounts, the Reddit anonymous token and its renewal, the next Reddit page |
| `FacebookPageParserTest` | 9 | Reel metadata, post identity, caption author validation, video URL decoding, photo fallback |
| `EmbedNavigationTest` | 7 | Embed document confinement, `data:` wrappers, non-http links |
| `ShortLinkDocumentTest` | 6 | `og:url`, canonical link, `canonical_url`, escaped URLs, login pages |
| `BrowserTargetsTest` | 6 | Browser selection with SocialView and host apps excluded |
| `HtmlTextTest` | 5 | HTML entity decoding and text cleanup |
| `TikTokOEmbedParserTest` | 5 | Player URL validation and optional metadata |
| `InstagramOEmbedParserTest` | 5 | Caption, author url priority, handle, thumbnail and media id parsing |
| `EmbedSubresourceTest` | 3 | Required script matching and failure messages |
| `XOEmbedParserTest` | 3 | oEmbed text, author, handle parsing |
| `PostIdentityTest` | 2 | Duplicate author and handle suppression |
| `PostFormatTest` | 6 | Relative age boundaries, compact counts, link hosts |
| `MediaDownloadsTest` | 4 | File names of saved media, numbered carousel items, the file of a stream, items without a file |
| `ManifestQueriesTest` | 1 | One data scheme per `<queries>` intent, the Android 17 install rule |

The tests use a fake `HttpFetcher`. They do not call the network.

## Limitations

- Single public posts and profiles only. No feeds, subreddits, stories, highlights, live
  streams, search or threads.
- Instagram, Reddit and TikTok profiles load more posts. A Facebook page shows 5 posts without
  links and an X profile 20 posts. Each of the two shows a note and the browser fallback after
  its posts.
- SocialView does not use the API of the TikTok Android app. That API answers only to requests
  that the app signs in its native security library. The TikTok sources are web endpoints
  without documentation, and TikTok can change them.
- The X profile source allows about 30 requests in 15 minutes for each address.
- TikTok's `robots.txt` asks crawlers not to read `/embed/@`. The app reads it once for each
  profile that the user opens.
- An Instagram profile shows its header and its post grid. It does not show the reels and
  tagged tabs or the follower lists. The profile picture is the 150 px version that the public
  page returns. The post count is Instagram's rounded label, for example `32K`.
- More profile posts come from a persisted GraphQL query. Its id is fixed in the app, because
  it is in a script bundle and not in the profile page. When Instagram changes the query, the
  profile shows its first 12 posts and the browser fallback.
- Instagram can serve the profile page as a login wall for datacenter and VPN addresses. The
  app then shows `Not found`.
- No login. Private, age-restricted, region-locked and login-gated posts may not render.
- Instagram can serve the public embed document with a login shell for datacenter and VPN
  addresses. When the public document has no media or caption, the app shows the note and the
  browser fallback. A reel that returns only oEmbed metadata shows its cover image with a note
  instead of a player. Instagram publishes comment text only to logged-in visitors, so the app
  usually shows a comment count without the comments.
- Instagram CDN URLs carry an expiry (`oe`). A post opened much later can fail to load its
  media until the post is loaded again.
- Facebook accepts a page as a native post only when its canonical URL carries a post id that
  matches the requested post. A login, checkpoint, home or error page falls back to the plugin.
  The author comes from the canonical handle, not from a free-text title suffix.
- Facebook can return a login page or an error page for private, age-gated and region-locked
  content, and for groups that are not public. Share links and `fb.watch` links resolve once and
  can expire. The plugin fallback can ask for a login.
- A TikTok post has no create time in its source: the app reads it from the post id. The media
  links of TikTok expire after about 48 hours.
- X can rate limit its oEmbed and embed endpoints. Protected posts need a login.
- Reddit posts and profiles use the persisted query ids, the client id and the User-Agent of
  Reddit Android app build 2026.35.0. A newer app build can retire them. Reddit's edge also
  checks the TLS handshake: Android's network stack passes. The anonymous session allows 300
  requests in its rate window. A post shows up to 30 comments and does not load more replies.
- A native post shows only what the public document exposes. It does not paginate comments,
  load more carousel items, or play a video that the platform does not publish to logged-out
  visitors. The official embed and the browser cover the rest.
- If no browser other than SocialView is installed, "Open in browser" can only show the
  system chooser, which may include host-specific apps.
- Short links are resolved once, with a maximum of six redirects.
- The WebView embeds have a fixed height per platform. Very long captions or comments can be
  cut off inside the embed. The browser fallback shows the full post.
- No cache and no offline mode. Every load fetches the post or the profile again. Back
  navigation keeps the screens that are already loaded.
- Minimum Android version is 8.0 (API 26). Target and compile SDK are 36.

## Verification status

Build and tests, with JDK 21:

- `./gradlew :app:testDebugUnitTest`: 211 tests, 0 failures, 0 errors, 0 skipped.
- `./gradlew :app:lintDebug`: 0 errors, 10 warnings: the target SDK notice, the Gradle version
  notice, five dependency update notices, the `mipmap-anydpi-v26` folder that equals `minSdk`,
  and two KTX suggestions in `Browser.kt`. One hint.
- `./gradlew :app:assembleDebug`: build successful.

On a Pixel 7a (`lynx`), Android 17 (API 37), build `CP3A.261005.002.A1`, 1080x2400, with the
debug APK installed through `adb install -t`:

- Home, viewer, error screen and profiles in the dark and the light theme, with the system
  wallpaper colors.
- Instagram profile `natgeo` opened through `ACTION_VIEW` with `?igsh=`: header, counts, bio,
  links and the 12-post grid. Scrolling to the end loads the next pages. A grid post opens in
  the app, and back returns to the same scroll position: the two screenshots are identical.
- Profiles of X `NASA`, TikTok `nasa`, `tiktok` and one large creator account, Facebook `NASA`
  and one Reddit user: header, counts and posts. A private TikTok account shows the private
  note, a missing one shows `Not found`. The Reddit list loads its next page at the end. The
  Reddit requests pass the TLS check of Reddit's edge from the device.
- The share action of the `natgeo` profile opens the system sheet with
  `https://www.instagram.com/natgeo/`. SocialView is not among the targets.
- A missing Instagram account shows `Not found` with the browser fallback.
- System back on a screen that another app opened leaves SocialView.
- Instagram reel `DfAWxdIFmVB` and Reddit video `redd.it/1uz79k3` play natively. A text post
  of `r/tifu` renders natively through the app API, with its flair and comments.
- The save button of carousel `Df147tCiAyB` asks "This one" or "All 3" and writes three files to
  `Pictures/SocialView`. The save button of a reel writes one file to `Movies/SocialView`.
- A Reddit post that Reddit marks NSFW shows the blurred cover with "Show" and no save button.
- A new link opens after the system stopped the process in the background.
- A post opened from the X profile `NASA` shows the official embed of the original post of a
  repost.
- TikTok video `7665075736742530317` plays in the native player, and its save button writes a
  3.3 MB MP4 to `Movies/SocialView`. TikTok photo post `7475761007382629634` shows a pager of 11
  images with its counts. The grid of the TikTok profile `nasa` loads its next pages down to
  the first post, and a post from the end of the grid opens natively.

Live checks from a JVM with the app loader and fetcher (the JDK needs
`sun.net.http.allowRestrictedHeaders=true` to send the `Sec-Fetch-*` headers, Android sends
them):

- Instagram `natgeo`: the profile, three pages of 12 posts through the GraphQL timeline query,
  a missing account, and the first grid post as a native carousel.
- Reddit: a text post of `r/tifu` with 14 comments, `redd.it/1uz79k3` (HLS video, with its
  canonical `r/aww` link), `r/aww/comments/1eigbzg` (gallery of two images) and the profile
  `spez`.

Not checked on the device:

- Saving media and the storage permission request on Android 9 and older.
- The plain cover of sensitive media on Android 11 and older.
- The pause on headphones unplugged: `AUDIO_BECOMING_NOISY` is a protected broadcast and the
  retail build is not debuggable, so adb cannot send it.
- The Facebook login-shell rejection is unit-tested with fixtures; a live login shell was not
  reproduced.
- Browser navigation past Chrome's first-run screen, which needs user interaction.
- Every short-link host, because some short links expire.

## Runtime notes

Install the debug APK on the device with `adb install -r
app/build/outputs/apk/debug/app-debug.apk`. The first check should open the app, load one
Instagram reel, one Instagram carousel and the Facebook acceptance share link, and confirm
native playback and the "Open in browser" fallback. The public endpoints can change without
notice.

## License

SocialView is free software under the GNU General Public License, version 3. See `LICENSE`.
