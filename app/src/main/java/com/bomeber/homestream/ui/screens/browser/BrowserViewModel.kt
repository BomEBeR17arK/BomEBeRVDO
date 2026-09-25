package com.bomeber.homestream.ui.screens.browser

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bomeber.homestream.media.DetectedMedia
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val TAG = "HomeStreamDetect"

/**
 * One-shot commands the ViewModel sends down to the real WebView instance
 * living inside [WebViewContainer]. The ViewModel never touches the WebView
 * directly (it's a View, not ViewModel-safe) — it only expresses intent.
 */
sealed interface WebViewCommand {
    data class LoadUrl(val url: String) : WebViewCommand
    data object GoBack : WebViewCommand
    data object GoForward : WebViewCommand
    data object Reload : WebViewCommand
    data object StopLoading : WebViewCommand
}

class BrowserViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState

    // Step 3 V2 — media is now detected by two independent mechanisms (DOM
    // scan and network observation, see WebViewContainer), so the two are
    // kept as separate internal sets and merged by URL into the single list
    // exposed to the UI. This replaces the old "network REPLACES the whole
    // list on every DOM rescan" behavior, which would have wiped out
    // network-only finds (e.g. from an iframe DOM detection can't see)
    // every time the MutationObserver fired again.
    private var domMedia: List<DetectedMedia> = emptyList()
    private val networkMedia = LinkedHashMap<String, DetectedMedia>() // key = url, insertion order preserved

    private val _detectedMedia = MutableStateFlow<List<DetectedMedia>>(emptyList())
    val detectedMedia: StateFlow<List<DetectedMedia>> = _detectedMedia

    /**
     * Merges [domMedia] and [networkMedia] into [_detectedMedia], de-duplicated
     * by URL. DOM entries win on conflict — they usually carry richer
     * metadata (title/duration/poster) that network observation can't see.
     */
    private fun recomputeDetectedMedia() {
        val merged = LinkedHashMap<String, DetectedMedia>()
        for (media in domMedia) merged[media.url] = media
        for ((url, media) in networkMedia) {
            if (!merged.containsKey(url)) merged[url] = media
        }
        _detectedMedia.value = merged.values.toList()
    }

    // Buffered so a button tap is never dropped even if the collector in
    // WebViewContainer is momentarily not listening (e.g. mid-recomposition).
    private val _commands = Channel<WebViewCommand>(Channel.BUFFERED)
    val commands: Flow<WebViewCommand> = _commands.receiveAsFlow()

    private fun sendCommand(command: WebViewCommand) {
        viewModelScope.launch { _commands.send(command) }
    }

    // ---- User actions from the UI ----

    fun onUrlBarTextChange(text: String) {
        _uiState.value = _uiState.value.copy(urlBarText = text)
    }

    /** Called when the user taps "Go" / presses the IME action on the URL bar. */
    fun onGoClicked() {
        val resolved = resolveInput(_uiState.value.urlBarText)
        _uiState.value = _uiState.value.copy(urlBarText = resolved, errorMessage = null)
        sendCommand(WebViewCommand.LoadUrl(resolved))
    }

    fun onBackClicked() {
        if (_uiState.value.canGoBack) sendCommand(WebViewCommand.GoBack)
    }

    fun onForwardClicked() {
        if (_uiState.value.canGoForward) sendCommand(WebViewCommand.GoForward)
    }

    /** Single button: reload while idle, stop while loading (requirement #7). */
    fun onReloadOrStopClicked() {
        if (_uiState.value.isLoading) {
            sendCommand(WebViewCommand.StopLoading)
        } else {
            sendCommand(WebViewCommand.Reload)
        }
    }

    fun onRetryClicked() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
        sendCommand(WebViewCommand.LoadUrl(_uiState.value.currentUrl))
    }

    // ---- Callbacks coming back from the real WebView ----

    fun onPageStarted(url: String) {
        _uiState.value = _uiState.value.copy(
            currentUrl = url,
            urlBarText = url,
            isLoading = true,
            loadingProgress = 0,
            errorMessage = null
        )
        // A new navigation started — drop the previous page's detected
        // media (both sources) so nothing stale lingers (Test 5 requirement).
        Log.d(TAG, "onPageStarted: clearing dom+network media — new url=$url")
        domMedia = emptyList()
        networkMedia.clear()
        _detectedMedia.value = emptyList()
    }

    fun onProgressChanged(progress: Int) {
        _uiState.value = _uiState.value.copy(loadingProgress = progress)
    }

    fun onPageFinished(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.value = _uiState.value.copy(
            currentUrl = url,
            urlBarText = url,
            pageTitle = title.orEmpty(),
            isLoading = false,
            loadingProgress = 100,
            canGoBack = canGoBack,
            canGoForward = canGoForward
        )
    }

    fun onReceivedError(message: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
    }

    /**
     * Called by WebViewContainer whenever a **DOM** detection pass (the
     * initial scan on page-finish, or a later MutationObserver-triggered
     * rescan) produces a result. Each call is a full re-scan of the page's
     * DOM, so it fully replaces [domMedia] (not additive) — but network
     * finds are kept separately and merged back in, so they survive a DOM
     * rescan.
     *
     * [pageUrl] is the page the scan actually ran against — only applied if
     * it still matches the page currently loaded, so a late result from a
     * page the user has already navigated away from can't repopulate stale
     * media.
     */
    fun onDomMediaDetected(pageUrl: String, media: List<DetectedMedia>) {
        if (pageUrl == _uiState.value.currentUrl) {
            Log.d(TAG, "onDomMediaDetected: accepted ${media.size} item(s) for pageUrl=$pageUrl")
            domMedia = media.distinctBy { it.url }
            recomputeDetectedMedia()
        } else {
            Log.w(
                TAG,
                "onDomMediaDetected: DROPPED ${media.size} item(s) — pageUrl=$pageUrl != " +
                        "currentUrl=${_uiState.value.currentUrl}"
            )
        }
    }

    /**
     * Called by WebViewContainer for each individual **network** request
     * that looked like media (see MediaUrlClassifier.looksLikeMediaCandidate).
     * Unlike [onDomMediaDetected], this is additive — one item at a time —
     * so it's stored into [networkMedia] keyed by URL rather than replacing
     * anything.
     */
    fun onNetworkMediaDetected(pageUrl: String, media: DetectedMedia) {
        if (pageUrl == _uiState.value.currentUrl) {
            networkMedia[media.url] = media
            Log.d(
                TAG,
                "onNetworkMediaDetected: accepted url=${media.url} accessType=${media.accessType} " +
                        "for pageUrl=$pageUrl (network set size=${networkMedia.size})"
            )
            recomputeDetectedMedia()
        } else {
            Log.w(
                TAG,
                "onNetworkMediaDetected: DROPPED url=${media.url} — pageUrl=$pageUrl != " +
                        "currentUrl=${_uiState.value.currentUrl}"
            )
        }
    }

    // ---- URL / search-query resolution (pure logic, no Android View deps) ----

    private fun resolveInput(rawInput: String): String {
        val input = rawInput.trim()
        if (input.isEmpty()) return _uiState.value.currentUrl

        val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(input)
        if (hasScheme) return input

        val looksLikeDomain = !input.contains(" ") &&
                Regex("^[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+(:[0-9]+)?(/.*)?$").matches(input)

        return if (looksLikeDomain) {
            "https://$input"
        } else {
            // Search engine ที่ไม่ต้องใช้ API key — เปลี่ยนเป็น DuckDuckGo ได้ถ้าต้องการ
            val encoded = java.net.URLEncoder.encode(input, "UTF-8")
            "https://www.google.com/search?q=$encoded"
        }
    }
}
