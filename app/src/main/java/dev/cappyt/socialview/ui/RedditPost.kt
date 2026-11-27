package dev.cappyt.socialview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cappyt.socialview.R
import dev.cappyt.socialview.core.PostComment
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostFormat
import dev.cappyt.socialview.core.PostMedia
import dev.cappyt.socialview.core.PostMediaType

@Composable
internal fun RedditPostCard(post: PostDetails) {
    ContentCard {
        Column {
            RedditHeader(post)

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                post.flair?.takeIf { it.isNotBlank() }?.let { RedditFlair(it) }
                post.title?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                post.text?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyLarge)
                }
            }

            val media = post.media.ifEmpty {
                post.images.map { PostMedia(type = PostMediaType.IMAGE, url = it) }
            }
            if (media.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                PostMediaSection(media, post.initialMediaIndex, post.browserUrl, post.sensitive)
            }

            post.linkUrl?.let {
                Spacer(modifier = Modifier.height(8.dp))
                RedditLinkCard(it)
            }

            post.embed?.let {
                Spacer(modifier = Modifier.height(8.dp))
                EmbedView(embed = it, browserUrl = post.browserUrl)
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RedditActions(post)
                RedditComments(post)
                post.note?.let { NoteSurface(it) }
                PlatformLimitation(post.platform)
            }
        }
    }
}

@Composable
private fun RedditHeader(post: PostDetails) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RedditAvatar(post.handle)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = post.handle ?: "Reddit",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val meta = listOfNotNull(
                post.author?.let { "Posted by $it" },
                PostFormat.relativeAge(post.createdAt),
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RedditAvatar(handle: String?) {
    val letter = handle
        ?.substringAfter("r/", missingDelimiterValue = handle)
        ?.firstOrNull()
        ?.uppercaseChar()
        ?: 'R'
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(38.dp),
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
private fun RedditFlair(flair: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = flair,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun RedditActions(post: PostDetails) {
    val score = post.score
    if (score == null && post.commentCount == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        score?.let {
            RedditCount(
                icon = R.drawable.ic_arrow_up,
                description = "Upvotes",
                value = PostFormat.compactCount(it) ?: "0",
            )
        }
        post.commentCount?.let {
            RedditCount(
                icon = R.drawable.ic_comment,
                description = "Comments",
                value = PostFormat.compactCount(it) ?: "0",
            )
        }
    }
}

@Composable
private fun RedditCount(icon: Int, description: String, value: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
                modifier = Modifier.size(18.dp),
            )
            Text(text = value, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RedditComments(post: PostDetails) {
    if (post.comments.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Comments", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.weight(1f))
        post.commentCount?.let {
            Text(
                text = "${PostFormat.compactCount(it)} total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    post.comments.forEach { comment ->
        RedditComment(comment = comment, opAuthor = post.author)
    }
}

@Composable
private fun RedditComment(comment: PostComment, opAuthor: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        if (comment.depth > 0) {
            Spacer(modifier = Modifier.width(((comment.depth - 1) * 14).dp))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = comment.author,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (opAuthor != null && comment.author == opAuthor) {
                    RedditBadge("OP")
                }
                if (comment.stickied) {
                    RedditBadge("Pinned")
                }
                comment.score?.let {
                    Text(
                        text = "· ${PostFormat.compactCount(it) ?: "0"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                comment.createdAt?.let {
                    Text(
                        text = "· ${PostFormat.relativeAge(it) ?: ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(text = comment.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RedditBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun RedditLinkCard(url: String) {
    val context = LocalContext.current
    val host = PostFormat.hostOf(url) ?: return
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { openInBrowser(context, url) },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_link),
                contentDescription = "Link",
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = host, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
