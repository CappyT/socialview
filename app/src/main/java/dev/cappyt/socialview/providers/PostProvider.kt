package dev.cappyt.socialview.providers

import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostLink

interface PostProvider {
    val platform: Platform

    suspend fun load(link: PostLink): PostDetails
}