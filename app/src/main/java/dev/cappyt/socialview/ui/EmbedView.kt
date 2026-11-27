package dev.cappyt.socialview.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.cappyt.socialview.core.EmbedSpec

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbedView(
    embed: EmbedSpec,
    browserUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var failure by remember(embed.url) { mutableStateOf<String?>(null) }
    var navigationMessage by remember(embed.url) { mutableStateOf<String?>(null) }
    val requiredScript = embed.requiredScriptUrl

    Column(modifier = modifier.fillMaxWidth()) {
        if (failure == null) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(embed.heightDp.dp)
                    .clip(RoundedCornerShape(12.dp)),
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        tag = embed.url
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            setSupportMultipleWindows(false)
                            javaScriptCanOpenWindowsAutomatically = false
                            setGeolocationEnabled(false)
                            loadsImagesAutomatically = true
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        setBackgroundColor(Color.TRANSPARENT)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                if (!request.isForMainFrame) return false
                                val target = request.url.toString()
                                return when (
                                    val decision = EmbedNavigation.decide(
                                        target = target,
                                        embedUrl = embed.url,
                                        allowedHosts = embed.allowedHosts,
                                    )
                                ) {
                                    EmbedNavigation.Load -> false

                                    is EmbedNavigation.External -> {
                                        val opened = openInBrowser(view.context, decision.url)
                                        navigationMessage = if (opened) {
                                            "Link opened in the browser."
                                        } else {
                                            "No browser is available for this link."
                                        }
                                        true
                                    }

                                    is EmbedNavigation.Unsupported -> {
                                        navigationMessage =
                                            "This link needs another app: ${decision.url}"
                                        true
                                    }
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView,
                                detail: RenderProcessGoneDetail,
                            ): Boolean {
                                failure = if (detail.didCrash()) {
                                    "the embed process crashed"
                                } else {
                                    "the embed process was stopped by the system"
                                }
                                return true
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (!request.isForMainFrame) {
                                    if (requiredScript != null &&
                                        failure == null &&
                                        EmbedSubresource.matches(
                                            request.url.toString(),
                                            requiredScript,
                                        )
                                    ) {
                                        failure = EmbedSubresource.failureMessage()
                                    }
                                    return
                                }
                                if (failure == null) {
                                    failure = error.description?.toString().orEmpty()
                                        .ifBlank { "network error" }
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView,
                                request: WebResourceRequest,
                                errorResponse: WebResourceResponse,
                            ) {
                                if (!request.isForMainFrame) {
                                    if (requiredScript != null &&
                                        failure == null &&
                                        EmbedSubresource.matches(
                                            request.url.toString(),
                                            requiredScript,
                                        )
                                    ) {
                                        failure = EmbedSubresource.failureMessage(
                                            errorResponse.statusCode,
                                        )
                                    }
                                    return
                                }
                                if (failure == null) failure = "HTTP ${errorResponse.statusCode}"
                            }
                        }
                        loadUrl(embed.url)
                    }
                },
                update = { view ->
                    if (view.tag != embed.url) {
                        view.tag = embed.url
                        view.loadUrl(embed.url)
                    }
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
                },
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    text = "The official embed did not load: $failure",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { openInBrowser(context, browserUrl) }) {
                    Text("Open post in browser")
                }
            }
        }

        navigationMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}