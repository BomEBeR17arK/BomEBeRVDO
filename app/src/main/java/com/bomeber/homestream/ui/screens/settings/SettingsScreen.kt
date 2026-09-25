package com.bomeber.homestream.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    var wifiOnly by remember { mutableStateOf(true) }
    var autoResume by remember { mutableStateOf(true) }

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
