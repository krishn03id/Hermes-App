package id.krishn03.hermes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.krishn03.hermes.data.ApiKeyEntry
import id.krishn03.hermes.data.ChatMessage
import id.krishn03.hermes.data.ChatSession
import id.krishn03.hermes.data.Provider
import id.krishn03.hermes.data.Role
import id.krishn03.hermes.data.SettingsStore
import id.krishn03.hermes.data.UsageStat
import id.krishn03.hermes.net.LlmClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    /** Saved conversations for the sidebar, newest first. */
    val sessions: List<ChatSession> = emptyList(),
    /** The session currently open; null means an unsaved fresh chat. */
    val activeSessionId: String? = null,
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
        // Keep the sidebar's session list live.
        viewModelScope.launch {
            store.sessions.collect { sessions ->
                _ui.value = _ui.value.copy(sessions = sessions)
            }
        }
        // On startup, restore the last-open session — migrating the legacy
        // single-chat blob into a session the first time if needed.
        viewModelScope.launch {
            migrateLegacyChatIfNeeded()
            val sessions = store.sessions.first()
            val activeId = store.activeSessionId.first()
            val open = sessions.firstOrNull { it.id == activeId } ?: sessions.firstOrNull()
            if (open != null && _ui.value.messages.isEmpty()) {
                _ui.value = _ui.value.copy(
                    messages = open.messages,
                    activeSessionId = open.id,
                )
                store.setActiveSession(open.id)
            }
        }
    }

    /** One-time: fold the old `chat_history` entry into a first session. */
    private suspend fun migrateLegacyChatIfNeeded() {
        if (store.sessions.first().isNotEmpty()) return
        val legacy = store.chat.first()
        if (legacy.isEmpty()) return
        val session = ChatSession(
            id = newSessionId(),
            title = titleFrom(legacy),
            messages = legacy,
            updatedAt = System.currentTimeMillis(),
        )
        store.saveSession(session)
        store.setActiveSession(session.id)
        store.saveChat(emptyList()) // clear the legacy slot
    }

    fun updateActiveKey(id: String) = viewModelScope.launch { store.setActive(id) }

    /** Switches the model on the active key (in-place, persisted). */
    fun selectModel(model: String) = viewModelScope.launch {
        val key = _ui.value.activeKey ?: return@launch
        val list = _ui.value.keys.map { if (it.id == key.id) it.copy(model = model) else it }
        store.saveKeys(list)
    }

    /** Switches to the first configured key of the given provider, if any. */
    fun selectProvider(provider: Provider) = viewModelScope.launch {
        _ui.value.keys.firstOrNull { it.provider == provider }?.let { store.setActive(it.id) }
    }

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
            activeSessionId = null,
        )
        viewModelScope.launch { store.setActiveSession(null) }
    }

    /** Opens a saved session from the sidebar. */
    fun selectSession(id: String) {
        streamJob?.cancel()
        val session = _ui.value.sessions.firstOrNull { it.id == id } ?: return
        _ui.value = _ui.value.copy(
            messages = session.messages,
            activeSessionId = session.id,
            isStreaming = false, error = null,
            pendingImageBase64 = null, pendingImageMime = null,
        )
        viewModelScope.launch { store.setActiveSession(session.id) }
    }

    fun deleteSession(id: String) = viewModelScope.launch {
        store.deleteSession(id)
        // If the open chat was the one deleted, fall back to a fresh chat.
        if (_ui.value.activeSessionId == id) {
            _ui.value = _ui.value.copy(messages = emptyList(), activeSessionId = null)
        }
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
                // NonCancellable: on Stop/newChat this coroutine is cancelled, and
                // a plain suspend save here would itself throw and drop the write.
                withContext(NonCancellable) { persistCurrentSession() }
            }
        }
    }

    /** Writes the open conversation to its session, creating one on first send. */
    private suspend fun persistCurrentSession() {
        val msgs = _ui.value.messages
        if (msgs.isEmpty()) return
        val id = _ui.value.activeSessionId ?: newSessionId().also {
            _ui.value = _ui.value.copy(activeSessionId = it)
        }
        val existing = _ui.value.sessions.firstOrNull { it.id == id }
        store.saveSession(
            ChatSession(
                id = id,
                // Keep the first title once set; otherwise derive from the chat.
                title = existing?.title?.takeIf { it.isNotBlank() } ?: titleFrom(msgs),
                messages = msgs,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        store.setActiveSession(id)
    }

    private fun newSessionId(): String =
        "chat_" + System.currentTimeMillis().toString(36) + "_" + _ui.value.sessions.size

    /** A short title from the first user message, for the sidebar. */
    private fun titleFrom(messages: List<ChatMessage>): String {
        val first = messages.firstOrNull { it.role == Role.USER }?.content?.trim().orEmpty()
        val title = first.replace(Regex("\\s+"), " ").take(40)
        return title.ifBlank { "New chat" }
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
