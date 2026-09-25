package com.bomeber.homestream.ui.screens.browser

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bomeber.homestream.media.DetectedMedia
import com.bomeber.homestream.media.MediaAccessType

private const val TAG = "HomeStreamDetect"

/**
 * Step 5: DetectedMediaRow now also shows a "ดาวน์โหลด" action for
 * DIRECT_FILE items (see [isDownloadable]) that calls [onPlayMedia]'s new
 * sibling [onPlayMedia]... i.e. [onDownloadMedia], wired by
 * HomeStreamNavHost to DownloadsViewModel.onDownloadMedia. Play (Step 4)
 * is completely unchanged — the two actions are independent per the
 * "PLAY vs DOWNLOAD are separate flows" rule.
 */
@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = viewModel(),
    onPlayMedia: (DetectedMedia) -> Unit = {},
    onDownloadMedia: (DetectedMedia) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val detectedMedia by viewModel.detectedMedia.collectAsState()
    var mediaPanelExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(detectedMedia) {
        Log.d(TAG, "Compose UI: detectedMedia size=${detectedMedia.size}")
    }

    Column(modifier = modifier.fillMaxSize()) {
        BrowserTopBar(
            urlBarText = uiState.urlBarText,
            isLoading = uiState.isLoading,
            canGoBack = uiState.canGoBack,
            canGoForward = uiState.canGoForward,
            onUrlBarTextChange = viewModel::onUrlBarTextChange,
            onGoClicked = viewModel::onGoClicked,
            onBackClicked = viewModel::onBackClicked,
            onForwardClicked = viewModel::onForwardClicked,
            onReloadOrStopClicked = viewModel::onReloadOrStopClicked
        )

        if (uiState.isLoading) {
            LinearProgressIndicator(
                progress = { uiState.loadingProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        DetectedMediaPanel(
            items = detectedMedia,
            expanded = mediaPanelExpanded,
            onToggle = { mediaPanelExpanded = !mediaPanelExpanded },
            onPlayMedia = onPlayMedia,
            onDownloadMedia = onDownloadMedia
        )

        Box(modifier = Modifier.fillMaxSize()) {
            WebViewContainer(
                initialUrl = uiState.currentUrl,
                commands = viewModel.commands,
                onPageStarted = viewModel::onPageStarted,
                onProgressChanged = viewModel::onProgressChanged,
                onPageFinished = viewModel::onPageFinished,
                onReceivedError = viewModel::onReceivedError,
                onDomMediaDetected = viewModel::onDomMediaDetected,
                onNetworkMediaDetected = viewModel::onNetworkMediaDetected,
                modifier = Modifier.fillMaxSize()
            )

            uiState.errorMessage?.let { message ->
                BrowserErrorOverlay(message = message, onRetry = viewModel::onRetryClicked)
            }
        }
    }
}

@Composable
private fun BrowserTopBar(
    urlBarText: String,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onUrlBarTextChange: (String) -> Unit,
    onGoClicked: () -> Unit,
    onBackClicked: () -> Unit,
    onForwardClicked: () -> Unit,
    onReloadOrStopClicked: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onBackClicked, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = onForwardClicked, enabled = canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = onReloadOrStopClicked) {
                if (isLoading) {
                    Icon(Icons.Default.Close, contentDescription = "Stop")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                }
            }
            OutlinedTextField(
                value = urlBarText,
                onValueChange = onUrlBarTextChange,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        if (urlBarText.startsWith("https://")) Icons.Default.Lock else Icons.Default.Public,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGoClicked() })
            )
        }
    }
}

private fun isPlayable(accessType: MediaAccessType): Boolean =
    accessType == MediaAccessType.DIRECT_FILE || accessType == MediaAccessType.HLS

/** Step 5 — only DIRECT_FILE is downloadable for now; HLS stays play-only (see project.md). */
private fun isDownloadable(accessType: MediaAccessType): Boolean =
    accessType == MediaAccessType.DIRECT_FILE

@Composable
private fun DetectedMediaPanel(
    items: List<DetectedMedia>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlayMedia: (DetectedMedia) -> Unit,
    onDownloadMedia: (DetectedMedia) -> Unit
) {
    Surface(tonalElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Detected Media (${items.size})", style = MaterialTheme.typography.labelLarge)
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "ย่อ" else "ขยาย"
                )
            }
            if (expanded) {
                if (items.isEmpty()) {
                    Text(
                        text = "No media detected",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                } else {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        items.forEachIndexed { index, media ->
                            DetectedMediaRow(
                                index = index,
                                media = media,
                                onPlayMedia = onPlayMedia,
                                onDownloadMedia = onDownloadMedia
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectedMediaRow(
    index: Int,
    media: DetectedMedia,
    onPlayMedia: (DetectedMedia) -> Unit,
    onDownloadMedia: (DetectedMedia) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}. ${accessTypeShortLabel(media.accessType)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Row {
                if (isPlayable(media.accessType)) {
                    TextButton(onClick = { onPlayMedia(media) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("เล่น")
                    }
                }
                if (isDownloadable(media.accessType)) {
                    TextButton(onClick = { onDownloadMedia(media) }) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("ดาวน์โหลด")
                    }
                }
            }
        }
        Text(text = media.detectionSource.name, style = MaterialTheme.typography.bodySmall)
        Text(text = media.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        media.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(text = title, style = MaterialTheme.typography.bodySmall)
        }
        val durationText = formatDuration(media.metadata.durationSeconds)
        val resolutionText = if (media.metadata.width != null && media.metadata.height != null) {
            "${media.metadata.width}×${media.metadata.height}"
        } else null
        if (durationText != null || resolutionText != null) {
            Text(
                text = listOfNotNull(durationText, resolutionText).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(text = "MIME: ${media.mimeType ?: "ไม่ทราบ"}", style = MaterialTheme.typography.bodySmall)
    }
}

private fun accessTypeShortLabel(type: MediaAccessType): String = when (type) {
    MediaAccessType.DIRECT_FILE -> "DIRECT FILE"
    MediaAccessType.HLS -> "HLS"
    MediaAccessType.DASH -> "DASH"
    MediaAccessType.HLS_SEGMENT -> "HLS SEGMENT"
    MediaAccessType.DASH_SEGMENT -> "DASH SEGMENT"
    MediaAccessType.BLOB_MSE -> "BLOB/MSE"
    MediaAccessType.UNKNOWN -> "UNKNOWN"
}

private fun formatDuration(seconds: Double?): String? {
    if (seconds == null || seconds <= 0.0 || !seconds.isFinite()) return null
    val total = seconds.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun BrowserErrorOverlay(message: String, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text("โหลดหน้าเว็บไม่สำเร็จ", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            Button(onClick = onRetry) { Text("ลองอีกครั้ง") }
        }
    }
}