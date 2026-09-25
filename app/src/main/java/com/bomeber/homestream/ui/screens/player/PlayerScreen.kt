package com.bomeber.homestream.ui.screens.player

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bomeber.homestream.media.MediaAccessType

private const val TAG = "HomeStreamPlayer"

/**
 * Step 4 — Media3/ExoPlayer playback screen.
 *
 * Reached only from BrowserScreen's "Play" action on a [MediaAccessType.DIRECT_FILE]
 * or [MediaAccessType.HLS] [com.bomeber.homestream.media.DetectedMedia] item
 * (see BrowserScreen.isPlayable). Only the media URL and access-type NAME
 * travel through navigation as plain strings — never the whole DetectedMedia
 * object (per Step 4 navigation rules).
 *
 * ExoPlayer is created with `remember(mediaUrl)` and released in
 * [DisposableEffect]'s onDispose — so it's torn down whenever this
 * composable leaves the composition (back navigation, process death via
 * config change, etc.), and rebuilt if [mediaUrl] itself ever changes.
 * Nothing here reads response bodies, bypasses DRM/auth, or does anything
 * beyond asking Media3 to play a URL the WebView itself already exposed —
 * same security boundary as Step 3's detection.
 */
@Composable
fun PlayerScreen(
    mediaUrl: String,
    accessType: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var isBuffering by remember(mediaUrl) { mutableStateOf(true) }
    var errorMessage by remember(mediaUrl) { mutableStateOf<String?>(null) }

    val exoPlayer = remember(mediaUrl) {
        runCatching {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(mediaUrl)
                    .apply {
                        // Explicit MIME hint for HLS so Media3 doesn't have to
                        // rely on sniffing the URL's file extension (which can
                        // be missing/obscured behind query params on some
                        // sites). DIRECT_FILE is left to normal extension-based
                        // resolution, matching how Step 3 classifies it.
                        if (accessType == MediaAccessType.HLS.name) {
                            setMimeType(MimeTypes.APPLICATION_M3U8)
                        }
                    }
                    .build()
                setMediaItem(mediaItem)
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        // Playback error (unsupported stream, network failure,
                        // missing HLS module, protected content the player
                        // can't normally access, etc.) — never crash the app,
                        // just surface it. Full exception goes to Logcat only.
                        Log.w(TAG, "onPlayerError: url=$mediaUrl accessType=$accessType", error)
                        errorMessage = "ไม่สามารถเล่นสื่อนี้ได้"
                    }
                })
                prepare()
                playWhenReady = true
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to create/prepare player for url=$mediaUrl accessType=$accessType", e)
        }.getOrElse {
            errorMessage = "ไม่สามารถเล่นสื่อนี้ได้"
            null
        }
    }

    // Release the ExoPlayer instance whenever this composable leaves the
    // composition — required so the Player screen never leaks decoder/
    // Activity resources when the user navigates back to Browser.
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        if (exoPlayer != null && errorMessage == null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Keep the background dark even before/without a player view so
            // the error state below doesn't flash against a blank white screen.
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {}
        }

        if (isBuffering && errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        errorMessage?.let { message ->
            PlayerErrorOverlay(message = message, onBack = onBack)
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun PlayerErrorOverlay(message: String, onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(message, style = MaterialTheme.typography.titleMedium)
            Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                Text("กลับ")
            }
        }
    }
}
