package com.bomeber.homestream.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bomeber.homestream.ui.model.DownloadItem
import com.bomeber.homestream.ui.model.DownloadStatus
import com.bomeber.homestream.ui.model.SampleData
import com.bomeber.homestream.ui.navigation.Screen

@Composable
fun HomeScreen(onNavigateToTab: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("HomeStream", style = MaterialTheme.typography.headlineMedium)

        QuickActionsRow(onNavigateToTab)

        HomeSection(title = "Continue Watching") {
            if (SampleData.continueWatching.isEmpty()) {
                EmptyRowPlaceholder("ยังไม่มีวิดีโอที่ดูค้างไว้")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(SampleData.continueWatching) { item ->
                        VideoThumbCard(item.title, item.progressPercent, item.durationLabel)
                    }
                }
            }
        }

        HomeSection(title = "Recent Downloads") {
            if (SampleData.recentDownloads.isEmpty()) {
                EmptyRowPlaceholder("ยังไม่มีการดาวน์โหลด")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SampleData.recentDownloads.forEach { DownloadRow(it) }
                }
            }
        }

        HomeSection(title = "Recently Watched") {
            if (SampleData.recentlyWatched.isEmpty()) {
                EmptyRowPlaceholder("ยังไม่มีประวัติการดู")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(SampleData.recentlyWatched) { item ->
                        VideoThumbCard(item.title, 100, item.durationLabel ?: "")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QuickActionsRow(onNavigateToTab: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            label = "Open Browser",
            icon = Icons.Filled.Public,
            modifier = Modifier.weight(1f)
        ) { onNavigateToTab(Screen.Browser.route) }

        QuickActionButton(
            label = "Downloads",
            icon = Icons.Filled.CloudDownload,
            modifier = Modifier.weight(1f)
        ) { onNavigateToTab(Screen.Downloads.route) }

        QuickActionButton(
            label = "Library",
            icon = Icons.Filled.VideoLibrary,
            modifier = Modifier.weight(1f)
        ) { onNavigateToTab(Screen.Library.route) }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label)
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun EmptyRowPlaceholder(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun VideoThumbCard(title: String, progressPercent: Int, durationLabel: String) {
    ElevatedCard(modifier = Modifier.width(160.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progressPercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(durationLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem) {
    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = {
            if (item.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { item.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(item.status.name)
            }
        },
        trailingContent = { Text(item.sizeLabel, style = MaterialTheme.typography.labelSmall) }
    )
}
