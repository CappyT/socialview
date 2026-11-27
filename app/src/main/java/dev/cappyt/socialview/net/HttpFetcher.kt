package dev.cappyt.socialview.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HttpResult(
    val status: Int,
    val body: String,
    val contentType: String?,
) {
    val isSuccess: Boolean get() = status in 200..299
}

interface HttpFetcher {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult

    suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResult

    suspend fun resolve(url: String, headers: Map<String, String> = emptyMap()): String
}

class UrlConnectionFetcher(
    private val timeoutMs: Int = 15_000,
    private val userAgent: String = "SocialView/0.1 (Android; public post viewer)",
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HttpFetcher {
    override suspend fun get(url: String, headers: Map<String, String>): HttpResult = withContext(dispatcher) {
        val connection = open(url, headers, followRedirects = true)
        try {
            read(connection)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): HttpResult = withContext(dispatcher) {
        val connection = open(url, headers, followRedirects = false)
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            read(connection)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun resolve(url: String, headers: Map<String, String>): String = withContext(dispatcher) {
        var current = url
        repeat(MAX_REDIRECTS) {
            val connection = open(current, headers, followRedirects = false)
            val status = connection.responseCode
            val location = if (status in 300..399) connection.getHeaderField("Location") else null
            // The body is not needed. Close the connection before following the next hop.
            connection.disconnect()
            if (status !in 300..399) return@withContext current

            val next = URL(URL(current), location ?: throw IOException("Redirect without a Location header.")).toString()
            if (!next.startsWith("https://") && !next.startsWith("http://")) {
                throw IOException("Redirect to an unsupported scheme.")
            }
            current = next.replaceFirst("http://", "https://")
        }
        throw IOException("Too many redirects.")
    }

    private fun read(connection: HttpURLConnection): HttpResult {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        return HttpResult(status = status, body = body, contentType = connection.contentType)
    }

    private fun open(
        url: String,
        headers: Map<String, String>,
        followRedirects: Boolean,
    ): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = followRedirects
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.8")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        return connection
    }

    private companion object {
        const val MAX_REDIRECTS = 6
    }
}