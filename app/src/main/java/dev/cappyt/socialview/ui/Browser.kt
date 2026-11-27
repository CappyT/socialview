package dev.cappyt.socialview.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

private const val BROWSER_PROBE_URL = "https://example.com/"

fun openInBrowser(context: Context, url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    if (uri.scheme != "http" && uri.scheme != "https") return false

    val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val actualHandlers = handlersFor(context, intent)
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse(BROWSER_PROBE_URL))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    val browserHandlers = handlersFor(context, probe)
    val browserPackages = browserHandlers.map { it.packageName }.toSet()
    val exclusions = chooserExclusions(actualHandlers, browserPackages, context.packageName)

    return when (val destination = chooseBrowserDestination(browserHandlers, context.packageName)) {
        is BrowserDestination.Direct -> {
            val direct = Intent(intent).setPackage(destination.packageName)
            start(context, direct) || startChooser(context, intent, exclusions)
        }

        is BrowserDestination.Chooser -> startChooser(context, intent, exclusions)

        BrowserDestination.None -> startChooser(context, intent, exclusions)
    }
}

/** Shares [url] as text through the system sheet, without SocialView as a target. */
fun shareLink(context: Context, url: String): Boolean {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, url)
    val own = handlersFor(context, send).filter { it.packageName == context.packageName }
    return startChooser(context, send, own)
}

private fun handlersFor(context: Context, intent: Intent): List<BrowserHandler> =
    context.packageManager
        .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .mapNotNull { resolved ->
            val activity = resolved.activityInfo ?: return@mapNotNull null
            BrowserHandler(activity.packageName, activity.name)
        }

private fun startChooser(
    context: Context,
    intent: Intent,
    excluded: List<BrowserHandler>,
): Boolean {
    val chooser = Intent.createChooser(intent, null)
    if (excluded.isNotEmpty()) {
        chooser.putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            excluded
                .distinct()
                .map { ComponentName(it.packageName, it.activityName) }
                .toTypedArray(),
        )
    }
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return start(context, chooser)
}

private fun start(context: Context, intent: Intent): Boolean =
    runCatching { context.startActivity(intent) }.isSuccess