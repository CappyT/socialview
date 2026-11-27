package dev.cappyt.socialview

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.cappyt.socialview.ui.IntentReader
import dev.cappyt.socialview.ui.SocialViewApp
import dev.cappyt.socialview.ui.SocialViewTheme
import dev.cappyt.socialview.ui.SocialViewViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: SocialViewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            IntentReader.read(intent)?.let { viewModel.submit(it.text) }
        }
        setContent {
            SocialViewTheme {
                SocialViewApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        IntentReader.read(intent)?.let { viewModel.submit(it.text) }
    }
}