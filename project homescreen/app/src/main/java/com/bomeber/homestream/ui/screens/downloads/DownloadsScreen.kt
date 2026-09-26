package com.bomeber.homestream.ui.screens.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bomeber.homestream.download.DownloadEntity
import com.bomeber.homestream.download.DownloadState
import kotlin.math.roundToInt

/**
 * Step 5.1 — real downloads observed from [DownloadsViewModel]/
 * [com.bomeber.homestream.download.DownloadRepository]. [onPlayLocal] opens
 * a completed download in the existing PlayerScreen (DIRECT_FILE, local
 * file:// URI). Step 5.1 fix: every card now has a "ลบ" (remove) action —
 * was previously missing even though the DAO already supported it.
 */
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = viewModel(),
    onPlayLocal: (String) -> Unit = {}
) {
    val downloads by viewModel.downloads.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Downloads", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        if (downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ยังไม่มีรายการดาวน์โหลด", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(downloads, key = { it.id }) { item ->
                    DownloadCard(
                        item = item,
                        onPause = { viewModel.pause(item.id) },
                        onResume = { viewModel.resume(item.id) },
                        onCancel = { viewModel.cancel(item.id) },
                        onRetry = { viewModel.retry(item.id) },
                        onRemove = { viewModel.remove(item.id) },
                        onPlay = { item.localFilePath?.let { onPlayLocal(it) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onPlay: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(stateLabel(item.state)) })
            }
            Spacer(Modifier.height(8.dp))

            val fraction = progressFraction(item)
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    // Step 5.1 fix — now shows downloaded/total for HLS too
                    // (HlsDownloadEngine estimates totalBytes via HEAD probes
                    // before downloading), not just "% + unknown size".
                    if (item.totalBytes > 0) {
                        "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)} (${(fraction * 100).roundToInt()}%)"
                    } else {
                        "${item.completedUnits}/${item.totalUnits} segments (${(fraction * 100).roundToInt()}%)"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            } else if (item.state == DownloadState.DOWNLOADING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) // indeterminate — still probing/unknown
                Spacer(Modifier.height(4.dp))
                Text(formatBytes(item.downloadedBytes) + " (กำลังเตรียมข้อมูล...)", style = MaterialTheme.typography.labelSmall)
            }

            item.errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (item.state) {
                    DownloadState.QUEUED, DownloadState.DOWNLOADING -> TextButton(onClick = onPause) { Text("หยุดชั่วคราว") }
                    DownloadState.PAUSED -> TextButton(onClick = onResume) { Text("ดำเนินการต่อ") }
                    DownloadState.FAILED -> TextButton(onClick = onRetry) { Text("ลองใหม่") }
                    DownloadState.COMPLETED -> TextButton(onClick = onPlay) { Text("เล่น") }
                    DownloadState.CANCELLED -> {}
                }
                if (item.state != DownloadState.COMPLETED && item.state != DownloadState.CANCELLED) {
                    TextButton(onClick = onCancel) { Text("ยกเลิก") }
                }
                // Step 5.1 fix — remove button now present for every state.
                TextButton(onClick = onRemove) { Text("ลบ") }
            }
        }
    }
}

private fun progressFraction(item: DownloadEntity): Float? = when {
    item.totalBytes > 0 -> (item.downloadedBytes.toFloat() / item.totalBytes).coerceIn(0f, 1f)
    item.totalUnits > 0 -> (item.completedUnits.toFloat() / item.totalUnits).coerceIn(0f, 1f)
    else -> null
}

private fun stateLabel(state: DownloadState): String = when (state) {
    DownloadState.QUEUED -> "รอคิว"
    DownloadState.DOWNLOADING -> "กำลังดาวน์โหลด"
    DownloadState.PAUSED -> "หยุดชั่วคราว"
    DownloadState.COMPLETED -> "เสร็จสิ้น"
    DownloadState.FAILED -> "ล้มเหลว"
    DownloadState.CANCELLED -> "ยกเลิกแล้ว"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}