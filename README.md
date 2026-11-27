# SocialView

SocialView is a small Android app that shows one public post from Instagram, Facebook, TikTok,
X or Reddit. Paste a link, share a link from another app, or open a supported link with
SocialView.

The app has no backend, no login, no API keys, no feeds, no analytics and no ads. Instagram and
Facebook posts are read from their public post documents and rendered with native Compose views
and Media3. TikTok and X use their official embeds. Reddit reads its public RSS feed first, then
its public JSON for scores, comment totals and threaded comments. Reddit posts get a native
post card, not the embed, with the official embed as the last fallback.

## Supported links

| Platform  | Accepted post links | Metadata source | Renderer |
|-----------|---------------------|-----------------|----------|
| Instagram | `/p/<id>/`, `/reel/<id>/`, `/reels/<id>/`, `/tv/<id>/`, `/share/...` short links | Public embed document (`instagram.com/<kind>/<id>/embed/captioned/`), then the canonical post page, including its Polaris media object, then public oEmbed JSON | Native text, author and media: single photo, video, reel and mixed photo/video carousel |
| Facebook  | `/<user>/posts/<id>`, `/groups/<group>/posts/<id>`, `/groups/<group>/permalink/<id>`, `/<user>/videos/<slug>/<id>`, `/<user>/videos/<id>`, `/reel/<id>`, `/watch/?v=<id>`, `/video.php?v=<id>`, `/photo.php?fbid=<id>`, `story.php` and `permalink.php` with `story_fbid`, `/share/...` and `fb.watch` short links | Public page HTML: Open Graph tags, canonical link and `browser_native_*` video URLs | Native text, author and video or image. The official plugin is a fallback when public metadata is missing. |
| TikTok    | `/@user/video/<id>`, `/@user/photo/<id>`, `/embed/v2/<id>`, `/player/v1/<id>`, `vm.tiktok.com`, `vt.tiktok.com`, `/t/...` short links | Public oEmbed JSON | WebView, player URL from oEmbed, or the official `player/v1` URL |
| X         | `/<user>/status/<id>`, `/i/status/<id>`, `/statuses/<id>`, `t.co` short links | Public oEmbed JSON | WebView, `platform.twitter.com/embed/Tweet.html` |
| Reddit    | `/r/<sub>/comments/<id>`, `/comments/<id>`, `redd.it`, `/r/<sub>/s/...`, `reddit.app.link` | Public `.rss` feed with the title, author, body, timestamps, preview images and up to five flat comments, then `comments/<id>.json` with scores, comment totals, flair and threaded replies when it is reachable. The subreddit is resolved when the link omits it. | Native Compose post card: subreddit avatar, `r/<sub>`, author, relative time, title, body, flair, link card, gallery or HLS video, vote and comment counts, and threaded comments with OP and pinned labels. Official `embed.reddit.com/r/<sub>/comments/<id>` fallback, or the official comment embed script on `www.reddit.com/comments/<id>/.embed` when the subreddit is unknown |

The link parser accepts `http` and `https`, upgrades `http` to `https`, and rejects hosts that
only look similar (for example `notinstagram.com`). A link must point to a single post.
Profiles, feeds, stories and searches show an explicit "unsupported link" message.

Short links are resolved before the first provider request. The resolved URL is parsed again, so
the provider receives the canonical post URL. If a short link resolves to a login page, the app
reads the public document and looks for `og:url`, `canonical_url` or a post link.

### Tracking parameters

Each provider keeps only the query parameters its post URL needs:

| Platform | Kept parameters |
|----------|-----------------|
| Instagram | `img_index` |
| Facebook | `story_fbid`, `id`, `fbid`, `set`, `v` |
| TikTok, X, Reddit | none |

The allowlist removes `utm_*`, `igsh`, `exln`, `fbclid`, `gclid`, `mibextid`, `s`, `t` and the
other tracking or share baggage from post links. The canonical URL is the one the app requests,
shows and opens in a browser.

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

A supported link to a profile, feed or story opens SocialView and shows the unsupported
message plus an "Open in browser" button. This is intentional: the filters cannot tell a post
path from a profile path before the app reads the URL.

## Theme

The UI uses Material 3. On Android 12 and newer it follows Material You and uses the system
wallpaper color scheme. On older versions it uses the bundled teal light and dark schemes. The
app follows the system dark mode setting.

The top app bar, loading indicator, errors, buttons, text fields, cards and text all use the
Material 3 color roles. Status and navigation bars are transparent, and the system bar icons
switch with the theme. Native posts follow the theme. The TikTok, X and fallback Reddit embeds
keep their own appearance.

## Behavior and errors

The app shows one of these errors with a browser fallback when the URL is known:

- `Post not found`: the platform says the post is missing or removed.
- `Network error`: the request or the short link failed.
- `Unexpected response`: the response did not match the expected format.
- `Unsupported link`: the URL is not a single supported post.

The provider code also shows a short note when it falls back to an official embed, or when
public data is incomplete. Examples:

- Instagram returns no caption or media: the post is shown with a note and a browser fallback.
  A copyright-blocked reel omits the stream from its embed document; the canonical page
  Polaris object supplies it. A video whose embed and canonical page both omit the stream
  keeps its cover image and adds a note. A video that fails to load in the native player can
  be opened in the browser.
- Instagram publishes comment counts, not comment text, to logged-out visitors. The app tries
  the public comments endpoint and shows the count with a note when the text is not available.
- Facebook returns no public metadata: the official plugin is shown and can ask for a login.
  A reel or video without a public stream keeps its cover image and adds a note.
- TikTok oEmbed returns `429` or an unexpected body: the official player is shown.
- Reddit returns `401`, `403` or `429` for its public RSS feed and JSON: the official Reddit
  embed is shown. The app reads the feed first, so the native post appears whenever the feed is
  reachable; the JSON request only adds scores, totals and threaded replies. When the link has no subreddit
  and Reddit does not redirect to the canonical path, the comment embed is shown without the
  post header.
- X oEmbed is unavailable: the official Tweet embed is shown.

The input and the screen state live in a ViewModel. Rotation keeps the loaded post. When the
system recreates the process, the app restores the input and reloads the last request.

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
- A Reddit video uses the HLS stream from the public JSON, with the DASH MP4 fallback when HLS
  is absent. An external Reddit link post shows a tappable link card with its host.
- A Reddit post built from the RSS feed shows the five comments the feed returns, without
  scores or totals. The JSON adds scores, totals and replies up to a depth of three.

When the platform does not report the dimensions, the image fills the width and a video uses a
9:16 frame.

## Security

- The app declares only the `INTERNET` permission. It is a normal permission, granted at
  install time; the app does not request it at runtime. The merged manifest also declares
  AndroidX's signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; the system does not
  prompt the user for it.
- Cleartext traffic is disabled (`android:usesCleartextTraffic="false"`).
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
  providers/  one strategy per platform, response parsers for Instagram, Facebook and Reddit
  loader/     link parsing, short-link resolution, provider selection
  ui/         Compose screen, native media pager, WebView embed, browser helper
app/src/test/java/dev/cappyt/socialview/
  core/       link classification, normalization, short-link documents, count and age formatting
  providers/  Instagram and Facebook page parsing, oEmbed, Reddit JSON and RSS, HTML text
  loader/     provider selection, short links, error mapping
  ui/         browser target selection, embed navigation, required subresources
```

Provider endpoints are isolated in `providers/`. The rest of the app depends on `PostProvider`,
`PostDetails`, `PostMedia` and `PostException`, not on platform response formats.

## Tests

`./gradlew :app:testDebugUnitTest` runs 171 JVM unit tests:

| Test class | Tests | Coverage |
|------------|-------|----------|
| `LinkParserTest` | 34 | Hosts, paths, short links, canonical URLs, tracking removal including `vrfl`, required query parameters, invalid input |
| `ProviderBehaviourTest` | 33 | Native Instagram and Facebook posts, oEmbed metadata, `img_index`, copyright-blocked reel fallback, TikTok/X/Reddit metadata and fallbacks |
| `InstagramPostParserTest` | 13 | Video, mixed carousel, legacy and Polaris shapes, copyright-blocked canonical page, Open Graph captions, shortcode match, comments, error shell |
| `PostLoaderTest` | 12 | Provider selection, short-link resolution, error mapping, cancellation |
| `RedditParserTest` | 15 | Text, images, galleries, HLS video, link posts, metadata, threaded comments, NSFW |
| `FacebookPageParserTest` | 9 | Reel metadata, post identity, caption author validation, video URL decoding, photo fallback |
| `EmbedNavigationTest` | 7 | Embed document confinement, `data:` wrappers, non-http links |
| `ShortLinkDocumentTest` | 6 | `og:url`, canonical link, `canonical_url`, escaped URLs, login pages |
| `BrowserTargetsTest` | 6 | Browser selection with SocialView and host apps excluded |
| `HtmlTextTest` | 5 | HTML entity decoding and text cleanup |
| `TikTokOEmbedParserTest` | 5 | Player URL validation and optional metadata |
| `InstagramOEmbedParserTest` | 5 | Caption, author url priority, handle, thumbnail and media id parsing |
| `RedditRssParserTest` | 7 | Post selection, timestamps, link posts, comments, linked image, preview image, malformed feed |
| `EmbedSubresourceTest` | 3 | Required script matching and failure messages |
| `XOEmbedParserTest` | 3 | oEmbed text, author, handle parsing |
| `PostIdentityTest` | 2 | Duplicate author and handle suppression |
| `PostFormatTest` | 5 | Relative age boundaries, compact counts, link hosts |
| `ManifestQueriesTest` | 1 | One data scheme per `<queries>` intent, the Android 17 install rule |

The tests use a fake `HttpFetcher`. They do not call the network.

## Limitations

- Single public posts only. No profiles, feeds, stories, live streams, search or threads.
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
- TikTok oEmbed can be rate limited and does not always return the caption. The official
  player still works in most cases.
- X can rate limit its oEmbed and embed endpoints. Protected posts need a login.
- Reddit's public JSON endpoint can be blocked per network, and both public endpoints are rate
  limited per IP. The app reads the RSS feed first, which returns up to five flat comments
  without scores or totals and can lag behind the JSON. The JSON request then adds scores,
  comment totals, flair, gallery or HLS video and threaded replies, up to 30 comments and a
  depth of three, when it succeeds. When both sources fail, the embed fallback is shown and
  comments come only from the embed. When the link has no subreddit and Reddit does not
  redirect to the canonical path, the fallback shows the comment embed without the post header.
- A native post shows only what the public document exposes. It does not paginate comments,
  load more carousel items, or play a video that the platform does not publish to logged-out
  visitors. The official embed and the browser cover the rest.
- If no browser other than SocialView is installed, "Open in browser" can only show the
  system chooser, which may include host-specific apps.
- Short links are resolved once, with a maximum of six redirects.
- The WebView embeds have a fixed height per platform. Very long captions or comments can be
  cut off inside the embed. The browser fallback shows the full post.
- No cache and no offline mode. Every load fetches the post again.
- Minimum Android version is 8.0 (API 26). Target and compile SDK are 36.

## Verification status

Build and tests, with JDK 21:

- `./gradlew :app:testDebugUnitTest`: 171 tests, 0 failures, 0 errors, 0 skipped.
- `./gradlew :app:lintDebug`: 0 errors, 9 warnings: the Gradle 9.8.2 notice, five dependency
  update notices, the `mipmap-anydpi-v26` folder that equals `minSdk`, and two KTX suggestions
  in `Browser.kt`. One hint.
- `./gradlew :app:assembleDebug`: build successful.

On a Pixel 7a (`lynx`), Android 17 (API 37), build `CP3A.261005.002.A1`, 1080x2400, with the
debug APK installed through `adb install -r -t`:

- Cold launch through `ACTION_VIEW` with a shared link: 653 to 696 ms.
- Instagram reel `De5-myeTp52` (the copyright-blocked acceptance case) renders its caption
  `2067 dystopian`, author `CUURLEY (歌厉)(Bo)`, handle `@cuurley` and a native video that
  advances between frames. `vrfl` is dropped from the canonical URL.
- Instagram reel `DaduG1us3DU` and Facebook reel `2000381177287760` render natively and play
  their video.
- Instagram carousel `DeRxxZzkZu1?img_index=3` opens at page 3 of 7.
- Reddit `r/Android/comments/1wza3xh` renders natively from the public RSS feed while the JSON
  endpoint returns HTTP 403: `r/Android`, `u/kbDL-`, a relative age, the title, a tappable
  `support.google.com` link card, the five comments with relative times, a `5 shown` count and
  the RSS note after the comments.
- The same Reddit link shows the official `embed.reddit.com` post when the RSS window is spent
  (HTTP 429). The embed reported 53 upvotes and 35 comments, confirming the totals that the
  JSON path parses.
- Rotation recreates the activity and keeps the post, the scroll position and the playback
  position. Playback resumes after rotation.
- The browser fallback sends `ACTION_VIEW` to the default browser.

Live HTTP checks with the app User-Agent:

- `instagram.com/reel/DaduG1us3DU/embed/captioned/` returned HTTP 200 with a public
  `contextJSON` video and cover image.
- `instagram.com/reel/De5-myeTp52/embed/captioned/` returned HTTP 200 with
  `"copyright_blocked":true`, `is_video:true` and a cover image, but no `video_url`. Its
  canonical page returned HTTP 200 with `video_versions` in the `xig_polaris_media` object.
  Parsing that object is what restores the native stream.
- `instagram.com/p/DeRxxZzkZu1/embed/captioned/` returned HTTP 200 with a public sidecar
  of image and video children.
- `facebook.com/share/r/18UZyPa2pP/` returned HTTP 302 to `facebook.com/reel/2000381177287760/`.
  The reel page returned HTTP 200 with Open Graph text and a `browser_native_hd_url` MP4.
  A range request to that MP4 returned HTTP 206 `video/mp4`.
- The public Instagram comments endpoint redirected to the login page, and the mobile
  endpoint returned a failure body. This is why comment text is a limitation.
- `reddit.com/comments/1wza3xh.json` returned HTTP 403 with a block page.
  `reddit.com/r/Android/comments/1wza3xh/.rss` returned HTTP 200 `application/atom+xml`
  (27,825 bytes); the slugged `.rss` path returned HTTP 429. The RSS endpoint allows about
  one request per IP per minute and reports `x-ratelimit-remaining: 0.0`. A JSON 403 does
  not consume that budget.
- The debug APK manifest and signature were inspected with `aapt2` and `apksigner`:
  target SDK 36, APK Signature Scheme v2 verified.

Not checked on the device:

- The pause on headphones unplugged: `AUDIO_BECOMING_NOISY` is a protected broadcast and the
  retail build is not debuggable, so adb cannot send it. The player enables
  `setHandleAudioBecomingNoisy(true)`.
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