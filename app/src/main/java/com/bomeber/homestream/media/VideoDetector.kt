package com.bomeber.homestream.media

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "HomeStreamDetect"

/**
 * Detects HTML5 <video>/<audio> media that the current page normally
 * exposes to the browser's DOM, and classifies each into a
 * [MediaAccessType]. This is the **DOM detection** half of Step 3's hybrid
 * pipeline — see [MediaUrlClassifier] for the **network observation** half
 * (WebViewContainer's `shouldInterceptRequest`).
 *
 * Reads nothing beyond what the page's own DOM already shows: `currentSrc`/
 * `src` on the media element, its `<source>` children, a small set of
 * common `data-*` attributes on that same element, and standard element
 * properties (`duration`, `videoWidth`/`videoHeight`, `poster`). No network
 * interception, no DRM/auth bypass.
 *
 * The classification rules inside DETECTION_SCRIPT below are a JavaScript
 * mirror of [MediaUrlClassifier.classify] — kept in sync manually, since
 * JS running inside the WebView cannot call Kotlin code directly. If you
 * change one, change the other.
 */
object VideoDetector {

    /** Name of the JavaScript interface installed on the WebView. */
    const val JS_INTERFACE_NAME = "HomeStreamMedia"

    val DETECTION_SCRIPT: String = """
        (function() {
          try {
            if (window.__homestreamInstalled) {
              return window.__homestreamScan();
            }
            window.__homestreamInstalled = true;

            function classifyAccessType(url, mime) {
              if (/^blob:/i.test(url)) return 'BLOB_MSE';
              if (!/^https?:\/\//i.test(url)) return null;
              var lowerUrl = url.split('?')[0].split('#')[0].toLowerCase();
              var lowerMime = (mime || '').toLowerCase();
              if (lowerUrl.indexOf('.ts') !== -1 && /\.ts($|[?#])/.test(lowerUrl)) return 'HLS_SEGMENT';
              if (lowerUrl.indexOf('.m4s') !== -1) return 'DASH_SEGMENT';
              if (lowerUrl.indexOf('.m3u8') !== -1 || lowerMime.indexOf('mpegurl') !== -1) return 'HLS';
              if (lowerUrl.indexOf('.mpd') !== -1 || lowerMime.indexOf('dash+xml') !== -1) return 'DASH';
              var directExt = ['.mp4', '.webm', '.mov', '.mkv', '.m4v', '.mp3', '.aac', '.ogg', '.oga', '.wav', '.flac', '.m4a'];
              for (var i = 0; i < directExt.length; i++) {
                if (lowerUrl.indexOf(directExt[i]) !== -1) return 'DIRECT_FILE';
              }
              if (lowerMime.indexOf('video/') === 0 || lowerMime.indexOf('audio/') === 0) return 'DIRECT_FILE';
              return 'UNKNOWN';
            }

            window.__homestreamScan = function() {
              var out = [];
              var seen = {};

              function push(rawUrl, mime, kind, elTitle, meta) {
                if (!rawUrl) return;
                var url;
                try { url = new URL(rawUrl, document.baseURI).href; } catch (e) { return; }
                var accessType = classifyAccessType(url, mime);
                if (accessType === null) return;
                if (seen[url]) return;
                seen[url] = true;
                out.push({
                  url: url,
                  accessType: accessType,
                  mimeType: mime || null,
                  mediaType: kind,
                  title: elTitle || document.title || null,
                  duration: (meta && typeof meta.duration === 'number' && isFinite(meta.duration)) ? meta.duration : null,
                  width: (meta && meta.width) ? meta.width : null,
                  height: (meta && meta.height) ? meta.height : null,
                  poster: (meta && meta.poster) ? meta.poster : null
                });
              }

              var medias = document.querySelectorAll('video, audio');
              for (var i = 0; i < medias.length; i++) {
                var el = medias[i];
                var kind = el.tagName.toLowerCase() === 'video' ? 'video' : 'audio';
                var elTitle = el.getAttribute('title') || el.getAttribute('aria-label') || null;
                var vw = (kind === 'video' && el.videoWidth > 0) ? el.videoWidth : null;
                var vh = (kind === 'video' && el.videoHeight > 0) ? el.videoHeight : null;
                var meta = {
                  duration: el.duration,
                  width: vw,
                  height: vh,
                  poster: kind === 'video' ? (el.getAttribute('poster') || null) : null
                };

                var cur = el.currentSrc || el.src;
                if (cur) push(cur, el.type || null, kind, elTitle, meta);

                var sources = el.querySelectorAll('source');
                for (var j = 0; j < sources.length; j++) {
                  push(sources[j].getAttribute('src'), sources[j].getAttribute('type'), kind, elTitle, meta);
                }

                var dataAttrs = ['data-src', 'data-hls-src', 'data-dash-src', 'data-manifest', 'data-video-src'];
                for (var k = 0; k < dataAttrs.length; k++) {
                  var val = el.getAttribute(dataAttrs[k]);
                  if (val) push(val, null, kind, elTitle, meta);
                }
              }
              return out;
            };

            var hsDebounce = null;
            window.__homestreamNotify = function() {
              if (hsDebounce) clearTimeout(hsDebounce);
              hsDebounce = setTimeout(function() {
                try {
                  if (window.$JS_INTERFACE_NAME && window.$JS_INTERFACE_NAME.onMediaDetected) {
                    window.$JS_INTERFACE_NAME.onMediaDetected(JSON.stringify(window.__homestreamScan()));
                  }
                } catch (e) {
                  console.error('[HomeStream] notify error: ' + (e && e.message ? e.message : e));
                }
              }, 400);
            };

            var hsObserver = new MutationObserver(function() { window.__homestreamNotify(); });
            hsObserver.observe(document.documentElement, {
              childList: true, subtree: true, attributes: true, attributeFilter: ['src']
            });

            return window.__homestreamScan();
          } catch (e) {
            console.error('[HomeStream] detection script error: ' + (e && e.message ? e.message : e));
            return [];
          }
        })();
    """.trimIndent()

    val DIAGNOSTIC_SCRIPT: String = """
        (function() {
          try {
            var video = document.querySelector('video');
            var scheme = null;
            if (video) {
              var raw = video.currentSrc || video.src || '';
              if (raw) {
                try { scheme = new URL(raw, document.baseURI).protocol; } catch (e) { scheme = 'unparseable'; }
              }
            }
            return JSON.stringify({
              iframeCount: document.querySelectorAll('iframe').length,
              videoCount: document.querySelectorAll('video').length,
              audioCount: document.querySelectorAll('audio').length,
              firstVideoSrcScheme: scheme
            });
          } catch (e) {
            return JSON.stringify({ error: String(e && e.message ? e.message : e) });
          }
        })();
    """.trimIndent()

    fun parse(json: String?, pageUrl: String): List<DetectedMedia> {
        if (json.isNullOrBlank() || json == "null") return emptyList()
        return try {
            val array = JSONArray(json)
            val result = ArrayList<DetectedMedia>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val url = obj.optString("url").takeIf { it.isNotBlank() } ?: continue
                val accessType = when (obj.optString("accessType")) {
                    "DIRECT_FILE" -> MediaAccessType.DIRECT_FILE
                    "HLS" -> MediaAccessType.HLS
                    "DASH" -> MediaAccessType.DASH
                    "HLS_SEGMENT" -> MediaAccessType.HLS_SEGMENT
                    "DASH_SEGMENT" -> MediaAccessType.DASH_SEGMENT
                    "BLOB_MSE" -> MediaAccessType.BLOB_MSE
                    else -> MediaAccessType.UNKNOWN
                }
                result += DetectedMedia(
                    url = url,
                    accessType = accessType,
                    detectionSource = MediaDetectionSource.DOM,
                    mediaType = when (obj.optString("mediaType")) {
                        "video" -> MediaKind.VIDEO
                        "audio" -> MediaKind.AUDIO
                        else -> MediaKind.UNKNOWN
                    },
                    mimeType = obj.optStringOrNull("mimeType"),
                    title = obj.optStringOrNull("title"),
                    sourcePageUrl = pageUrl,
                    metadata = MediaMetadata(
                        durationSeconds = obj.optDoubleOrNull("duration"),
                        width = obj.optIntOrNull("width"),
                        height = obj.optIntOrNull("height"),
                        posterUrl = obj.optStringOrNull("poster")
                    )
                )
            }
            result.distinctBy { it.url }
        } catch (e: Exception) {
            Log.w(TAG, "VideoDetector.parse: failed to parse JSON for $pageUrl", e)
            emptyList()
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name) else null

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null
}

class MediaDetectionJsBridge(
    private val onResult: (json: String) -> Unit
) {
    @android.webkit.JavascriptInterface
    fun onMediaDetected(json: String) {
        onResult(json)
    }
}
