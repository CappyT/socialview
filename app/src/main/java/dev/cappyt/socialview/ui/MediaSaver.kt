package dev.cappyt.socialview.ui

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import dev.cappyt.socialview.core.MediaDownload
import dev.cappyt.socialview.net.BrowserHeaders

private const val FOLDER = "SocialView"

/**
 * Returns the action that saves media files with the system download manager: pictures to
 * `Pictures/SocialView`, videos to `Movies/SocialView`.
 */
@Composable
internal fun rememberMediaSaver(): (List<MediaDownload>) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<List<MediaDownload>>(emptyList()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            !granted -> toast(context, "Storage permission is needed to save media.")
            // A rotation while the system asks loses the list. Then there is nothing to report.
            pending.isNotEmpty() -> enqueue(context, pending)
        }
        pending = emptyList()
    }
    return { downloads ->
        // Android 9 and older need the storage permission for a public folder.
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pending = downloads
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            enqueue(context, downloads)
        }
    }
}

private fun enqueue(context: Context, downloads: List<MediaDownload>) {
    val manager = context.getSystemService(DownloadManager::class.java)
    val started = downloads.count { download ->
        runCatching {
            val folder = if (download.video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            val request = DownloadManager.Request(download.url.toUri())
                .setTitle(download.fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(folder, "$FOLDER/${download.fileName}")
            (mapOf("User-Agent" to BrowserHeaders.USER_AGENT) + download.headers)
                .forEach { (name, value) -> request.addRequestHeader(name, value) }
            // From Android 10 the system adds the file to the gallery by itself.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                request.allowScanningByMediaScanner()
            }
            manager.enqueue(request)
        }.isSuccess
    }
    toast(
        context,
        when {
            started == 0 -> "Could not start the download."
            started == 1 -> "Saving 1 file to the $FOLDER folder."
            else -> "Saving $started files to the $FOLDER folder."
        },
    )
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
