package com.bomeber.homestream.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bomeber.homestream.download.DownloadPrefs

@Composable
fun SettingsScreen() {
    var wifiOnly by remember { mutableStateOf(true) }
    var autoResume by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val downloadPrefs = remember { DownloadPrefs(context) }
    var maxConnections by remember { mutableStateOf(downloadPrefs.getMaxConnections()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        SettingsSectionHeader("Downloads")
        ListItem(
            headlineContent = { Text("Download location") },
            supportingContent = { Text("Internal storage / Movies") },
            modifier = Modifier.clickable { /* TODO: Step 2 - open folder picker */ }
        )
        ListItem(
            headlineContent = { Text("Wi-Fi only") },
            supportingContent = { Text("ดาวน์โหลดเฉพาะตอนต่อ Wi-Fi") },
            trailingContent = {
                Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
            }
        )
        ListItem(
            headlineContent = { Text("Auto resume") },
            supportingContent = { Text("ดาวน์โหลดต่ออัตโนมัติเมื่อกลับมาต่อเน็ต") },
            trailingContent = {
                Switch(checked = autoResume, onCheckedChange = { autoResume = it })
            }
        )

        // Step 5.1 — the only setting actually wired to a real engine: persisted
        // via DownloadPrefs (SharedPreferences), read fresh by DownloadWorker on
        // every run (Step 5.1L requirement — never hardcoded).
        SettingsSectionHeader("Maximum download connections")
        Column {
            DownloadPrefs.ALLOWED_CONNECTIONS.forEach { option ->
                val selected = maxConnections == option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, onClick = {
                            maxConnections = option
                            downloadPrefs.setMaxConnections(option)
                        })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = {
                        maxConnections = option
                        downloadPrefs.setMaxConnections(option)
                    })
                    Text("$option connection${if (option > 1) "s" else ""}")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionHeader("Player")
        ListItem(
            headlineContent = { Text("Player settings") },
            supportingContent = { Text("ความละเอียด, subtitle, buffer") },
            modifier = Modifier.clickable { /* TODO: Step 2 - open player settings */ }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}