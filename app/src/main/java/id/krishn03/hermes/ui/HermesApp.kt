package id.krishn03.hermes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.krishn03.hermes.R
import id.krishn03.hermes.data.Role
import id.krishn03.hermes.ui.theme.HermesPurple
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesApp(viewModel: ChatViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }

    if (showUsage) {
        val usage by viewModel.usage.collectAsStateWithLifecycle()
        UsageScreen(
            usage = usage,
            onBack = { showUsage = false },
            onClear = viewModel::clearUsage,
        )
        return
    }

    if (showSettings) {
        SettingsScreen(
            keys = state.keys,
            activeKeyId = state.activeKeyId,
            onBack = { showSettings = false },
            onSave = viewModel::saveKey,
            onDelete = viewModel::deleteKey,
            onSetActive = viewModel::updateActiveKey,
            newBlankKey = viewModel::blankKey,
            onOpenUsage = { showUsage = true },
            onDetectModels = viewModel::detectModels,
        )
        return
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHost.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SidebarContent(
                activeModelLabel = state.activeKey?.let { "${it.provider.label} · ${it.model}" },
                keys = state.keys,
                activeKeyId = state.activeKeyId,
                onNewChat = {
                    viewModel.newChat()
                    scope.launch { drawerState.close() }
                },
                onSelectKey = { viewModel.updateActiveKey(it) },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    showSettings = true
                },
                onDemo = { feature ->
                    scope.launch {
                        drawerState.close()
                        snackbarHost.showSnackbar("$feature — coming soon")
                    }
                },
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = state.activeKey?.model ?: "Hermes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        // Terminal — demo only.
                        IconButton(onClick = {
                            scope.launch { snackbarHost.showSnackbar("Terminal — coming soon") }
                        }) {
                            Icon(
                                Icons.Filled.Terminal,
                                contentDescription = "Terminal",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Provider switcher — fully working.
                        ProviderMenu(
                            keys = state.keys,
                            activeProvider = state.activeKey?.provider,
                            onSelectProvider = viewModel::selectProvider,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (state.messages.isEmpty()) {
                        EmptyState(hasKey = state.activeKey != null)
                    } else {
                        MessageList(
                            messages = state.messages,
                            isStreaming = state.isStreaming,
                            modelName = state.activeKey?.model ?: "Assistant",
                        )
                    }
                }
                InputBar(
                    isStreaming = state.isStreaming,
                    keys = state.keys,
                    activeKey = state.activeKey,
                    pendingImageBase64 = state.pendingImageBase64,
                    onSelectKey = viewModel::updateActiveKey,
                    onSelectModel = viewModel::selectModel,
                    onAttachImage = viewModel::attachImage,
                    onClearImage = viewModel::clearPendingImage,
                    onSend = viewModel::send,
                    onStop = viewModel::stopStreaming,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(hasKey: Boolean) {
    // Greeting sits in the upper-middle, not dead center (~32% down).
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.32f))
        Text(
            text = "✳",
            fontSize = 40.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (hasKey) "Hey there" else "Welcome to Hermes",
            fontFamily = FontFamily.Serif,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (!hasKey) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Open the menu → Settings to add an API key.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(0.68f))
    }
}

@Composable
private fun MessageList(
    messages: List<id.krishn03.hermes.data.ChatMessage>,
    isStreaming: Boolean,
    modelName: String,
) {
    val listState = rememberLazyListState()
    // Keep the latest content in view as tokens stream in.
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(messages) { msg ->
            if (msg.role == Role.USER) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.widthIn(max = 320.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            msg.imageBase64?.let { b64 ->
                                MessageImage(b64)
                                if (msg.content.isNotBlank()) Spacer(Modifier.height(8.dp))
                            }
                            if (msg.content.isNotBlank()) {
                                Text(
                                    text = msg.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            } else {
                if (msg.content.isEmpty() && isStreaming) {
                    ThinkingIndicator(modelName)
                } else {
                    Text(
                        text = msg.content,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 26.sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

/** Decodes a base64 image once and renders it as a rounded thumbnail. */
@Composable
private fun MessageImage(base64: String) {
    val bitmap = remember(base64) {
        runCatching {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "Attached image",
            modifier = Modifier
                .widthIn(max = 220.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}

/** Minimal "<model> is thinking…" — light-grey serif, gently pulsing. */
@Composable
private fun ThinkingIndicator(modelName: String) {
    val alpha by rememberInfiniteTransition(label = "think").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Text(
        text = "$modelName is thinking…",
        fontFamily = FontFamily.Serif,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputBar(
    isStreaming: Boolean,
    keys: List<id.krishn03.hermes.data.ApiKeyEntry>,
    activeKey: id.krishn03.hermes.data.ApiKeyEntry?,
    pendingImageBase64: String?,
    onSelectKey: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onAttachImage: (String, String) -> Unit,
    onClearImage: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val canSend = text.isNotBlank() || pendingImageBase64 != null
    val context = androidx.compose.ui.platform.LocalContext.current
    // System photo picker → read the image, base64-encode it, stage it.
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            onAttachImage(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP), mime)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        // Floating rounded input container.
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // Staged-image chip, removable.
                if (pendingImageBase64 != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MessageImage(pendingImageBase64)
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onClearImage, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove image",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // Text field — transparent, sits inside the container.
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Chat with Hermes…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF8C8A86),
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Action row: [+]  [Model ▾]        [send]
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // "+" — attach an image.
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp),
                    ) {
                        IconButton(onClick = { picker.launch("image/*") }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Attach image",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    ModelPill(
                        keys = keys,
                        activeKey = activeKey,
                        onSelectKey = onSelectKey,
                        onSelectModel = onSelectModel,
                    )
                    Spacer(Modifier.weight(1f))
                    // Send / stop — solid off-white circle with a dark glyph.
                    val active = isStreaming || canSend
                    Surface(
                        shape = CircleShape,
                        color = if (active) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(44.dp),
                    ) {
                        IconButton(
                            enabled = active,
                            onClick = {
                                if (isStreaming) onStop()
                                else if (canSend) {
                                    onSend(text.ifBlank { "What's in this image?" })
                                    text = ""
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (isStreaming) Icons.Filled.Stop
                                else Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (isStreaming) "Stop" else "Send",
                                tint = if (active) MaterialTheme.colorScheme.background
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Rounded pill showing the active model. The dropdown lists every model of
 *  every key — pick a model directly, or switch to another key's default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPill(
    keys: List<id.krishn03.hermes.data.ApiKeyEntry>,
    activeKey: id.krishn03.hermes.data.ApiKeyEntry?,
    onSelectKey: (String) -> Unit,
    onSelectModel: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            onClick = { if (keys.isNotEmpty()) expanded = true },
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = activeKey?.model ?: "No model",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "Choose model",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 420.dp),
        ) {
            keys.forEach { key ->
                // One header per key (its provider), then every detected model.
                Text(
                    text = key.label.ifBlank { key.provider.label },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
                )
                val models = key.models.ifEmpty { listOf(key.model) }
                models.forEach { m ->
                    val isActive = key.id == activeKey?.id && m == activeKey?.model
                    DropdownMenuItem(
                        leadingIcon = {
                            if (isActive) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        text = { Text(m, maxLines = 1) },
                        onClick = {
                            if (key.id != activeKey?.id) onSelectKey(key.id)
                            onSelectModel(m)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** Top-bar provider switcher. Lists providers that have at least one key. */
@Composable
private fun ProviderMenu(
    keys: List<id.krishn03.hermes.data.ApiKeyEntry>,
    activeProvider: id.krishn03.hermes.data.Provider?,
    onSelectProvider: (id.krishn03.hermes.data.Provider) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val available = remember(keys) { keys.map { it.provider }.distinct() }
    Box {
        IconButton(onClick = { if (available.isNotEmpty()) expanded = true }) {
            Icon(
                Icons.Filled.SwapHoriz,
                contentDescription = "Switch provider",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            available.forEach { provider ->
                DropdownMenuItem(
                    leadingIcon = {
                        if (provider == activeProvider) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    text = { Text(provider.label) },
                    onClick = {
                        onSelectProvider(provider)
                        expanded = false
                    },
                )
            }
        }
    }
}
