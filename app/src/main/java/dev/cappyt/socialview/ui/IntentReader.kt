package dev.cappyt.socialview.ui

import android.content.Intent

data class IncomingRequest(
    val text: String,
)

object IntentReader {
    fun read(intent: Intent?): IncomingRequest? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
                ?.takeIf { it.isNotBlank() }
                ?.let(::IncomingRequest)

            Intent.ACTION_SEND -> {
                if (!intent.type.orEmpty().startsWith("text/")) return null
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::IncomingRequest)
            }

            else -> null
        }
    }
}