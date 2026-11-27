package dev.cappyt.socialview.core

enum class ProfilePostType {
    IMAGE,
    VIDEO,
    CAROUSEL,
    TEXT,
}

data class ProfilePost(
    /** The canonical post link, or null when the platform does not publish it. */
    val browserUrl: String?,
    val type: ProfilePostType,
    val thumbnailUrl: String? = null,
    /** The post text in a list, or the alternative text of a grid tile. */
    val text: String? = null,
    /** A short line above the text, for example `Pinned` or `r/Android`. */
    val label: String? = null,
    /** Epoch seconds. */
    val createdAt: Long? = null,
    val score: Long? = null,
    val commentCount: Long? = null,
    /** The platform marks the post as NSFW or sensitive. Its thumbnail stays covered. */
    val sensitive: Boolean = false,
)

data class ProfileBioLink(
    val title: String?,
    val url: String,
)

data class ProfileStat(
    val value: String,
    val label: String,
) {
    companion object {
        fun of(count: Long?, label: String): ProfileStat? =
            PostFormat.compactCount(count)?.let { ProfileStat(it.uppercase(), label) }
    }
}

enum class ProfileLayout {
    /** Media tiles, for platforms where every post has a cover image. */
    GRID,

    /** Text rows with an optional thumbnail. */
    LIST,
}

/** What the provider needs to load the next page of posts. */
data class ProfilePageRequest(
    val userId: String,
    val cursor: String,
)

data class ProfilePage(
    val posts: List<ProfilePost>,
    val next: ProfilePageRequest?,
)

data class ProfileDetails(
    val platform: Platform,
    val browserUrl: String,
    /** The account name as the platform writes it, for example `@natgeo` or `u/spez`. */
    val handle: String,
    val fullName: String? = null,
    val biography: String? = null,
    val avatarUrl: String? = null,
    val verified: Boolean = false,
    val private: Boolean = false,
    val stats: List<ProfileStat> = emptyList(),
    val links: List<ProfileBioLink> = emptyList(),
    val posts: List<ProfilePost> = emptyList(),
    val layout: ProfileLayout = ProfileLayout.GRID,
    val nextPage: ProfilePageRequest? = null,
    /** What the platform holds back from logged-out visitors, shown after the posts. */
    val note: String? = null,
)
