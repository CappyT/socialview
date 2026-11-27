package dev.cappyt.socialview.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaDownloadsTest {
    private fun image(url: String) = PostMedia(PostMediaType.IMAGE, url)

    @Test
    fun `a single item is named after the site and the post id`() {
        val downloads = MediaDownloads.of(
            "https://www.instagram.com/p/Df147tCiAyB/?img_index=2",
            listOf(image("https://cdn.example/v/t51/912_n.webp?stp=dst-jpg&oh=1&oe=2")),
        )

        assertEquals(
            listOf(MediaDownload("https://cdn.example/v/t51/912_n.webp?stp=dst-jpg&oh=1&oe=2", "instagram-Df147tCiAyB.webp", false)),
            downloads,
        )
    }

    @Test
    fun `carousel items are numbered in order and keep their kind`() {
        val media = listOf(
            image("https://cdn.example/a.jpg"),
            PostMedia(PostMediaType.VIDEO, "https://cdn.example/b.mp4?oe=1"),
            image("https://cdn.example/media?id=3"),
        )

        val downloads = MediaDownloads.of("https://www.reddit.com/r/aww/comments/1eigbzg/", media)

        assertEquals(
            listOf("reddit-1eigbzg-1.jpg", "reddit-1eigbzg-2.mp4", "reddit-1eigbzg-3.jpg"),
            downloads.map { it!!.fileName },
        )
        assertEquals(listOf(false, true, false), downloads.map { it!!.video })
    }

    @Test
    fun `a stream saves its file and an item without a file has no download`() {
        val stream = PostMedia(
            type = PostMediaType.VIDEO,
            url = "https://v.redd.it/x/HLSPlaylist.m3u8",
            downloadUrl = "https://packaged-media.redd.it/x/muxed.mp4?e=1",
        )
        val streamOnly = stream.copy(downloadUrl = null)
        val guarded = image("https://cdn.example/b.jpg").copy(headers = mapOf("Referer" to "https://www.tiktok.com/"))
        assertEquals(
            mapOf("Referer" to "https://www.tiktok.com/"),
            MediaDownloads.of("https://www.tiktok.com/@nasa/video/1", listOf(guarded)).single()!!.headers,
        )
        val insecure = image("http://cdn.example/a.jpg")

        val downloads = MediaDownloads.of("https://www.reddit.com/r/aww/comments/1uz79k3/", listOf(stream, streamOnly, insecure))

        assertEquals("https://packaged-media.redd.it/x/muxed.mp4?e=1", downloads[0]!!.url)
        assertEquals("reddit-1uz79k3-1.mp4", downloads[0]!!.fileName)
        assertNull(downloads[1])
        assertNull(downloads[2])
    }

    @Test
    fun `the name is safe for a link with a query id or odd characters`() {
        val photo = MediaDownloads.of("https://www.facebook.com/photo.php?fbid=123&set=a.1", listOf(image("https://cdn.example/a.png")))
        assertEquals("facebook-fbid-123-set-a-1.png", photo.single()!!.fileName)

        val watch = MediaDownloads.of("https://www.facebook.com/watch/?v=123456", listOf(image("https://cdn.example/a.jpg")))
        assertEquals("facebook-v-123456.jpg", watch.single()!!.fileName)

        val odd = MediaDownloads.of("https://www.facebook.com/a/videos/../%2e%2e/9/", listOf(image("https://cdn.example/a")))
        assertEquals("facebook-9.jpg", odd.single()!!.fileName)

        val none = MediaDownloads.of("not a link", listOf(image("https://cdn.example/a.gif")))
        assertEquals("media.gif", none.single()!!.fileName)
    }
}
