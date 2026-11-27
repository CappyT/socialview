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
import dev.cappyt.socialview.core.ProfileDetails
import dev.cappyt.socialview.loader.Loaded
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

    data class Profile(
        val profile: ProfileDetails,
        val more: MorePosts = MorePosts.IDLE,
    ) : ScreenState

    data class Error(val error: PostException) : ScreenState
}

/** The state of the request for the next page of profile posts. */
enum class MorePosts {
    IDLE,
    LOADING,
    FAILED,
}

/** One open request. The id tells two screens of the same link apart. */
data class StackEntry(
    val id: Int,
    val request: String,
)

class SocialViewViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val loader = PostLoader()
    private var activeJob: Job? = null
    private var requestId = 0

    /** Loaded screens of the entries in [stack], by entry id. Back does not fetch them again. */
    private val loaded = HashMap<Int, ScreenState>()

    /** True when the first request came from another app. Back then leaves SocialView. */
    private var external = savedStateHandle.get<Boolean>(EXTERNAL) ?: false

    var input by mutableStateOf(savedStateHandle.get<String>(INPUT).orEmpty())
        private set

    var state by mutableStateOf<ScreenState>(ScreenState.Idle)
        private set

    /** The open requests. The last one is on screen. An empty stack is the home screen. */
    var stack by mutableStateOf(restoredStack())
        private set

    private var nextId = (stack.maxOfOrNull { it.id } ?: 0) + 1

    val canGoBack: Boolean get() = stack.size > 1 || (stack.size == 1 && !external)

    init {
        stack.lastOrNull()?.let(::load)
    }

    private fun restoredStack(): List<StackEntry> {
        val ids = savedStateHandle.get<ArrayList<Int>>(STACK_IDS).orEmpty()
        val requests = savedStateHandle.get<ArrayList<String>>(STACK).orEmpty()
        return ids.zip(requests, ::StackEntry)
    }

    fun updateInput(value: String) {
        input = value
        savedStateHandle[INPUT] = value
    }

    /** Opens [text] as the first request. */
    fun submit(text: String, external: Boolean = false) {
        updateInput(text)
        val value = text.trim()
        if (value.isEmpty()) return
        this.external = external
        savedStateHandle[EXTERNAL] = external
        loaded.clear()
        val entry = StackEntry(nextId++, value)
        updateStack(listOf(entry))
        load(entry)
    }

    /** Opens a link from the screen content. Back returns to that content. */
    fun open(url: String) {
        val top = stack.lastOrNull()
        val below = stack.getOrNull(stack.size - 2)
        when {
            top != null && canonicalUrl(top) == url -> return
            below != null && canonicalUrl(below) == url -> back()
            else -> {
                val entry = StackEntry(nextId++, url)
                updateStack(stack + entry)
                load(entry)
            }
        }
    }

    /** The link of a loaded entry without tracking parameters, or its request before it loads. */
    private fun canonicalUrl(entry: StackEntry): String = when (val screen = loaded[entry.id]) {
        is ScreenState.Content -> screen.post.browserUrl
        is ScreenState.Profile -> screen.profile.browserUrl
        else -> entry.request
    }

    fun back() {
        val top = stack.lastOrNull() ?: return
        loaded.remove(top.id)
        updateStack(stack.dropLast(1))
        activeJob?.cancel()
        requestId++
        val previous = stack.lastOrNull()
        val known = previous?.let { loaded[it.id] }
        when {
            previous == null -> state = ScreenState.Idle
            known != null -> state = known
            else -> load(previous)
        }
    }

    fun retry() {
        stack.lastOrNull()?.let(::load)
    }

    /** Appends the next page of posts to the profile on screen. */
    fun loadMorePosts() {
        val key = stack.lastOrNull()?.id ?: return
        val current = state as? ScreenState.Profile ?: return
        if (current.profile.nextPage == null || current.more == MorePosts.LOADING) return
        val loading = current.copy(more = MorePosts.LOADING)
        replace(key, current, loading)
        viewModelScope.launch {
            val next = try {
                val page = withContext(Dispatchers.Default) { loader.loadMorePosts(current.profile) }
                // A pinned post can come back in a later page.
                val posts = (current.profile.posts + page.posts).distinctBy { it.browserUrl ?: it }
                ScreenState.Profile(
                    current.profile.copy(
                        posts = posts,
                        // A page without new posts would make the grid ask for the next one forever.
                        nextPage = page.next.takeIf { posts.size > current.profile.posts.size },
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                current.copy(more = MorePosts.FAILED)
            }
            replace(key, loading, next)
        }
    }

    /** Swaps [old] for [new] on screen and in the back cache, unless the user moved on. */
    private fun replace(key: Int, old: ScreenState, new: ScreenState) {
        if (state === old) state = new
        if (loaded[key] === old) loaded[key] = new
    }

    private fun updateStack(value: List<StackEntry>) {
        stack = value
        savedStateHandle[STACK_IDS] = ArrayList(value.map { it.id })
        savedStateHandle[STACK] = ArrayList(value.map { it.request })
    }

    private fun load(entry: StackEntry) {
        val value = entry.request
        val id = ++requestId
        activeJob?.cancel()
        state = ScreenState.Loading(LinkParser.platformHint(value))
        activeJob = viewModelScope.launch {
            val next = try {
                // Parsing a large document on Main blocks the UI. The fetcher uses IO itself.
                when (val result = withContext(Dispatchers.Default) { loader.load(value) }) {
                    is Loaded.Post -> ScreenState.Content(result.post)
                    is Loaded.Profile -> ScreenState.Profile(result.profile)
                }
            } catch (error: PostException) {
                ScreenState.Error(error)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ScreenState.Error(
                    PostException(
                        kind = ErrorKind.UNEXPECTED,
                        message = "Could not load this link.",
                        cause = error,
                    ),
                )
            }
            if (id == requestId) {
                state = next
                if (next !is ScreenState.Error) loaded[entry.id] = next
            }
        }
    }

    private companion object {
        const val INPUT = "input"
        const val STACK = "stack"
        const val STACK_IDS = "stackIds"
        const val EXTERNAL = "external"
    }
}
