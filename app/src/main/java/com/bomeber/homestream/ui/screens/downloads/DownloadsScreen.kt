package com.bomeber.homestream.ui.screens.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bomeber.homestream.download.DownloadEntity
import com.bomeber.homestream.download.DownloadStatus

/**
 * Step 5: replaces the Step 0 sample-data placeholder with the real
 * download queue from [DownloadsViewModel]/[com.bomeber.homestream.download.DownloadRepository].
 * [viewModel] is normally passed in from HomeStreamNavHost so Browser's
 * "Download" action and this screen share the same instance/state.
 */
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = viewModel(),
    onOpenCompleted: (DownloadEntity) -> Unit = {}
) {
    val downloads by viewModel.downloads.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Downloads", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

            if (downloads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ยังไม่มีรายการดาวน์โหลด", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(downloads, key = { it.id }) { item ->
                        DownloadListCard(
                            item = item,
                            onPause = { viewModel.onPause(item.id) },
                            onResume = { viewModel.onResume(item.id) },
                            onCancel = { viewModel.onCancel(item.id) },
                            onRetry = { viewModel.onRetry(item.id) },
                            onRemove = { viewModel.onRemove(item.id) },
                            onOpen = { onOpenCompleted(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadListCard(
    item: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusChip(item.status)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { item.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.progressPercent}%", style = MaterialTheme.typography.labelSmall)
                Text(formatBytes(item.downloadedBytes, item.totalBytes), style = MaterialTheme.typography.labelSmall)
            }
            if (item.status == DownloadStatus.FAILED && item.errorMessage != null) {
                Text(
                    item.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (item.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                        TextButton(onClick = onPause) { Text("หยุดชั่วคราว") }
                        TextButton(onClick = onCancel) { Text("ยกเลิก") }
                    }
                    DownloadStatus.PAUSED -> {
                        TextButton(onClick = onResume) { Text("ดาวน์โหลดต่อ") }
                        TextButton(onClick = onCancel) { Text("ยกเลิก") }
                    }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        TextButton(onClick = onRetry) { Text("ลองใหม่") }
                        TextButton(onClick = onRemove) { Text("ลบ") }
                    }
                    DownloadStatus.COMPLETED -> {
                        TextButton(onClick = onOpen) { Text("เปิด") }
                        TextButton(onClick = onRemove) { Text("ลบ") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: DownloadStatus) {
    val label = when (status) {
        DownloadStatus.QUEUED -> "Queued"
        DownloadStatus.DOWNLOADING -> "Downloading"
        DownloadStatus.PAUSED -> "Paused"
        DownloadStatus.COMPLETED -> "Completed"
        DownloadStatus.FAILED -> "Failed"
        DownloadStatus.CANCELLED -> "Cancelled"
    }
    AssistChip(onClick = {}, label = { Text(label) })
}

private fun formatBytes(downloaded: Long, total: Long): String {
    fun fmt(bytes: Long): String {
        if (bytes < 0) return "?"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
    }
    return if (total > 0) "${fmt(downloaded)} / ${fmt(total)}" else fmt(downloaded)
}