package dev.cappyt.socialview.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cappyt.socialview.R
import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.LinkParser
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostException
import kotlinx.coroutines.launch

/** One entry of the navigation: its id (null for Home), its state and its depth in the stack. */
private data class Screen(
    val id: Int?,
    val state: ScreenState,
    val depth: Int,
)

@Composable
fun SocialViewApp(viewModel: SocialViewViewModel) {
    val stateHolder = rememberSaveableStateHolder()
    val screen = Screen(
        id = viewModel.stack.lastOrNull()?.id,
        state = viewModel.state,
        depth = viewModel.stack.size,
    )
    BackHandler(enabled = viewModel.canGoBack) { viewModel.back() }

    // A closed screen must not give its scroll position to a later screen.
    val ids = viewModel.stack.map { it.id }
    val kept = remember { mutableSetOf<Int>() }
    LaunchedEffect(ids) {
        (kept - ids.toSet()).forEach(stateHolder::removeState)
        kept.retainAll(ids.toSet())
        kept.addAll(ids)
    }

    AnimatedContent(
        targetState = screen,
        // A state change inside one request is not a navigation. The viewer cross-fades it.
        contentKey = { it.id },
        transitionSpec = {
            val direction = if (targetState.depth >= initialState.depth) 1 else -1
            (slideInHorizontally { direction * it / 8 } + fadeIn()) togetherWith
                (slideOutHorizontally { -direction * it / 8 } + fadeOut())
        },
        label = "screen",
    ) { current ->
        if (current.id == null) {
            HomeScreen(
                input = viewModel.input,
                onInputChange = viewModel::updateInput,
                onOpen = viewModel::submit,
            )
        } else {
            // Keeps the scroll position of a profile while one of its posts is open.
            stateHolder.SaveableStateProvider(current.id) {
                ViewerScreen(
                    state = current.state,
                    onBack = viewModel::back,
                    onOpen = viewModel::open,
                    onRetry = viewModel::retry,
                    onLoadMore = viewModel::loadMorePosts,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    input: String,
    onInputChange: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(56.dp))
            Text(
                text = "SocialView",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Open a public post or a profile. No login, no tracking.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))
            LinkField(value = input, onValueChange = onInputChange, onOpen = onOpen)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onOpen(input) },
                enabled = input.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Text("Open", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Works with",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Platform.entries.forEach { PlatformPill(it.label) }
            }

            Spacer(modifier = Modifier.height(24.dp))
            ContentCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HomeTip(
                        icon = R.drawable.ic_link,
                        text = "Paste a link, share one to SocialView, or open it with SocialView.",
                    )
                    HomeTip(
                        icon = R.drawable.ic_person,
                        text = "A profile link shows the profile and its latest posts.",
                    )
                    HomeTip(
                        icon = R.drawable.ic_share,
                        text = "Share sends the clean link, without tracking parameters.",
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LinkField(
    value: String,
    onValueChange: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Post or profile link") },
        leadingIcon = {
            Icon(painter = painterResource(R.drawable.ic_link), contentDescription = null)
        },
        trailingIcon = {
            if (value.isEmpty()) {
                IconButton(
                    onClick = {
                        scope.launch {
                            val text = clipboard.getClipEntry()?.clipData
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                                .orEmpty()
                            onValueChange(text)
                            if (LinkParser.findUrl(text) != null) onOpen(text)
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_paste),
                        contentDescription = "Paste and open",
                    )
                }
            } else {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Clear",
                    )
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onOpen(value) }),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    )
}

@Composable
private fun PlatformPill(label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun HomeTip(icon: Int, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerScreen(
    state: ScreenState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val title = when (state) {
        is ScreenState.Profile -> state.profile.handle
        is ScreenState.Content -> state.post.platform.label
        is ScreenState.Loading -> state.platform?.label.orEmpty()
        is ScreenState.Error -> state.error.platform?.label.orEmpty()
        ScreenState.Idle -> ""
    }
    // The canonical link of the content. The link parser removed the tracking parameters.
    val cleanUrl = when (state) {
        is ScreenState.Profile -> state.profile.browserUrl
        is ScreenState.Content -> state.post.browserUrl
        else -> null
    }
    val browserUrl = cleanUrl ?: (state as? ScreenState.Error)?.error?.browserUrl

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (cleanUrl != null) {
                        IconButton(onClick = { shareLink(context, cleanUrl) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_share),
                                contentDescription = "Share the link without tracking",
                            )
                        }
                    }
                    if (browserUrl != null) {
                        IconButton(onClick = { openInBrowser(context, browserUrl) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_open_in_new),
                                contentDescription = "Open in browser",
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        // Loading and its result cross-fade. A profile that grows by a page keeps its grid.
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "content",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                is ScreenState.Loading -> LoadingView(current.platform, padding)
                is ScreenState.Content -> ScrollingContent(padding) {
                    if (current.post.platform == Platform.REDDIT) {
                        RedditPostCard(current.post)
                    } else {
                        PostCard(current.post, onOpenProfile = onOpen)
                    }
                }

                is ScreenState.Profile -> ProfileScreen(
                    profile = current.profile,
                    more = current.more,
                    contentPadding = padding,
                    onOpenPost = onOpen,
                    onOpenBrowser = { url -> openInBrowser(context, url) },
                    onLoadMore = onLoadMore,
                )

                is ScreenState.Error -> ScrollingContent(padding) {
                    ErrorView(
                        error = current.error,
                        onRetry = onRetry,
                        onOpenBrowser = { url -> openInBrowser(context, url) },
                    )
                }

                ScreenState.Idle -> Unit
            }
        }
    }
}

/** Scrolls under the transparent navigation bar: the insets are padding inside the scroll. */
@Composable
private fun ScrollingContent(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 16.dp),
    ) {
        content()
    }
}

@Composable
private fun LoadingView(platform: Platform?, padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text(
            text = "Loading from ${platform?.label ?: "the web"}...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorView(
    error: PostException,
    onRetry: () -> Unit,
    onOpenBrowser: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_error),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Text(
            text = error.kind.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // A new request can succeed only when the failure was not about the link itself.
            if (error.kind == ErrorKind.NETWORK || error.kind == ErrorKind.UNEXPECTED) {
                TextButton(onClick = onRetry) { Text("Try again") }
            }
            error.browserUrl?.let { url ->
                FilledTonalButton(onClick = { onOpenBrowser(url) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_open_in_new),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open in browser")
                }
            }
        }
        error.platform?.let { platform ->
            PlatformLimitation(platform = platform, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
