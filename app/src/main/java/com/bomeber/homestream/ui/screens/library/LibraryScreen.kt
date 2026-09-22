package com.bomeber.homestream.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bomeber.homestream.ui.model.LibraryVideoItem
import com.bomeber.homestream.ui.model.SampleData

/**
 * GridCells.Adaptive ทำให้จำนวนคอลัมน์ปรับเองอัตโนมัติ
 * มือถือจอแคบ = 2 คอลัมน์, tablet จอกว้าง = 4-5 คอลัมน์ ไม่ต้องเช็ค screen size เอง
 */
@Composable
fun LibraryScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Library", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        if (SampleData.libraryVideos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "ยังไม่มีวิดีโอที่ดาวน์โหลด",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(SampleData.libraryVideos) { LibraryVideoCard(it) }
            }
        }
    }
}

@Composable
private fun LibraryVideoCard(item: LibraryVideoItem) {
    ElevatedCard {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", style = MaterialTheme.typography.titleLarge)
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.durationLabel?.let { "${item.fileSizeLabel} • $it" } ?: item.fileSizeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
