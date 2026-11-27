package dev.cappyt.socialview.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.cappyt.socialview.R
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostFormat
import dev.cappyt.socialview.core.ProfileBioLink
import dev.cappyt.socialview.core.ProfileDetails
import dev.cappyt.socialview.core.ProfileLayout
import dev.cappyt.socialview.core.ProfilePost
import dev.cappyt.socialview.core.ProfilePostType
import dev.cappyt.socialview.core.ProfileStat

@Composable
internal fun ProfileScreen(
    profile: ProfileDetails,
    more: MorePosts,
    contentPadding: PaddingValues,
    onOpenPost: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val direction = LocalLayoutDirection.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(direction) + 12.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            end = contentPadding.calculateEndPadding(direction) + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        fullWidthItem { ProfileHeader(profile, onOpenBrowser) }

        when {
            profile.private -> fullWidthItem {
                ProfileMessage(
                    icon = R.drawable.ic_lock,
                    title = "This account is private",
                    text = "Its posts are visible only to approved followers.",
                )
            }

            // An empty first page with a next page is still loading its posts.
            profile.posts.isEmpty() && profile.nextPage == null && profile.note == null -> fullWidthItem {
                ProfileMessage(
                    icon = R.drawable.ic_info,
                    title = "No public posts",
                    text = "${profile.platform.label} did not return posts for this profile.",
                )
            }
        }

        when (profile.layout) {
            ProfileLayout.GRID -> itemsIndexed(
                items = profile.posts,
                key = { index, post -> post.browserUrl ?: index },
            ) { _, post ->
                ProfilePostTile(
                    post = post,
                    // TikTok covers are vertical videos. Instagram crops its grid to 4:5.
                    aspectRatio = if (profile.platform == Platform.TIKTOK) 9f / 16f else 4f / 5f,
                    onOpenPost = onOpenPost,
                )
            }

            ProfileLayout.LIST -> itemsIndexed(
                items = profile.posts,
                key = { index, post -> post.browserUrl ?: index },
                span = { _, _ -> GridItemSpan(maxLineSpan) },
            ) { _, post ->
                ProfilePostRow(post, profile.platform, onOpenPost)
            }
        }

        val nextPage = profile.nextPage
        if (nextPage != null && more == MorePosts.FAILED) {
            fullWidthItem {
                MorePostsFailed(
                    onRetry = onLoadMore,
                    onOpenBrowser = { onOpenBrowser(profile.browserUrl) },
                )
            }
        } else if (nextPage != null) {
            // The grid composes this item when the user reaches the end of the loaded posts.
            fullWidthItem {
                LaunchedEffect(nextPage) { onLoadMore() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        profile.note?.let { note ->
            fullWidthItem {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NoteSurface(note)
                    OpenInBrowserButton(onClick = { onOpenBrowser(profile.browserUrl) })
                }
            }
        }
        fullWidthItem {
            PlatformLimitation(
                platform = profile.platform,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )
        }
    }
}

private fun LazyGridScope.fullWidthItem(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHeader(profile: ProfileDetails, onOpenBrowser: (String) -> Unit) {
    Column(
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(profile)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.fullName ?: profile.handle,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (profile.verified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_verified),
                            contentDescription = "Verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                if (profile.fullName != null) {
                    Text(
                        text = profile.handle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        ProfileStats(profile.stats)

        profile.biography?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge)
        }

        if (profile.links.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.links.forEach { link -> BioLinkChip(link, onOpenBrowser) }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(profile: ProfileDetails) {
    val modifier = Modifier
        .size(88.dp)
        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
        .padding(5.dp)
        .clip(CircleShape)
    if (profile.avatarUrl != null) {
        AsyncImage(
            model = profile.avatarUrl,
            contentDescription = "Profile picture",
            contentScale = ContentScale.Crop,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_person),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun ProfileStats(stats: List<ProfileStat>) {
    if (stats.isEmpty()) return
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(vertical = 14.dp)) {
            stats.forEach { stat ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stat.value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = stat.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun BioLinkChip(link: ProfileBioLink, onOpenBrowser: (String) -> Unit) {
    val label = link.title ?: PostFormat.hostOf(link.url) ?: link.url
    AssistChip(
        onClick = { onOpenBrowser(link.url) },
        label = { Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_link),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

@Composable
private fun ProfilePostTile(post: ProfilePost, aspectRatio: Float, onOpenPost: (String) -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(aspectRatio)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .openPost(post, onOpenPost),
    ) {
        PostThumbnail(post, contentDescription = post.text ?: "Post")
        val badge = when (post.type) {
            ProfilePostType.VIDEO -> R.drawable.ic_play to "Video"
            ProfilePostType.CAROUSEL -> R.drawable.ic_carousel to "Carousel"
            ProfilePostType.IMAGE, ProfilePostType.TEXT -> null
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(badge.first),
                    contentDescription = badge.second,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** The thumbnail of a post. A sensitive one is blurred, or left out before Android 12. */
@Composable
private fun PostThumbnail(post: ProfilePost, contentDescription: String?) {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    if (post.sensitive && !canBlur) return
    AsyncImage(
        model = post.thumbnailUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .then(if (post.sensitive) Modifier.blur(24.dp) else Modifier),
    )
}

/** A post without a public link is not a button. */
private fun Modifier.openPost(post: ProfilePost, onOpenPost: (String) -> Unit): Modifier {
    val url = post.browserUrl ?: return this
    return clickable(onClickLabel = "Open post", role = Role.Button, onClick = { onOpenPost(url) })
}

@Composable
private fun ProfilePostRow(post: ProfilePost, platform: Platform, onOpenPost: (String) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .openPost(post, onOpenPost),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val label = listOfNotNull(post.label, "NSFW".takeIf { post.sensitive }).joinToString(" · ")
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                post.text?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ProfilePostMeta(post, platform)
            }
            if (post.thumbnailUrl != null) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    PostThumbnail(post, contentDescription = null)
                    if (post.type == ProfilePostType.VIDEO) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play),
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilePostMeta(post: ProfilePost, platform: Platform) {
    val age = PostFormat.relativeAge(post.createdAt)
    if (age == null && post.score == null && post.commentCount == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        age?.let { Text(text = it, style = MaterialTheme.typography.labelMedium, color = color) }
        post.score?.let {
            if (platform == Platform.REDDIT) {
                MetaCount(R.drawable.ic_arrow_up, "Upvotes", it)
            } else {
                MetaCount(R.drawable.ic_favorite, "Likes", it)
            }
        }
        post.commentCount?.let { MetaCount(R.drawable.ic_comment, "Comments", it) }
    }
}

@Composable
internal fun MetaCount(icon: Int, description: String, count: Long) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = PostFormat.compactCount(count)?.uppercase().orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun OpenInBrowserButton(onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_open_in_new),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Open in browser")
    }
}

@Composable
private fun ProfileMessage(icon: Int, title: String, text: String) {
    ContentCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MorePostsFailed(onRetry: () -> Unit, onOpenBrowser: () -> Unit) {
    ContentCard(modifier = Modifier.padding(top = 8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "The platform did not return more posts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text("Try again") }
                OpenInBrowserButton(onClick = onOpenBrowser)
            }
        }
    }
}
