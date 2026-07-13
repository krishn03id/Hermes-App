package id.krishn03.hermes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.krishn03.hermes.data.ApiKeyEntry
import id.krishn03.hermes.data.Provider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    keys: List<ApiKeyEntry>,
    activeKeyId: String?,
    onBack: () -> Unit,
    onSave: (ApiKeyEntry) -> Unit,
    onDelete: (String) -> Unit,
    onSetActive: (String) -> Unit,
    newBlankKey: (Provider) -> ApiKeyEntry,
    onOpenUsage: () -> Unit,
    onDetectModels: suspend (ApiKeyEntry) -> List<String>,
) {
    var editing by remember { mutableStateOf<ApiKeyEntry?>(null) }

    editing?.let { entry ->
        KeyEditor(
            initial = entry,
            onDismiss = { editing = null },
            onDetectModels = onDetectModels,
            onConfirm = {
                onSave(it)
                editing = null
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = newBlankKey(Provider.OPENAI) },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add key", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Usage entry — opens the per-model pie chart.
            Card(
                onClick = onOpenUsage,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.PieChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Usage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "See a per-model breakdown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "API Keys",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
            )
            if (keys.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Tap + to add your first API key.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(keys) { key ->
                        KeyCard(
                            entry = key,
                            isActive = key.id == activeKeyId,
                            onEdit = { editing = key },
                            onDelete = { onDelete(key.id) },
                            onUse = { onSetActive(key.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCard(
    entry: ApiKeyEntry,
    isActive: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUse: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.label.ifBlank { "${entry.provider.label} key" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${entry.provider.label} · ${entry.model}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = maskKey(entry.key),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entry.customHeaders.isNotEmpty()) {
                        Text(
                            text = "${entry.customHeaders.size} custom header(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = isActive,
                    onClick = onUse,
                    label = { Text(if (isActive) "Active" else "Use") },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyEditor(
    initial: ApiKeyEntry,
    onDismiss: () -> Unit,
    onDetectModels: suspend (ApiKeyEntry) -> List<String>,
    onConfirm: (ApiKeyEntry) -> Unit,
) {
    var label by remember { mutableStateOf(initial.label) }
    var provider by remember { mutableStateOf(initial.provider) }
    var key by remember { mutableStateOf(initial.key) }
    var model by remember { mutableStateOf(initial.model) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    // Custom headers as an editable list of pairs.
    var headers by remember {
        mutableStateOf(initial.customHeaders.map { it.key to it.value })
    }
    // Model auto-detect state.
    val scope = rememberCoroutineScope()
    var detecting by remember { mutableStateOf(false) }
    var detectError by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf(initial.models) }
    var modelsExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        initial.copy(
                            label = label.trim(),
                            provider = provider,
                            key = key.trim(),
                            model = model.trim().ifBlank { provider.defaultModel() },
                            baseUrl = baseUrl.trim().ifBlank { provider.defaultBaseUrl() },
                            models = models,
                            customHeaders = headers
                                .filter { it.first.isNotBlank() }
                                .associate { it.first.trim() to it.second },
                        ),
                    )
                },
                enabled = key.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("API key") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Provider", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Provider.entries.forEach { p ->
                        FilterChip(
                            selected = provider == p,
                            onClick = {
                                // Adopt the new provider's defaults if fields were untouched.
                                if (baseUrl.isBlank() || baseUrl == provider.defaultBaseUrl()) {
                                    baseUrl = p.defaultBaseUrl()
                                }
                                if (model.isBlank() || model == provider.defaultModel()) {
                                    model = p.defaultModel()
                                }
                                provider = p
                            },
                            label = { Text(p.label) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box {
                        if (detecting) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            AssistChip(
                                onClick = {
                                    detectError = null
                                    detecting = true
                                    scope.launch {
                                        val probe = initial.copy(
                                            provider = provider,
                                            key = key.trim(),
                                            baseUrl = baseUrl.trim().ifBlank { provider.defaultBaseUrl() },
                                        )
                                        val result = runCatching { onDetectModels(probe) }
                                        detecting = false
                                        result.onSuccess {
                                            models = it
                                            modelsExpanded = it.isNotEmpty()
                                            // Auto-pick the first if none set yet.
                                            if (it.isNotEmpty() && model.isBlank()) model = it.first()
                                            if (it.isEmpty()) detectError = "No models returned"
                                        }.onFailure { detectError = it.message ?: "Detect failed" }
                                    }
                                },
                                enabled = key.isNotBlank(),
                                label = { Text("Detect") },
                            )
                        }
                        DropdownMenu(
                            expanded = modelsExpanded,
                            onDismissRequest = { modelsExpanded = false },
                        ) {
                            models.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        model = m
                                        modelsExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                detectError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Custom headers", style = MaterialTheme.typography.labelMedium)
                    AssistChip(
                        onClick = { headers = headers + ("" to "") },
                        label = { Text("Add") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                }
                headers.forEachIndexed { index, (hName, hValue) ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = hName,
                            onValueChange = { nv ->
                                headers = headers.toMutableList().also { it[index] = nv to it[index].second }
                            },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(
                            value = hValue,
                            onValueChange = { vv ->
                                headers = headers.toMutableList().also { it[index] = it[index].first to vv }
                            },
                            label = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            headers = headers.toMutableList().also { it.removeAt(index) }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove header")
                        }
                    }
                }
            }
        },
    )
}

private fun maskKey(key: String): String = when {
    key.isBlank() -> "(no key)"
    key.length <= 8 -> "••••"
    else -> key.take(4) + "…" + key.takeLast(4)
}
