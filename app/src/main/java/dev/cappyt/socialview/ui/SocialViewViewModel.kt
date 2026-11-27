package dev.cappyt.socialview.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cappyt.socialview.core.ErrorKind
import dev.cappyt.socialview.core.LinkParser
import dev.cappyt.socialview.core.Platform
import dev.cappyt.socialview.core.PostDetails
import dev.cappyt.socialview.core.PostException
import dev.cappyt.socialview.loader.PostLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScreenState {
    data object Idle : ScreenState

    data class Loading(val platform: Platform?) : ScreenState

    data class Content(val post: PostDetails) : ScreenState

    data class Error(val error: PostException) : ScreenState
}

class SocialViewViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val loader = PostLoader()
    private var activeJob: Job? = null
    private var requestId = 0

    var input by mutableStateOf(savedStateHandle.get<String>(INPUT).orEmpty())
        private set

    var state by mutableStateOf<ScreenState>(ScreenState.Idle)
        private set

    init {
        savedStateHandle.get<String>(LAST_REQUEST)?.let(::load)
    }

    fun updateInput(value: String) {
        input = value
        savedStateHandle[INPUT] = value
    }

    fun submit(text: String) {
        input = text
        savedStateHandle[INPUT] = text
        load(text)
    }

    private fun load(text: String) {
        val value = text.trim()
        if (value.isEmpty()) {
            activeJob?.cancel()
            requestId++
            savedStateHandle.remove<String>(LAST_REQUEST)
            state = ScreenState.Error(
                PostException(ErrorKind.UNSUPPORTED, "Paste a post link first."),
            )
            return
        }

        savedStateHandle[LAST_REQUEST] = value
        val id = ++requestId
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            state = ScreenState.Loading(LinkParser.platformHint(value))
            val next = try {
                // Parsing a large post document on Main blocks the UI. The fetcher uses IO itself.
                ScreenState.Content(withContext(Dispatchers.Default) { loader.load(value) })
            } catch (error: PostException) {
                ScreenState.Error(error)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ScreenState.Error(
                    PostException(
                        kind = ErrorKind.UNEXPECTED,
                        message = "Could not load this post.",
                        cause = error,
                    ),
                )
            }
            if (id == requestId) state = next
        }
    }

    private companion object {
        const val INPUT = "input"
        const val LAST_REQUEST = "lastRequest"
    }
}