package dev.cappyt.socialview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostComment
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.core.PostIdentity
import dev.cappyt.socialview.core.PostMedia
import dev.cappyt.socialview.core.PostMediaType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialViewApp(viewModel: SocialViewViewModel) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SocialView") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "One public post from Instagram, Facebook, TikTok, X or Reddit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = viewModel.input,
                onValueChange = viewModel::updateInput,
                label = { Text("Post link") },
                placeholder = { Text("https://www.instagram.com/p/...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { viewModel.submit(viewModel.input) }),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { viewModel.submit(viewModel.input) },
                enabled = viewModel.input.isNotBlank() && viewModel.state !is ScreenState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("View post")
            }

            when (val current = viewModel.state) {
                is ScreenState.Idle -> IntroCard()
                is ScreenState.Loading -> LoadingCard(current.platform)
                is ScreenState.Content -> if (current.post.platform == Platform.REDDIT) {
                    RedditPostCard(current.post)
                } else {
                    PostCard(current.post)
                }
                is ScreenState.Error -> ErrorCard(
                    error = current.error,
                    onOpenBrowser = { url -> openInBrowser(context, url) },
                )
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Public posts only", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Paste a link, share a link from another app, or open a supported link " +
                    "with SocialView. Supported platforms: Instagram, Facebook, TikTok, X and Reddit.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Profiles, feeds, stories, private posts and account login are not supported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingCard(platform: Platform?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = "Loading ${platform?.label ?: "post"}...",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PostCard(post: PostDetails) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = post.platform.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { openInBrowser(context, post.browserUrl) }) {
                    Text("Open in browser")
                }
            }

            post.title?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.titleLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PostIdentity.displayAuthor(post.author, post.handle)?.let {
                    Text(text = it, style = MaterialTheme.typography.titleSmall)
                }
                val handle = post.handle?.takeIf { it.isNotBlank() && it != post.author }
                if (handle != null) {
                    if (post.author != null) Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = handle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            post.text?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }

            PostMediaSection(post.media, post.initialMediaIndex)

            post.images.forEach { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                )
            }

            post.embed?.let { embed ->
                EmbedView(embed = embed, browserUrl = post.browserUrl)
            }

            if (post.comments.isNotEmpty()) {
                HorizontalDivider()
                Text("Comments", style = MaterialTheme.typography.titleSmall)
                post.comments.forEach { comment -> CommentRow(comment) }
            }

            post.note?.let {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            HorizontalDivider()
            Text(
                text = post.platform.limitation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PostMediaSection(media: List<PostMedia>, initialPage: Int) {
    when {
        media.isEmpty() -> Unit
        media.size == 1 -> MediaItemView(media.first(), active = true)
        else -> key(media) {
            val startPage = initialPage.coerceIn(0, media.lastIndex)
            val pagerState = rememberPagerState(
                initialPage = startPage,
                pageCount = { media.size },
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    MediaItemView(media[page], active = pagerState.currentPage == page)
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${media.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
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
                )
                .clip(RoundedCornerShape(8.dp)),
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
        ExoPlayer.Builder(context).build().apply {
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
            .aspectRatio(media.aspectRatio() ?: (9f / 16f))
            .clip(RoundedCornerShape(8.dp)),
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
            Text(text = comment.author, style = MaterialTheme.typography.labelMedium)
            comment.score?.let { score ->
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$score points",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(text = comment.body, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ErrorCard(
    error: PostException,
    onOpenBrowser: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = error.kind.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            error.platform?.let { platform ->
                Text(
                    text = platform.limitation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            error.browserUrl?.let { url ->
                Button(onClick = { onOpenBrowser(url) }) {
                    Text("Open in browser")
                }
            }
        }
    }
}