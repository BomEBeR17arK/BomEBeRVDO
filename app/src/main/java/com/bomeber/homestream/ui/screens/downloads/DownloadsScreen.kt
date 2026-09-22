package com.bomeber.homestream.ui.screens.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bomeber.homestream.ui.model.DownloadItem
import com.bomeber.homestream.ui.model.DownloadStatus
import com.bomeber.homestream.ui.model.SampleData

@Composable
fun DownloadsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Downloads", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        if (SampleData.allDownloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "ยังไม่มีรายการดาวน์โหลด",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SampleData.allDownloads) { DownloadListCard(it) }
            }
        }
    }
}

@Composable
private fun DownloadListCard(item: DownloadItem) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(item.status)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { item.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${item.progressPercent}%", style = MaterialTheme.typography.labelSmall)
                Text(item.sizeLabel, style = MaterialTheme.typography.labelSmall)
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
    }
    AssistChip(onClick = { /* no-op for now */ }, label = { Text(label) })
}
