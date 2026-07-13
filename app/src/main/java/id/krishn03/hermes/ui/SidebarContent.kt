package id.krishn03.hermes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.krishn03.hermes.data.ApiKeyEntry

@Composable
fun SidebarContent(
    activeModelLabel: String?,
    keys: List<ApiKeyEntry>,
    activeKeyId: String?,
    onNewChat: () -> Unit,
    onSelectKey: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDemo: (String) -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Hermes",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(16.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                label = { Text("New chat") },
                selected = false,
                onClick = onNewChat,
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "MODELS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )

            LazyColumn(Modifier.weight(1f)) {
                if (keys.isEmpty()) {
                    items(listOf(Unit)) {
                        Text(
                            text = "No API keys yet.\nOpen Settings to add one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    items(keys) { key ->
                        val selected = key.id == activeKeyId
                        NavigationDrawerItem(
                            icon = {
                                if (selected) Icon(Icons.Filled.Check, contentDescription = null)
                            },
                            label = {
                                Column {
                                    Text(
                                        text = key.label.ifBlank { key.model },
                                        maxLines = 1,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    Text(
                                        text = "${key.provider.label} · ${key.model}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            },
                            selected = selected,
                            onClick = { onSelectKey(key.id) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }

            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Hub, contentDescription = null) },
                label = { Text("MCP") },
                selected = false,
                onClick = { onDemo("MCP") },
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Memory, contentDescription = null) },
                label = { Text("Memory") },
                selected = false,
                onClick = { onDemo("Memory") },
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Extension, contentDescription = null) },
                label = { Text("Plugins") },
                selected = false,
                onClick = { onDemo("Plugins") },
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                label = { Text("Settings") },
                selected = false,
                onClick = onOpenSettings,
                modifier = Modifier.padding(bottom = 16.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        }
    }
}
