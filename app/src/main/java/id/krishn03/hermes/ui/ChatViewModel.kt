package id.krishn03.hermes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.krishn03.hermes.data.ApiKeyEntry
import id.krishn03.hermes.data.ChatMessage
import id.krishn03.hermes.data.Provider
import id.krishn03.hermes.data.Role
import id.krishn03.hermes.data.SettingsStore
import id.krishn03.hermes.data.UsageStat
import id.krishn03.hermes.net.LlmClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val keys: List<ApiKeyEntry> = emptyList(),
    val activeKeyId: String? = null,
    val isStreaming: Boolean = false,
    val error: String? = null,
    /** Image staged by the [+] button, sent with the next message. */
    val pendingImageBase64: String? = null,
    val pendingImageMime: String? = null,
) {
    val activeKey: ApiKeyEntry?
        get() = keys.firstOrNull { it.id == activeKeyId } ?: keys.firstOrNull()
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)

    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

    /** Per-model usage, surfaced to the Usage screen as a pie chart. */
    val usage: StateFlow<List<UsageStat>> = store.usage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            combine(store.keys, store.activeKeyId) { keys, active -> keys to active }
                .collect { (keys, active) ->
                    _ui.value = _ui.value.copy(keys = keys, activeKeyId = active)
                }
        }
        // Restore the last chat session once, on startup.
        viewModelScope.launch {
            val saved = store.chat.first()
            if (saved.isNotEmpty() && _ui.value.messages.isEmpty()) {
                _ui.value = _ui.value.copy(messages = saved)
            }
        }
    }

    fun updateActiveKey(id: String) = viewModelScope.launch { store.setActive(id) }

    /** Stages an image (base64) to attach to the next sent message. */
    fun attachImage(base64: String, mime: String) {
        _ui.value = _ui.value.copy(pendingImageBase64 = base64, pendingImageMime = mime)
    }

    fun clearPendingImage() {
        _ui.value = _ui.value.copy(pendingImageBase64 = null, pendingImageMime = null)
    }

    /** Fetches the provider's model list for the Settings auto-detect. */
    suspend fun detectModels(entry: ApiKeyEntry): List<String> = LlmClient.listModels(entry)

    fun newChat() {
        streamJob?.cancel()
        _ui.value = _ui.value.copy(
            messages = emptyList(), isStreaming = false, error = null,
            pendingImageBase64 = null, pendingImageMime = null,
        )
        viewModelScope.launch { store.saveChat(emptyList()) }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        _ui.value = _ui.value.copy(isStreaming = false)
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _ui.value.isStreaming) return
        val key = _ui.value.activeKey
        if (key == null) {
            _ui.value = _ui.value.copy(error = "Add an API key in Settings first.")
            return
        }

        val history = _ui.value.messages + ChatMessage(
            role = Role.USER,
            content = prompt,
            imageBase64 = _ui.value.pendingImageBase64,
            imageMime = _ui.value.pendingImageMime,
        )
        // Append the user message plus an empty assistant message we stream into.
        _ui.value = _ui.value.copy(
            messages = history + ChatMessage(Role.ASSISTANT, ""),
            isStreaming = true,
            error = null,
            pendingImageBase64 = null,
            pendingImageMime = null,
        )

        streamJob = viewModelScope.launch {
            var received = 0L
            try {
                LlmClient.stream(key, history) { token ->
                    received += token.length
                    val msgs = _ui.value.messages.toMutableList()
                    val last = msgs.lastOrNull() ?: return@stream
                    if (last.role == Role.ASSISTANT) {
                        msgs[msgs.lastIndex] = last.copy(content = last.content + token)
                        _ui.value = _ui.value.copy(messages = msgs)
                    }
                }
                // Tally this exchange only once it produced output.
                if (received > 0) store.recordUsage(key.model, key.provider, received)
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (received > 0) store.recordUsage(key.model, key.provider, received)
                throw e
            } catch (e: Exception) {
                val msgs = _ui.value.messages.toMutableList()
                val last = msgs.lastOrNull()
                // Drop the empty assistant placeholder on failure.
                if (last != null && last.role == Role.ASSISTANT && last.content.isEmpty()) {
                    msgs.removeAt(msgs.lastIndex)
                }
                _ui.value = _ui.value.copy(
                    messages = msgs,
                    error = e.message ?: "Request failed",
                )
            } finally {
                _ui.value = _ui.value.copy(isStreaming = false)
                store.saveChat(_ui.value.messages)
            }
        }
    }

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null)
    }

    fun clearUsage() = viewModelScope.launch { store.clearUsage() }

    // ---- Settings: key management ----

    fun saveKey(entry: ApiKeyEntry) = viewModelScope.launch {
        val list = _ui.value.keys.toMutableList()
        val idx = list.indexOfFirst { it.id == entry.id }
        if (idx >= 0) list[idx] = entry else list.add(entry)
        store.saveKeys(list)
        if (_ui.value.activeKeyId == null) store.setActive(entry.id)
    }

    fun deleteKey(id: String) = viewModelScope.launch {
        val list = _ui.value.keys.filterNot { it.id == id }
        store.saveKeys(list)
        if (_ui.value.activeKeyId == id) store.setActive(list.firstOrNull()?.id)
    }

    /** Factory for a blank key pre-filled with a provider's defaults. */
    fun blankKey(provider: Provider = Provider.OPENAI): ApiKeyEntry = ApiKeyEntry(
        id = "key_" + System.currentTimeMillis().toString(36) + "_" + (_ui.value.keys.size),
        label = "",
        provider = provider,
        key = "",
        model = provider.defaultModel(),
        baseUrl = provider.defaultBaseUrl(),
        customHeaders = emptyMap(),
    )
}
