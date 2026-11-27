package dev.cappyt.socialview.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.cappyt.socialview.R
import dev.cappyt.socialview.core.LinkParser
import dev.cappyt.socialview.core.MediaDownload
import dev.cappyt.socialview.core.MediaDownloads
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostComment
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostFormat
import dev.cappyt.socialview.core.PostIdentity
import dev.cappyt.socialview.core.PostMedia
import dev.cappyt.socialview.core.PostMediaType

@Composable
internal fun PostCard(post: PostDetails, onOpenProfile: (String) -> Unit) {
    ContentCard {
        PostAuthor(post, onOpenProfile)
        PostMediaSection(post.media, post.initialMediaIndex, post.browserUrl, post.sensitive)

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            post.title?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.titleLarge)
            }
            post.text?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodyLarge)
            }
            PostCounts(post)

            post.images.forEach { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large),
                )
            }

            post.embed?.let { embed ->
                EmbedView(embed = embed, browserUrl = post.browserUrl)
            }

            if (post.comments.isNotEmpty()) {
                HorizontalDivider()
                Text("Comments", style = MaterialTheme.typography.titleMedium)
                post.comments.forEach { comment -> CommentRow(comment) }
            }

            post.note?.let { NoteSurface(it) }
            PlatformLimitation(post.platform)
        }
    }
}

/** The age of the post and its like and comment counts, for the platforms that publish them. */
@Composable
private fun PostCounts(post: PostDetails) {
    val age = PostFormat.relativeAge(post.createdAt)
    if (age == null && post.score == null && post.commentCount == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        age?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        post.score?.let { MetaCount(R.drawable.ic_favorite, "Likes", it.toLong()) }
        post.commentCount?.let { MetaCount(R.drawable.ic_comment, "Comments", it.toLong()) }
    }
}

/** The tonal container of a post or of a block of profile information. */
@Composable
internal fun ContentCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column { content() }
    }
}

@Composable
private fun PostAuthor(post: PostDetails, onOpenProfile: (String) -> Unit) {
    val name = PostIdentity.displayAuthor(post.author, post.handle)
    val handle = post.handle?.takeIf { it.isNotBlank() && it != post.author }
    if (name == null && handle == null) return

    val profileUrl = handle?.let { LinkParser.profileUrl(post.platform, it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (profileUrl != null) {
                    Modifier.clickable(
                        onClickLabel = "Open profile",
                        role = Role.Button,
                        onClick = { onOpenProfile(profileUrl) },
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterAvatar(name ?: handle.orEmpty())
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name ?: handle.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (name != null && handle != null) {
                Text(
                    text = handle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (profileUrl != null) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun LetterAvatar(name: String, modifier: Modifier = Modifier) {
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun NoteSurface(note: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text = note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun PlatformLimitation(platform: Platform, modifier: Modifier = Modifier) {
    Text(
        text = platform.limitation,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
internal fun PostMediaSection(
    media: List<PostMedia>,
    initialPage: Int,
    postUrl: String,
    sensitive: Boolean,
) {
    if (media.isEmpty()) return
    // Sensitive media loads and plays only after the user asks for it.
    var revealed by rememberSaveable(postUrl) { mutableStateOf(!sensitive) }
    if (!revealed) {
        SensitiveCover(
            media = media[initialPage.coerceIn(0, media.lastIndex)],
            onReveal = { revealed = true },
        )
        return
    }
    val downloads = remember(media, postUrl) { MediaDownloads.of(postUrl, media) }
    val save = rememberMediaSaver()
    when {
        media.size == 1 -> Box {
            MediaItemView(media.first(), active = true)
            downloads.single()?.let { download ->
                SaveMediaButton(
                    onClick = { save(listOf(download)) },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }

        else -> key(media) {
            val startPage = initialPage.coerceIn(0, media.lastIndex)
            val pagerState = rememberPagerState(
                initialPage = startPage,
                pageCount = { media.size },
            )
            Box {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    MediaItemView(media[page], active = pagerState.currentPage == page)
                }
                val all = downloads.filterNotNull()
                if (all.isNotEmpty()) {
                    var asking by remember { mutableStateOf(false) }
                    SaveMediaButton(
                        // One file leaves nothing to choose.
                        onClick = { if (all.size > 1) asking = true else save(all) },
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                    if (asking) {
                        SaveMediaDialog(
                            current = downloads[pagerState.currentPage],
                            all = all,
                            onSave = { chosen ->
                                asking = false
                                save(chosen)
                            },
                            onDismiss = { asking = false },
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${media.size}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SensitiveCover(media: PostMedia, onReveal: () -> Unit) {
    val preview = if (media.type == PostMediaType.VIDEO) media.thumbnailUrl else media.url
    // The blur effect exists from Android 12. An older version shows no preview at all.
    val blurred = preview != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(media.aspectRatio() ?: if (media.type == PostMediaType.VIDEO) 9f / 16f else 1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (blurred) {
            AsyncImage(
                // A small decode is enough under the blur, and it keeps the detail out of memory.
                model = ImageRequest.Builder(LocalContext.current).data(preview).size(96).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            )
        }
        CompositionLocalProvider(
            LocalContentColor provides if (blurred) Color.White else MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painter = painterResource(R.drawable.ic_visibility_off), contentDescription = null)
                Text("Sensitive content", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "The platform marks this post as NSFW.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                FilledTonalButton(onClick = onReveal) { Text("Show") }
            }
        }
    }
}

@Composable
private fun SaveMediaButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
        ),
        modifier = modifier.padding(4.dp),
    ) {
        Icon(painter = painterResource(R.drawable.ic_download), contentDescription = "Save media")
    }
}

/** Asks whether to save the item on screen or every item of the post. */
@Composable
private fun SaveMediaDialog(
    current: MediaDownload?,
    all: List<MediaDownload>,
    onSave: (List<MediaDownload>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painter = painterResource(R.drawable.ic_download), contentDescription = null) },
        title = { Text("Save media") },
        text = {
            Text(
                if (current != null) {
                    "Save the item on screen, or all ${all.size} items of this post?"
                } else {
                    "The item on screen has no file to save. Save the other ${all.size} of this post?"
                },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(all) }) { Text("All ${all.size}") } },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = { onSave(listOf(current)) }) { Text("This one") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
internal fun MediaItemView(media: PostMedia, active: Boolean) {
    when (media.type) {
        PostMediaType.IMAGE -> AsyncImage(
            model = media.url,
            contentDescription = null,
            contentScale = if (media.aspectRatio() != null) {
                ContentScale.Fit
            } else {
                ContentScale.FillWidth
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    media.aspectRatio()?.let { ratio ->
                        Modifier.aspectRatio(ratio)
                    } ?: Modifier,
                ),
        )

        PostMediaType.VIDEO -> VideoPlayer(media, active)
    }
}

@Composable
private fun VideoPlayer(media: PostMedia, active: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activeNow = rememberUpdatedState(active)
    var savedPosition by rememberSaveable(media.url) { mutableStateOf(0L) }
    val player = remember(media.url) {
        playerBuilder(context, media.headers).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(media.url))
            prepare()
            if (savedPosition > 0) seekTo(savedPosition)
        }
    }
    LaunchedEffect(player, active) {
        if (active) player.play() else player.pause()
    }
    DisposableEffect(player, lifecycleOwner) {
        var resumeOnStart = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeOnStart = player.isPlaying
                    player.pause()
                }

                Lifecycle.Event.ON_START -> if (resumeOnStart && activeNow.value) {
                    resumeOnStart = false
                    player.play()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            savedPosition = player.currentPosition
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).also { view ->
                view.player = player
                view.useController = true
            }
        },
        update = { view -> view.player = player },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(media.aspectRatio() ?: (9f / 16f)),
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun playerBuilder(context: Context, headers: Map<String, String>): ExoPlayer.Builder {
    val builder = ExoPlayer.Builder(context)
    if (headers.isEmpty()) return builder
    // The media host of some platforms checks the headers of the request.
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(headers["User-Agent"])
        .setDefaultRequestProperties(headers - "User-Agent")
    return builder.setMediaSourceFactory(
        DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)),
    )
}

private fun PostMedia.aspectRatio(): Float? {
    val width = width ?: return null
    val height = height ?: return null
    if (width <= 0 || height <= 0) return null
    return width.toFloat() / height.toFloat()
}

@Composable
private fun CommentRow(comment: PostComment) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = comment.author,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            comment.score?.let { score ->
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$score points",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(text = comment.body, style = MaterialTheme.typography.bodyMedium)
    }
}
