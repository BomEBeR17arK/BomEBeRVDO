package com.bomeber.homestream.ui.screens.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bomeber.homestream.media.DetectedMedia
import com.bomeber.homestream.media.MediaDetectionJsBridge
import com.bomeber.homestream.media.MediaDetectionSource
import com.bomeber.homestream.media.MediaUrlClassifier
import com.bomeber.homestream.media.VideoDetector
import kotlinx.coroutines.flow.Flow

private const val TAG = "HomeStreamDetect"

/**
 * Hosts a single real android.webkit.WebView inside Compose.
 *
 * - WebView is created once with `remember`, destroyed in
 *   DisposableEffect.onDispose (prevents leaks, handles Test 7 — rotation).
 * - Navigation history/scroll/forms are preserved across configuration
 *   changes via WebView.saveState/restoreState into a Bundle kept by
 *   rememberSaveable.
 * - This composable is the ONLY place that calls goBack()/goForward()/
 *   reload()/stopLoading()/loadUrl() — driven by [commands] from the ViewModel.
 * - Step 3: the only place media detection happens, via TWO independent
 *   mechanisms:
 *     1. **DOM detection** — evaluateJavascript on page-finish + a
 *        MutationObserver bridge for media that appears later. Only sees
 *        the WebView's main frame (evaluateJavascript's own limitation).
 *     2. **Network observation** (Step 3 V2, this session) —
 *        `shouldInterceptRequest()`, which fires for resource requests from
 *        *every* frame including iframes, letting media inside a
 *        cross-origin iframe be detected even though DOM detection can't
 *        see into it. Only ever reads the *request URL* to classify it —
 *        never reads, modifies, or blocks the request/response. Always
 *        delegates to `super.shouldInterceptRequest(view, request)` so
 *        WebView's normal loading behavior is completely unchanged.
 *   No network interception is used to bypass anything — see
 *   [MediaUrlClassifier] and [VideoDetector] for exactly what is and isn't
 *   read.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContainer(
    initialUrl: String,
    commands: Flow<WebViewCommand>,
    onPageStarted: (String) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onPageFinished: (url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onReceivedError: (String) -> Unit,
    onDomMediaDetected: (pageUrl: String, media: List<DetectedMedia>) -> Unit,
    onNetworkMediaDetected: (pageUrl: String, media: DetectedMedia) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val savedState = rememberSaveable { Bundle() }

    val currentOnPageStarted = rememberUpdatedState(onPageStarted)
    val currentOnProgressChanged = rememberUpdatedState(onProgressChanged)
    val currentOnPageFinished = rememberUpdatedState(onPageFinished)
    val currentOnReceivedError = rememberUpdatedState(onReceivedError)
    val currentOnDomMediaDetected = rememberUpdatedState(onDomMediaDetected)
    val currentOnNetworkMediaDetected = rememberUpdatedState(onNetworkMediaDetected)

    val fullscreenHelper = remember(activity) { FullscreenVideoHelper(activity) }

    // Both the JS-interface callback (MutationObserver rescans) and
    // shouldInterceptRequest() land on threads other than the UI thread —
    // hop back through this before touching Compose state.
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // shouldInterceptRequest() runs off the UI thread. Never read WebView
    // properties from that callback; keep the last page URL in thread-safe
    // state instead.
    val currentPageUrl = remember { AtomicReference<String?>(initialUrl) }

    // Prevent duplicate network callbacks (especially HLS/DASH segments)
    // from flooding the main thread and Compose state.
    val observedNetworkUrls = remember { ConcurrentHashMap.newKeySet<String>() }

    // Lifecycle guard for callbacks that may race with DisposableEffect.onDispose.
    val detectionActive = remember { AtomicBoolean(true) }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            configureSecureDefaults()

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    // Keep normal navigation inside the WebView (#16).
                    // Only hand off schemes WebView can't render itself.
                    return if (scheme == "http" || scheme == "https") {
                        false
                    } else {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        }
                        true
                    }
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    currentPageUrl.set(url)
                    observedNetworkUrls.clear()
                    currentOnPageStarted.value(url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    currentOnPageFinished.value(url, view.title, view.canGoBack(), view.canGoForward())

                    // Step 3 — DOM detection: scan for normally-exposed
                    // HTML5 <video>/<audio> media once the page has
                    // settled. Also (re-)installs the MutationObserver
                    // bridge for media added afterward. Pure DOM-read, main
                    // frame only — see network observation below for the
                    // iframe case.
                    Log.d(TAG, "onPageFinished: injecting DOM detection script — url=$url")
                    view.evaluateJavascript(VideoDetector.DETECTION_SCRIPT) { rawResult ->
                        Log.d(
                            TAG,
                            "DOM initial scan raw result (len=${rawResult?.length ?: 0}) = " +
                                    rawResult?.take(1000)
                        )
                        val items = VideoDetector.parse(rawResult, url)
                        Log.d(TAG, "DOM initial scan parsed ${items.size} item(s) for url=$url")
                        currentOnDomMediaDetected.value(url, items)
                    }

                    // TEMP DEBUG (from the HD432 troubleshooting session) —
                    // see VideoDetector.DIAGNOSTIC_SCRIPT doc. Read-only,
                    // logged only, never used for detection results.
                    view.evaluateJavascript(VideoDetector.DIAGNOSTIC_SCRIPT) { rawDiag ->
                        Log.d(TAG, "onPageFinished: diagnostic (iframe/video/audio counts) = $rawDiag")
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        currentOnReceivedError.value(error.description?.toString() ?: "โหลดหน้าเว็บไม่สำเร็จ")
                    }
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    // Never accept a bad certificate.
                    handler.cancel()
                    currentOnReceivedError.value("ใบรับรอง SSL ของเว็บไซต์นี้ไม่ถูกต้อง (โค้ด ${error.primaryError})")
                }

                // Step 3 V2 — network observation: called for resource
                // requests from EVERY frame, including iframes (main-frame
                // filtering is deliberately NOT applied — that would defeat
                // the whole point of adding this for iframe-hosted
                // players). Only ever reads request.url to classify it via
                // MediaUrlClassifier; never touches request/response
                // content, never blocks, never modifies anything, and
                // always returns the result of super's default handling so
                // WebView's normal loading is completely unaffected.
                //
                // Runs on a background thread — logging is fine directly,
                // but reporting into Compose state hops through
                // mainHandler like the JS bridge below does.
                //
                // Deliberately does NOT log request.requestHeaders (could
                // contain Authorization/Cookie) — only the URL and our own
                // classification result are logged.
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    if (detectionActive.get() &&
                        MediaUrlClassifier.looksLikeMediaCandidate(url) &&
                        observedNetworkUrls.add(url)
                    ) {
                        val result = MediaUrlClassifier.classify(url)
                        Log.d(
                            TAG,
                            "NETWORK request: url=$url mainFrame=${request.isForMainFrame} " +
                                    "classification=${result.accessType}"
                        )
                        // NOTE: `this` here is the anonymous WebViewClient object, not the
                        // WebView — must use the `view` parameter, not `this.url`.
                        // IMPORTANT: shouldInterceptRequest() is not a UI-thread
                        // callback. Do not access view.url (or any other WebView
                        // property) here. Read the thread-safe snapshot instead.
                        val pageUrl = currentPageUrl.get()
                        if (pageUrl == null) {
                            Log.w(TAG, "NETWORK: webView.url is null, dropping — url=$url")
                        } else {
                            val media = DetectedMedia(
                                url = url,
                                accessType = result.accessType,
                                detectionSource = MediaDetectionSource.NETWORK,
                                mediaType = result.mediaType,
                                mimeType = null, // response Content-Type isn't observed — see MediaUrlClassifier doc
                                sourcePageUrl = pageUrl
                            )
                            if (detectionActive.get()) {
                                mainHandler.post {
                                    if (detectionActive.get()) {
                                        currentOnNetworkMediaDetected.value(pageUrl, media)
                                    }
                                }
                            }
                        }
                    }
                    // Always defer to normal handling — never intercept the
                    // actual request/response.
                    return super.shouldInterceptRequest(view, request)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    currentOnProgressChanged.value(newProgress)
                }

                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    fullscreenHelper.show(view, callback)
                }

                override fun onHideCustomView() {
                    fullscreenHelper.hide()
                }

                // DEBUG (Step 3 troubleshooting) — forwards the page's own
                // console.log/warn/error to Logcat under a separate tag,
                // including exceptions thrown inside our injected detection
                // script (see the try/catch in VideoDetector). WebView JS
                // errors are otherwise invisible from the Android side.
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Log.d(
                        "HomeStreamDetectJS",
                        "${consoleMessage.messageLevel()}: ${consoleMessage.message()} " +
                                "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                    )
                    return false
                }
            }

            // Step 3 — DOM detection: receives rescans triggered by the
            // page's own DOM changes (MutationObserver installed by
            // VideoDetector.DETECTION_SCRIPT). Only ever passed a JSON
            // string produced by our own injected script.
            addJavascriptInterface(
                MediaDetectionJsBridge { json ->
                    Log.d(TAG, "JS bridge: onMediaDetected called, raw json (len=${json.length}) = ${json.take(1000)}")
                    mainHandler.post {
                        val pageUrl = currentPageUrl.get()
                        if (pageUrl == null) {
                            Log.w(TAG, "JS bridge: webView.url is null, dropping result")
                            return@post
                        }
                        val items = VideoDetector.parse(json, pageUrl)
                        Log.d(TAG, "JS bridge: parsed ${items.size} item(s) for pageUrl=$pageUrl")
                        currentOnDomMediaDetected.value(pageUrl, items)
                    }
                },
                VideoDetector.JS_INTERFACE_NAME
            )

            if (!savedState.isEmpty) restoreState(savedState) else loadUrl(initialUrl)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            detectionActive.set(false)
            webView.saveState(savedState)
            fullscreenHelper.hide()
            webView.removeJavascriptInterface(VideoDetector.JS_INTERFACE_NAME)
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
    }

    LaunchedEffect(webView) {
        commands.collect { command ->
            when (command) {
                is WebViewCommand.LoadUrl -> webView.loadUrl(command.url)
                WebViewCommand.GoBack -> if (webView.canGoBack()) webView.goBack()
                WebViewCommand.GoForward -> if (webView.canGoForward()) webView.goForward()
                WebViewCommand.Reload -> webView.reload()
                WebViewCommand.StopLoading -> webView.stopLoading()
            }
        }
    }

    AndroidView(factory = { webView }, modifier = modifier.fillMaxSize())
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureSecureDefaults() {
    settings.apply {
        javaScriptEnabled = true // #11
        CookieManager.getInstance().apply { // #12
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@configureSecureDefaults, true)
        }
        domStorageEnabled = true // #13
        mediaPlaybackRequiresUserGesture = false // #14/#15 HTML5 video + fullscreen
        allowFileAccess = false // security: no wide file access
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW // security: no http on https page
        loadWithOverviewMode = true
        useWideViewPort = true
        setSupportMultipleWindows(false)
        cacheMode = WebSettings.LOAD_DEFAULT
        // Pinch-to-zoom: setSupportZoom alone isn't enough — builtInZoomControls
        // is what actually enables the gesture handling. displayZoomControls=false
        // just hides the on-screen +/- buttons, pinch still works.
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * True fullscreen video (WebChromeClient.onShowCustomView). Attaches the
 * custom view directly to the Activity's decor view so it covers everything
 * including the bottom nav bar — no changes needed to MainActivity/BottomBar.
 */
class FullscreenVideoHelper(private val activity: Activity?) {

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var container: FrameLayout? = null

    fun show(view: View, callback: WebChromeClient.CustomViewCallback) {
        val act = activity ?: run { callback.onCustomViewHidden(); return }
        if (customView != null) { callback.onCustomViewHidden(); return }
        val decorView = act.window.decorView as? ViewGroup ?: return

        val frame = FrameLayout(act).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            addView(
                view,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }

        decorView.addView(frame)
        container = frame
        customView = view
        customViewCallback = callback
        hideSystemBars(act)
    }

    fun hide() {
        val act = activity ?: return
        val decorView = act.window.decorView as? ViewGroup ?: return
        val frame = container ?: return

        frame.removeAllViews()
        decorView.removeView(frame)
        showSystemBars(act)

        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        container = null
    }

    private fun hideSystemBars(activity: Activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemBars(activity: Activity) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }
}
