package dev.cappyt.socialview

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.cappyt.socialview.ui.IncomingRequest
import dev.cappyt.socialview.ui.IntentReader
import dev.cappyt.socialview.ui.SocialViewApp
import dev.cappyt.socialview.ui.SocialViewTheme
import dev.cappyt.socialview.ui.SocialViewViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: SocialViewViewModel by viewModels()

    // The link of the last intent that reached the view model. The system recreates the activity
    // with the same intent after a rotation, and with a new one when a link arrives after the
    // process was stopped.
    private var handledRequest: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handledRequest = savedInstanceState?.getString(HANDLED_REQUEST)
        IntentReader.read(intent)?.takeIf { it.text != handledRequest }?.let(::submit)
        setContent {
            SocialViewTheme {
                SocialViewApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        IntentReader.read(intent)?.let(::submit)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(HANDLED_REQUEST, handledRequest)
    }

    private fun submit(request: IncomingRequest) {
        handledRequest = request.text
        viewModel.submit(request.text, external = true)
    }

    private companion object {
        const val HANDLED_REQUEST = "handledRequest"
    }
}
